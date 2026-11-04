package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @Description: 后台手工立项（固定价服务从订单立项时用）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ProjectCreateDTO {
	private Long demandId;
	private Long quoteId;
	private String orderNo;
	private Long userId;
	private String name;
	private BigDecimal amount;
	private LocalDate startDate;
	private LocalDate endDate;
	/** 首个里程碑，可不填 */
	private String firstMilestoneName;
}
