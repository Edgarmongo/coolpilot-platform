package top.naccl.modules.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.naccl.modules.product.entity.ProductCategory;

import java.util.List;

/**
 * @Description: 商品分类持久层
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface ProductCategoryMapper {

	@Select("<script>" +
			"SELECT * FROM product_category " +
			"<where><if test='type != null and type != \"\"'>AND product_type = #{type}</if></where>" +
			"ORDER BY sort ASC, id ASC" +
			"</script>")
	List<ProductCategory> listByType(@Param("type") String type);

	@Insert("INSERT INTO product_category (name, product_type, icon, sort, create_time) " +
			"VALUES (#{name}, #{productType}, #{icon}, #{sort}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ProductCategory category);
}
