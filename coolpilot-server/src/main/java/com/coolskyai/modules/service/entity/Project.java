package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * @Description: 项目
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Project {
	private Long id;
	private String projectNo;
	private Long demandId;
	private Long quoteId;
	private String orderNo;
	private Long userId;
	private String name;
	private BigDecimal amount;
	/** NOT_STARTED / IN_PROGRESS / PENDING_ACCEPT / COMPLETED / ARCHIVED */
	private String status;
	private LocalDate startDate;
	private LocalDate endDate;
	private Date createTime;
	private Date updateTime;

	/** 展示用 */
	private String demandNo;
	private String nickname;
	private Integer milestoneCount;
	private Integer acceptedCount;
}
