package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.Delivery;

import java.util.Date;
import java.util.List;

/**
 * @Description: 交付物持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface DeliveryMapper {

	String COLUMNS = "dv.*, f.original_name AS file_name FROM delivery dv LEFT JOIN file f ON f.id = dv.file_id ";

	@Insert("INSERT INTO delivery (service_order_id, project_id, milestone_id, delivery_type, file_id, content, " +
			"delivered_by, delivered_time, status) VALUES (#{serviceOrderId}, #{projectId}, #{milestoneId}, " +
			"#{deliveryType}, #{fileId}, #{content}, #{deliveredBy}, #{deliveredTime}, #{status})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Delivery delivery);

	@Select("SELECT " + COLUMNS + "WHERE dv.project_id = #{projectId} ORDER BY dv.id DESC")
	List<Delivery> listByProject(@Param("projectId") Long projectId);

	@Select("SELECT " + COLUMNS + "WHERE dv.milestone_id = #{milestoneId} ORDER BY dv.id DESC")
	List<Delivery> listByMilestone(@Param("milestoneId") Long milestoneId);

	@Select("SELECT " + COLUMNS + "WHERE dv.milestone_id = #{milestoneId} ORDER BY dv.id DESC LIMIT 1")
	Delivery findLatestByMilestone(@Param("milestoneId") Long milestoneId);

	/**
	 * 交付文件的下载权限：交付物所属项目的客户本人可下载
	 */
	@Select("SELECT COUNT(*) FROM delivery dv JOIN project p ON p.id = dv.project_id " +
			"WHERE dv.file_id = #{fileId} AND p.user_id = #{userId}")
	int countByFileAndUser(@Param("fileId") Long fileId, @Param("userId") Long userId);

	@Update("UPDATE delivery SET status = #{to}, accepted_time = #{acceptedTime} WHERE id = #{id} AND status = #{from}")
	int move(@Param("id") Long id,
	         @Param("from") String from,
	         @Param("to") String to,
	         @Param("acceptedTime") Date acceptedTime);
}
