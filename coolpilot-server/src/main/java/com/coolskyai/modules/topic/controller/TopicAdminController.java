package com.coolskyai.modules.topic.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.topic.dto.TopicArticleBindDTO;
import com.coolskyai.modules.topic.dto.TopicSaveDTO;
import com.coolskyai.modules.topic.service.TopicService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 专题管理（平台所有者）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class TopicAdminController {

	@Autowired
	private TopicService topicService;

	@GetMapping("/admin/topics")
	public Result list(@RequestParam(required = false) Boolean published,
	                   @RequestParam(required = false) String keyword,
	                   @RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", topicService.pageAdmin(published, keyword, pageNum, pageSize));
	}

	@GetMapping("/admin/topic/{id}")
	public Result detail(@PathVariable Long id) {
		return Result.ok("OK", topicService.adminDetail(id));
	}

	@OperationLogger("创建专题")
	@PostMapping("/admin/topic")
	public Result create(@RequestBody TopicSaveDTO dto) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", topicService.create(dto));
		return Result.ok("创建成功", data);
	}

	@OperationLogger("编辑专题")
	@PutMapping("/admin/topic/{id}")
	public Result update(@PathVariable Long id, @RequestBody TopicSaveDTO dto) {
		topicService.update(id, dto);
		return Result.ok("保存成功");
	}

	@OperationLogger("变更专题状态")
	@PutMapping("/admin/topic/{id}/status")
	public Result changeStatus(@PathVariable Long id, @RequestParam Boolean published) {
		topicService.changePublished(id, published);
		return Result.ok("操作成功");
	}

	@OperationLogger("绑定专题文章")
	@PutMapping("/admin/topic/{id}/articles")
	public Result bind(@PathVariable Long id, @RequestBody TopicArticleBindDTO dto) {
		topicService.bindArticles(id, dto.getArticleIds());
		return Result.ok("已保存", null);
	}

	@OperationLogger("删除专题")
	@DeleteMapping("/admin/topic/{id}")
	public Result delete(@PathVariable Long id) {
		topicService.delete(id);
		return Result.ok("删除成功");
	}
}
