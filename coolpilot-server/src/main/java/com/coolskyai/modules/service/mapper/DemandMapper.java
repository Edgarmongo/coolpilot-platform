package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.Demand;

import java.util.Date;
import java.util.List;

/**
 * @Description: 需求持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface DemandMapper {

	String COLUMNS = "d.*, u.username, u.nickname, p.title AS product_title " +
			"FROM demand d LEFT JOIN user u ON u.id = d.user_id LEFT JOIN product p ON p.id = d.product_id ";

	@Insert("INSERT INTO demand (demand_no, user_id, product_id, title, project_type, tech_stack, budget_min, budget_max, " +
			"budget_negotiable, expect_date, description, attachment_file_id, contact, status, reject_reason, " +
			"create_time, update_time) VALUES (#{demandNo}, #{userId}, #{productId}, #{title}, #{projectType}, " +
			"#{techStack}, #{budgetMin}, #{budgetMax}, #{budgetNegotiable}, #{expectDate}, #{description}, " +
			"#{attachmentFileId}, #{contact}, #{status}, #{rejectReason}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Demand demand);

	@Select("SELECT " + COLUMNS + "WHERE d.demand_no = #{demandNo}")
	Demand findByNo(@Param("demandNo") String demandNo);

	@Select("SELECT " + COLUMNS + "WHERE d.id = #{id}")
	Demand findById(@Param("id") Long id);

	@Select("<script>" +
			"SELECT " + COLUMNS +
			"<where><if test='userId != null'>AND d.user_id = #{userId}</if>" +
			"<if test='status != null and status != &quot;&quot;'>AND d.status = #{status}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (d.title LIKE CONCAT('%', #{keyword}, '%') OR d.demand_no LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"</where> ORDER BY d.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Demand> page(@Param("userId") Long userId,
	                  @Param("status") String status,
	                  @Param("keyword") String keyword,
	                  @Param("offset") int offset,
	                  @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM demand d " +
			"<where><if test='userId != null'>AND d.user_id = #{userId}</if>" +
			"<if test='status != null and status != &quot;&quot;'>AND d.status = #{status}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (d.title LIKE CONCAT('%', #{keyword}, '%') OR d.demand_no LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"</where></script>")
	int count(@Param("userId") Long userId,
	          @Param("status") String status,
	          @Param("keyword") String keyword);

	/**
	 * 带原状态条件的状态流转，避免并发下重复推进
	 */
	@Update("UPDATE demand SET status = #{to}, reject_reason = #{rejectReason}, audit_time = #{auditTime}, " +
			"update_time = NOW() WHERE id = #{id} AND status = #{from}")
	int move(@Param("id") Long id,
	         @Param("from") String from,
	         @Param("to") String to,
	         @Param("rejectReason") String rejectReason,
	         @Param("auditTime") Date auditTime);

	@Select("SELECT COUNT(*) FROM demand WHERE status IN ('PENDING_REVIEW', 'PENDING_QUOTE', 'PENDING_CONFIRM')")
	int countPending();
}
