package com.coolskyai.modules.portfolio.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 项目展示（作品集）
 *
 * 注意与 built-in 的 `project` 表区分：那张表是技术服务项目进度
 * （客户看里程碑与交付），这张表是站长用来展示自己作品的。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Portfolio {
	private Long id;
	/** 前台地址 /projects/{slug} */
	private String slug;
	private String name;
	/** 列表页一句话简介 */
	private String summary;
	private String coverUrl;
	/** 技术栈，逗号分隔，前端按逗号拆成标签 */
	private String techStack;
	private String repoUrl;
	private String demoUrl;
	/** ACTIVE / MAINTAINING / WIP / ARCHIVED */
	private String status;
	/** 详情正文，Markdown */
	private String content;
	private Integer sort;
	private Boolean enabled;
	private Date createTime;
	private Date updateTime;
}
