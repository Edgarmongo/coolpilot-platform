package com.coolskyai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.constant.Role;
import com.coolskyai.entity.User;
import com.coolskyai.model.dto.LoginInfo;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.LoginGuard;
import com.coolskyai.service.UserService;
import com.coolskyai.util.JwtUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 前台登录
 * @Author: CoolSkyAI
 * @Date: 2020-09-02
 */
@RestController
public class LoginController {
	@Autowired
	UserService userService;
	@Autowired
	LoginGuard loginGuard;

	/**
	 * 登录成功后，签发博主身份Token
	 *
	 * @param loginInfo
	 * @return
	 */
	/**
	 * 博主登录。
	 *
	 * 两层防护：
	 *   1. @AccessLimit 按「IP + 接口」限制，挡住单机高频尝试
	 *   2. LoginGuard 按账号计失败次数，连续 5 次锁 15 分钟，换 IP 也绕不过
	 *
	 * 失败提示统一为「用户名或密码错误」，不区分账号不存在与密码错误，避免枚举账号。
	 */
	@AccessLimit(seconds = 60, maxCount = 5, msg = "登录尝试过于频繁，请 1 分钟后再试")
	@PostMapping("/login")
	public Result login(@RequestBody LoginInfo loginInfo) {
		if (loginGuard.isLocked(loginInfo.getUsername())) {
			return Result.create(429, loginGuard.lockMessage());
		}
		User user;
		try {
			user = userService.findUserByUsernameAndPassword(loginInfo.getUsername(), loginInfo.getPassword());
		} catch (UsernameNotFoundException e) {
			int fails = loginGuard.onFail(loginInfo.getUsername());
			if (fails >= 5) {
				return Result.create(429, loginGuard.lockMessage());
			}
			throw e;
		}
		if (!Role.isOwner(user.getRole())) {
			// 角色不对不算密码错误，不计入锁定，避免误锁访客账号
			return Result.create(403, "无权限");
		}
		loginGuard.onSuccess(loginInfo.getUsername());
		user.setPassword(null);
		String jwt = JwtUtils.generateToken(JwtConstants.OWNER_PREFIX + user.getUsername());
		Map<String, Object> map = new HashMap<>(4);
		map.put("user", user);
		map.put("token", jwt);
		return Result.ok("登录成功", map);
	}
}
