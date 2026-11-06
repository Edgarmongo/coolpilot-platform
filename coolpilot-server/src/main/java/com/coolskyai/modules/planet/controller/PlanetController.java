package com.coolskyai.modules.planet.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.planet.service.PlanetService;

/**
 * @Description: 知识星球（公开）
 *
 * 首页卡片和 /planet 页共用这一个接口；未启用时返回 enabled=false，
 * 前台据此隐藏卡片、页面显示「尚未开放」。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class PlanetController {

	@Autowired
	private PlanetService planetService;

	@GetMapping("/planet")
	public Result get(@RequestParam(required = false) String lang) {
		return Result.ok("OK", planetService.get(lang));
	}
}
