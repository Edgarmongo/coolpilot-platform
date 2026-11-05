package com.coolskyai.modules.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.ai.dto.ChatDTO;
import com.coolskyai.modules.ai.service.AiSearchService;
import com.coolskyai.modules.ai.service.RagChatService;

/**
 * @Description: 知识库检索与问答
 *
 * 未登录也能搜、能问，但只能命中公开内容；
 * 登录且持有权益时，付费课时的片段才会进入候选（过滤在打分前完成）。
 * 问答的会话历史只在登录后保存。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class AiController {

	@Autowired
	private AiSearchService aiSearchService;
	@Autowired
	private RagChatService ragChatService;
	@Autowired
	private AccountService accountService;

	@GetMapping("/ai/search")
	public Result search(@RequestParam String q,
	                     @RequestParam(defaultValue = "8") Integer limit) {
		Long userId = accountService.currentUserId();
		return Result.ok("OK", aiSearchService.search(q, userId, limit));
	}

	/**
	 * 提问：一次调用返回答案 + 引用。
	 *
	 * 每次都可能触发一次模型调用，按 IP 限流，避免被当成免费 API 刷。
	 */
	@AccessLimit(seconds = 60, maxCount = 12, msg = "提问过于频繁，请稍后再试")
	@PostMapping("/ai/chat")
	public Result chat(@RequestBody ChatDTO dto) {
		Long userId = accountService.currentUserId();
		return Result.ok("OK", ragChatService.chat(userId, dto));
	}

	@GetMapping("/ai/conversations")
	public Result conversations(@RequestParam(defaultValue = "1") Integer pageNum,
	                            @RequestParam(defaultValue = "20") Integer pageSize) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", ragChatService.myConversations(userId, pageNum, pageSize));
	}

	@GetMapping("/ai/conversation/{id}")
	public Result conversation(@PathVariable Long id) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", ragChatService.conversationDetail(id, userId));
	}

	@DeleteMapping("/ai/conversation/{id}")
	public Result deleteConversation(@PathVariable Long id) {
		Long userId = accountService.requireUserId();
		ragChatService.deleteConversation(id, userId);
		return Result.ok("已删除");
	}
}
