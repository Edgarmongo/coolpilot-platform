package com.coolskyai.modules.ai.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: 分享出去的答案快照（公开可读，不含任何用户信息）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class SharedAnswer {
	private String question;
	private String answer;
	private List<Map<String, Object>> citations;
	private String model;
	private Date createTime;
}
