package com.coolskyai.modules.account.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 前台修改密码入参
 *
 * 必须校验原密码：上游的 changeAccount 不校验，
 * 任何已登录会话都能直接覆盖密码，这里不能沿用。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
public class PasswordDTO {
	private String oldPassword;
	private String newPassword;
}
