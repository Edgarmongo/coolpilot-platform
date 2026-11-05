package com.coolskyai.modules.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.ai.dto.ChatDTO;
import com.coolskyai.modules.ai.service.AiSearchService;
import com.coolskyai.modules.ai.service.RagChatService;
import com.coolskyai.modules.ai.service.ShareCommentService;
import com.coolskyai.util.ClientIpResolver;

import java.util.Map;

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
	@Autowired
	private ClientIpResolver clientIpResolver;
	@Autowired
	private ShareCommentService shareCommentService;

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

	/**
	 * 流式提问：边生成边返回，长回答不用干等
	 *
	 * 用 SSE 而不是 WebSocket：单向输出、走同一套鉴权与限流、断线自动结束。
	 *
	 * 注意这里**不写 produces**：前端经过 Nitro / nginx 代理时，代理可能带
	 * Accept: application/json，声明 produces 会直接 406/500；
	 * SseEmitter 自己会把 Content-Type 设为 text/event-stream。
	 */
	@AccessLimit(seconds = 60, maxCount = 12, msg = "提问过于频繁，请稍后再试")
	@PostMapping("/ai/chat/stream")
	public SseEmitter chatStream(@RequestBody ChatDTO dto, HttpServletResponse response) {
		// 告诉 nginx 不要缓冲：否则增量会被攒成一次性返回，流式就名存实亡
		response.setHeader("X-Accel-Buffering", "no");
		response.setHeader("Cache-Control", "no-cache");
		Long userId = accountService.currentUserId();
		SseEmitter emitter = new SseEmitter(180_000L);
		ragChatService.chatStream(userId, dto, emitter);
		return emitter;
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

	/**
	 * 分享一条回答：返回 12 位 token，前端拼成 /share/{token}
	 *
	 * 可选参数 days：有效天数，不传默认 30 天，0 表示长期有效（最长 365 天）。
	 */
	@PostMapping("/ai/answer/{id}/share")
	public Result share(@PathVariable Long id,
	                    @RequestBody(required = false) Map<String, Object> body) {
		Long userId = accountService.requireUserId();
		Integer days = null;
		if (body != null && body.get("days") != null) {
			try {
				days = Integer.parseInt(String.valueOf(body.get("days")).trim());
			} catch (NumberFormatException e) {
				throw new com.coolskyai.exception.BadRequestException("有效期格式有误");
			}
		}
		return Result.ok("OK", ragChatService.shareAnswer(id, userId, days));
	}

	/**
	 * 撤销分享：链接立即失效
	 */
	@DeleteMapping("/ai/answer/{id}/share")
	public Result revokeShare(@PathVariable Long id) {
		Long userId = accountService.requireUserId();
		ragChatService.revokeShare(id, userId);
		return Result.ok("已取消分享");
	}

	/**
	 * 公开读取分享的答案（无需登录）
	 */
	@GetMapping("/share/answer/{token}")
	public Result sharedAnswer(@PathVariable String token) {
		return Result.ok("OK", ragChatService.sharedAnswer(token));
	}

	/**
	 * 阅读上报：由浏览器在页面加载后调用，拿得到真实访客 IP
	 */
	@PostMapping("/share/answer/{token}/view")
	public Result viewShare(@PathVariable String token, HttpServletRequest request) {
		return Result.ok("OK", ragChatService.viewShare(token, clientIpResolver.resolve(request)));
	}

	/**
	 * 点赞 / 取消点赞：同一 IP 只算一次，第二次点即撤销
	 */
	@PostMapping("/share/answer/{token}/like")
	public Result likeShare(@PathVariable String token, HttpServletRequest request) {
		return Result.ok("OK", ragChatService.likeShare(token, clientIpResolver.resolve(request), true));
	}

	/**
	 * 分享页的讨论（公开可读，不需要登录）
	 */
	@GetMapping("/share/answer/{token}/comments")
	public Result shareComments(@PathVariable String token) {
		return Result.ok("OK", shareCommentService.list(token));
	}

	/**
	 * 发表留言：只允许登录用户，未登录由 SecurityPaths 拦在 403
	 */
	@AccessLimit(seconds = 30, maxCount = 3, msg = "留言太频繁，稍后再试")
	@PostMapping("/share/answer/{token}/comments")
	public Result postShareComment(@PathVariable String token,
	                               @RequestBody(required = false) Map<String, Object> body,
	                               HttpServletRequest request) {
		if (body == null) {
			throw new com.coolskyai.exception.BadRequestException("说点什么吧");
		}
		Long parentId = null;
		Object raw = body.get("parentId");
		if (raw != null && !"-1".equals(String.valueOf(raw))) {
			try {
				parentId = Long.parseLong(String.valueOf(raw).trim());
			} catch (NumberFormatException e) {
				throw new com.coolskyai.exception.BadRequestException("回复的评论不存在");
			}
		}
		Object content = body.get("content");
		return Result.ok("留言成功", shareCommentService.add(token,
				content == null ? null : String.valueOf(content), parentId,
				clientIpResolver.resolve(request)));
	}

	/**
	 * 删除自己的留言
	 */
	@DeleteMapping("/share/answer/{token}/comments/{id}")
	public Result deleteShareComment(@PathVariable String token, @PathVariable Long id) {
		return Result.ok("已删除", shareCommentService.delete(token, id));
	}

	@DeleteMapping("/share/answer/{token}/like")
	public Result unlikeShare(@PathVariable String token, HttpServletRequest request) {
		return Result.ok("OK", ragChatService.likeShare(token, clientIpResolver.resolve(request), false));
	}

	@DeleteMapping("/ai/conversation/{id}")
	public Result deleteConversation(@PathVariable Long id) {
		Long userId = accountService.requireUserId();
		ragChatService.deleteConversation(id, userId);
		return Result.ok("已删除");
	}
}
