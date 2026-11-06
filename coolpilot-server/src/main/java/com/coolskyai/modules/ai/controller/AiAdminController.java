package com.coolskyai.modules.ai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.ai.service.AiSearchService;
import com.coolskyai.modules.ai.service.KnowledgeIndexService;
import com.coolskyai.modules.ai.service.AiAdminService;
import com.coolskyai.modules.ai.service.RagChatService;
import com.coolskyai.modules.ai.service.AiSettingService;
import com.coolskyai.modules.ai.service.AiUsageService;

import java.util.LinkedHashMap;
import java.util.Map;

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
	private AiAdminService aiAdminService;

	@Autowired
	private KnowledgeIndexService knowledgeIndexService;
	@Autowired
	private AiSearchService aiSearchService;
	@Autowired
	private RagChatService ragChatService;
	@Autowired
	private AiSettingService aiSettingService;
	@Autowired
	private AiUsageService aiUsageService;

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
	/**
	 * 问答会话列表（后台排查用）
	 */
	/**
	 * 检索质量：0 引用占比、平均引用数、改写率，以及「严格度该设哪档」的建议
	 */
	@GetMapping("/ai/quality")
	public Result quality(@RequestParam(defaultValue = "7") Integer days) {
		return Result.ok("请求成功", aiAdminService.quality(days, 500));
	}

	@GetMapping("/ai/conversations")
	public Result conversations(@RequestParam(required = false) String keyword,
	                            @RequestParam(defaultValue = "1") Integer pageNum,
	                            @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("请求成功", aiAdminService.conversations(keyword, pageNum, pageSize));
	}

	/**
	 * 会话详情：问题与回答成对，附引用条数
	 */
	@GetMapping("/ai/conversation/{id}")
	public Result conversationDetail(@PathVariable Long id) {
		return Result.ok("请求成功", aiAdminService.detail(id));
	}

	/**
	 * 模型配置：当前生效值 + 配置文件默认值 + 哪些项被改过。
	 * 密钥只返回掩码，前端原样回传也不会把 Key 写坏。
	 */
	@GetMapping("/ai/config")
	public Result config() {
		return Result.ok("OK", aiSettingService.getForAdmin());
	}

	/**
	 * 保存模型配置。保存后立即热加载，不需要重启。
	 * 例外：向量/重排的 provider 是启动时按条件装配的，换方案仍要重启。
	 */
	@PutMapping("/ai/config")
	public Result saveConfig(@RequestBody Map<String, Object> body) {
		Map<String, String> values = new LinkedHashMap<>();
		Object raw = body == null ? null : body.get("values");
		if (raw instanceof Map<?, ?> map) {
			map.forEach((key, value) -> values.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
		}
		aiSettingService.save(values);
		return Result.ok("已保存，立即生效", aiSettingService.getForAdmin());
	}

	/**
	 * 强制重新读取配置。
	 *
	 * 后端每 10 秒会自己检查一次 ai_setting 的变更，这个接口是给
	 * 「改完立刻要看」用的，省掉等待。
	 */
	@PostMapping("/ai/config/reload")
	public Result reloadConfig() {
		aiSettingService.reload();
		return Result.ok("已重新加载", aiSettingService.getForAdmin());
	}

	/**
	 * 用量统计：今日 + 区间汇总 + 按天 / 模型 / 场景
	 */
	@GetMapping("/ai/usage")
	public Result usage(@RequestParam(defaultValue = "7") Integer days) {
		return Result.ok("OK", aiUsageService.stats(days == null ? 7 : days));
	}

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
