package com.coolskyai.modules.trade.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 支付单（渠道事实）
 *
 * 状态机：INIT → WAIT_CONFIRM（人工模式下用户提交凭证）→ SUCCESS
 *             ↘ FAIL / CLOSED
 *
 * 幂等：SUCCESS 只能由条件更新（WHERE status IN ('INIT','WAIT_CONFIRM')）写入一次，
 * 影响行数为 1 的那次才允许发放权益。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Payment {
	private Long id;
	private String paymentNo;
	private String bizType;
	private String bizNo;
	private Long userId;
	/** MANUAL_WECHAT / MANUAL_ALIPAY / MANUAL_PAYPAL */
	private String channel;
	private String tradeNo;
	private BigDecimal amount;
	private String status;
	private Date paidTime;
	private String payerName;
	private Date transferTime;
	private Long proofFileId;
	private Long confirmUserId;
	private String remark;
	private String notifyRaw;
	private Date createTime;
	private Date updateTime;
}
