package com.coolskyai.modules.account.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @Description: 前台修改个人资料入参
 *
 * 用 JSON body 而不是 query 参数：昵称通常是中文，
 * Tomcat 11 对未编码的中文 query 参数会直接返回 400。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
public class ProfileDTO {
	private String nickname;
	private String avatar;
	private String email;
	/**
	 * 修改邮箱时必填的验证码。
	 * 邮箱没变时不需要传 —— 否则只改昵称也得先收一封邮件。
	 */
	private String code;
}
