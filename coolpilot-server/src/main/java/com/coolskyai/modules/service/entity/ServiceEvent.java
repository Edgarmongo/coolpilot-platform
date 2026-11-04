package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 服务域状态流水
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ServiceEvent {
	private Long id;
	/** DEMAND / QUOTE / PROJECT / MILESTONE / DELIVERY */
	private String bizType;
	private Long bizId;
	private String bizNo;
	private String fromStatus;
	private String toStatus;
	private String action;
	private Long operatorId;
	private String operatorName;
	private String remark;
	private Date createTime;
}
