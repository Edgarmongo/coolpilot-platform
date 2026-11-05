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
	/** 本轮实际用于检索的查询（追问改写后）；为空表示按原话检索 */
	private String retrievalQuery;
	/** 引用快照 JSON */
	private String citations;
	private String model;
	/** 分享令牌，为空表示未分享 */
	private String shareToken;
	/** 分享到期时间，为空表示长期有效 */
	private Date shareExpiresAt;
	/** 分享页阅读数 */
	private Integer shareViews;
	/** 分享页点赞数 */
	private Integer shareLikes;
	private Date createTime;
}
