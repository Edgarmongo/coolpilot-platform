package com.coolskyai.modules.dashboard.service;

import com.coolskyai.modules.dashboard.mapper.DashboardMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 管理驾驶舱统计
 *
 * <p>只做三件事：解析区间 → 调 Mapper 取数 → 补齐空桶并组装口径说明。
 *
 * <p><b>三条不可违反的规则</b>：
 * <ol>
 *   <li>算不出来的指标返回 {@code null} 并附 {@code ...Reason}，<b>不用 0 冒充</b>。
 *       0 表示「确实是零」，null 表示「算不出来」，业务含义完全不同；</li>
 *   <li>金额一律 BigDecimal，除法显式 scale + HALF_UP，禁止 double；</li>
 *   <li>每个金额指标都在 {@code notes} 里写明时间归属，让口径跟着数据走。</li>
 * </ol>
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
@Service
public class AdminDashboardService {

	private static final String TZ = "Asia/Shanghai";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** 成交状态集合，与 OrderMapper 的成交口径保持一致 */
	private static final String PAID_STATUSES = "PAID,COMPLETED,REFUNDING,REFUNDED";

	@Autowired
	private DashboardMapper mapper;

	/** 健康度要真实 PING Redis，不能拿 MySQL 查询冒充 */
	@Autowired
	private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	// ==================================================================
	// 一、经营总览
	// ==================================================================

