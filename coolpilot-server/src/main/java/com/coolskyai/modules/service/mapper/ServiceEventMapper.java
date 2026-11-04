package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.coolskyai.modules.service.entity.ServiceEvent;

import java.util.List;

/**
 * @Description: 状态流水持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ServiceEventMapper {

	@Insert("INSERT INTO service_event (biz_type, biz_id, biz_no, from_status, to_status, action, operator_id, " +
			"operator_name, remark, create_time) VALUES (#{bizType}, #{bizId}, #{bizNo}, #{fromStatus}, #{toStatus}, " +
			"#{action}, #{operatorId}, #{operatorName}, #{remark}, NOW())")
	int insert(ServiceEvent event);

	@Select("SELECT * FROM service_event WHERE biz_type = #{bizType} AND biz_id = #{bizId} ORDER BY id ASC")
	List<ServiceEvent> listByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

	@Select("SELECT * FROM service_event WHERE biz_id IN " +
			"(SELECT id FROM demand WHERE demand_no = #{demandNo}) AND biz_type IN ('DEMAND','QUOTE') ORDER BY id ASC")
	List<ServiceEvent> listDemandTimeline(@Param("demandNo") String demandNo);
}
