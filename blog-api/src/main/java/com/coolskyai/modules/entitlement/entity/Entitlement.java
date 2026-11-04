package com.coolskyai.modules.entitlement.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 访问权益
 *
 * 权益是"付费内容能不能看"的唯一准入凭证，不靠查订单推导。
 * 原因：退款要撤销、限时课要过期、活动要赠送，这些都表达不了订单状态。
 *
 * 唯一键 (user_id, product_id, ent_type)：同人同商品同类型只有一条。
 * 因此撤销必须按 order_no 精确定位，否则会误删用户此前合法购买的权益。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Entitlement {
	private Long id;
	private Long userId;
	private Long productId;
	private String orderNo;
	/** ACCESS / DOWNLOAD / LEARN */
	private String entType;
	/** PURCHASE / GIFT / TRIAL */
	private String source;
	private Date startTime;
	private Date expireTime;
	/** ACTIVE / EXPIRED / REVOKED */
	private String status;
	private Date createTime;
	private Date updateTime;

	/** 列表展示用，不落库 */
	private String productTitle;
	/** 列表展示用，不落库 */
	private String productNo;
	/** 列表展示用，不落库 */
	private String productType;
	/** 列表展示用，不落库 */
	private String productCover;
}
