package com.coolskyai.service.impl;

import com.coolskyai.entity.Comment;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.CommentMapper;
import com.coolskyai.model.vo.PageComment;
import com.coolskyai.service.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * 评论业务。
 *
 * <p>评论是树形结构（楼中楼），但数据库只有一张平表。这里的两个难点：
 * <ul>
 *   <li><b>组装</b>：按 parent_id 逐层取，直到取不到为止。用显式队列而非递归，
 *       避免深层回复把调用栈打穿。</li>
 *   <li><b>级联</b>：隐藏或删除一条评论时，它的所有后代都要跟着变。
 *       必须先子后父 —— 否则父记录先没了，子记录就变成孤儿。</li>
 * </ul>
 *
 * @author CoolSkyAI
 */
@Service
public class CommentServiceImpl implements CommentService {

	private final CommentMapper commentMapper;

	public CommentServiceImpl(CommentMapper commentMapper) {
		this.commentMapper = commentMapper;
	}

	@Override
	public List<Comment> getListByPageAndParentCommentId(Integer page, Long blogId, Long parentCommentId) {
		return loadReplyTree(page, blogId, parentCommentId);
	}

	/**
	 * 前台展示用的评论：树结构取出来后，把每个根节点的后代拍平成一列并按时间排序。
	 *
	 * <p>两列布局下，平铺比缩进树更好读 —— 用户不需要数缩进层级，按时间读完就行。
	 */
	@Override
	public List<PageComment> getPageCommentList(Integer page, Long blogId, Long parentCommentId) {
		List<PageComment> threads = loadPageReplyTree(page, blogId, parentCommentId);
		for (PageComment thread : threads) {
			List<PageComment> flatten = new ArrayList<>();
			flattenInOrder(thread.getReplyComments(), flatten);
			flatten.sort(Comparator.comparing(PageComment::getCreateTime));
			thread.setReplyComments(flatten);
		}
		return threads;
	}

	@Override
	public Comment getCommentById(Long id) {
		Comment comment = commentMapper.getCommentById(id);
		if (comment == null) {
			throw new PersistenceException("评论不存在");
		}
		return comment;
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateCommentPublishedById(Long commentId, Boolean published) {
		// 隐藏父评论时，子评论一起隐藏；反向（恢复显示）不级联，子评论保持各自状态
		if (Boolean.FALSE.equals(published)) {
			cascadeFromLeafToRoot(commentId,
					c -> requireAffected(commentMapper.updateCommentPublishedById(c.getId(), false), "操作失败"));
		}
		requireAffected(commentMapper.updateCommentPublishedById(commentId, published), "操作失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateCommentNoticeById(Long commentId, Boolean notice) {
		requireAffected(commentMapper.updateCommentNoticeById(commentId, notice), "操作失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteCommentById(Long commentId) {
		cascadeFromLeafToRoot(commentId,
				c -> requireAffected(commentMapper.deleteCommentById(c.getId()), "评论删除失败"));
		requireAffected(commentMapper.deleteCommentById(commentId), "评论删除失败");
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void deleteCommentsByBlogId(Long blogId) {
		commentMapper.deleteCommentsByBlogId(blogId);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void updateComment(Comment comment) {
		requireAffected(commentMapper.updateComment(comment), "评论修改失败");
	}

	@Override
	public int countByPageAndIsPublished(Integer page, Long blogId, Boolean isPublished) {
		return commentMapper.countByPageAndIsPublished(page, blogId, isPublished);
	}

	@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveComment(com.coolskyai.model.dto.Comment comment) {
		requireAffected(commentMapper.saveComment(comment), "评论失败");
	}

	// ------------------------------------------------------------------
	// 树组装
	// ------------------------------------------------------------------

	/**
	 * 广度优先逐层展开：每取出一个节点，查它的直接子节点挂上去，再把子节点入队。
	 * 队列空时整棵树就建好了，不会因为回复层级深而 StackOverflow。
	 */
	private List<Comment> loadReplyTree(Integer page, Long blogId, Long parentId) {
		List<Comment> roots = commentMapper.getListByPageAndParentCommentId(page, blogId, parentId);
		Deque<Comment> pending = new ArrayDeque<>(roots);
		while (!pending.isEmpty()) {
			Comment node = pending.poll();
			List<Comment> children = commentMapper.getListByPageAndParentCommentId(page, blogId, node.getId());
			node.setReplyComments(children);
			pending.addAll(children);
		}
		return roots;
	}

	private List<PageComment> loadPageReplyTree(Integer page, Long blogId, Long parentId) {
		List<PageComment> roots = commentMapper.getPageCommentListByPageAndParentCommentId(page, blogId, parentId);
		Deque<PageComment> pending = new ArrayDeque<>(roots);
		while (!pending.isEmpty()) {
			PageComment node = pending.poll();
			List<PageComment> children =
					commentMapper.getPageCommentListByPageAndParentCommentId(page, blogId, node.getId());
			node.setReplyComments(children);
			pending.addAll(children);
		}
		return roots;
	}

	/**
	 * 前序平铺：先放自己，再放子孙。用栈模拟递归，逆序压栈保证取出顺序与递归一致。
	 */
	private void flattenInOrder(List<PageComment> nodes, List<PageComment> sink) {
		if (nodes == null || nodes.isEmpty()) {
			return;
		}
		Deque<PageComment> stack = new ArrayDeque<>();
		pushReversed(stack, nodes);
		while (!stack.isEmpty()) {
			PageComment node = stack.pop();
			sink.add(node);
			pushReversed(stack, node.getReplyComments());
		}
	}

	private void pushReversed(Deque<PageComment> stack, List<PageComment> nodes) {
		if (nodes == null) {
			return;
		}
		for (int i = nodes.size() - 1; i >= 0; i--) {
			stack.push(nodes.get(i));
		}
	}

	// ------------------------------------------------------------------
	// 级联
	// ------------------------------------------------------------------

	/**
	 * 对指定评论的所有后代执行操作，**自底向上**。
	 *
	 * <p>做法：先自顶向下收集全部后代，再整体倒序 —— 倒序后任何一个节点都排在它的父节点之前，
	 * 满足「先子后父」，删除时不会留下孤儿记录，隐藏时也不会出现父隐藏子可见。
	 *
	 * <p>注意不包含传入的 commentId 本身，调用方自己处理根节点。
	 */
	private void cascadeFromLeafToRoot(Long rootId, Consumer<Comment> action) {
		List<Comment> collected = new ArrayList<>();
		Deque<Comment> stack = new ArrayDeque<>(commentMapper.getListByParentCommentId(rootId));
		while (!stack.isEmpty()) {
			Comment node = stack.pop();
			collected.add(node);
			stack.addAll(commentMapper.getListByParentCommentId(node.getId()));
		}
		Collections.reverse(collected);
		collected.forEach(action);
	}

	private void requireAffected(int rows, String message) {
		if (rows != 1) {
			throw new PersistenceException(message);
		}
	}
}
