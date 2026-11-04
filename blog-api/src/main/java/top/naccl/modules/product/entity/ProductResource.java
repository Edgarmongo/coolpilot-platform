package top.naccl.modules.product.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * @Description: 商品可下载资源
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ProductResource {
	private Long id;
	private Long productId;
	/** DOC / CODE */
	private String resourceType;
	private Long fileId;
	private String displayName;
	private String version;
	private Boolean isLatest;
	private Integer sort;
	private Date createTime;
}
