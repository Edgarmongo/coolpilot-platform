package com.coolskyai.modules.ai.service;

/**
 * @Description: 大模型调用异常
 *
 * 独立成类，是为了让上层能区分「模型没配好/调不通」和「业务参数不合法」：
 * 前者要降级成「先看检索片段」，后者要直接告诉用户改输入。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

	public LlmException(String message, Throwable cause) {
		super(message, cause);
	}
}
