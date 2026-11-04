package com.coolskyai.modules.trade.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.trade.entity.Order;

import java.util.List;

/**
 * @Description: 订单持久层
 *
 * 所有状态流转都用「带原状态条件的 UPDATE」，靠影响行数判断是否抢到了这次流转，
 * 避免并发下重复发货 / 重复退款。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface OrderMapper {

	@Insert("INSERT INTO orders (order_no, user_id, biz_type, total_amount, discount_amount, pay_amount, status, " +
			"pay_expire_time, remark, create_time, update_time) " +
			"VALUES (#{orderNo}, #{userId}, #{bizType}, #{totalAmount}, #{discountAmount}, #{payAmount}, #{status}, " +
			"#{payExpireTime}, #{remark}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Order order);

	@Select("SELECT * FROM orders WHERE order_no = #{orderNo}")
	Order findByNo(@Param("orderNo") String orderNo);

	@Select("SELECT * FROM orders WHERE id = #{id}")
	Order findById(@Param("id") Long id);

	@Select("<script>" +
			"SELECT * FROM orders WHERE user_id = #{userId} " +
			"<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
			"ORDER BY id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Order> pageByUser(@Param("userId") Long userId,
	                       @Param("status") String status,
	                       @Param("offset") int offset,
	                       @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM orders WHERE user_id = #{userId} " +
			"<if test='status != null and status != \"\"'>AND status = #{status} </if>" +
			"</script>")
	int countByUser(@Param("userId") Long userId, @Param("status") String status);

	@Select("<script>" +
			"SELECT o.* FROM orders o " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND o.status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (o.order_no LIKE CONCAT('%', #{keyword}, '%') " +
			"OR EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id = o.id AND oi.title_snapshot LIKE CONCAT('%', #{keyword}, '%')))" +
			"</if>" +
			"<if test='startTime != null and startTime != \"\"'>AND o.create_time &gt;= #{startTime}</if>" +
			"<if test='endTime != null and endTime != \"\"'>AND o.create_time &lt;= #{endTime}</if>" +
			"</where>" +
			"ORDER BY o.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Order> pageAdmin(@Param("status") String status,
	                      @Param("keyword") String keyword,
	                      @Param("startTime") String startTime,
	                      @Param("endTime") String endTime,
	                      @Param("offset") int offset,
	                      @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM orders o " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND o.status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (o.order_no LIKE CONCAT('%', #{keyword}, '%') " +
			"OR EXISTS (SELECT 1 FROM order_item oi WHERE oi.order_id = o.id AND oi.title_snapshot LIKE CONCAT('%', #{keyword}, '%')))" +
			"</if>" +
			"<if test='startTime != null and startTime != \"\"'>AND o.create_time &gt;= #{startTime}</if>" +
			"<if test='endTime != null and endTime != \"\"'>AND o.create_time &lt;= #{endTime}</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("status") String status,
	               @Param("keyword") String keyword,
	               @Param("startTime") String startTime,
	               @Param("endTime") String endTime);

	/**
	 * 待支付 → 待确认（人工模式下用户已提交转账凭证）
	 */
	@Update("UPDATE orders SET status = 'PENDING_CONFIRM', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'PENDING_PAY'")
	int markPendingConfirm(@Param("orderNo") String orderNo);

	/**
	 * 待支付/待确认 → 已支付。影响行数为 1 才允许发放权益。
	 */
	@Update("UPDATE orders SET status = 'PAID', paid_time = NOW(), update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status IN ('PENDING_PAY', 'PENDING_CONFIRM')")
	int markPaid(@Param("orderNo") String orderNo);

	@Update("UPDATE orders SET status = 'COMPLETED', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'PAID'")
	int markCompleted(@Param("orderNo") String orderNo);

	@Update("UPDATE orders SET status = 'CANCELLED', cancel_time = NOW(), update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = 'PENDING_PAY'")
	int cancel(@Param("orderNo") String orderNo, @Param("userId") Long userId);

	/**
	 * 同一用户对同一商品未过期的待支付订单，用于防连点重复下单
	 */
	@Select("SELECT o.* FROM orders o JOIN order_item oi ON oi.order_id = o.id " +
			"WHERE o.user_id = #{userId} AND oi.product_id = #{productId} AND o.status = 'PENDING_PAY' " +
			"AND o.pay_expire_time > NOW() ORDER BY o.id DESC LIMIT 1")
	Order findPending(@Param("userId") Long userId, @Param("productId") Long productId);

	@Update("UPDATE orders SET status = 'CLOSED', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'PENDING_PAY'")
	int close(@Param("orderNo") String orderNo);

	/**
	 * 批量关闭超时未支付订单
	 */
	@Update("UPDATE orders SET status = 'CLOSED', update_time = NOW() " +
			"WHERE status IN ('PENDING_PAY', 'PENDING_CONFIRM') AND pay_expire_time IS NOT NULL AND pay_expire_time < NOW()")
	int closeExpired();

	@Update("UPDATE orders SET status = 'REFUNDING', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status IN ('PAID', 'COMPLETED')")
	int markRefunding(@Param("orderNo") String orderNo);

	@Update("UPDATE orders SET status = 'REFUNDED', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'REFUNDING'")
	int markRefunded(@Param("orderNo") String orderNo);

	@Update("UPDATE orders SET status = 'COMPLETED', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'REFUNDING'")
	int restoreAfterRefundRejected(@Param("orderNo") String orderNo);

	/**
	 * 统计：已支付订单数与金额（后台销售概览）
	 */
	@Select("SELECT IFNULL(SUM(pay_amount), 0) FROM orders " +
			"WHERE status IN ('PAID', 'COMPLETED', 'REFUNDING', 'REFUNDED') " +
			"AND (#{startTime} IS NULL OR create_time >= #{startTime}) " +
			"AND (#{endTime} IS NULL OR create_time <= #{endTime})")
	java.math.BigDecimal sumPaidAmount(@Param("startTime") String startTime, @Param("endTime") String endTime);

	@Select("SELECT COUNT(*) FROM orders " +
			"WHERE status IN ('PAID', 'COMPLETED', 'REFUNDING', 'REFUNDED') " +
			"AND (#{startTime} IS NULL OR create_time >= #{startTime}) " +
			"AND (#{endTime} IS NULL OR create_time <= #{endTime})")
	int countPaid(@Param("startTime") String startTime, @Param("endTime") String endTime);
}
