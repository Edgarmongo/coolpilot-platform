package com.coolskyai.modules.trade.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Description: 订单（交易契约）
 *
 * 状态机：
 * PENDING_PAY → PENDING_CONFIRM → PAID → COMPLETED
 *            ↘ CANCELLED / CLOSED
 * PAID/COMPLETED → REFUNDING → REFUNDED
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Order {
	private Long id;
	private String orderNo;
	private Long userId;
	/** PRODUCT / SERVICE */
	private String bizType;
	private BigDecimal totalAmount;
	private BigDecimal discountAmount;
	private BigDecimal payAmount;
	private String status;
	private Date payExpireTime;
	private Date paidTime;
	private Date cancelTime;
	private String remark;
	private Date createTime;
	private Date updateTime;

	/** 列表展示用，不落库 */
	private List<OrderItem> items;
	/** 列表展示用，不落库 */
	private String titleSummary;
}
