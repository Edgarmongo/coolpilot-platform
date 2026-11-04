package com.coolskyai.modules.product.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 课时
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Lesson {
	private Long id;
	private Long courseId;
	private String title;
	private Integer sort;
	/** VIDEO / DOC */
	private String contentType;
	private Long videoFileId;
	private String content;
	private Integer duration;
	private Boolean isFreePreview;
	private Date createTime;
	private Date updateTime;
}