	public Map<String, Object> overview(String startTime, String endTime, boolean comparePeriod) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, "DAY");

		BigDecimal sales = nz(mapper.sumPaid(period.startText(), period.endText()));
		BigDecimal refund = nz(mapper.sumRefund(period.startText(), period.endText()));
		int paidOrders = mapper.countPaidOrders(period.startText(), period.endText());
		int newOrders = mapper.countCreatedOrders(period.startText(), period.endText());

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("salesAmount", sales);
		data.put("refundAmount", refund);
		data.put("netAmount", sales.subtract(refund));
		data.put("pendingPaymentCount", mapper.countWaitingConfirm());
		data.put("newOrderCount", newOrders);
		data.put("paidOrderCount", paidOrders);
		// 分母为 0 时返回 null，不返回 0 或 Infinity
		data.put("averageOrderValue", paidOrders == 0 ? null
				: sales.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP));
		// 分子分母属于不同批订单，只作趋势参考，UI 必须写清是「本期退款额 / 本期成交额」
		data.put("refundRate", sales.compareTo(BigDecimal.ZERO) == 0 ? null
				: refund.divide(sales, 4, RoundingMode.HALF_UP));
		data.put("currency", "CNY");
		data.put("comparison", comparePeriod ? compare(period, sales, newOrders) : null);
		data.put("notes", overviewNotes());
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	/** 环比：同口径查上一等长区间，只比金额与订单数 */
	private Map<String, Object> compare(DashboardPeriod period, BigDecimal sales, int newOrders) {
		DashboardPeriod prev = period.previous();
		BigDecimal prevSales = nz(mapper.sumPaid(prev.startText(), prev.endText()));
		int prevOrders = mapper.countCreatedOrders(prev.startText(), prev.endText());

		Map<String, Object> c = new LinkedHashMap<>();
		c.put("prevSalesAmount", prevSales);
		c.put("salesAmountDelta", ratio(sales, prevSales));
		c.put("prevNewOrderCount", prevOrders);
		c.put("newOrderCountDelta", ratio(BigDecimal.valueOf(newOrders), BigDecimal.valueOf(prevOrders)));
		return c;
	}

	/** 变化率；基数为 0 时无意义，返回 null 而不是 100% */
	private BigDecimal ratio(BigDecimal now, BigDecimal prev) {
		if (prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		return now.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP);
	}

	private Map<String, String> overviewNotes() {
		Map<String, String> notes = new LinkedHashMap<>();
		notes.put("salesAmount", "按支付确认时间统计，含已退款订单");
		notes.put("refundAmount", "按退款完成时间统计，仅含成功退款");
		notes.put("netAmount", "成交 − 退款；两项时间归属不同，跨期退款不回溯原成交期");
		notes.put("pendingPaymentCount", "当前等待人工确认的支付单数量，是快照值");
		notes.put("newOrderCount", "按下单时间统计，含未支付与已关闭");
		notes.put("averageOrderValue", "成交金额 ÷ 成交订单数");
		notes.put("refundRate", "本期退款额 ÷ 本期成交额，分子分母不是同一批订单");
		return notes;
	}

	// ==================================================================
	// 二、经营趋势
	// ==================================================================

	public Map<String, Object> trends(String startTime, String endTime, String granularity) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, granularity);
		List<String> buckets = period.buckets();

		// 每个序列先补零成同长度的数组，前端不用再处理缺桶
		double[] sales = new double[buckets.size()];
		double[] refund = new double[buckets.size()];
		long[] paidOrders = new long[buckets.size()];
		long[] newOrders = new long[buckets.size()];
		long[] newUsers = new long[buckets.size()];

		if (period.hourly()) {
			fillHourly(period, buckets, sales, paidOrders, mapper.hourlyPaid(period.startText(), period.endText()));
			fillHourlyCount(period, buckets, newOrders, mapper.hourlyNewOrders(period.startText(), period.endText()));
			// 小时粒度下用户增长按日聚合后再落到小时桶会失真，这里改为按日数据摊到当天 0 点桶
			fillDailyIntoHourly(period, buckets, newUsers, mapper.dailyNewUsers(period.startText(), period.endText()));
		} else {
			fillDaily(period, buckets, sales, refund, paidOrders, newOrders, newUsers);
		}

		double[] net = new double[buckets.size()];
		for (int i = 0; i < buckets.size(); i++) {
			net[i] = sales[i] - refund[i];
		}

		Map<String, Object> series = new LinkedHashMap<>();
		series.put("salesAmount", sales);
		series.put("refundAmount", refund);
		series.put("netAmount", net);
		series.put("paidOrderCount", paidOrders);
		series.put("newOrderCount", newOrders);
		series.put("newUserCount", newUsers);

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("granularity", period.granularity().name());
		data.put("buckets", buckets);
		data.put("series", series);
		data.put("notes", Map.of(
				"salesAmount", "按支付确认时间",
				"refundAmount", "按退款完成时间",
				"newOrderCount", "按下单时间，含未支付"
		));
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	private void fillDaily(DashboardPeriod period, List<String> buckets,
	                       double[] sales, double[] refund, long[] paidOrders, long[] newOrders, long[] newUsers) {
		Map<String, Integer> index = indexOf(buckets);
		for (Map<String, Object> row : mapper.dailyPaid(period.startText(), period.endText())) {
			Integer i = index.get(period.bucketOf(bucketDate(row)));
			if (i != null) {
				sales[i] = dec(row, "amount").doubleValue();
				paidOrders[i] = lng(row, "cnt");
			}
		}
		for (Map<String, Object> row : mapper.dailyRefund(period.startText(), period.endText())) {
			Integer i = index.get(period.bucketOf(bucketDate(row)));
			if (i != null) {
				refund[i] = dec(row, "amount").doubleValue();
			}
		}
		for (Map<String, Object> row : mapper.dailyNewOrders(period.startText(), period.endText())) {
			Integer i = index.get(period.bucketOf(bucketDate(row)));
			if (i != null) {
				newOrders[i] = lng(row, "cnt");
			}
		}
		for (Map<String, Object> row : mapper.dailyNewUsers(period.startText(), period.endText())) {
			Integer i = index.get(period.bucketOf(bucketDate(row)));
			if (i != null) {
				newUsers[i] = lng(row, "cnt");
			}
		}
	}

	private void fillHourly(DashboardPeriod period, List<String> buckets, double[] sales, long[] paidOrders,
	                        List<Map<String, Object>> rows) {
		Map<String, Integer> index = indexOf(buckets);
		for (Map<String, Object> row : rows) {
			Integer i = index.get(str(row, "bucket"));
			if (i != null) {
				sales[i] = dec(row, "amount").doubleValue();
				paidOrders[i] = lng(row, "cnt");
			}
		}
	}

	private void fillHourlyCount(DashboardPeriod period, List<String> buckets, long[] target,
	                             List<Map<String, Object>> rows) {
		Map<String, Integer> index = indexOf(buckets);
		for (Map<String, Object> row : rows) {
			Integer i = index.get(str(row, "bucket"));
			if (i != null) {
				target[i] = lng(row, "cnt");
			}
		}
	}

	/** 小时粒度下把「按日」的用户数落到当天 00:00 那个桶，其余小时为 0 */
	private void fillDailyIntoHourly(DashboardPeriod period, List<String> buckets, long[] target,
	                                 List<Map<String, Object>> rows) {
		Map<String, Integer> index = indexOf(buckets);
		Map<String, Long> byDay = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			byDay.merge(bucketDate(row).toString(), lng(row, "cnt"), Long::sum);
		}
		for (Map.Entry<String, Long> e : byDay.entrySet()) {
			Integer i = index.get(e.getKey() + " 00:00:00");
			if (i != null) {
				target[i] = e.getValue();
			}
		}
	}

	private Map<String, Integer> indexOf(List<String> buckets) {
		Map<String, Integer> index = new LinkedHashMap<>(buckets.size() * 2);
		for (int i = 0; i < buckets.size(); i++) {
			index.put(buckets.get(i), i);
		}
		return index;
	}

	// ==================================================================
	// 三、待办
	// ==================================================================

	public Map<String, Object> todos() {
		List<Map<String, Object>> demandRows = mapper.demandStatusCounts();
		List<Map<String, Object>> refundRows = mapper.refundStatusCounts();
		List<Map<String, Object>> projectRows = mapper.projectStatusCounts();
		List<Map<String, Object>> feedbackRows = mapper.feedbackStatusCounts();

		Map<String, long[]> demand = statusCounts(demandRows);
		Map<String, long[]> refund = statusCounts(refundRows);
		Map<String, long[]> project = statusCounts(projectRows);
		Map<String, long[]> feedback = statusCounts(feedbackRows);

		Map<String, String> demandAt = earliestMap(demandRows);
		Map<String, String> refundAt = earliestMap(refundRows);
		Map<String, String> feedbackAt = earliestMap(feedbackRows);

		List<Map<String, Object>> items = new ArrayList<>();

		items.add(todo("payment_confirm", "待确认收款", mapper.countWaitingConfirm(),
				"/trade/order?status=PENDING_CONFIRM", "danger",
				"用户已提交转账凭证，核对后确认即发权益",
				mapper.earliestWaitingConfirm()));
		items.add(todo("refund_applied", "待处理退款", count(refund, "APPLIED"),
				"/trade/refund?status=APPLIED", "danger",
				"同意申请 → 线下打款 → 确认后撤销权益",
				refundAt.get("APPLIED")));
		items.add(todo("demand_review", "待审核需求", count(demand, "PENDING_REVIEW"),
				"/service/demand?status=PENDING_REVIEW", "info",
				"客户新提交的需求，需要先确认是否接单",
				demandAt.get("PENDING_REVIEW")));
		items.add(todo("demand_quote", "待报价需求", count(demand, "PENDING_QUOTE"),
				"/service/demand?status=PENDING_QUOTE", "warn",
				"已通过审核，等待给出报价",
				demandAt.get("PENDING_QUOTE")));
		items.add(todo("demand_confirm", "待确认报价", count(demand, "PENDING_CONFIRM"),
				"/service/demand?status=PENDING_CONFIRM", "info",
				"报价单已发出，等待客户确认",
				demandAt.get("PENDING_CONFIRM")));
		items.add(todo("project_delivering", "待交付项目",
				count(project, "IN_PROGRESS") + count(project, "PENDING_ACCEPT"),
				"/service/project?status=IN_PROGRESS", "info",
				"进行中与待验收的项目合计",
				null));
		items.add(todo("milestone_overdue", "逾期里程碑", mapper.countOverdueMilestone(),
				"/service/project?overdue=1", "danger",
				"已过计划日期且未验收，due_date 为空的不计入",
				mapper.earliestOverdueMilestone()));
		items.add(todo("feedback_pending", "未处理反馈", pendingFeedback(feedback),
				"/community/feedback", "warn",
				"站内反馈待回复",
				feedbackAt.get("PENDING")));

		long total = 0;
		for (Map<String, Object> item : items) {
			total += ((Number) item.get("count")).longValue();
		}

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("total", total);
		data.put("items", items);
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	private Map<String, Object> todo(String key, String label, long count, String path,
	                                 String level, String hint, String earliest) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("key", key);
		m.put("label", label);
		// 为 0 时前端统一置灰，避免满屏红点
		m.put("count", count);
		m.put("path", path);
		m.put("level", count == 0 ? "muted" : level);
		m.put("hint", hint);
		m.put("earliest", earliest);
		return m;
	}

	/** 从 status 聚合结果里抽出每种的「最早发生时间」，供待办区排序参考 */
	private Map<String, String> earliestMap(List<Map<String, Object>> rows) {
		Map<String, String> map = new LinkedHashMap<>();
		if (rows == null) {
			return map;
		}
		for (Map<String, Object> row : rows) {
			map.put(str(row, "status").toUpperCase(), str(row, "earliest"));
		}
		return map;
	}

	private long pendingFeedback(Map<String, long[]> counts) {
		// 反馈表的状态取值：PENDING 待处理，其余视为已处理
		long n = count(counts, "PENDING");
		if (n == 0) {
			n = count(counts, "NEW");
		}
		return n;
	}

	// ==================================================================
	// 四、内容运营
	// ==================================================================

	public Map<String, Object> content(String startTime, String endTime, int limit) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, "DAY");
		int pv = mapper.countVisitByBehavior("BLOG", period.startText(), period.endText());

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("blogTotal", mapper.countBlog());
		data.put("blogPublished", mapper.countPublishedBlog());
		data.put("blogCreated", mapper.countBlogInRange(period.startText(), period.endText()));
		data.put("totalViews", mapper.sumBlogViews());
		data.put("pageViews", pv);
		// UV 当前不可用：代理未透传真实 IP/UA，访客标识坍缩。不用 PV 冒充
		data.put("uniqueVisitors", null);
		data.put("uniqueVisitorsReason",
				"访客标识由「小时戳+IP+UA」生成，而当前网关未透传真实 IP 与 User-Agent，"
						+ "同一小时内所有访客会被合并为一个，UV 暂时不具参考价值");
		data.put("favoriteCount", mapper.countFavoriteInRange(period.startText(), period.endText()));
		data.put("likeCount", mapper.countLikeInRange(period.startText(), period.endText()));
		data.put("commentCount", mapper.countCommentInRange(period.startText(), period.endText()));
		data.put("topArticles", mapper.topArticles(period.startText(), period.endText(), clampLimit(limit)));
		data.put("topKeywords", mapper.topSearch(period.startText(), period.endText(), 8));
		data.put("publishTrend", publishTrend(period));
		data.put("notes", Map.of(
				"totalViews", "blog.views 的累计值，无时间切片，只反映历史总量",
				"pageViews", "期间内 BLOG 行为的访问事件数",
				"topArticles", "按访问日志中的标题字符串聚合，文章改名会断档"
		));
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	/** 发布趋势：按日补齐空桶，前端柱状图直接用 */
	private Map<String, Object> publishTrend(DashboardPeriod period) {
		List<String> buckets = period.buckets();
		long[] counts = new long[buckets.size()];
		Map<String, Integer> index = indexOf(buckets);
		for (Map<String, Object> row : mapper.dailyBlogPublished(period.startText(), period.endText())) {
			Integer i = index.get(period.bucketOf(bucketDate(row)));
			if (i != null) {
				counts[i] = lng(row, "cnt");
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("buckets", buckets);
		m.put("counts", counts);
		return m;
	}

	// ==================================================================
	// 五、用户运营
	// ==================================================================
	public Map<String, Object> users(String startTime, String endTime, int limit) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, "DAY");
		int total = mapper.countUsers();
		int paid = mapper.countPaidUsers(period.startText(), period.endText());

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("userTotal", total);
		data.put("newUsers", mapper.countNewUsers(period.startText(), period.endText()));
		data.put("paidUsers", paid);
		data.put("activeUsers", null);
		data.put("activeUsersReason",
				"当前没有用户行为事件源：登录日志只记录后台管理员，last_login_time 是被覆盖写的单值，"
						+ "无法还原区间内的活跃情况。不用注册数替代");
		data.put("payRate", total == 0 ? null
				: BigDecimal.valueOf(paid).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
		data.put("paidUserRateNote", "分母是注册用户总数，不是访客数");
		data.put("topPayingUsers", mapper.topPayingUsers(period.startText(), period.endText(), clampLimit(limit)));
		data.put("latestUsers", mapper.latestUsers(clampLimit(limit)));
		data.put("notes", Map.of(
				"userTotal", "当前快照，不是期间发生量",
				"paidUsers", "期间内存在成交订单的去重用户"
		));
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	// ==================================================================
	// 六、商品运营
	// ==================================================================

	public Map<String, Object> products(String startTime, String endTime, int limit) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, "DAY");
		Map<String, Object> totals = mapper.productTotals();

		long views = totals == null ? 0 : lng(totals, "views");
		long sales = totals == null ? 0 : lng(totals, "sales");

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("productTotal", mapper.countProduct());
		data.put("onSaleCount", mapper.countOnSaleProduct());
		data.put("topProducts", mapper.topProducts(period.startText(), period.endText(), clampLimit(limit)));
		data.put("productViews", views);
		data.put("productSales", sales);
		// 两个计数字段都是单调累加，没有时间切片，只能给累计转化率
		data.put("convertRate", views == 0 ? null
				: BigDecimal.valueOf(sales).divide(BigDecimal.valueOf(views), 4, RoundingMode.HALF_UP));
		data.put("convertRateNote", "累计口径：sales_count ÷ view_count，无法按时间区间计算");
		data.put("notes", Map.of(
				"topProducts", "金额取自订单明细快照，不按商品当前价推算",
				"productSales", "sales_count 是净销量，退款会扣减"
		));
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	// ==================================================================
	// 七、服务交付
	// ==================================================================

	public Map<String, Object> services(String startTime, String endTime, int limit) {
		DashboardPeriod period = DashboardPeriod.of(startTime, endTime, "DAY");

		Map<String, long[]> demand = statusCounts(mapper.demandStatusCounts());
		Map<String, long[]> quote = statusCounts(mapper.quoteStatusCounts());
		Map<String, long[]> project = statusCounts(mapper.projectStatusCounts());
		Map<String, long[]> milestone = statusCounts(mapper.milestoneStatusCounts());

		long quoteAccepted = count(quote, "ACCEPTED");
		long quoteClosed = quoteAccepted + count(quote, "REJECTED") + count(quote, "EXPIRED");

		List<Map<String, Object>> funnel = new ArrayList<>();
		funnel.add(funnelStep("提交需求", count(demand, "PENDING_REVIEW") + count(demand, "PENDING_CONTACT")
				+ count(demand, "EVALUATING") + count(demand, "PENDING_QUOTE") + count(demand, "PENDING_CONFIRM")
				+ count(demand, "IN_PROGRESS") + count(demand, "PENDING_ACCEPT") + count(demand, "COMPLETED")
				+ count(demand, "DISPUTED") + count(demand, "REJECTED") + count(demand, "CANCELLED")));
		funnel.add(funnelStep("已报价", count(demand, "PENDING_CONFIRM") + count(demand, "IN_PROGRESS")
				+ count(demand, "PENDING_ACCEPT") + count(demand, "COMPLETED") + count(demand, "DISPUTED")));
		funnel.add(funnelStep("已确认", count(demand, "IN_PROGRESS") + count(demand, "PENDING_ACCEPT")
				+ count(demand, "COMPLETED") + count(demand, "DISPUTED")));
		funnel.add(funnelStep("进行中", count(demand, "IN_PROGRESS") + count(demand, "PENDING_ACCEPT")));
		funnel.add(funnelStep("已完成", count(demand, "COMPLETED")));

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("period", period(period));
		data.put("newDemand", mapper.countDemandInRange(period.startText(), period.endText()));
		data.put("demandPendingReview", count(demand, "PENDING_REVIEW"));
		data.put("demandPendingQuote", count(demand, "PENDING_QUOTE"));
		data.put("demandPendingConfirm", count(demand, "PENDING_CONFIRM"));
		data.put("projectInProgress", count(project, "IN_PROGRESS"));
		data.put("projectPendingAccept", count(project, "PENDING_ACCEPT"));
		data.put("projectCompleted", count(project, "COMPLETED"));
		data.put("milestoneOverdue", mapper.countOverdueMilestone());
		data.put("milestoneDueInWeek", mapper.countMilestoneDueInWeek());
		data.put("milestoneAccepted", count(milestone, "ACCEPTED"));
		data.put("acceptedAmount", nz(mapper.sumAcceptedMilestone()));
		data.put("quoteConvertRate", quoteClosed == 0 ? null
				: BigDecimal.valueOf(quoteAccepted).divide(BigDecimal.valueOf(quoteClosed), 4, RoundingMode.HALF_UP));
		data.put("funnel", funnel);
		data.put("activeProjects", mapper.activeProjects(clampLimit(limit)));
		data.put("notes", Map.of(
				"funnel", "各节点按需求当前状态归类，是存量而非同时期转化",
				"milestoneOverdue", "due_date 为空的不计入逾期",
				"quoteConvertRate", "已接受 ÷ (已接受+已拒绝+已过期)"
		));
		data.put("updatedAt", STAMP.format(LocalDateTime.now()));
		return data;
	}

	private Map<String, Object> funnelStep(String name, long count) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", name);
		m.put("count", count);
		return m;
	}

	// ==================================================================
	// 八、平台健康
	// ==================================================================

	public Map<String, Object> health() {
		List<Map<String, Object>> items = new ArrayList<>();

		items.add(healthItem("backend", "后端服务", "UP", "进程存活", null));

		boolean sqlOk = probeSql();
		items.add(healthItem("mysql", "MySQL", sqlOk ? "UP" : "DOWN",
				sqlOk ? "SELECT 1 正常" : "SELECT 1 失败", null));

		boolean redisOk = probeRedis();
		items.add(healthItem("redis", "Redis", redisOk ? "UP" : "DOWN",
				redisOk ? "PING 正常" : "PING 失败", null));

		items.add(storageItem());
		items.add(aiItem());

		Map<String, Object> job = mapper.latestJobLog();
		if (job == null) {
			items.add(healthItem("job", "定时任务", "UNKNOWN", "还没有执行记录", null));
		} else {
			// 写入侧是 jobLog.setStatus(true) 表示成功（Boolean → tinyint，true 存 1）。
			// 这里不能用 0 判成功，会把正常任务报成 DOWN
			long st = lng(job, "status");
			String detail = str(job, "methodName") + " · " + shortTime(job, "createTime")
					+ " · " + lng(job, "times") + "ms";
			items.add(healthItem("job", "定时任务", st == 1 ? "UP" : "DOWN",
					(st == 1 ? "最近一次执行成功 · " : "最近一次执行失败 · ") + detail, null));
		}

		DashboardPeriod payWindow = DashboardPeriod.of(null, null, "DAY");
		int payFail = mapper.countPaymentFail(payWindow.startText(), payWindow.endText());
		items.add(healthItem("payment", "支付链路", payFail == 0 ? "UP" : "WARN",
				"近 30 天失败 " + payFail + " 笔", null));

		// 阈值按个人站实际情况定：个别异常是常态，只有明显放量才需要报警
		int exceptions = mapper.countExceptionWithin(24);
		String exStatus = exceptions == 0 ? "UP" : (exceptions > 10 ? "DOWN" : "WARN");
		items.add(healthItem("exception", "系统异常", exStatus, "近 24 小时 " + exceptions + " 条", null));

		long down = items.stream().filter(i -> "DOWN".equals(i.get("status"))).count();
		long warn = items.stream().filter(i -> "WARN".equals(i.get("status"))).count();

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("overall", down > 0 ? "DOWN" : (warn > 0 ? "DEGRADED" : "HEALTHY"));
		data.put("checkedAt", STAMP.format(LocalDateTime.now()));
		data.put("items", items);
		return data;
	}

	private Map<String, Object> healthItem(String key, String label, String status, String message, Long latency) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("key", key);
		m.put("label", label);
		m.put("status", status);
		m.put("message", message);
		m.put("latencyMs", latency);
		return m;
	}

	private Map<String, Object> storageItem() {
		try {
			File root = new File(System.getProperty("user.dir"));
			long usable = root.getUsableSpace();
			long total = root.getTotalSpace();
			long usedBytes = mapper.sumFileSize();
			double usableRatio = total == 0 ? 1 : (double) usable / total;
			String status = usableRatio < 0.05 ? "DOWN" : (usableRatio < 0.12 ? "WARN" : "UP");
			String message = "文件 " + mb(usedBytes) + " · 磁盘剩余 " + (int) (usableRatio * 100) + "%";
			return healthItem("storage", "文件存储", status, message, null);
		} catch (Exception e) {
			// 取不到磁盘信息时明确说不知道，不假装正常
			return healthItem("storage", "文件存储", "UNKNOWN", "无法读取磁盘容量", null);
		}
	}

	private Map<String, Object> aiItem() {
		Map<String, Object> usage = mapper.aiUsage24h();
		long total = usage == null ? 0 : lng(usage, "total");
		if (total == 0) {
			return healthItem("ai", "AI 服务", "UNKNOWN", "近 24 小时无调用，无法判断", null);
		}
		long ok = lng(usage, "ok");
		long avg = lng(usage, "avgMs");
		double rate = (double) ok / total;
		String status = rate >= 0.95 ? "UP" : (rate >= 0.8 ? "WARN" : "DOWN");
		return healthItem("ai", "AI 服务", status,
				"近 24h " + total + " 次，成功率 " + Math.round(rate * 100) + "% · 平均 " + avg + "ms", avg);
	}

	private boolean probeSql() {
		try {
			mapper.countBlog();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 真实探测 Redis 连接。
	 * 不能拿「MySQL 查询成功」冒充 —— 那会把 Redis 宕机报告成正常。
	 */
	private boolean probeRedis() {
		try {
			String pong = redisTemplate.execute(
					(org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
			return "PONG".equalsIgnoreCase(pong);
		} catch (Exception e) {
			return false;
		}
	}

	// ==================================================================
	// 工具
	// ==================================================================

	private Map<String, Object> period(DashboardPeriod period) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("startTime", period.startText());
		p.put("endTime", period.endText());
		p.put("timezone", TZ);
		p.put("granularity", period.granularity().name());
		return p;
	}

	private Map<String, long[]> statusCounts(List<Map<String, Object>> rows) {
		Map<String, long[]> map = new LinkedHashMap<>();
		if (rows == null) {
			return map;
		}
		for (Map<String, Object> row : rows) {
			map.put(str(row, "status").toUpperCase(), new long[]{lng(row, "cnt")});
		}
		return map;
	}

	private long count(Map<String, long[]> counts, String status) {
		long[] v = counts.get(status);
		return v == null ? 0 : v[0];
	}

	private LocalDate bucketDate(Map<String, Object> row) {
		String bucket = str(row, "bucket");
		if (bucket.length() >= 10) {
			return LocalDate.parse(bucket.substring(0, 10));
		}
		return LocalDate.now();
	}

	/** 大小写不敏感地取列值，兼容 MyBatis 不同版本的 Map key 形态 */
	private Object pick(Map<String, Object> row, String key) {
		if (row == null) {
			return null;
		}
		if (row.containsKey(key)) {
			return row.get(key);
		}
		for (Map.Entry<String, Object> e : row.entrySet()) {
			if (e.getKey().equalsIgnoreCase(key)) {
				return e.getValue();
			}
		}
		return null;
	}

	private String str(Map<String, Object> row, String key) {
		Object v = pick(row, key);
		return v == null ? "" : String.valueOf(v);
	}

	/** 时间列统一收敛成 MM-dd HH:mm，JDBC 可能给 Timestamp、也可能给 ISO 串 */
	private String shortTime(Map<String, Object> row, String key) {
		String raw = str(row, key);
		if (raw.length() >= 16) {
			return raw.substring(5, 16).replace('T', ' ');
		}
		return raw;
	}

	private long lng(Map<String, Object> row, String key) {
		Object v = pick(row, key);
		if (v == null) {
			return 0L;
		}
		if (v instanceof Number n) {
			return n.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(v));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private BigDecimal dec(Map<String, Object> row, String key) {
		Object v = pick(row, key);
		if (v == null) {
			return BigDecimal.ZERO;
		}
		if (v instanceof BigDecimal b) {
			return b;
		}
		if (v instanceof Number n) {
			return BigDecimal.valueOf(n.doubleValue());
		}
		try {
			return new BigDecimal(String.valueOf(v));
		} catch (NumberFormatException e) {
			return BigDecimal.ZERO;
		}
	}

	private BigDecimal nz(BigDecimal v) {
		return v == null ? BigDecimal.ZERO : v;
	}

	private int clampLimit(int limit) {
		if (limit <= 0) {
			return 5;
		}
		return Math.min(limit, 20);
	}

	private String mb(long bytes) {
		if (bytes <= 0) {
			return "0B";
		}
		double m = bytes / 1024.0 / 1024.0;
		return m >= 1024 ? String.format("%.1fGB", m / 1024) : String.format("%.0fMB", m);
	}
}
