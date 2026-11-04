package com.coolskyai.modules.ai.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 知识库分块
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class KbChunk {
	private Long id;
	private Long docId;
	private Long kbId;
	private Integer chunkIndex;
	private String content;
	private Long productId;
	private Boolean isPublic;
	/** float32 小端向量 */
	private byte[] embedding;
	private Integer dim;
	private String model;
	private Date createTime;
}
