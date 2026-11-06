package com.coolskyai.modules.portfolio.controller;

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
import com.coolskyai.modules.portfolio.dto.PortfolioSaveDTO;
import com.coolskyai.modules.portfolio.service.PortfolioService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 项目展示管理（平台所有者）
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@RestController
public class PortfolioAdminController {

	@Autowired
	private PortfolioService portfolioService;

	/**
	 * 列表不含 content（可能几十 KB），编辑时再按 id 取全文
	 */
	@GetMapping("/admin/portfolios")
	public Result list() {
		return Result.ok("OK", portfolioService.listAll());
	}

	@GetMapping("/admin/portfolio/{id}")
	public Result detail(@PathVariable Long id) {
		return Result.ok("OK", portfolioService.getById(id));
	}

	@OperationLogger("新增项目展示")
	@PostMapping("/admin/portfolio")
	public Result create(@RequestBody PortfolioSaveDTO dto) {
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", portfolioService.create(dto));
		return Result.ok("已添加", data);
	}

	@OperationLogger("编辑项目展示")
	@PutMapping("/admin/portfolio/{id}")
	public Result update(@PathVariable Long id, @RequestBody PortfolioSaveDTO dto) {
		portfolioService.update(id, dto);
		return Result.ok("已保存");
	}

	/**
	 * @param direction up / down
	 */
	@OperationLogger("调整项目顺序")
	@PutMapping("/admin/portfolio/{id}/move")
	public Result move(@PathVariable Long id, @RequestParam String direction) {
		portfolioService.move(id, direction);
		return Result.ok("已调整");
	}

	@OperationLogger("变更项目显隐")
	@PutMapping("/admin/portfolio/{id}/visible")
	public Result changeVisible(@PathVariable Long id, @RequestParam Boolean visible) {
		portfolioService.changeEnabled(id, visible);
		return Result.ok(visible != null && visible ? "已显示" : "已隐藏");
	}

	@OperationLogger("删除项目展示")
	@DeleteMapping("/admin/portfolio/{id}")
	public Result delete(@PathVariable Long id) {
		portfolioService.delete(id);
		return Result.ok("已删除");
	}
}
