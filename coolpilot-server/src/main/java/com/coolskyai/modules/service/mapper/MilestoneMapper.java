package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.Milestone;

import java.util.Date;
import java.util.List;

/**
 * @Description: 里程碑持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface MilestoneMapper {

	@Select("SELECT * FROM milestone WHERE project_id = #{projectId} ORDER BY sort ASC, id ASC")
	List<Milestone> listByProject(@Param("projectId") Long projectId);

	@Select("SELECT * FROM milestone WHERE id = #{id}")
	Milestone findById(@Param("id") Long id);

	@Select("SELECT COALESCE(MAX(sort), 0) FROM milestone WHERE project_id = #{projectId}")
	int maxSort(@Param("projectId") Long projectId);

	@Insert("INSERT INTO milestone (project_id, name, sort, amount, due_date, status, remark) " +
			"VALUES (#{projectId}, #{name}, #{sort}, #{amount}, #{dueDate}, #{status}, #{remark})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Milestone milestone);

	@Update("UPDATE milestone SET name = #{name}, sort = #{sort}, amount = #{amount}, due_date = #{dueDate}, " +
			"remark = #{remark} WHERE id = #{id}")
	int update(Milestone milestone);

	@Update("UPDATE milestone SET status = #{to}, delivered_time = #{deliveredTime}, accepted_time = #{acceptedTime} " +
			"WHERE id = #{id} AND status = #{from}")
	int move(@Param("id") Long id,
	         @Param("from") String from,
	         @Param("to") String to,
	         @Param("deliveredTime") Date deliveredTime,
	         @Param("acceptedTime") Date acceptedTime);

	@Select("SELECT COUNT(*) FROM milestone WHERE project_id = #{projectId}")
	int countByProject(@Param("projectId") Long projectId);

	@Select("SELECT COUNT(*) FROM milestone WHERE project_id = #{projectId} AND status <> 'ACCEPTED'")
	int countUnaccepted(@Param("projectId") Long projectId);

	@Select("SELECT COUNT(*) FROM milestone WHERE project_id = #{projectId} AND status = 'ACCEPTED'")
	int countAccepted(@Param("projectId") Long projectId);
}
