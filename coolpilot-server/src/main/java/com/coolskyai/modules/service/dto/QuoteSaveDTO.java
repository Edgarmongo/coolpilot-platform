package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @Description: 新增/修改报价
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class QuoteSaveDTO {
	private Long demandId;
	private BigDecimal amount;
	private String currency;
	private String scope;
	private LocalDate validUntil;
}
