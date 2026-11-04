package com.coolskyai.modules.topic.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 专题（系列文章合集）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Topic {
	private Long id;
	private String slug;
	private String title;
	private String description;
	private String cover;
	private Boolean isPublished;
	private Integer sort;
	private Date createTime;
	private Date updateTime;

	/** 展示用，不落库 */
	private Integer articleCount;
}
