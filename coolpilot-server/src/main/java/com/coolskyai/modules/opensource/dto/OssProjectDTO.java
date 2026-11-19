package com.coolskyai.modules.opensource.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 后台保存项目档案。
 *
 * 标签与关联文章走「全量覆盖」而不是增量：后台编辑页本来就是整表提交，
 * 增量接口会多出一堆「先删哪条」的分支，这里保持一个语义 —— 提交什么就是什么。
 */
@NoArgsConstructor
@Getter
@Setter
public class OssProjectDTO {

	private Long id;
	private String slug;
	private String name;
	private String summary;
	private String description;
	private String repoUrl;
	private String officialUrl;
	private String docsUrl;
	private String license;
	private String language;
	private String category;
	private String coverUrl;
	private Integer stars;
	private Date starsAt;
	private String verdict;
	private String assessmentJson;
	private String researchStatus;
	private String publishStatus;
	private Boolean featured;
	private Date lastVerifiedAt;
	private Integer sort;

	/** 全量覆盖：不传或空数组 = 清空标签 */
	private List<Long> tagIds = new ArrayList<>();

	/** 全量覆盖：不传或空数组 = 清空关联 */
	private List<ArticleBind> articles = new ArrayList<>();

	@NoArgsConstructor
	@Getter
	@Setter
	public static class ArticleBind {
		private Long blogId;
		private String relationType;
		private Integer sort;
	}
}
