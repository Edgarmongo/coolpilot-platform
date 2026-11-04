package com.coolskyai.modules.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.coolskyai.modules.service.entity.ServiceEvent;
import com.coolskyai.modules.service.mapper.ServiceEventMapper;

import java.util.List;

/**
 * @Description: 服务域状态流水记录
 *
 * 每次状态变更都落一条，满足 PRD 6.4「谁、何时、从什么到什么」的要求。
 * 顺手把流水写进业务事务里——状态变了但流水没记，等于没法追责。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class ServiceEventLogger {

	@Autowired
	private ServiceEventMapper serviceEventMapper;

	public void log(String bizType, Long bizId, String bizNo, String from, String to, String action,
	                Long operatorId, String operatorName, String remark) {
		ServiceEvent event = new ServiceEvent();
		event.setBizType(bizType);
		event.setBizId(bizId);
		event.setBizNo(bizNo == null ? "" : bizNo);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setAction(action);
		event.setOperatorId(operatorId);
		event.setOperatorName(operatorName == null ? "" : operatorName);
		event.setRemark(remark == null ? "" : remark);
		serviceEventMapper.insert(event);
	}

	public List<ServiceEvent> timeline(String bizType, Long bizId) {
		return serviceEventMapper.listByBiz(bizType, bizId);
	}

	public List<ServiceEvent> demandTimeline(String demandNo) {
		return serviceEventMapper.listDemandTimeline(demandNo);
	}
}
