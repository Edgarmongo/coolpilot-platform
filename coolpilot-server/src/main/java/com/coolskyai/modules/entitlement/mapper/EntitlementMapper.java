package com.coolskyai.modules.entitlement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.entitlement.entity.Entitlement;

import java.util.Date;
import java.util.List;

/**
 * @Description: 权益持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface EntitlementMapper {

	String JOIN_PRODUCT = "e.*, p.title AS product_title, p.product_no AS product_no, " +
			"p.product_type AS product_type, p.cover AS product_cover " +
			"FROM entitlement e LEFT JOIN product p ON p.id = e.product_id ";

	/**
	 * 发放权益：同人同商品同类型已存在则复活（续期/重新购买）
	 */
	@Insert("INSERT INTO entitlement (user_id, product_id, order_no, ent_type, source, start_time, expire_time, status, create_time, update_time) " +
			"VALUES (#{userId}, #{productId}, #{orderNo}, #{entType}, #{source}, #{startTime}, #{expireTime}, 'ACTIVE', NOW(), NOW()) " +
			"ON DUPLICATE KEY UPDATE order_no = VALUES(order_no), source = VALUES(source), " +
			"start_time = VALUES(start_time), expire_time = VALUES(expire_time), status = 'ACTIVE', update_time = NOW()")
	int upsert(Entitlement entitlement);

	@Select("SELECT id FROM entitlement WHERE user_id = #{userId} AND product_id = #{productId} AND ent_type = #{entType}")
	Long findIdByUnique(@Param("userId") Long userId,
	                    @Param("productId") Long productId,
	                    @Param("entType") String entType);

	/**
	 * 查单条（用于鉴权）
	 */
	@Select("SELECT * FROM entitlement WHERE user_id = #{userId} AND product_id = #{productId} AND status = 'ACTIVE' " +
			"AND (expire_time IS NULL OR expire_time > NOW()) LIMIT 1")
	Entitlement findActive(@Param("userId") Long userId, @Param("productId") Long productId);

	/**
	 * 最近一条（含已失效），用于向用户解释为什么不能看
	 */
	@Select("SELECT * FROM entitlement WHERE user_id = #{userId} AND product_id = #{productId} " +
			"ORDER BY id DESC LIMIT 1")
	Entitlement findLatest(@Param("userId") Long userId, @Param("productId") Long productId);

	/**
	 * 只要有一条可用权益就放行（ACCESS/LEARN 均可看内容）
	 */
	@Select("SELECT COUNT(*) FROM entitlement WHERE user_id = #{userId} AND product_id = #{productId} " +
			"AND status = 'ACTIVE' AND (expire_time IS NULL OR expire_time > NOW())")
	int countActive(@Param("userId") Long userId, @Param("productId") Long productId);

	@Select("SELECT " + JOIN_PRODUCT + "WHERE e.user_id = #{userId} AND e.status = 'ACTIVE' " +
			"AND (e.expire_time IS NULL OR e.expire_time > NOW()) ORDER BY e.id DESC")
	List<Entitlement> listActiveByUser(@Param("userId") Long userId);

	@Select("SELECT " + JOIN_PRODUCT + "WHERE e.order_no = #{orderNo} ORDER BY e.id ASC")
	List<Entitlement> listByOrderNo(@Param("orderNo") String orderNo);

	@Select("SELECT " + JOIN_PRODUCT + "WHERE e.id = #{id}")
	Entitlement findById(@Param("id") Long id);

	@Select("<script>" +
			"SELECT " + JOIN_PRODUCT +
			"<where>" +
			"<if test='userId != null and userId > 0'>AND e.user_id = #{userId}</if>" +
			"<if test='productId != null and productId > 0'>AND e.product_id = #{productId}</if>" +
			"<if test='status != null and status != \"\"'>AND e.status = #{status}</if>" +
			"</where>" +
			"ORDER BY e.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Entitlement> pageAdmin(@Param("userId") Long userId,
	                            @Param("productId") Long productId,
	                            @Param("status") String status,
	                            @Param("offset") int offset,
	                            @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM entitlement e " +
			"<where>" +
			"<if test='userId != null and userId > 0'>AND e.user_id = #{userId}</if>" +
			"<if test='productId != null and productId > 0'>AND e.product_id = #{productId}</if>" +
			"<if test='status != null and status != \"\"'>AND e.status = #{status}</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("userId") Long userId,
	               @Param("productId") Long productId,
	               @Param("status") String status);

	/**
	 * 退款撤销：只撤销这笔订单发出去的权益
	 */
	@Update("UPDATE entitlement SET status = 'REVOKED', update_time = NOW() " +
			"WHERE order_no = #{orderNo} AND status = 'ACTIVE'")
	int revokeByOrderNo(@Param("orderNo") String orderNo);

	@Update("UPDATE entitlement SET status = 'REVOKED', update_time = NOW() " +
			"WHERE id = #{id} AND status = 'ACTIVE'")
	int revokeById(@Param("id") Long id);

	@Update("UPDATE entitlement SET status = 'EXPIRED', update_time = NOW() " +
			"WHERE status = 'ACTIVE' AND expire_time IS NOT NULL AND expire_time <= NOW()")
	int expireOutdated();

	/**
	 * 人工发放/续期
	 */
	@Update("UPDATE entitlement SET status = 'ACTIVE', start_time = #{startTime}, expire_time = #{expireTime}, update_time = NOW() " +
			"WHERE id = #{id}")
	int renew(@Param("id") Long id, @Param("startTime") Date startTime, @Param("expireTime") Date expireTime);
}
