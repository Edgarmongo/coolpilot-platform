package com.coolskyai.modules.social.controller;

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
import com.coolskyai.modules.social.dto.SocialAccountSaveDTO;
import com.coolskyai.modules.social.service.SocialAccountService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 社交账号管理（平台所有者）
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class SocialAdminController {

	@Autowired
	private SocialAccountService socialAccountService;

	@GetMapping("/admin/socials")
	public Result list() {
		return Result.ok("OK", socialAccountService.listAll());
	}

	@OperationLogger("新增社交账号")
	@PostMapping("/admin/social")
	public Result create(@RequestBody SocialAccountSaveDTO dto) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", socialAccountService.create(dto));
		return Result.ok("已添加", data);
	}

	@OperationLogger("编辑社交账号")
	@PutMapping("/admin/social/{id}")
	public Result update(@PathVariable Long id, @RequestBody SocialAccountSaveDTO dto) {
		socialAccountService.update(id, dto);
		return Result.ok("已保存");
	}

	/**
	 * @param direction up / down
	 */
	@OperationLogger("调整社交账号顺序")
	@PutMapping("/admin/social/{id}/move")
	public Result move(@PathVariable Long id, @RequestParam String direction) {
		socialAccountService.move(id, direction);
		return Result.ok("已调整");
	}

	@OperationLogger("变更社交账号显隐")
	@PutMapping("/admin/social/{id}/visible")
	public Result changeVisible(@PathVariable Long id, @RequestParam Boolean visible) {
		socialAccountService.changeEnabled(id, visible);
		return Result.ok(visible != null && visible ? "已显示" : "已隐藏");
	}

	@OperationLogger("删除社交账号")
	@DeleteMapping("/admin/social/{id}")
	public Result delete(@PathVariable Long id) {
		socialAccountService.delete(id);
		return Result.ok("已删除");
	}
}
