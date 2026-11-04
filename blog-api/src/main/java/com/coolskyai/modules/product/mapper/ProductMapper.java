package com.coolskyai.modules.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.product.entity.Product;

import java.util.List;

/**
 * @Description: 商品持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ProductMapper {

	String LIST_COLUMNS = "p.*, pc.name AS category_name FROM product p LEFT JOIN product_category pc ON pc.id = p.category_id ";

	@Select("<script>" +
			"SELECT " + LIST_COLUMNS +
			"WHERE p.status = 'ON_SALE' " +
			"<if test='type != null and type != \"\"'>AND p.product_type = #{type} </if>" +
			"<if test='categoryId != null and categoryId > 0'>AND p.category_id = #{categoryId} </if>" +
			"<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%') </if>" +
			"ORDER BY p.is_top DESC, p.sort DESC, p.id DESC " +
			"LIMIT #{offset}, #{size}" +
			"</script>")
	List<Product> pageOnSale(@Param("type") String type,
	                         @Param("categoryId") Long categoryId,
	                         @Param("keyword") String keyword,
	                         @Param("offset") int offset,
	                         @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM product p WHERE p.status = 'ON_SALE' " +
			"<if test='type != null and type != \"\"'>AND p.product_type = #{type} </if>" +
			"<if test='categoryId != null and categoryId > 0'>AND p.category_id = #{categoryId} </if>" +
			"<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%') </if>" +
			"</script>")
	int countOnSale(@Param("type") String type,
	                @Param("categoryId") Long categoryId,
	                @Param("keyword") String keyword);

	@Select("SELECT " + LIST_COLUMNS + "WHERE p.product_no = #{productNo} AND p.status = 'ON_SALE'")
	Product findOnSaleByNo(@Param("productNo") String productNo);

	@Select("SELECT " + LIST_COLUMNS + "WHERE p.product_no = #{productNo}")
	Product findByNo(@Param("productNo") String productNo);

	@Select("SELECT " + LIST_COLUMNS + "WHERE p.id = #{id}")
	Product findById(@Param("id") Long id);

	@Select("<script>" +
			"SELECT " + LIST_COLUMNS +
			"<where>" +
			"<if test='type != null and type != \"\"'>AND p.product_type = #{type}</if>" +
			"<if test='status != null and status != \"\"'>AND p.status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>" +
			"</where>" +
			"ORDER BY p.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Product> pageAdmin(@Param("type") String type,
	                        @Param("status") String status,
	                        @Param("keyword") String keyword,
	                        @Param("offset") int offset,
	                        @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM product p " +
			"<where>" +
			"<if test='type != null and type != \"\"'>AND p.product_type = #{type}</if>" +
			"<if test='status != null and status != \"\"'>AND p.status = #{status}</if>" +
			"<if test='keyword != null and keyword != \"\"'>AND p.title LIKE CONCAT('%', #{keyword}, '%')</if>" +
			"</where>" +
			"</script>")
	int countAdmin(@Param("type") String type,
	               @Param("status") String status,
	               @Param("keyword") String keyword);

	@Insert("INSERT INTO product (product_no, product_type, category_id, title, subtitle, cover, summary, detail, " +
			"price, original_price, price_mode, sales_count, view_count, sort, status, is_top, owner_id, create_time, update_time) " +
			"VALUES (#{productNo}, #{productType}, #{categoryId}, #{title}, #{subtitle}, #{cover}, #{summary}, #{detail}, " +
			"#{price}, #{originalPrice}, #{priceMode}, 0, 0, #{sort}, #{status}, #{isTop}, #{ownerId}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Product product);

	@Update("UPDATE product SET product_type = #{productType}, category_id = #{categoryId}, title = #{title}, " +
			"subtitle = #{subtitle}, cover = #{cover}, summary = #{summary}, detail = #{detail}, price = #{price}, " +
			"original_price = #{originalPrice}, price_mode = #{priceMode}, sort = #{sort}, is_top = #{isTop}, " +
			"update_time = NOW() WHERE id = #{id}")
	int update(Product product);

	@Update("UPDATE product SET status = #{status}, update_time = NOW() WHERE id = #{id}")
	int changeStatus(@Param("id") Long id, @Param("status") String status);

	@Update("UPDATE product SET view_count = view_count + 1 WHERE id = #{id}")
	int increaseView(@Param("id") Long id);

	/**
	 * 销量增减（退款时传负数，且不会减到 0 以下）
	 */
	@Update("UPDATE product SET sales_count = GREATEST(sales_count + #{delta}, 0) WHERE id = #{id}")
	int addSales(@Param("id") Long id, @Param("delta") int delta);

	@Select("SELECT COUNT(*) FROM product WHERE product_no = #{productNo}")
	int countByNo(@Param("productNo") String productNo);
}
