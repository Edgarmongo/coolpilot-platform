package com.coolskyai.modules.product.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 商品分类
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ProductCategory {
	private Long id;
	private String name;
	private String productType;
	private String icon;
	private Integer sort;
	private Date createTime;
}
