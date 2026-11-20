package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.Project;

import java.util.List;

/**
 * @Description: 项目持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ProjectMapper {

	String COLUMNS = "p.*, d.demand_no, u.nickname, " +
			"(SELECT COUNT(*) FROM milestone m WHERE m.project_id = p.id) AS milestone_count, " +
			"(SELECT COUNT(*) FROM milestone m WHERE m.project_id = p.id AND m.status = 'ACCEPTED') AS accepted_count, " +
			// 逾期里程碑数：due_date 为空视为未排期，不计入逾期。
			// 这里刻意写成 NOT (>= ) 而不是 <，因为本常量同时被带 <script> 和不带 <script> 的查询复用，
			// 带 script 的必须转义 <，不带 script 的又不能出现转义符，反向条件两边都合法
			"(SELECT COUNT(*) FROM milestone m WHERE m.project_id = p.id AND m.due_date IS NOT NULL " +
			"AND NOT (m.due_date >= CURDATE()) AND m.status NOT IN ('ACCEPTED','REJECTED')) AS overdue_count " +
			"FROM project p LEFT JOIN demand d ON d.id = p.demand_id LEFT JOIN user u ON u.id = p.user_id ";

	@Insert("INSERT INTO project (project_no, demand_id, quote_id, order_no, user_id, name, amount, status, " +
			"start_date, end_date, create_time, update_time) VALUES (#{projectNo}, #{demandId}, #{quoteId}, " +
			"#{orderNo}, #{userId}, #{name}, #{amount}, #{status}, #{startDate}, #{endDate}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Project project);

	@Select("SELECT " + COLUMNS + "WHERE p.id = #{id}")
	Project findById(@Param("id") Long id);

	@Select("SELECT " + COLUMNS + "WHERE p.project_no = #{projectNo}")
	Project findByNo(@Param("projectNo") String projectNo);

	@Select("SELECT " + COLUMNS + "WHERE p.demand_id = #{demandId} ORDER BY p.id DESC")
	List<Project> listByDemand(@Param("demandId") Long demandId);

	@Select("<script>" +
			"SELECT " + COLUMNS +
			"<where><if test='userId != null'>AND p.user_id = #{userId}</if>" +
			"<if test='status != null and status != &quot;&quot;'>AND p.status = #{status}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.project_no LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"<if test='overdue != null and overdue'>AND EXISTS (SELECT 1 FROM milestone m WHERE m.project_id = p.id " +
			"AND m.due_date IS NOT NULL AND m.due_date &lt; CURDATE() AND m.status NOT IN ('ACCEPTED','REJECTED'))</if>" +
			"</where> ORDER BY p.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Project> page(@Param("userId") Long userId,
	                   @Param("status") String status,
	                   @Param("keyword") String keyword,
	                   @Param("overdue") Boolean overdue,
	                   @Param("offset") int offset,
	                   @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM project p " +
			"<where><if test='userId != null'>AND p.user_id = #{userId}</if>" +
			"<if test='status != null and status != &quot;&quot;'>AND p.status = #{status}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.project_no LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"<if test='overdue != null and overdue'>AND EXISTS (SELECT 1 FROM milestone m WHERE m.project_id = p.id " +
			"AND m.due_date IS NOT NULL AND m.due_date &lt; CURDATE() AND m.status NOT IN ('ACCEPTED','REJECTED'))</if>" +
			"</where></script>")
	int count(@Param("userId") Long userId,
	          @Param("status") String status,
	          @Param("keyword") String keyword,
	          @Param("overdue") Boolean overdue);

	@Update("UPDATE project SET status = #{to}, end_date = #{endDate}, update_time = NOW() WHERE id = #{id}")
	int move(@Param("id") Long id, @Param("to") String to, @Param("endDate") java.time.LocalDate endDate);
}
