package com.coolskyai.modules.community.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 收藏
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Favorite {
	private Long id;
	private Long userId;
	/** ARTICLE / MOMENT / PRODUCT */
	private String targetType;
	private Long targetId;
	private Date createTime;

	/** 以下为列表展示用的关联字段，不落库 */
	private String targetTitle;
	private String targetCover;
	private Date targetTime;
}
