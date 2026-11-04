package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @Description: 提交需求
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class DemandCreateDTO {
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
	/** 手机/微信/邮箱，仅平台所有者可见 */
	private String contact;
}
