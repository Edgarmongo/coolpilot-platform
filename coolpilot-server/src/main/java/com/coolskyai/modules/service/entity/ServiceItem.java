package com.coolskyai.modules.service.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 服务商品扩展信息（product_type=SERVICE 才有）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ServiceItem {
	private Long id;
	private Long productId;
	/** 预计交付天数，0 表示面议 */
	private Integer deliveryDays;
	private String scope;
	private String process;
	private String faq;
	private Date createTime;
	private Date updateTime;
}
