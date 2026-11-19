package com.coolskyai.modules.opensource.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.opensource.service.OssProjectService;

/**
 * 开源项目商业观察（前台，匿名可读）。
 *
 * 白名单是 /open-source/**：这条通配是**有意**的。
 * 栏目下全是公开内容，逐个列举容易漏一个就是 403；
 * 代价是以后不能在 /open-source/ 下放任何需要鉴权的接口 —— 真要放，换前缀。
 *
 * 所有返回都只含 publish_status='published' 的数据，参数无法绕过。
 */
@RestController
public class OpenSourceController {

	@Autowired
	private OssProjectService ossProjectService;

	/** 项目分页列表：category / tag / q / featured / sort(default|density) */
	@GetMapping("/open-source/projects")
	public Result projects(@RequestParam(required = false) String category,
	                       @RequestParam(required = false) String tag,
	                       @RequestParam(required = false) String q,
	                       @RequestParam(required = false) Boolean featured,
	                       @RequestParam(required = false) String sort,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "12") Integer pageSize) {
		return Result.ok("OK", ossProjectService.pageProjects(category, tag, q, featured, sort, pageNum, pageSize));
	}

	/** 档案详情：含 contentHtml、关联文章、相关项目、评估卡 */
	@GetMapping("/open-source/projects/{slug}")
	public Result project(@PathVariable String slug) {
		return Result.ok("OK", ossProjectService.detail(slug));
	}

	/** 某项目的关联文章（只含已发布文章） */
	@GetMapping("/open-source/projects/{slug}/articles")
	public Result projectArticles(@PathVariable String slug) {
		return Result.ok("OK", ossProjectService.articlesOfSlug(slug));
	}

	/** 文章流：栏目首页「最新项目研究」用 */
	@GetMapping("/open-source/articles")
	public Result articles(@RequestParam(required = false) String category,
	                       @RequestParam(required = false) String tag,
	                       @RequestParam(required = false) String q,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "12") Integer pageSize) {
		return Result.ok("OK", ossProjectService.pageArticles(category, tag, q, pageNum, pageSize));
	}

	/** 领域枚举 + 项目数（无项目的领域也返回 0） */
	@GetMapping("/open-source/categories")
	public Result categories() {
		return Result.ok("OK", ossProjectService.categories());
	}

	/** 项目标签 + 计数，只统计已发布项目 */
	@GetMapping("/open-source/tags")
	public Result tags() {
		return Result.ok("OK", ossProjectService.tags());
	}

	/** 精选：featured 项目的商业分析文章；没有则退回项目本身 */
	@GetMapping("/open-source/featured")
	public Result featured() {
		return Result.ok("OK", ossProjectService.featured());
	}

	/** 栏目内搜索，返回 {projects, articles} */
	@GetMapping("/open-source/search")
	public Result search(@RequestParam(required = false) String q) {
		return Result.ok("OK", ossProjectService.search(q));
	}

	/** 文章反查所属项目，供文章页底部卡片用 */
	@GetMapping("/open-source/blog/{blogId}/project")
	public Result projectOfBlog(@PathVariable Long blogId) {
		return Result.ok("OK", ossProjectService.projectOfBlog(blogId));
	}
}
