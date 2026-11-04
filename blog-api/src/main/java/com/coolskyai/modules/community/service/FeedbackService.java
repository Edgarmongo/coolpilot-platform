package com.coolskyai.modules.community.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.community.entity.Feedback;
import com.coolskyai.modules.community.mapper.FeedbackMapper;

import java.util.List;

/**
 * @Description: 技术问题反馈
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
@Service
public class FeedbackService {
	@Autowired
	private FeedbackMapper feedbackMapper;

	public Long submit(Long userId, String content, String images, String contact) {
		if (content == null || content.trim().length() < 5) {
			throw new BadRequestException("反馈内容至少 5 个字");
		}
		if (content.length() > 2000) {
			throw new BadRequestException("反馈内容过长");
		}
		Feedback feedback = new Feedback();
		feedback.setUserId(userId);
		feedback.setContent(content.trim());
		feedback.setImages(images == null ? "" : images);
		feedback.setContact(contact == null ? "" : contact);
		feedbackMapper.insert(feedback);
		return feedback.getId();
	}

	public PageResult<Feedback> page(String status, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1 || pageSize > 100) ? 10 : pageSize;
		int total = feedbackMapper.count(status);
		List<Feedback> list = feedbackMapper.page(status, (page - 1) * size, size);
		int totalPage = (total + size - 1) / size;
		return new PageResult<>(totalPage, total, list);
	}

	public void reply(Long id, String reply, String status) {
		if (id == null) {
			throw new BadRequestException("反馈ID不能为空");
		}
		if (reply == null || reply.trim().isEmpty()) {
			throw new BadRequestException("回复内容不能为空");
		}
		String targetStatus = (status == null || status.isEmpty()) ? "RESOLVED" : status;
		feedbackMapper.reply(id, reply.trim(), targetStatus);
	}
}
