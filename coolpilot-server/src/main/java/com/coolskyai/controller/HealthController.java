package com.coolskyai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Description: 健康检查
 *
 * 给容器 healthcheck、负载均衡、uptime 监控用。
 * 与业务接口不同，这里返回真实的 HTTP 状态码：
 *   200 = 全部依赖可用，503 = 至少一个依赖不可用。
 * 因为监控系统只看状态码，body 里的 detail 是给人排查用的。
 *
 * 放在 PUBLIC 白名单里，但只暴露「通/不通」和组件名，
 * 不输出连接串、版本号以外的任何环境信息。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class HealthController {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		Map<String, Object> body = new LinkedHashMap<>(8);
		body.put("time", OffsetDateTime.now().toString());
		body.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

		boolean dbUp = checkDatabase();
		boolean redisUp = checkRedis();
		body.put("db", dbUp ? "UP" : "DOWN");
		body.put("redis", redisUp ? "UP" : "DOWN");
		boolean up = dbUp && redisUp;
		body.put("status", up ? "UP" : "DOWN");
		return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

	/**
	 * 轻量存活探针：不查依赖，只证明进程还能响应
	 */
	@GetMapping("/health/live")
	public ResponseEntity<Map<String, Object>> live() {
		Map<String, Object> body = new LinkedHashMap<>(2);
		body.put("status", "UP");
		return ResponseEntity.ok(body);
	}

	private boolean checkDatabase() {
		try {
			Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return one != null && one == 1;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean checkRedis() {
		try {
			// execute 会在回调结束后归还连接，直接 getConnection() 容易漏关
			String pong = redisTemplate.execute(
					(org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
			return "PONG".equalsIgnoreCase(pong);
		} catch (Exception e) {
			return false;
		}
	}
}
