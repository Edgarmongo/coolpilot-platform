package com.coolskyai.modules.trade.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * @Description: 下单入参
 * 注意：金额不接收前端传值，一律由服务端按商品当前售价计算。
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class CreateOrderDTO {
	private String productNo;
	private Integer quantity;
	private String remark;
}
