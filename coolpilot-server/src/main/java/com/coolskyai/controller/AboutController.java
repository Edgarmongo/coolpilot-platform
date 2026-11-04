package com.coolskyai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.VisitLogger;
import com.coolskyai.enums.VisitBehavior;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.AboutService;

/**
 * @Description: 关于我页面
 * @Author: CoolSkyAI
 * @Date: 2020-08-31
 */
@RestController
public class AboutController {
	@Autowired
	AboutService aboutService;

	/**
	 * 获取关于我页面信息
	 *
	 * @return
	 */
	@VisitLogger(VisitBehavior.ABOUT)
	@GetMapping("/about")
	public Result about() {
		return Result.ok("获取成功", aboutService.getAboutInfo());
	}
}
