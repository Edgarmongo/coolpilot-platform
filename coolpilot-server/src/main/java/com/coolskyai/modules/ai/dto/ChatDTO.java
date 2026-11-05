package com.coolskyai.modules.ai.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 提问入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ChatDTO {
	private String question;
	/** 继续追问时带上，为空则新建会话 */
	private Long conversationId;
}
