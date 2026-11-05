package com.coolskyai.modules.nav.controller;

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
import com.coolskyai.modules.nav.dto.NavMenuSaveDTO;
import com.coolskyai.modules.nav.service.NavMenuService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 导航管理（平台所有者）
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class NavAdminController {

	@Autowired
	private NavMenuService navMenuService;

	@GetMapping("/admin/navs")
	public Result list() {
		return Result.ok("OK", navMenuService.listAll());
	}

	@OperationLogger("新增导航菜单")
	@PostMapping("/admin/nav")
	public Result create(@RequestBody NavMenuSaveDTO dto) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", navMenuService.create(dto));
		return Result.ok("已添加", data);
	}

	@OperationLogger("编辑导航菜单")
	@PutMapping("/admin/nav/{id}")
	public Result update(@PathVariable Long id, @RequestBody NavMenuSaveDTO dto) {
		navMenuService.update(id, dto);
		return Result.ok("已保存");
	}

	/**
	 * @param direction up / down
	 */
	@OperationLogger("调整导航顺序")
	@PutMapping("/admin/nav/{id}/move")
	public Result move(@PathVariable Long id, @RequestParam String direction) {
		navMenuService.move(id, direction);
		return Result.ok("已调整");
	}

	@OperationLogger("变更导航显隐")
	@PutMapping("/admin/nav/{id}/visible")
	public Result changeVisible(@PathVariable Long id, @RequestParam Boolean visible) {
		navMenuService.changeVisible(id, visible);
		return Result.ok(visible != null && visible ? "已显示" : "已隐藏");
	}

	@OperationLogger("删除导航菜单")
	@DeleteMapping("/admin/nav/{id}")
	public Result delete(@PathVariable Long id) {
		navMenuService.delete(id);
		return Result.ok("已删除");
	}
}
