package com.coolskyai.modules.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.ai.service.AiSearchService;

/**
 * @Description: 知识库检索（公开）
 *
 * 未登录也能搜，但只能命中公开内容；
 * 登录且持有权益时，付费课时的片段才会进入候选（过滤在打分前完成）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class AiController {

	@Autowired
	private AiSearchService aiSearchService;
	@Autowired
	private AccountService accountService;

	@GetMapping("/ai/search")
	public Result search(@RequestParam String q,
	                     @RequestParam(defaultValue = "8") Integer limit) {
		Long userId = accountService.currentUserId();
		return Result.ok("OK", aiSearchService.search(q, userId, limit));
	}
}
