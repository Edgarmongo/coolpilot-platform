package top.naccl.modules.trade.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.naccl.modules.trade.entity.OrderItem;

import java.util.List;

/**
 * @Description: 订单项持久层
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface OrderItemMapper {

	@Insert("INSERT INTO order_item (order_id, product_id, product_no, product_type, title_snapshot, cover_snapshot, " +
			"price_snapshot, quantity, amount, create_time) " +
			"VALUES (#{orderId}, #{productId}, #{productNo}, #{productType}, #{titleSnapshot}, #{coverSnapshot}, " +
			"#{priceSnapshot}, #{quantity}, #{amount}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(OrderItem item);

	@Select("SELECT * FROM order_item WHERE order_id = #{orderId}")
	List<OrderItem> listByOrderId(@Param("orderId") Long orderId);

	/**
	 * 批量取订单项，避免列表页 N+1 查询
	 */
	@Select("<script>" +
			"SELECT * FROM order_item WHERE order_id IN " +
			"<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
			"ORDER BY id ASC" +
			"</script>")
	List<OrderItem> listByOrderIds(@Param("orderIds") List<Long> orderIds);
}
