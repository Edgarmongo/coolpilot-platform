package com.coolskyai.modules.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 找回密码 / 邮箱验证的入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@NoArgsConstructor
@Getter
@Setter
public class PasswordResetDTO {
	/** 发码时只填它；重置时作为账号定位 */
	private String email;
	/** 6 位数字验证码 */
	private String code;
	/** 新密码 */
	private String password;
}
