package com.coolskyai.modules.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 退款申请入参
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RefundApplyDTO {
	private String orderNo;
	private String reason;
}
