package com.coolskyai.modules.social.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.social.service.SocialAccountService;

/**
 * @Description: 社交账号（公开）
 *
 * 只返回 enabled = 1 的条目：站长关掉的平台不应该被访客看到，
 * 也不该出现在首页「关注我」那一行里。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class SocialController {

	@Autowired
	private SocialAccountService socialAccountService;

	@GetMapping("/social")
	public Result list() {
		return Result.ok("OK", socialAccountService.listEnabled());
	}
}
