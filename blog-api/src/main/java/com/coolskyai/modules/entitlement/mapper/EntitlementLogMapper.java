package com.coolskyai.modules.entitlement.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.coolskyai.modules.entitlement.entity.EntitlementLog;

import java.util.List;

/**
 * @Description: 权益流水持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface EntitlementLogMapper {

	@Insert("INSERT INTO entitlement_log (entitlement_id, user_id, product_id, action, reason, operator_id, create_time) " +
			"VALUES (#{entitlementId}, #{userId}, #{productId}, #{action}, #{reason}, #{operatorId}, NOW())")
	int insert(EntitlementLog log);

	@Select("<script>" +
			"SELECT * FROM entitlement_log " +
			"<where>" +
			"<if test='userId != null and userId > 0'>AND user_id = #{userId}</if>" +
			"<if test='productId != null and productId > 0'>AND product_id = #{productId}</if>" +
			"<if test='action != null and action != \"\"'>AND action = #{action}</if>" +
			"</where>" +
			"ORDER BY id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<EntitlementLog> pageAdmin(@Param("userId") Long userId,
	                               @Param("productId") Long productId,
	                               @Param("action") String action,
	                               @Param("offset") int offset,
	                               @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM entitlement_log " +
			"<where>" +
			"<if test='userId != null and userId > 0'>AND user_id = #{userId}</if>" +
			"<if test='productId != null and productId > 0'>AND product_id = #{productId}</if>" +
			"<if test='action != null and action != \"\"'>AND action = #{action}</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("userId") Long userId,
	               @Param("productId") Long productId,
	               @Param("action") String action);
}
