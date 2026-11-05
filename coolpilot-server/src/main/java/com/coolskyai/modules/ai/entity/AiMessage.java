package com.coolskyai.modules.ai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: AI 问答消息
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class AiMessage {
	private Long id;
	private Long conversationId;
	/** USER / ASSISTANT */
	private String role;
	private String content;
	/** 引用快照 JSON */
	private String citations;
	private String model;
	/** 分享令牌，为空表示未分享 */
	private String shareToken;
	private Date createTime;
}
