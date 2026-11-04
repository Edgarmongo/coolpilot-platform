package com.coolskyai.modules.ai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 知识库文档（一个文章 / 商品 / 课时）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class KbDocument {
	private Long id;
	private Long kbId;
	/** BLOG / PRODUCT / LESSON */
	private String sourceType;
	private Long sourceId;
	/** 所属商品；为空表示公开内容 */
	private Long productId;
	private String title;
	private String url;
	private Boolean isPublic;
	private String status;
	private Integer chunkCount;
	private Date updateTime;
}
