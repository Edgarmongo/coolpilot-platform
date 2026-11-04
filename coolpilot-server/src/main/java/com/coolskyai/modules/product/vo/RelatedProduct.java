package com.coolskyai.modules.product.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * @Description: 文章关联的付费内容（前台展示用）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class RelatedProduct {
	private Long id;
	private String productNo;
	private String productType;
	private String title;
	private String cover;
	private String summary;
	private BigDecimal price;
	private BigDecimal originalPrice;
	private String priceMode;
	private String status;
	private Integer sort;
	/** 当前用户是否已拥有；未登录恒为 false */
	private Boolean owned;
}
