package com.coolskyai.modules.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.ai.entity.AiMessage;
import com.coolskyai.modules.ai.entity.ShareComment;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;
import com.coolskyai.modules.ai.mapper.ShareCommentMapper;
import com.coolskyai.modules.notify.service.NotifyService;
import com.coolskyai.util.CurrentUser;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 分享页的讨论
 *
 * 几条刻意定下的规则：
 *   1. **只允许登录后评论**。分享本身是匿名可读的，但如果评论也匿名，
 *      没有账号可挂的留言既没法追责、也没法做「我的评论」，很快就变成垃圾场。
 *   2. 复用 comment 表（target_type='SHARE'），不新建表：
 *      审核、软删除、父子结构都是现成的，后台也不用多一个入口。
 *   3. 只做两层：回复「回复」时挂到同一父级下。
 *      无限嵌套在这么窄的一栏里既显示不下，也没人真的会去读第三层。
 *   4. 昵称/头像在写入时**快照**一份：用户之后改昵称，历史评论保持当时的称呼，
 *      和文章评论的行为一致，也省掉列表查询的 join。
 *   5. 有新人留言时通知分享作者（自己评论自己不通知）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class ShareCommentService {

	/** 评论长度上限：比文章评论（250）宽一点，讨论问题时经常要贴一段现象 */
	private static final int MAX_CONTENT = 500;
	/** 每页顶楼数：默认 20，允许客户端调到 50，再大就失去分页的意义 */
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;
	/** 顶级评论的父ID */
	private static final long ROOT = -1L;

	@Autowired
	private ShareCommentMapper commentMapper;
	@Autowired
	private AiConversationMapper conversationMapper;
	@Autowired
	private RagChatService ragChatService;
	@Autowired
	private AccountService accountService;
	@Autowired
	private NotifyService notifyService;

	/**
	 * 公开读取评论（未登录也能看，但不含邮箱/IP）
	 *
	 * 分页只切「顶楼」，一页的顶楼连带它们的全部回复一起返回 ——
	 * 按条数切会把一段对话劈成两半，读起来莫名其妙。
	 *
	 * @param pageNum  从 1 开始
	 * @param pageSize 每页顶楼数，默认 20，上限 50
	 */
	public Map<String, Object> list(String token, Integer pageNum, Integer pageSize) {
		AiMessage message = ragChatService.requireShare(token);
		return buildPage(message.getId(), pageNum, pageSize);
	}

	/**
	 * 发表评论或回复
	 *
	 * @param parentId 回复的评论ID，null 或 -1 表示顶级评论
	 */
	public Map<String, Object> add(String token, String content, Long parentId, String ip) {
		User user = accountService.currentUser();
		if (user == null) {
			throw new BadRequestException("请先登录后再评论");
		}
		AiMessage message = ragChatService.requireShare(token);
		String text = content == null ? "" : content.trim();
		if (text.isEmpty()) {
			throw new BadRequestException("说点什么吧");
		}
		if (text.length() > MAX_CONTENT) {
			throw new BadRequestException("评论最多 " + MAX_CONTENT + " 个字");
		}

		long parent = parentId == null ? ROOT : parentId;
		ShareComment origin = null;
		if (parent != ROOT) {
			origin = commentMapper.findOne(parent, message.getId());
			if (origin == null) {
				throw new BadRequestException("回复的评论不存在或已删除");
			}
			// 只保留两层：回复「回复」时挂到它的父级下，避免越滚越深
			long originParent = origin.getParentCommentId() == null ? ROOT : origin.getParentCommentId();
			parent = originParent == ROOT ? origin.getId() : originParent;
		}

		ShareComment comment = new ShareComment();
		comment.setTargetId(message.getId());
		comment.setUserId(user.getId());
		comment.setNickname(nicknameOf(user));
		comment.setAvatar(avatarOf(user));
		comment.setEmail(emailOf(user));
		comment.setIp(ip);
		comment.setContent(text);
		comment.setParentCommentId(parent);
		comment.setAdminComment(CurrentUser.isOwner());
		comment.setCreateTime(new Date());
		commentMapper.insert(comment);

		notifyParticipants(message, user, text, token, origin);
		return counts(message.getId());
	}

	/**
	 * 删除自己的评论（平台所有者可以删任何一条）
	 *
	 * 软删除：内容留着，只是不再展示 —— 后台审核需要能回看。
	 */
	public Map<String, Object> delete(String token, Long commentId) {
		User user = accountService.currentUser();
		if (user == null) {
			throw new BadRequestException("请先登录");
		}
		AiMessage message = ragChatService.requireShare(token);
		ShareComment comment = commentMapper.findOne(commentId, message.getId());
		if (comment == null) {
			throw new BadRequestException("评论不存在或已删除");
		}
		boolean mine = comment.getUserId() != null && comment.getUserId().equals(user.getId());
		if (!mine && !CurrentUser.isOwner()) {
			throw new BadRequestException("只能删除自己的评论");
		}
		commentMapper.hide(commentId, message.getId());
		return counts(message.getId());
	}

	/**
	 * 通知相关的人：分享作者（新留言）与被回复者（有人回你）
	 *
	 * 同一个人只发一条；自己说给自己听不通知。
	 * 通知失败不能影响评论本身，所以整体吞掉异常 —— 留言已经写进库了。
	 */
	private void notifyParticipants(AiMessage message, User commenter, String text,
	                                String token, ShareComment origin) {
		try {
			String brief = text.length() > 60 ? text.substring(0, 60) + "…" : text;
			Long authorId = conversationMapper.findOwnerId(message.getConversationId());
			Long originUserId = origin == null ? null : origin.getUserId();
			String who = nicknameOf(commenter);

			if (authorId != null && !authorId.equals(commenter.getId())) {
				notifyService.toUser(authorId, "COMMENT", "你的问答分享收到新留言",
						who + "：" + brief, "/share/" + token, "AI_ANSWER", message.getId());
			}
			if (originUserId != null && !originUserId.equals(commenter.getId())
					&& !originUserId.equals(authorId)) {
				notifyService.toUser(originUserId, "COMMENT", "有人回复了你的留言",
						who + "：" + brief, "/share/" + token, "AI_ANSWER", message.getId());
			}
		} catch (Exception e) {
			// 通知是附属动作，失败只丢日志
		}
	}

	private Map<String, Object> buildPage(Long messageId, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

		int totalTop = commentMapper.countRoots(messageId);
		List<ShareComment> roots = commentMapper.pageRoots(messageId, (page - 1) * size, size);
		List<ShareComment> all = new ArrayList<>(roots);
		if (!roots.isEmpty()) {
			List<Long> ids = new ArrayList<>(roots.size());
			for (ShareComment root : roots) {
				ids.add(root.getId());
			}
			all.addAll(commentMapper.listReplies(messageId, ids));
		}

		List<Map<String, Object>> list = new ArrayList<>(all.size());
		for (ShareComment row : all) {
			list.add(toItem(row));
		}
		Map<String, Object> data = new LinkedHashMap<>(8);
		data.put("pageNum", page);
		data.put("pageSize", size);
		data.put("totalTop", totalTop);
		data.put("totalReplies", commentMapper.countReplies(messageId));
		data.put("hasMore", (long) page * size < totalTop);
		data.put("comments", list);
		return data;
	}

	/**
	 * 写操作后只需要新的计数：列表由前端按已加载页数重新取，
	 * 免得「第 3 页发完言只回来第 1 页」把用户已经翻到的内容吞掉
	 */
	private Map<String, Object> counts(Long messageId) {
		Map<String, Object> data = new LinkedHashMap<>(4);
		data.put("totalTop", commentMapper.countRoots(messageId));
		data.put("totalReplies", commentMapper.countReplies(messageId));
		return data;
	}

	private Map<String, Object> toItem(ShareComment row) {
		Map<String, Object> item = new LinkedHashMap<>(8);
		item.put("id", row.getId());
		item.put("userId", row.getUserId());
		item.put("nickname", row.getNickname());
		item.put("avatar", row.getAvatar());
		item.put("content", row.getContent());
		item.put("createTime", row.getCreateTime());
		item.put("parentId", row.getParentCommentId() == null ? ROOT : row.getParentCommentId());
		item.put("admin", Boolean.TRUE.equals(row.getAdminComment()));
		return item;
	}

	private String nicknameOf(User user) {
		String name = user.getNickname() == null || user.getNickname().isBlank()
				? user.getUsername() : user.getNickname();
		if (name == null || name.isBlank()) {
			name = "用户" + user.getId();
		}
		return name.length() > 20 ? name.substring(0, 20) : name;
	}

	private String avatarOf(User user) {
		return user.getAvatar() == null || user.getAvatar().isBlank() ? "/img/avatar.jpg" : user.getAvatar();
	}

	/**
	 * email 列非空，账号没填邮箱时用占位地址（不参与发信）
	 */
	private String emailOf(User user) {
		return user.getEmail() == null || user.getEmail().isBlank()
				? "user" + user.getId() + "@coolskyai.local" : user.getEmail();
	}
}
