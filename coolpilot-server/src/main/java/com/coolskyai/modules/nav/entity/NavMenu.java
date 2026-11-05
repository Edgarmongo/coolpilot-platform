package com.coolskyai.modules.nav.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 前台导航菜单
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class NavMenu {
	private Long id;
	private String label;
	/** 英文菜单文字，留空则前台用 label */
	private String labelEn;
	/** 站内路径（/ 开头）或完整外链（http(s):// 开头） */
	private String path;
	private Integer sort;
	private Boolean visible;
	/** _self / _blank */
	private String target;
	private Date createTime;
	private Date updateTime;
}
