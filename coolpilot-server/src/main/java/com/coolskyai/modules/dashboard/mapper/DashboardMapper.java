package com.coolskyai.modules.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @Description: 驾驶舱只读聚合
 *
 * <p><b>这里只有 SELECT。</b>仪表盘是业务数据的只读视图，任何写操作都不该出现在本层。
 *
 * <p>几条贯穿全文件的约定：
 * <ul>
 *   <li>区间一律左闭右开 {@code >= startTime AND < endTime}；</li>
 *   <li>成交按 {@code paid_time}（支付确认时间）、退款按 {@code refund_time}（到账时间），
 *       两者都是资金实际发生的时间；</li>
 *   <li>成交状态集合固定为 PAID / COMPLETED / REFUNDING / REFUNDED，
 *       与 {@code OrderMapper.sumPaidAmount} 保持一致；</li>
 *   <li>趋势类 SQL 一律按「日」聚合，周/月由 Service 二次合并 —— 一次查询服务三种粒度，
 *       也避免为每种粒度各写一套 SQL。</li>
 * </ul>
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-21
 */
@Mapper
public interface DashboardMapper {

	// ==================================================================
	// 一、经营总览
	// ==================================================================

	/** 期间成交金额：按支付确认时间归属 */
	@Select("SELECT IFNULL(SUM(pay_amount), 0) FROM orders " +
			"WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND paid_time >= #{startTime} AND paid_time < #{endTime}")
	BigDecimal sumPaid(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 期间成交订单数 */
	@Select("SELECT COUNT(*) FROM orders " +
			"WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND paid_time >= #{startTime} AND paid_time < #{endTime}")
	int countPaidOrders(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 期间新增订单：按下单时间，含未支付 */
	@Select("SELECT COUNT(*) FROM orders WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countCreatedOrders(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 期间退款金额：按退款完成时间归属，仅成功 */
	@Select("SELECT IFNULL(SUM(amount), 0) FROM refund " +
			"WHERE status = 'SUCCESS' " +
			"AND refund_time >= #{startTime} AND refund_time < #{endTime}")
	BigDecimal sumRefund(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 待确认收款：数的是支付单，且是当前快照，不带时间条件 */
	@Select("SELECT COUNT(*) FROM payment WHERE status = 'WAIT_CONFIRM'")
	int countWaitingConfirm();

	// ==================================================================
	// 二、经营趋势（按日 / 按小时）
	// ==================================================================

	@Select("SELECT DATE_FORMAT(paid_time, '%Y-%m-%d 00:00:00') AS bucket, " +
			"IFNULL(SUM(pay_amount), 0) AS amount, COUNT(*) AS cnt FROM orders " +
			"WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND paid_time >= #{startTime} AND paid_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> dailyPaid(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(refund_time, '%Y-%m-%d 00:00:00') AS bucket, " +
			"IFNULL(SUM(amount), 0) AS amount FROM refund " +
			"WHERE status = 'SUCCESS' " +
			"AND refund_time >= #{startTime} AND refund_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> dailyRefund(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d 00:00:00') AS bucket, COUNT(*) AS cnt FROM user " +
			"WHERE role = 'ROLE_user' AND create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> dailyNewUsers(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d 00:00:00') AS bucket, COUNT(*) AS cnt FROM orders " +
			"WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> dailyNewOrders(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(paid_time, '%Y-%m-%d %H:00:00') AS bucket, " +
			"IFNULL(SUM(pay_amount), 0) AS amount, COUNT(*) AS cnt FROM orders " +
			"WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND paid_time >= #{startTime} AND paid_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> hourlyPaid(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:00:00') AS bucket, COUNT(*) AS cnt FROM orders " +
			"WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> hourlyNewOrders(@Param("startTime") String startTime, @Param("endTime") String endTime);

	// ==================================================================
	// 三、内容运营
	// ==================================================================

	@Select("SELECT COUNT(*) FROM blog")
	int countBlog();

	@Select("SELECT COUNT(*) FROM blog WHERE is_published = 1")
	int countPublishedBlog();

	@Select("SELECT COUNT(*) FROM blog WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countBlogInRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d 00:00:00') AS bucket, COUNT(*) AS cnt FROM blog " +
			"WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY bucket ORDER BY bucket")
	List<Map<String, Object>> dailyBlogPublished(@Param("startTime") String startTime,
	                                             @Param("endTime") String endTime);

	/** 累计阅读：blog.views 由 Redis 累加后定时刷盘，是单调累加值，没有时间切片 */
	@Select("SELECT IFNULL(SUM(views), 0) FROM blog")
	long sumBlogViews();

	@Select("SELECT COUNT(*) FROM visit_log " +
			"WHERE behavior = #{behavior} " +
			"AND create_time >= #{startTime} AND create_time < #{endTime}")
	int countVisitByBehavior(@Param("behavior") String behavior,
	                         @Param("startTime") String startTime,
	                         @Param("endTime") String endTime);

	/**
	 * 热门文章。content 存的是标题字符串而不是 blog_id —— 文章改名会导致同一篇分裂成两行，
	 * 这是当前采集粒度的已知限制，接口层会把标题原样返回。
	 */
	@Select("SELECT content AS title, COUNT(*) AS pv FROM visit_log " +
			"WHERE behavior = 'BLOG' AND content IS NOT NULL AND content <> '' " +
			"AND create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY content ORDER BY pv DESC LIMIT #{limit}")
	List<Map<String, Object>> topArticles(@Param("startTime") String startTime,
	                                      @Param("endTime") String endTime,
	                                      @Param("limit") int limit);

	@Select("SELECT content AS keyword, COUNT(*) AS pv FROM visit_log " +
			"WHERE behavior = 'SEARCH' AND content IS NOT NULL AND content <> '' " +
			"AND create_time >= #{startTime} AND create_time < #{endTime} " +
			"GROUP BY content ORDER BY pv DESC LIMIT #{limit}")
	List<Map<String, Object>> topSearch(@Param("startTime") String startTime,
	                                    @Param("endTime") String endTime,
	                                    @Param("limit") int limit);

	@Select("SELECT COUNT(*) FROM favorite WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countFavoriteInRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT COUNT(*) FROM like_record WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countLikeInRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT COUNT(*) FROM comment WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countCommentInRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

	// ==================================================================
	// 四、用户运营
	// ==================================================================

	@Select("SELECT COUNT(*) FROM user WHERE role = 'ROLE_user'")
	int countUsers();

	@Select("SELECT COUNT(*) FROM user WHERE role = 'ROLE_user' " +
			"AND create_time >= #{startTime} AND create_time < #{endTime}")
	int countNewUsers(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 期间付费用户：按支付确认时间与成交状态去重 */
	@Select("SELECT COUNT(DISTINCT user_id) FROM orders " +
			"WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND paid_time >= #{startTime} AND paid_time < #{endTime}")
	int countPaidUsers(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT u.id AS userId, u.username AS username, u.nickname AS nickname, " +
			"COUNT(o.id) AS orderCount, IFNULL(SUM(o.pay_amount), 0) AS paidAmount FROM orders o " +
			"JOIN user u ON u.id = o.user_id " +
			"WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND o.paid_time >= #{startTime} AND o.paid_time < #{endTime} " +
			"GROUP BY u.id, u.username, u.nickname ORDER BY paidAmount DESC LIMIT #{limit}")
	List<Map<String, Object>> topPayingUsers(@Param("startTime") String startTime,
	                                         @Param("endTime") String endTime,
	                                         @Param("limit") int limit);

	@Select("SELECT u.id AS userId, u.username AS username, u.nickname AS nickname, " +
			"u.create_time AS createTime, u.last_login_time AS lastLoginTime FROM user u " +
			"WHERE u.role = 'ROLE_user' ORDER BY u.create_time DESC LIMIT #{limit}")
	List<Map<String, Object>> latestUsers(@Param("limit") int limit);

	// ==================================================================
	// 五、商品运营
	// ==================================================================

	@Select("SELECT COUNT(*) FROM product")
	int countProduct();

	@Select("SELECT COUNT(*) FROM product WHERE status = 'ON_SALE'")
	int countOnSaleProduct();

	/**
	 * 商品销售排行。走 order_item 快照拿到历史成交价与标题，
	 * 不用 product 当前价推算，也不依赖可变的 sales_count。
	 */
	@Select("SELECT oi.product_id AS productId, oi.title_snapshot AS title, " +
			"SUM(oi.quantity) AS quantity, SUM(oi.amount) AS amount FROM order_item oi " +
			"JOIN orders o ON o.id = oi.order_id " +
			"WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') " +
			"AND o.paid_time >= #{startTime} AND o.paid_time < #{endTime} " +
			"GROUP BY oi.product_id, oi.title_snapshot ORDER BY amount DESC LIMIT #{limit}")
	List<Map<String, Object>> topProducts(@Param("startTime") String startTime,
	                                      @Param("endTime") String endTime,
	                                      @Param("limit") int limit);

	/** 商品累计浏览与净销量，仅用于算累计转化率（两者都无时间切片） */
	@Select("SELECT IFNULL(SUM(view_count), 0) AS views, IFNULL(SUM(sales_count), 0) AS sales FROM product")
	Map<String, Object> productTotals();

	// ==================================================================
	// 六、服务交付
	// ==================================================================

	@Select("SELECT status, COUNT(*) AS cnt, MIN(create_time) AS earliest FROM demand GROUP BY status")
	List<Map<String, Object>> demandStatusCounts();

	@Select("SELECT COUNT(*) FROM demand WHERE create_time >= #{startTime} AND create_time < #{endTime}")
	int countDemandInRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT status, COUNT(*) AS cnt FROM quote GROUP BY status")
	List<Map<String, Object>> quoteStatusCounts();

	@Select("SELECT status, COUNT(*) AS cnt FROM project GROUP BY status")
	List<Map<String, Object>> projectStatusCounts();

	@Select("SELECT status, COUNT(*) AS cnt FROM milestone GROUP BY status")
	List<Map<String, Object>> milestoneStatusCounts();

	/** 逾期里程碑：due_date 为空的不计入，避免把「没排期」当成「逾期」 */
	@Select("SELECT COUNT(*) FROM milestone " +
			"WHERE due_date IS NOT NULL AND due_date < CURDATE() " +
			"AND status NOT IN ('ACCEPTED','REJECTED')")
	int countOverdueMilestone();

	/** 逾期里程碑中最早到期的那一个，用于待办区的「最早发生时间」 */
	@Select("SELECT MIN(due_date) FROM milestone " +
			"WHERE due_date IS NOT NULL AND due_date < CURDATE() " +
			"AND status NOT IN ('ACCEPTED','REJECTED')")
	String earliestOverdueMilestone();

	@Select("SELECT MIN(create_time) FROM payment WHERE status = 'WAIT_CONFIRM'")
	String earliestWaitingConfirm();

	@Select("SELECT COUNT(*) FROM milestone " +
			"WHERE due_date IS NOT NULL AND due_date >= CURDATE() AND due_date < DATE_ADD(CURDATE(), INTERVAL 7 DAY) " +
			"AND status NOT IN ('ACCEPTED','REJECTED')")
	int countMilestoneDueInWeek();

	@Select("SELECT IFNULL(SUM(amount), 0) FROM milestone WHERE status = 'ACCEPTED'")
	BigDecimal sumAcceptedMilestone();

	/** 最近需要跟进的项目：未完成的按截止日升序 */
	@Select("SELECT p.project_no AS projectNo, p.name AS name, p.status AS status, p.end_date AS endDate, " +
			"(SELECT COUNT(*) FROM milestone m WHERE m.project_id = p.id) AS totalMilestones, " +
			"(SELECT COUNT(*) FROM milestone m WHERE m.project_id = p.id AND m.status = 'ACCEPTED') AS acceptedMilestones " +
			"FROM project p WHERE p.status IN ('NOT_STARTED','IN_PROGRESS','PENDING_ACCEPT') " +
			"ORDER BY p.end_date IS NULL, p.end_date ASC, p.id DESC LIMIT #{limit}")
	List<Map<String, Object>> activeProjects(@Param("limit") int limit);

	// ==================================================================
	// 七、待办与健康
	// ==================================================================

	@Select("SELECT status, COUNT(*) AS cnt, MIN(apply_time) AS earliest FROM refund GROUP BY status")
	List<Map<String, Object>> refundStatusCounts();

	@Select("SELECT status, COUNT(*) AS cnt, MIN(create_time) AS earliest FROM feedback GROUP BY status")
	List<Map<String, Object>> feedbackStatusCounts();

	@Select("SELECT method_name AS methodName, status AS status, times AS times, " +
			"error AS error, create_time AS createTime FROM schedule_job_log " +
			"ORDER BY log_id DESC LIMIT 1")
	Map<String, Object> latestJobLog();

	@Select("SELECT COUNT(*) FROM exception_log " +
			"WHERE create_time >= DATE_SUB(NOW(), INTERVAL #{hours} HOUR)")
	int countExceptionWithin(@Param("hours") int hours);

	@Select("SELECT COUNT(*) FROM payment WHERE status = 'FAIL' " +
			"AND create_time >= #{startTime} AND create_time < #{endTime}")
	int countPaymentFail(@Param("startTime") String startTime, @Param("endTime") String endTime);

	/** 近 24 小时 AI 调用：样本为 0 时上层返回 UNKNOWN，不猜 */
	@Select("SELECT COUNT(*) AS total, IFNULL(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS ok, " +
			"IFNULL(AVG(duration_ms), 0) AS avgMs FROM ai_usage " +
			"WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)")
	Map<String, Object> aiUsage24h();

	@Select("SELECT IFNULL(SUM(file_size), 0) FROM file")
	long sumFileSize();

	@Select("SELECT COUNT(*) FROM user WHERE role = 'ROLE_user' AND status = 0")
	int countActiveUsers();

	@Select("SELECT COUNT(*) FROM demand WHERE status = 'PENDING_REVIEW'")
	int countDemandPendingReview();
}
