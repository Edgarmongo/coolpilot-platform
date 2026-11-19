package com.coolskyai.modules.opensource.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * 开源项目档案。
 *
 * 与 portfolio（作品集）是两件事：这里记的是第三方开源项目，
 * 目的是判断「能不能变成生意」，不是展示自己的能力。
 *
 * 两条状态轴互相独立：
 *   research_status  todo / researching / done
 *   publish_status   draft / published / archived
 * 公开接口只认 publish_status = 'published'，与研究对象进度无关。
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class OssProject {

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
	private Date createTime;
	private Date updateTime;

	/** 展示用，不落库：站内关联的已发布分析篇数 */
	private Integer articleCount;

	/** 展示用，不落库：标签名逗号拼接，由 SQL GROUP_CONCAT 得到 */
	private String tagsCsv;
}
