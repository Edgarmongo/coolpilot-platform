package com.coolskyai.modules.community.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.community.entity.Feedback;
import com.coolskyai.modules.community.service.FeedbackService;

/**
 * @Description: 反馈管理（后台）
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@RestController
public class FeedbackAdminController {
	@Autowired
	private FeedbackService feedbackService;

	@GetMapping("/admin/feedbacks")
	public Result feedbacks(@RequestParam(required = false) String status,
	                        @RequestParam(defaultValue = "1") Integer pageNum,
	                        @RequestParam(defaultValue = "10") Integer pageSize) {
		PageResult<Feedback> page = feedbackService.page(status, pageNum, pageSize);
		return Result.ok("OK", page);
	}

	@OperationLogger("回复用户反馈")
	@PutMapping("/admin/feedback/{id}/reply")
	public Result reply(@PathVariable Long id,
	                    @RequestParam String reply,
	                    @RequestParam(required = false, defaultValue = "RESOLVED") String status) {
		feedbackService.reply(id, reply, status);
		return Result.ok("回复成功");
	}
}
