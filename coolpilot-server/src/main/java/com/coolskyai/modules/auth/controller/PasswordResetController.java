package com.coolskyai.modules.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.auth.dto.PasswordResetDTO;
import com.coolskyai.modules.auth.service.PasswordResetService;

/**
 * @Description: 找回密码（公开）
 *
 * 两个接口都加了新的 @AccessLimit：业务层已经有「60 秒一次」的 Redis 限流，
 * 这里再按 IP 挡一层，避免有人用不同邮箱轮换着刷发信额度。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class PasswordResetController {

	@Autowired
	private PasswordResetService passwordResetService;

	@AccessLimit(seconds = 60, maxCount = 5, msg = "操作过于频繁，请稍后再试")
	@PostMapping("/auth/password/code")
	public Result sendCode(@RequestBody PasswordResetDTO dto) {
		passwordResetService.sendCode(dto.getEmail(), false);
		return Result.ok("如果该邮箱已注册，验证码会在几分钟内送达");
	}

	@AccessLimit(seconds = 60, maxCount = 10, msg = "操作过于频繁，请稍后再试")
	@PostMapping("/auth/password/reset")
	public Result reset(@RequestBody PasswordResetDTO dto) {
		passwordResetService.resetPassword(dto.getEmail(), dto.getCode(), dto.getPassword());
		return Result.ok("密码已重置，请用新密码登录");
	}
}
