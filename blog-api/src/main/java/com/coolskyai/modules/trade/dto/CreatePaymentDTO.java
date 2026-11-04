package com.coolskyai.modules.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 创建支付单入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class CreatePaymentDTO {
	private String orderNo;
	/** MANUAL_WECHAT / MANUAL_ALIPAY / MANUAL_PAYPAL */
	private String channel;
}
