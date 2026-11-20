package com.coolskyai.service.impl;

import com.coolskyai.entity.Comment;
import com.coolskyai.mapper.CommentMapper;
import com.coolskyai.model.vo.PageComment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * 评论树的两条不变量：
 * <ol>
 *   <li>前台展示时，每个楼层的回复被拍平成一列并按时间升序</li>
 *   <li>级联删除/隐藏必须**先子后父**，否则会留下孤儿记录</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

	@Mock
	private CommentMapper commentMapper;

	@InjectMocks
	private CommentServiceImpl commentService;

	@Test
	@DisplayName("多层回复被拍平成一条时间线，孙评论也包含在内")
	void flattensRepliesIntoOneTimeline() {
		// A(98) -> B(99), C(100)；B -> D(101)
		PageComment a = pageNode(98, 1_000);
		PageComment b = pageNode(99, 3_000);
		PageComment c = pageNode(100, 2_000);
		PageComment d = pageNode(101, 1_500);

		when(commentMapper.getPageCommentListByPageAndParentCommentId(0, 1L, -1L)).thenReturn(List.of(a));
		when(commentMapper.getPageCommentListByPageAndParentCommentId(0, 1L, 98L)).thenReturn(List.of(b, c));
		when(commentMapper.getPageCommentListByPageAndParentCommentId(0, 1L, 99L)).thenReturn(List.of(d));

		List<PageComment> threads = commentService.getPageCommentList(0, 1L, -1L);

		assertThat(threads).hasSize(1);
		// 按 createTime 升序：D(1500) < C(2000) < B(3000)，与树的层级无关
		assertThat(threads.get(0).getReplyComments())
				.extracting(PageComment::getId)
				.containsExactly(101L, 100L, 99L);
	}

	@Test
	@DisplayName("删除父评论时先删子孙再删自己")
	void deletesDescendantsBeforeRoot() {
		Comment b = entityNode(99L);
		Comment d = entityNode(101L);

		when(commentMapper.getListByParentCommentId(98L)).thenReturn(List.of(b));
		when(commentMapper.getListByParentCommentId(99L)).thenReturn(List.of(d));
		when(commentMapper.deleteCommentById(anyLong())).thenReturn(1);

		commentService.deleteCommentById(98L);

		InOrder order = inOrder(commentMapper);
		order.verify(commentMapper).deleteCommentById(101L);
		order.verify(commentMapper).deleteCommentById(99L);
		order.verify(commentMapper).deleteCommentById(98L);
	}

	@Test
	@DisplayName("隐藏父评论时子孙一并隐藏，且先子后父")
	void hidesDescendantsBeforeRoot() {
		Comment b = entityNode(99L);
		Comment d = entityNode(101L);

		when(commentMapper.getListByParentCommentId(98L)).thenReturn(List.of(b));
		when(commentMapper.getListByParentCommentId(99L)).thenReturn(List.of(d));
		when(commentMapper.updateCommentPublishedById(anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
				.thenReturn(1);

		commentService.updateCommentPublishedById(98L, false);

		InOrder order = inOrder(commentMapper);
		order.verify(commentMapper).updateCommentPublishedById(101L, false);
		order.verify(commentMapper).updateCommentPublishedById(99L, false);
		order.verify(commentMapper).updateCommentPublishedById(98L, false);
	}

	@Test
	@DisplayName("恢复显示不级联：子评论保持各自状态")
	void publishingDoesNotCascade() {
		when(commentMapper.updateCommentPublishedById(98L, true)).thenReturn(1);

		commentService.updateCommentPublishedById(98L, true);

		// 只更新自己，不去查子评论
		org.mockito.Mockito.verify(commentMapper, org.mockito.Mockito.never()).getListByParentCommentId(anyLong());
	}

	private PageComment pageNode(long id, long timeMillis) {
		PageComment node = new PageComment();
		node.setId(id);
		node.setContent("c" + id);
		node.setCreateTime(new Date(timeMillis));
		return node;
	}

	private Comment entityNode(long id) {
		Comment node = new Comment();
		node.setId(id);
		return node;
	}
}
