package com.coolskyai.modules.opensource.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

/**
 * 栏目文章流的一项：一篇文章 + 它所属的项目。
 *
 * 为什么首页卡片是文章而不是项目：一个项目 4 篇分析就是 4 个可点击标题、
 * 4 个可被收录的 URL；而按项目展示，在只有 1 个已发布项目时页面会空掉。
 */
@NoArgsConstructor
@Getter
@Setter
public class OssArticleItem {

	private Long id;
	private String title;
	private String slug;
	private String description;
	private String coverUrl;
	private Integer readTime;
	private Date createTime;
	private Date updateTime;
	/** business / research / practice / compare */
	private String relationType;

	private ProjectBrief project;

	@NoArgsConstructor
	@Getter
	@Setter
	public static class ProjectBrief {
		private String slug;
		private String name;
		private String verdict;
		private String category;
		private String summary;
	}
}
