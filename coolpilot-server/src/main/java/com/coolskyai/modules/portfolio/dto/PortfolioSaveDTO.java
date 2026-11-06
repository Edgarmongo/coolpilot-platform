package com.coolskyai.modules.portfolio.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 项目展示新增/编辑入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
public class PortfolioSaveDTO {
	private String slug;
	private String name;
	private String summary;
	private String coverUrl;
	private String techStack;
	private String repoUrl;
	private String demoUrl;
	private String status;
	/** Markdown 正文 */
	private String content;
	private Integer sort;
	private Boolean enabled;
}
