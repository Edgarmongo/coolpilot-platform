package com.coolskyai.modules.topic.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 专题内的一篇文章（只取列表需要的字段）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class TopicArticle {
	private Long id;
	private String title;
	private String slug;
	private String description;
	private Date createTime;
	private Integer readTime;
	private Integer views;
	/** 有密码即为私密文章，列表中不展示摘要 */
	private Boolean privacy;
	/** 专题内顺序 */
	private Integer sort;
}
