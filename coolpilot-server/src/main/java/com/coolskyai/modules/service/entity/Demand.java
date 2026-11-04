package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * @Description: 客户需求
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Demand {
	private Long id;
	private String demandNo;
	private Long userId;
	/** 来源服务商品，可为空 */
	private Long productId;
	private String title;
	/** BACKEND / AI / DEPLOY / CONSULT / OTHER */
	private String projectType;
	private String techStack;
	private BigDecimal budgetMin;
	private BigDecimal budgetMax;
	private Boolean budgetNegotiable;
	private LocalDate expectDate;
	private String description;
	private Long attachmentFileId;
	/** 仅平台所有者可见 */
	private String contact;
	private String status;
	private String rejectReason;
	private Date auditTime;
	private Date createTime;
	private Date updateTime;

	/** 展示用，不落库 */
	private String username;
	private String nickname;
	private String productTitle;
}
