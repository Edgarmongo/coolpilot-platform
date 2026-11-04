package top.naccl.modules.product.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @Description: 商品主体
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Product {
	private Long id;
	private String productNo;
	/** DOC / COURSE / COLUMN / CODE / SERVICE */
	private String productType;
	private Long categoryId;
	private String title;
	private String subtitle;
	private String cover;
	private String summary;
	private String detail;
	private BigDecimal price;
	private BigDecimal originalPrice;
	/** FIXED / STARTING / CUSTOM */
	private String priceMode;
	private Integer salesCount;
	private Integer viewCount;
	private Integer sort;
	/** DRAFT / ON_SALE / OFF_SALE / ARCHIVED */
	private String status;
	private Boolean isTop;
	private Long ownerId;
	private Date createTime;
	private Date updateTime;

	/** 列表展示用，不落库 */
	private String categoryName;
}
