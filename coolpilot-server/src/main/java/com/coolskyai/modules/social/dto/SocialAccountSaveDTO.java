package com.coolskyai.modules.social.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 社交账号新增/编辑入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
public class SocialAccountSaveDTO {
	private String platform;
	private String accountName;
	private String intro;
	private String homeUrl;
	private String qrUrl;
	private String icon;
	private Integer sort;
	private Boolean enabled;
}
