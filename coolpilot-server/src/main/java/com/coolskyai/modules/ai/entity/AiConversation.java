package com.coolskyai.modules.ai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: AI 问答会话
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class AiConversation {
	private Long id;
	private Long userId;
	private String title;
	private Integer messageCount;
	private Date createTime;
	private Date updateTime;
}
