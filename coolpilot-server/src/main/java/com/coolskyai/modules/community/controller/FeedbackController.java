package com.coolskyai.modules.community.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.community.entity.Feedback;
import com.coolskyai.modules.community.service.FeedbackService;

/**
 * @Description: 前台反馈提交（允许未登录提交，登录则记录用户）
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
@RestController
public class FeedbackController {
	@Autowired
	private FeedbackService feedbackService;
	@Autowired
	private AccountService accountService;

	/**
	 * 我的反馈列表：只返回本人提交的记录，附带后台回复
	 */
	@GetMapping("/me/feedbacks")
	public Result myFeedbacks(@RequestParam(defaultValue = "1") Integer pageNum,
	                          @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		PageResult<Feedback> page = feedbackService.pageByUser(userId, pageNum, pageSize);
		return Result.ok("OK", page);
	}

	@AccessLimit(seconds = 60, maxCount = 3, msg = "提交过于频繁，请稍后再试")
	@PostMapping("/feedback")
	public Result submit(@RequestParam String content,
	                     @RequestParam(required = false, defaultValue = "") String images,
	                     @RequestParam(required = false, defaultValue = "") String contact) {
		Long userId = accountService.currentUserId();
		Long id = feedbackService.submit(userId, content, images, contact);
		return Result.ok("提交成功，感谢反馈", id);
	}
}
