package com.coolskyai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.entity.User;
import com.coolskyai.model.dto.LoginInfo;
import com.coolskyai.model.dto.RegisterInfo;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.UserService;
import com.coolskyai.util.JwtUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 前台访客认证（自助注册 / 登录）
 * 与后台管理登录（/admin/login）完全分离：
 * 访客只拿到 ROLE_visitor 身份，不具备任何后台数据访问权限。
 *
 * @Author: Naccl
 */
@RestController
@RequestMapping("/visitor")
public class VisitorAuthController {
	@Autowired
	private UserService userService;

	/**
	 * 访客自助注册，注册成功后直接返回登录态
	 *
	 * @param registerInfo 注册信息
	 * @return 用户信息与token
	 */
	@AccessLimit(seconds = 60, maxCount = 3, msg = "注册过于频繁，请稍后再试")
	@PostMapping("/register")
	public Result register(@RequestBody RegisterInfo registerInfo) {
		User user = userService.registerVisitor(registerInfo);
		return Result.ok("注册成功", buildLoginResult(user));
	}

	/**
	 * 访客登录
	 *
	 * @param loginInfo 账号密码
	 * @return 用户信息与token
	 */
	@AccessLimit(seconds = 60, maxCount = 10, msg = "登录过于频繁，请稍后再试")
	@PostMapping("/login")
	public Result login(@RequestBody LoginInfo loginInfo) {
		User user = userService.loginVisitor(loginInfo);
		return Result.ok("登录成功", buildLoginResult(user));
	}

	/**
	 * 组装返回给前端的登录结果
	 */
	private Map<String, Object> buildLoginResult(User user) {
		String token = JwtUtils.generateToken(JwtConstants.VISITOR_PREFIX + user.getId(),
				AuthorityUtils.createAuthorityList("ROLE_visitor"));
		Map<String, Object> map = new HashMap<>(4);
		map.put("user", user);
		map.put("token", token);
		return map;
	}
}
