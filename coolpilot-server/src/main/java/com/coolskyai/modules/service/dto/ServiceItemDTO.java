package com.coolskyai.modules.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 服务商品扩展信息
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ServiceItemDTO {
	private Integer deliveryDays;
	private String scope;
	private String process;
	private String faq;
}
