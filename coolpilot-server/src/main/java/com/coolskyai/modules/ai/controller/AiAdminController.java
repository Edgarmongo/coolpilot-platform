package com.coolskyai.modules.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.ai.service.AiSearchService;
import com.coolskyai.modules.ai.service.KnowledgeIndexService;
import com.coolskyai.modules.ai.service.RagChatService;

/**
 * @Description: 知识库管理（平台所有者）
 *
 * 重建索引是全量替换，文章/商品有变动后手动触发一次即可；
 * 内容量不大，没必要做实时增量。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
@RequestMapping("/admin")
public class AiAdminController {

	@Autowired
	private KnowledgeIndexService knowledgeIndexService;
	@Autowired
	private AiSearchService aiSearchService;
	@Autowired
	private RagChatService ragChatService;

	@PostMapping("/ai/reindex")
	public Result reindex() {
		return Result.ok("索引已重建", knowledgeIndexService.rebuild());
	}

	@GetMapping("/ai/stats")
	public Result stats() {
		return Result.ok("OK", aiSearchService.stats());
	}

	/**
	 * 当前问答模型配置（不返回密钥）
	 */
	@GetMapping("/ai/llm")
	public Result llm() {
		return Result.ok("OK", ragChatService.llmStatus());
	}

	/**
	 * 模型连通性自测：配好 Key / 本地模型后点一下就知道通没通
	 */
	@PostMapping("/ai/llm/test")
	public Result llmTest() {
		return Result.ok("OK", ragChatService.llmTest());
	}
}
