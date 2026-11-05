package com.coolskyai.modules.ai.service;

/**
 * @Description: 流式输出被中途放弃
 *
 * 两种情况会抛它：
 *   1. 客户端断开（用户关页面 / 点「停止」），SSE 已经发不出去了
 *   2. 上层主动要求停止读取上游
 *
 * 与 LlmException 分开，是因为处理方式完全不同：
 * 模型出错要告诉用户「为什么没答上来」，客户端断开则什么都不用做，
 * 更不该把它记成模型故障。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public class StreamCancelledException extends RuntimeException {

	public StreamCancelledException(String message) {
		super(message);
	}

	public StreamCancelledException(String message, Throwable cause) {
		super(message, cause);
	}
}
