package com.coolskyai.modules.trade.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.trade.entity.Refund;

import java.util.List;

/**
 * @Description: 退款单持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface RefundMapper {

	@Insert("INSERT INTO refund (refund_no, biz_type, biz_no, payment_no, user_id, amount, reason, status, apply_time) " +
			"VALUES (#{refundNo}, #{bizType}, #{bizNo}, #{paymentNo}, #{userId}, #{amount}, #{reason}, #{status}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Refund refund);

	@Select("SELECT * FROM refund WHERE refund_no = #{refundNo}")
	Refund findByNo(@Param("refundNo") String refundNo);

	@Select("SELECT * FROM refund WHERE biz_no = #{bizNo} AND status IN ('APPLIED', 'APPROVED', 'REFUNDING') ORDER BY id DESC LIMIT 1")
	Refund findActiveByBizNo(@Param("bizNo") String bizNo);

	@Select("SELECT * FROM refund WHERE biz_no = #{bizNo} ORDER BY id DESC")
	List<Refund> listByBizNo(@Param("bizNo") String bizNo);

	@Select("SELECT * FROM refund WHERE user_id = #{userId} ORDER BY id DESC LIMIT #{offset}, #{size}")
	List<Refund> pageByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

	@Select("SELECT COUNT(*) FROM refund WHERE user_id = #{userId}")
	int countByUser(@Param("userId") Long userId);

	@Select("<script>" +
			"SELECT * FROM refund " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (refund_no LIKE CONCAT('%', #{keyword}, '%') OR biz_no LIKE CONCAT('%', #{keyword}, '%'))" +
			"</if>" +
			"</where>" +
			"ORDER BY id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Refund> pageAdmin(@Param("status") String status,
	                       @Param("keyword") String keyword,
	                       @Param("offset") int offset,
	                       @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM refund " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (refund_no LIKE CONCAT('%', #{keyword}, '%') OR biz_no LIKE CONCAT('%', #{keyword}, '%'))" +
			"</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("status") String status, @Param("keyword") String keyword);

	/**
	 * 审核通过：APPLIED → APPROVED
	 */
	@Update("UPDATE refund SET status = 'APPROVED', audit_time = NOW(), operator_id = #{operatorId}, remark = #{remark} " +
			"WHERE refund_no = #{refundNo} AND status = 'APPLIED'")
	int approve(@Param("refundNo") String refundNo,
	            @Param("operatorId") Long operatorId,
	            @Param("remark") String remark);

	@Update("UPDATE refund SET status = 'REJECTED', audit_time = NOW(), operator_id = #{operatorId}, remark = #{remark} " +
			"WHERE refund_no = #{refundNo} AND status = 'APPLIED'")
	int reject(@Param("refundNo") String refundNo,
	           @Param("operatorId") Long operatorId,
	           @Param("remark") String remark);

	/**
	 * 确认已线下退款：APPROVED → SUCCESS
	 */
	@Update("UPDATE refund SET status = 'SUCCESS', refund_time = NOW(), operator_id = #{operatorId}, remark = #{remark} " +
			"WHERE refund_no = #{refundNo} AND status = 'APPROVED'")
	int finish(@Param("refundNo") String refundNo,
	           @Param("operatorId") Long operatorId,
	           @Param("remark") String remark);

	/**
	 * 统计：已成功退款金额（后台销售概览）
	 *
	 * <p>时间轴是 <b>refund_time（退款完成时间）</b> —— 钱实际退出去的那一刻，
	 * 不是申请时间、也不是原订单的下单时间。跨期退款只减少退款发生期的净流入，
	 * 不回溯修改原成交期。
	 *
	 * <p>区间<b>左闭右开</b>：{@code <= endTime} 会让相邻区间重复统计边界那一秒。
	 */
	@Select("SELECT IFNULL(SUM(amount), 0) FROM refund WHERE status = 'SUCCESS' " +
			"AND (#{startTime} IS NULL OR refund_time >= #{startTime}) " +
			"AND (#{endTime} IS NULL OR refund_time < #{endTime})")
	java.math.BigDecimal sumRefunded(@Param("startTime") String startTime, @Param("endTime") String endTime);
}
