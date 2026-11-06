package com.coolskyai.modules.portfolio.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.portfolio.service.PortfolioService;

/**
 * @Description: 项目展示（公开）
 *
 * 只返回 enabled = 1 的项目；详情页正文在服务端渲染成 HTML，
 * 前台直接 v-html，与文章、商品详情同一条链路。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class PortfolioController {

	@Autowired
	private PortfolioService portfolioService;

	@GetMapping("/portfolios")
	public Result list() {
		return Result.ok("OK", portfolioService.listEnabled());
	}

	@GetMapping("/portfolio/{slug}")
	public Result detail(@PathVariable String slug) {
		return Result.ok("OK", portfolioService.getBySlug(slug));
	}
}
