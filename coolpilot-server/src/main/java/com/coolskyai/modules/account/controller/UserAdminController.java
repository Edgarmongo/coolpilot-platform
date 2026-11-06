package com.coolskyai.modules.account.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.UserAdminService;

import java.util.Map;

/**
 * @Description: 后台用户管理（/admin/** 仅平台所有者可访问）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
@RequestMapping("/admin")
public class UserAdminController {

	@Autowired
	private UserAdminService userAdminService;

	/**
	 * 注册用户列表
	 *
	 * @param keyword  用户名 / 昵称 / 邮箱模糊匹配
	 * @param status   0 正常，1 已禁用；不传表示全部
	 */
	@GetMapping("/users")
	public Result users(@RequestParam(required = false) String keyword,
	                    @RequestParam(required = false) Integer status,
	                    @RequestParam(defaultValue = "1") Integer pageNum,
	                    @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("请求成功", userAdminService.list(keyword, status, pageNum, pageSize));
	}

	/**
	 * 启用 / 禁用账号：禁用会同时踢掉该账号已登录的会话
	 */
	@OperationLogger("修改用户状态")
	@PutMapping("/user/{id}/status")
	public Result updateStatus(@PathVariable Long id, @RequestParam Integer status) {
		userAdminService.setStatus(id, status);
		return Result.ok(status == 1 ? "已禁用" : "已启用");
	}

	/**
	 * 重置用户密码：邮件服务不可用时的兜底，改完会踢掉该账号的已登录会话
	 */
	@OperationLogger("重置用户密码")
	@PutMapping("/user/{id}/password")
	public Result resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
		userAdminService.resetPassword(id, body.get("password"));
		return Result.ok("密码已重置，该用户的登录会话已失效");
	}
}
