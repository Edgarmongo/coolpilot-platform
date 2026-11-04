package com.coolskyai.modules.trade.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 退款单
 *
 * 状态机：APPLIED → APPROVED → REFUNDING → SUCCESS
 *                        ↘ REJECTED
 *
 * 人工收款渠道的退款由 OWNER 线下打款后点「确认退款完成」，与支付确认同一套幂等思路。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Refund {
	private Long id;
	private String refundNo;
	private String bizType;
	private String bizNo;
	private String paymentNo;
	private Long userId;
	private BigDecimal amount;
	private String reason;
	private String status;
	private Date applyTime;
	private Date auditTime;
	private Date refundTime;
	private Long operatorId;
	private String remark;
}
