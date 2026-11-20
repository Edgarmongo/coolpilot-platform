package com.coolskyai.modules.community.service;

import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.community.entity.Message;
import com.coolskyai.modules.community.mapper.MessageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内消息。
 *
 * <p>消息是**被动产生**的：订单状态变化、需求报价、权益到账时由其他模块调用 {@link #send}，
 * 没有独立的发送入口。所以这里只做三件事：写入、翻页、标记已读。
 *
 * @author CoolSkyAI
 */
@Service
public class MessageService {

	private static final String DEFAULT_TYPE = "SYSTEM";
	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 50;

	private final MessageMapper messageMapper;

	public MessageService(MessageMapper messageMapper) {
		this.messageMapper = messageMapper;
	}

	/**
	 * 发送一条站内消息。userId 为空直接忽略 —— 系统通知可能没有明确接收人，
	 * 这里不当作错误处理，调用方不必为此加判断。
	 */
	public void send(Long userId, String msgType, String title, String content,
	                 String bizType, Long bizId, String linkUrl) {
		if (userId == null) {
			return;
		}
		Message message = new Message();
		message.setUserId(userId);
		message.setMsgType(msgType == null ? DEFAULT_TYPE : msgType);
		message.setTitle(title);
		message.setContent(content == null ? "" : content);
		message.setBizType(bizType);
		message.setBizId(bizId);
		message.setLinkUrl(linkUrl);
		messageMapper.insert(message);
	}

	public PageResult<Message> page(Long userId, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : pageSize;

		int total = messageMapper.countByUser(userId);
		List<Message> list = messageMapper.pageByUser(userId, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public int unreadCount(Long userId) {
		return messageMapper.countUnread(userId);
	}

	/**
	 * 标记已读。
	 *
	 * <p>注意 SQL 里条件带 {@code user_id}，所以顺手就把越权挡住了 ——
	 * 别人拿到消息 id 也标不了你的。这条约束在 mapper 里，改 SQL 时别删。
	 */
	public void markRead(Long id, Long userId) {
		if (id == null) {
			throw new BadRequestException("消息ID不能为空");
		}
		messageMapper.markRead(id, userId);
	}
}
