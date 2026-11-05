package com.coolskyai.modules.ai.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 一条检索结果
 *
 * content 是完整片段（只给 RAG 拼上下文），snippet 是给页面展示的摘要。
 * 两者分开，避免为了拼 prompt 再把结果集里的摘要反查一遍原文。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Retrieved {
	private Long docId;
	private String sourceType;
	private String title;
	private String url;
	/** 完整片段正文 */
	private String content;
	/** 展示用摘要 */
	private String snippet;
	private Double score;
	private Long productId;
	/** 片段在文档内的序号，便于排查召回质量 */
	private Integer chunkIndex;
}
