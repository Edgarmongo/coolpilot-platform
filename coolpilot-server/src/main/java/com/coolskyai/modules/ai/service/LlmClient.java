package com.coolskyai.modules.ai.service;

/**
 * @Description: 大模型调用接口
 *
 * 只定义一个方法：给系统提示与用户提示，拿回回答正文。
 * 换模型不换调用方 —— 本地 Ollama、DeepSeek、通义兼容模式、vLLM
 * 都走 OpenAI 兼容协议，用配置切换即可（见 application-dev.properties 的 ai.llm.*）。
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
	 * 单轮问答
	 *
	 * @param systemPrompt 系统提示（角色与约束）
	 * @param userPrompt   用户提示（资料 + 问题）
	 * @return 回答正文
	 * @throws LlmException 调用失败（超时、鉴权、返回格式异常等）
	 */
	String chat(String systemPrompt, String userPrompt);
}
