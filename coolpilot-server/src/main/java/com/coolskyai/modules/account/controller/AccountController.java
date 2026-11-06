package com.coolskyai.modules.account.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.entity.User;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.dto.PasswordDTO;
import com.coolskyai.modules.account.dto.ProfileDTO;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.account.vo.AccountVO;
import com.coolskyai.modules.auth.service.PasswordResetService;

/**
 * @Description: 账户中心（当前登录用户）
 *
 * 这是新业务区（modules/*）的第一个模块，也是前台身份链路的验收点：
 * 能正确返回当前用户，说明 JwtFilter → SecurityContext → 业务层 已打通。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
@RestController
public class AccountController {
	@Autowired
	private AccountService accountService;
	@Autowired
	private PasswordResetService passwordResetService;

	/**
	 * 当前登录用户信息
	 * 博主与访客共用此接口，返回体不包含密码
	 */
	@GetMapping("/me")
	public Result me() {
		User user = accountService.currentUser();
		if (user == null) {
			return Result.create(403, "请先登录");
		}
		return Result.ok("OK", AccountVO.of(user));
	}

	/**
	 * 修改昵称/头像/邮箱。
	 * 用 JSON body 传参：昵称多为中文，query 参数会被 Tomcat 11 拒掉。
	 */
	@PutMapping("/me/profile")
	public Result updateProfile(@RequestBody ProfileDTO dto) {
		Long userId = accountService.requireUserId();
		User user = accountService.updateProfile(userId, dto);
		return Result.ok("保存成功", AccountVO.of(user));
	}

	/**
	 * 修改邮箱前先发验证码到新邮箱。
	 *
	 * 放在 /me 下（需要登录）而不是公开接口：只有已登录的人才能给自己换邮箱，
	 * 否则等于给了一个「向任意邮箱发验证码」的入口。
	 */
	@AccessLimit(seconds = 60, maxCount = 3, msg = "操作过于频繁，请稍后再试")
	@PostMapping("/me/email/code")
	public Result sendEmailCode(@RequestBody java.util.Map<String, String> body) {
		accountService.requireUserId();
		passwordResetService.sendCode(body.get("email"), true);
		return Result.ok("验证码已发送到新邮箱");
	}

	/**
	 * 登出：吊销当前身份的全部 token。
	 * 前端即使不调用也能"看起来登出"，但调用后旧 token 才真的失效。
	 */
	@PostMapping("/me/logout")
	public Result logout() {
		accountService.logoutCurrent();
		return Result.ok("已退出登录", null);
	}

	/**
	 * 修改密码。必须提供原密码，校验通过才允许覆盖。
	 */
	@PutMapping("/me/password")
	public Result changePassword(@RequestBody PasswordDTO dto) {
		Long userId = accountService.requireUserId();
		accountService.changePassword(userId, dto);
		return Result.ok("密码已修改，请用新密码登录");
	}
}
