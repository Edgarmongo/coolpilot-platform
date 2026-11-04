package com.coolskyai.modules.community.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 点赞记录
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class LikeRecord {
	private Long id;
	private Long userId;
	/** ARTICLE / MOMENT / COMMENT */
	private String targetType;
	private Long targetId;
	private Date createTime;
}
