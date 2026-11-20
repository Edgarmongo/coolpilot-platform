package com.coolskyai.modules.dashboard.controller;

import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.dashboard.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Description: 管理驾驶舱（后台）
 *
 * <p>八个接口按业务域聚合，不为单个指标开接口。全部是只读 GET，不带任何写操作。
 *
 * <p>与旧 {@code GET /admin/dashboard}（博客流量看板）**路径不冲突**：那一个是父路径，
 * 这里是子路径。旧接口保留不动，等确认无消费者后再单独治理。
 *
 * <p>时间参数一律可选：不传时按最近 30 天，响应里的 {@code period} 会回显实际使用的区间，
 * 避免前端与后端对「最近 30 天」的理解不一致。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
@RestController
public class AdminDashboardController {

	@Autowired
	private AdminDashboardService dashboardService;

	/** 经营总览：成交、退款、净收入、待确认、订单数、客单价 */
	@GetMapping("/admin/dashboard/overview")
	public Result overview(@RequestParam(required = false) String startTime,
	                       @RequestParam(required = false) String endTime,
	                       @RequestParam(defaultValue = "false") boolean comparePeriod) {
		return Result.ok("OK", dashboardService.overview(startTime, endTime, comparePeriod));
	}

	/** 经营趋势：成交/退款/净流入/订单/用户，缺失时间桶补零 */
	@GetMapping("/admin/dashboard/trends")
	public Result trends(@RequestParam(required = false) String startTime,
	                     @RequestParam(required = false) String endTime,
	                     @RequestParam(required = false) String granularity) {
		return Result.ok("OK", dashboardService.trends(startTime, endTime, granularity));
	}

	/** 业务待办：八类待处理事项的计数与跳转 */
	@GetMapping("/admin/dashboard/todos")
	public Result todos() {
		return Result.ok("OK", dashboardService.todos());
	}

	/** 内容运营 */
	@GetMapping("/admin/dashboard/content")
	public Result content(@RequestParam(required = false) String startTime,
	                      @RequestParam(required = false) String endTime,
	                      @RequestParam(defaultValue = "5") int limit) {
		return Result.ok("OK", dashboardService.content(startTime, endTime, limit));
	}

	/** 用户运营 */
	@GetMapping("/admin/dashboard/users")
	public Result users(@RequestParam(required = false) String startTime,
	                    @RequestParam(required = false) String endTime,
	                    @RequestParam(defaultValue = "5") int limit) {
		return Result.ok("OK", dashboardService.users(startTime, endTime, limit));
	}

	/** 商品运营 */
	@GetMapping("/admin/dashboard/products")
	public Result products(@RequestParam(required = false) String startTime,
	                       @RequestParam(required = false) String endTime,
	                       @RequestParam(defaultValue = "5") int limit) {
		return Result.ok("OK", dashboardService.products(startTime, endTime, limit));
	}

	/** 服务交付：需求漏斗、项目进度、里程碑逾期 */
	@GetMapping("/admin/dashboard/services")
	public Result services(@RequestParam(required = false) String startTime,
	                       @RequestParam(required = false) String endTime,
	                       @RequestParam(defaultValue = "5") int limit) {
		return Result.ok("OK", dashboardService.services(startTime, endTime, limit));
	}

	/** 平台健康：逐项真实探测，数据不足时返回 UNKNOWN */
	@GetMapping("/admin/dashboard/health")
	public Result health() {
		return Result.ok("OK", dashboardService.health());
	}
}
