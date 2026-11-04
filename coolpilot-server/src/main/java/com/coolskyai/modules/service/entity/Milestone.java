package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * @Description: 项目里程碑
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Milestone {
	private Long id;
	private Long projectId;
	private String name;
	private Integer sort;
	private BigDecimal amount;
	private LocalDate dueDate;
	/** PENDING / DELIVERING / DELIVERED / ACCEPTED / REJECTED */
	private String status;
	private Date deliveredTime;
	private Date acceptedTime;
	private String remark;
}
