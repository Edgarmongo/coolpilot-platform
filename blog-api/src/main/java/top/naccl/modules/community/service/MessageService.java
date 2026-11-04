package top.naccl.modules.community.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.naccl.exception.BadRequestException;
import top.naccl.model.vo.PageResult;
import top.naccl.modules.community.entity.Message;
import top.naccl.modules.community.mapper.MessageMapper;

import java.util.List;

/**
 * @Description: 站内消息
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Service
public class MessageService {
	@Autowired
	private MessageMapper messageMapper;

	/**
	 * 发送一条站内消息（供其他模块调用）
	 */
	public void send(Long userId, String msgType, String title, String content,
	                 String bizType, Long bizId, String linkUrl) {
		if (userId == null) {
			return;
		}
		Message message = new Message();
		message.setUserId(userId);
		message.setMsgType(msgType == null ? "SYSTEM" : msgType);
		message.setTitle(title);
		message.setContent(content == null ? "" : content);
		message.setBizType(bizType);
		message.setBizId(bizId);
		message.setLinkUrl(linkUrl);
		messageMapper.insert(message);
	}

	public PageResult<Message> page(Long userId, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > 50) ? 10 : pageSize;
		int total = messageMapper.countByUser(userId);
		List<Message> list = messageMapper.pageByUser(userId, (page - 1) * size, size);
		int totalPage = (total + size - 1) / size;
		return new PageResult<>(totalPage, total, list);
	}

	public int unreadCount(Long userId) {
		return messageMapper.countUnread(userId);
	}

	public void markRead(Long id, Long userId) {
		if (id == null) {
			throw new BadRequestException("消息ID不能为空");
		}
		// 条件里带 user_id，天然防止越权标记他人消息
		messageMapper.markRead(id, userId);
	}
}
