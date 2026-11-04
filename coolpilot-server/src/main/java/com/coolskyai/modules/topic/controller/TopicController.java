package com.coolskyai.modules.topic.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.topic.service.TopicService;

/**
 * @Description: 专题（前台，公开）
 *
 * 专题是 SEO 与分享入口，未登录可读；文章内容本身的可见性仍由文章接口把关，
 * 这里只决定「哪些文章出现在专题页」。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class TopicController {

	@Autowired
	private TopicService topicService;

	@GetMapping("/topics")
	public Result list() {
		return Result.ok("OK", topicService.listPublic());
	}

	@GetMapping("/topic/{slug}")
	public Result detail(@PathVariable String slug) {
		return Result.ok("OK", topicService.detailBySlug(slug));
	}

	/**
	 * 文章页用：本篇属于哪些专题
	 */
	@GetMapping("/blog/{blogId}/topics")
	public Result blogTopics(@PathVariable Long blogId) {
		return Result.ok("OK", topicService.topicsOfBlog(blogId));
	}
}
