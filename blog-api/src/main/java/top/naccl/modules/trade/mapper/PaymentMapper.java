package top.naccl.modules.trade.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import top.naccl.modules.trade.entity.Payment;

import java.util.Date;
import java.util.List;

/**
 * @Description: 支付单持久层
 *
 * markSuccess 是整个发权益链路的唯一闸门：
 * 只有把状态从 INIT/WAIT_CONFIRM 改成 SUCCESS 的那一次（影响行数 = 1）才继续往下走，
 * 其余情况（重复回调、重复点击确认）直接返回已处理。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface PaymentMapper {

	@Insert("INSERT INTO payment (payment_no, biz_type, biz_no, user_id, channel, amount, status, create_time, update_time) " +
			"VALUES (#{paymentNo}, #{bizType}, #{bizNo}, #{userId}, #{channel}, #{amount}, #{status}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Payment payment);

	@Select("SELECT * FROM payment WHERE payment_no = #{paymentNo}")
	Payment findByNo(@Param("paymentNo") String paymentNo);

	@Select("SELECT * FROM payment WHERE biz_no = #{bizNo} ORDER BY id DESC LIMIT 1")
	Payment findLatestByBizNo(@Param("bizNo") String bizNo);

	@Select("SELECT * FROM payment WHERE biz_no = #{bizNo} ORDER BY id DESC")
	List<Payment> listByBizNo(@Param("bizNo") String bizNo);

	/**
	 * 用户提交转账凭证：INIT → WAIT_CONFIRM
	 */
	@Update("UPDATE payment SET status = 'WAIT_CONFIRM', payer_name = #{payerName}, transfer_time = #{transferTime}, " +
			"proof_file_id = #{proofFileId}, update_time = NOW() " +
			"WHERE payment_no = #{paymentNo} AND user_id = #{userId} AND status = 'INIT'")
	int markWaitConfirm(@Param("paymentNo") String paymentNo,
	                    @Param("userId") Long userId,
	                    @Param("payerName") String payerName,
	                    @Param("transferTime") Date transferTime,
	                    @Param("proofFileId") Long proofFileId);

	/**
	 * 确认收款：INIT/WAIT_CONFIRM → SUCCESS。影响行数为 1 才是首次确认。
	 */
	@Update("UPDATE payment SET status = 'SUCCESS', trade_no = #{tradeNo}, paid_time = NOW(), " +
			"confirm_user_id = #{operatorId}, remark = #{remark}, notify_raw = #{notifyRaw}, update_time = NOW() " +
			"WHERE payment_no = #{paymentNo} AND status IN ('INIT', 'WAIT_CONFIRM')")
	int markSuccess(@Param("paymentNo") String paymentNo,
	                @Param("tradeNo") String tradeNo,
	                @Param("operatorId") Long operatorId,
	                @Param("remark") String remark,
	                @Param("notifyRaw") String notifyRaw);

	@Update("UPDATE payment SET status = 'FAIL', remark = #{remark}, update_time = NOW() " +
			"WHERE payment_no = #{paymentNo} AND status IN ('INIT', 'WAIT_CONFIRM')")
	int markFail(@Param("paymentNo") String paymentNo, @Param("remark") String remark);

	@Update("UPDATE payment SET status = 'CLOSED', remark = #{remark}, update_time = NOW() " +
			"WHERE payment_no = #{paymentNo} AND status IN ('INIT', 'WAIT_CONFIRM')")
	int close(@Param("paymentNo") String paymentNo, @Param("remark") String remark);

	@Select("<script>" +
			"SELECT p.* FROM payment p " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND p.status = #{status}</if>" +
			"<if test='channel != null and channel != \"\"'>AND p.channel = #{channel}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (p.payment_no LIKE CONCAT('%', #{keyword}, '%') OR p.biz_no LIKE CONCAT('%', #{keyword}, '%') " +
			"OR p.trade_no LIKE CONCAT('%', #{keyword}, '%') OR p.payer_name LIKE CONCAT('%', #{keyword}, '%'))" +
			"</if>" +
			"</where>" +
			"ORDER BY p.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Payment> pageAdmin(@Param("status") String status,
	                        @Param("channel") String channel,
	                        @Param("keyword") String keyword,
	                        @Param("offset") int offset,
	                        @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM payment p " +
			"<where>" +
			"<if test='status != null and status != \"\"'>AND p.status = #{status}</if>" +
			"<if test='channel != null and channel != \"\"'>AND p.channel = #{channel}</if>" +
			"<if test='keyword != null and keyword != \"\"'>" +
			"AND (p.payment_no LIKE CONCAT('%', #{keyword}, '%') OR p.biz_no LIKE CONCAT('%', #{keyword}, '%') " +
			"OR p.trade_no LIKE CONCAT('%', #{keyword}, '%') OR p.payer_name LIKE CONCAT('%', #{keyword}, '%'))" +
			"</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("status") String status,
	               @Param("channel") String channel,
	               @Param("keyword") String keyword);

	@Select("SELECT COUNT(*) FROM payment WHERE status = 'WAIT_CONFIRM'")
	int countWaitingConfirm();
}
