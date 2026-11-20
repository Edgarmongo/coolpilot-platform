package com.coolskyai.modules.dashboard;

import com.coolskyai.modules.dashboard.service.AdminDashboardService;
import com.coolskyai.util.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 驾驶舱统计的集成测试。
 *
 * <p>直接打服务层而不是 MockMvc：Controller 只是转发，真正容易出错的是 SQL 与补零逻辑，
 * 而这两样只有连上真实库才能验证。
 *
 * <p>需要数据库才跑 —— CI 没有库时整类跳过，不会把流水线弄红。
 *
 * <p>运行方式：
 * <pre>
 * export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:13306/coolpilot?..."
 * export DB_PASSWORD=...
 * export SPRING_DATA_REDIS_PORT=16379
 * export REDIS_PASSWORD=...
 * mvn test -Dtest=AdminDashboardServiceTest
 * </pre>
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class AdminDashboardServiceTest {

	private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Autowired
	private AdminDashboardService service;

	@Test
	void overviewIsSelfConsistent() {
		Map<String, Object> o = service.overview(null, null, true);
		print("overview", o);

		BigDecimal sales = (BigDecimal) o.get("salesAmount");
		BigDecimal refund = (BigDecimal) o.get("refundAmount");
		BigDecimal net = (BigDecimal) o.get("netAmount");
		assertNotNull(sales);
		assertTrue(sales.compareTo(BigDecimal.ZERO) >= 0, "成交金额不应为负");
		// 净收入必须等于成交减退款，三者不能各算各的
		assertEquals(0, sales.subtract(refund).compareTo(net), "netAmount 必须等于 salesAmount - refundAmount");

		// 待确认收款是快照值，不应随区间变化
		Object waiting = o.get("pendingPaymentCount");
		assertNotNull(waiting);

		// 口径说明必须随数据返回，否则前端只能写死文案
		@SuppressWarnings("unchecked")
		Map<String, String> notes = (Map<String, String>) o.get("notes");
		assertTrue(notes.containsKey("salesAmount"), "缺少成交金额口径说明");
		assertTrue(notes.get("salesAmount").contains("支付确认时间"), "成交口径必须写明按支付确认时间");
	}

	@Test
	void trendBucketsAreCompleteAndAligned() {
		Map<String, Object> t = service.trends(null, null, "DAY");
		print("trends(DAY)", t);

		@SuppressWarnings("unchecked")
		List<String> buckets = (List<String>) t.get("buckets");
		@SuppressWarnings("unchecked")
		Map<String, Object> series = (Map<String, Object>) t.get("series");

		assertFalse(buckets.isEmpty(), "日粒度应有完整桶序列");
		// 每个序列都必须和桶一一对齐，否则前端画图会错位
		for (String key : series.keySet()) {
			assertEquals(buckets.size(), java.lang.reflect.Array.getLength(series.get(key)),
					"序列 " + key + " 的长度必须与桶数量一致（缺失桶要补零）");
		}
		// 桶必须升序
		for (int i = 1; i < buckets.size(); i++) {
			assertTrue(buckets.get(i - 1).compareTo(buckets.get(i)) < 0, "桶序列必须严格递增");
		}
	}

	@Test
	void trendSupportsAllGranularities() {
		for (String g : new String[]{"HOUR", "DAY", "WEEK", "MONTH"}) {
			Map<String, Object> t = service.trends(null, null, g);
			@SuppressWarnings("unchecked")
			List<String> buckets = (List<String>) t.get("buckets");
			assertFalse(buckets.isEmpty(), g + " 粒度的桶不应为空");
			assertEquals(g, t.get("granularity"));
		}
	}

	@Test
	void todosCoverAllEightItems() {
		Map<String, Object> t = service.todos();
		print("todos", t);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) t.get("items");
		assertEquals(8, items.size(), "待办应有 8 类");

		long sum = 0;
		for (Map<String, Object> item : items) {
			assertNotNull(item.get("key"));
			assertNotNull(item.get("count"));
			assertNotNull(item.get("path"), item.get("key") + " 缺少跳转路径");
			assertTrue(String.valueOf(item.get("path")).startsWith("/"), "跳转路径必须是后台真实路由");
			if (((Number) item.get("count")).longValue() == 0) {
				assertEquals("muted", item.get("level"), "数量为 0 时应置灰");
			}
			sum += ((Number) item.get("count")).longValue();
		}
		assertEquals(sum, ((Number) t.get("total")).longValue(), "total 必须等于各项之和");
	}

	@Test
	void contentReportsUvAsUnavailable() {
		Map<String, Object> c = service.content(null, null, 5);
		print("content", c);

		// UV 当前不可用：必须返回 null 并给出原因，不能用 0 冒充
		assertNull(c.get("uniqueVisitors"), "UV 不可用时必须返回 null 而不是 0");
		assertNotNull(c.get("uniqueVisitorsReason"), "UV 不可用时必须说明原因");
		assertTrue(c.get("blogTotal") instanceof Number);
		assertTrue(((Number) c.get("totalViews")).longValue() >= 0);
	}

	@Test
	void usersReportActiveAsUnavailable() {
		Map<String, Object> u = service.users(null, null, 5);
		print("users", u);

		assertNull(u.get("activeUsers"), "活跃用户无数据源时必须返回 null");
		assertNotNull(u.get("activeUsersReason"));
		assertTrue(((Number) u.get("userTotal")).intValue() >= 0);
	}

	@Test
	void productsAndServicesReturnStructures() {
		Map<String, Object> p = service.products(null, null, 5);
		print("products", p);
		assertTrue(p.get("productTotal") instanceof Number);
		assertNotNull(p.get("topProducts"));
		assertNotNull(p.get("convertRateNote"), "转化率必须标注是累计口径");

		Map<String, Object> s = service.services(null, null, 5);
		print("services", s);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> funnel = (List<Map<String, Object>>) s.get("funnel");
		assertEquals(5, funnel.size(), "服务漏斗应有五个节点");
		// 漏斗不能倒挂：后一节点不应大于前一节点
		for (int i = 1; i < funnel.size(); i++) {
			long prev = ((Number) funnel.get(i - 1).get("count")).longValue();
			long cur = ((Number) funnel.get(i).get("count")).longValue();
			assertTrue(cur <= prev, "漏斗第 " + (i + 1) + " 级不应大于上一级");
		}
	}

	@Test
	void healthHasEightProbesWithFourStates() {
		Map<String, Object> h = service.health();
		print("health", h);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) h.get("items");
		assertEquals(8, items.size(), "健康度应有 8 个检查项");

		List<String> allowed = List.of("UP", "WARN", "DOWN", "UNKNOWN");
		for (Map<String, Object> item : items) {
			assertTrue(allowed.contains(item.get("status")),
					item.get("key") + " 的状态必须是四态之一，实际是 " + item.get("status"));
			assertNotNull(item.get("message"), item.get("key") + " 必须给出说明，哪怕是「无法判断」");
		}
		// MySQL 与 Redis 必须是真实探测结果，不能恒为 UP
		Map<String, Object> mysql = items.stream().filter(i -> "mysql".equals(i.get("key"))).findFirst().orElseThrow();
		assertEquals("UP", mysql.get("status"), "本地 MySQL 应可连通");
	}

	@Test
	void illegalRangeIsRejected() {
		// 开始晚于结束必须报错，不能静默纠正
		try {
			service.overview("2026-11-20 00:00:00", "2026-11-01 00:00:00", false);
			throw new AssertionError("应当拒绝 start > end 的区间");
		} catch (com.coolskyai.exception.BadRequestException e) {
			assertTrue(e.getMessage().contains("早于"), "错误信息应当说明开始时间必须早于结束时间");
		}
		// 小时粒度跨度过大必须拒绝
		try {
			service.trends("2026-01-01 00:00:00", LocalDateTime.now().format(F), "HOUR");
			throw new AssertionError("小时粒度超过 7 天应当被拒绝");
		} catch (com.coolskyai.exception.BadRequestException e) {
			assertTrue(e.getMessage().contains("7 天"));
		}
	}

	private void print(String name, Map<String, Object> data) {
		String json = JacksonUtils.writeValueAsString(data);
		// 截断，只为人工核对字段名，不代表完整响应
		System.out.println("[dashboard] " + name + " => " + (json.length() > 1200 ? json.substring(0, 1200) + "..." : json));
	}
}
