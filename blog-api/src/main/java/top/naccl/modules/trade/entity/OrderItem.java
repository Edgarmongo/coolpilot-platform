package top.naccl.modules.trade.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 订单项（含商品快照）
 *
 * 快照优先于关联：商品改价、改名、下架都不影响历史订单的展示与对账。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class OrderItem {
	private Long id;
	private Long orderId;
	private Long productId;
	private String productNo;
	private String productType;
	private String titleSnapshot;
	private String coverSnapshot;
	private BigDecimal priceSnapshot;
	private Integer quantity;
	private BigDecimal amount;
	private Date createTime;
}
