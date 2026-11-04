package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * @Description: 报价单，同一需求可多版本
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Quote {
	private Long id;
	private String quoteNo;
	private Long demandId;
	private Integer version;
	private BigDecimal amount;
	private String currency;
	private String scope;
	/** 有效期到哪一天 */
	private LocalDate validUntil;
	/** DRAFT / SENT / ACCEPTED / REJECTED / EXPIRED */
	private String status;
	private Date createTime;
	private Date updateTime;
}
