package com.coolskyai.modules.opensource.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.opensource.dto.OssProjectDTO;
import com.coolskyai.modules.opensource.service.OssProjectService;

import java.util.HashMap;
import java.util.Map;

/**
 * 开源项目档案管理（平台所有者）。
 *
 * 标签与关联文章不单独开接口：后台编辑页是整表提交，
 * 保存接口里做全量覆盖，语义只有一个 —— 提交什么就是什么。
 * 多开两个「只改标签」的接口，反而会出现「保存了正文但标签没保存」的歧义。
 */
@RestController
public class OssAdminController {

	@Autowired
	private OssProjectService ossProjectService;

	@GetMapping("/admin/oss/projects")
	public Result list(@RequestParam(required = false) String publishStatus,
	                   @RequestParam(required = false) String researchStatus,
	                   @RequestParam(required = false) String category,
	                   @RequestParam(required = false) String keyword,
	                   @RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", ossProjectService.pageAdmin(publishStatus, researchStatus, category,
				keyword, pageNum, pageSize));
	}

	@GetMapping("/admin/oss/project/{id}")
	public Result detail(@PathVariable Long id) {
		return Result.ok("OK", ossProjectService.adminDetail(id));
	}

	@OperationLogger("创建开源项目档案")
	@PostMapping("/admin/oss/project")
	public Result create(@RequestBody OssProjectDTO dto) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", ossProjectService.create(dto));
		return Result.ok("创建成功", data);
	}

	@OperationLogger("更新开源项目档案")
	@PutMapping("/admin/oss/project")
	public Result update(@RequestBody OssProjectDTO dto) {
		ossProjectService.update(dto);
		return Result.ok("更新成功", null);
	}

	@OperationLogger("发布开源项目档案")
	@PostMapping("/admin/oss/project/{id}/publish")
	public Result publish(@PathVariable Long id) {
		ossProjectService.publish(id);
		return Result.ok("已发布", null);
	}

	@OperationLogger("归档开源项目档案")
	@PostMapping("/admin/oss/project/{id}/archive")
	public Result archive(@PathVariable Long id) {
		ossProjectService.archive(id);
		return Result.ok("已归档", null);
	}
}
