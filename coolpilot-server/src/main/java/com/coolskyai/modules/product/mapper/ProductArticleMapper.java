package com.coolskyai.modules.product.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.coolskyai.modules.product.vo.RelatedProduct;

import java.util.List;

/**
 * @Description: 文章 ↔ 商品关联
 *
 * 表在 V1 快照里就有（product_article），但一直没代码用它；
 * 这次把它接上，用于「文章 → 付费内容」的转化入口。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ProductArticleMapper {

	@Select("SELECT p.id, p.product_no, p.product_type, p.title, p.cover, p.summary, p.price, p.original_price, " +
			"p.price_mode, p.status, pa.sort FROM product_article pa JOIN product p ON p.id = pa.product_id " +
			"WHERE pa.blog_id = #{blogId} ORDER BY pa.sort ASC, pa.id ASC")
	List<RelatedProduct> listByBlogId(@Param("blogId") Long blogId);

	@Select("SELECT product_id FROM product_article WHERE blog_id = #{blogId} ORDER BY sort ASC, id ASC")
	List<Long> listProductIdsByBlogId(@Param("blogId") Long blogId);

	@Delete("DELETE FROM product_article WHERE blog_id = #{blogId}")
	int deleteByBlogId(@Param("blogId") Long blogId);

	@Insert("INSERT INTO product_article (product_id, blog_id, sort, create_time) " +
			"VALUES (#{productId}, #{blogId}, #{sort}, NOW())")
	int insert(@Param("productId") Long productId, @Param("blogId") Long blogId, @Param("sort") int sort);

	@Select("SELECT COUNT(*) FROM blog WHERE id = #{blogId}")
	int countBlog(@Param("blogId") Long blogId);
}
