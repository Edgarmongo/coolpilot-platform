package top.naccl.modules.product.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.naccl.modules.product.entity.ProductResource;

import java.util.List;

/**
 * @Description: 商品可下载资源持久层
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface ProductResourceMapper {

	@Select("SELECT * FROM product_resource WHERE product_id = #{productId} ORDER BY sort ASC, id ASC")
	List<ProductResource> listByProduct(@Param("productId") Long productId);

	@Select("SELECT * FROM product_resource WHERE file_id = #{fileId} LIMIT 1")
	ProductResource findByFileId(@Param("fileId") Long fileId);

	@Insert("INSERT INTO product_resource (product_id, resource_type, file_id, display_name, version, is_latest, sort, create_time) " +
			"VALUES (#{productId}, #{resourceType}, #{fileId}, #{displayName}, #{version}, #{isLatest}, #{sort}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(ProductResource resource);

	@Delete("DELETE FROM product_resource WHERE id = #{id}")
	int delete(@Param("id") Long id);
}
