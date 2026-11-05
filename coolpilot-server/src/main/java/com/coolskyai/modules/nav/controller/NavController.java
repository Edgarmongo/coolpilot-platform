package com.coolskyai.modules.nav.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.nav.service.NavMenuService;

/**
 * @Description: 前台导航（公开）
 *
 * 导航是每个页面都要渲染的东西，必须匿名可读；
 * 只返回 visible = 1 的条目，隐藏项不泄露给前台。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class NavController {

	@Autowired
	private NavMenuService navMenuService;

	@GetMapping("/nav")
	public Result list(@RequestParam(required = false) String lang) {
		return Result.ok("OK", navMenuService.listPublic(lang));
	}
}
