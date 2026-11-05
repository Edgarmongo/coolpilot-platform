package com.coolskyai.modules.nav.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 导航菜单新增/编辑入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
public class NavMenuSaveDTO {
	private String label;
	private String path;
	private Integer sort;
	private Boolean visible;
	private String target;
}
