package com.coolskyai.modules.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * @Description: 商品新增/编辑入参
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ProductSaveDTO {
	private String productType;
	private Long categoryId;
	private String title;
	private String subtitle;
	private String cover;
	private String summary;
	private String detail;
	private BigDecimal price;
	private BigDecimal originalPrice;
	private String priceMode;
	private Integer sort;
	private Boolean isTop;
	/** 仅 COURSE 类型使用 */
	private String courseDescription;
}
