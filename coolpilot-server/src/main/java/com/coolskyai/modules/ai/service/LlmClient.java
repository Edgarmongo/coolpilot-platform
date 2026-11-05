package com.coolskyai.modules.ai.service;

import com.coolskyai.modules.ai.entity.AiUsage;

/**
 * @Description: 大模型调用接口
 *
 * 只定义两类方法：给系统提示与用户提示，拿回回答正文。
 * 换模型不换调用方 —— 本地 Ollama、DeepSeek、通义兼容模式、vLLM
 * 都走 OpenAI 兼容协议，用配置切换即可（见 application-dev.properties 的 ai.llm.*
 * 与后台「AI → 模型配置」，后者保存后即时生效）。
 *
 * 带 scene 的重载用于用量统计：同一个模型被问答、改写、重排共用，
 * 分开记才能看出「用户看到的回答」与「隐形成本」各占多少。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public interface LlmClient {

	/**
	 * 是否已配置可用（有 base-url 和 model 即视为可用；本地模型通常不需要 Key）
	 */
	boolean available();

	/**
	 * 当前模型标识，用于界面展示与记录
	 */
	String model();

	/**
	 * 单轮问答（按正式问答记账）
	 */
	default String chat(String systemPrompt, String userPrompt) {
		return chat(AiUsage.SCENE_CHAT, systemPrompt, userPrompt);
	}

	/**
	 * 单轮问答
	 *
	 * @param scene        用量统计的场景标签，见 {@link AiUsage} 的常量
	 * @param systemPrompt 系统提示（角色与约束）
	 * @param userPrompt   用户提示（资料 + 问题）
	 * @return 回答正文
	 * @throws LlmException 调用失败（超时、鉴权、返回格式异常等）
	 */
	String chat(String scene, String systemPrompt, String userPrompt);

	/**
	 * 流式问答：每收到一段增量就回调一次
	 *
	 * 回调式而不是返回 Stream，是为了让上层能把增量直接写进 SSE，
	 * 不必关心上游的分包格式（OpenAI 兼容实现都是 data: {...} 逐行）。
	 */
	default void stream(String systemPrompt, String userPrompt, java.util.function.Consumer<String> onDelta) {
		stream(AiUsage.SCENE_CHAT, systemPrompt, userPrompt, onDelta);
	}

	/**
	 * 流式问答
	 *
	 * @param onDelta 增量文本回调，可能被调用几十次
	 * @throws LlmException 调用失败（超时、鉴权、返回格式异常等）
	 */
	void stream(String scene, String systemPrompt, String userPrompt,
				java.util.function.Consumer<String> onDelta);
}
