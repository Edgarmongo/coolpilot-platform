package com.coolskyai.modules.topic.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * @Description: 新建 / 编辑专题
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class TopicSaveDTO {
	private String slug;
	private String title;
	private String description;
	private String cover;
	private Boolean published;
	private Integer sort;
	/** 关联文章，顺序即展示顺序；不传表示不改绑定（新建时为空） */
	private List<Long> articleIds;
}
