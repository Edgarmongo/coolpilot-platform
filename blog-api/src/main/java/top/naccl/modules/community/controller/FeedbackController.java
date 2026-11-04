package top.naccl.modules.community.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.annotation.AccessLimit;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.community.service.FeedbackService;

/**
 * @Description: 前台反馈提交（允许未登录提交，登录则记录用户）
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@RestController
public class FeedbackController {
	@Autowired
	private FeedbackService feedbackService;
	@Autowired
	private AccountService accountService;

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
