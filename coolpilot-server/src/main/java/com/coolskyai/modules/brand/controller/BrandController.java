package com.coolskyai.modules.brand.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.brand.service.BrandService;

/**
 * @Description: 品牌信息（公开）
 *
 * 顶栏与页脚在每个页面都要渲染，必须匿名可读；
 * 只暴露展示字段，不含邮箱、收款码等资料卡内容。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class BrandController {

	@Autowired
	private BrandService brandService;

	@GetMapping("/brand")
	public Result brand() {
		return Result.ok("OK", brandService.get());
	}
}
