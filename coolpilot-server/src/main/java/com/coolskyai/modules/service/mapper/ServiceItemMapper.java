package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.ServiceItem;

/**
 * @Description: 服务商品扩展持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ServiceItemMapper {

	@Select("SELECT * FROM service_item WHERE product_id = #{productId}")
	ServiceItem findByProductId(@Param("productId") Long productId);

	@Insert("INSERT INTO service_item (product_id, delivery_days, scope, process, faq, create_time, update_time) " +
			"VALUES (#{productId}, #{deliveryDays}, #{scope}, #{process}, #{faq}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(ServiceItem item);

	@Update("UPDATE service_item SET delivery_days = #{deliveryDays}, scope = #{scope}, process = #{process}, " +
			"faq = #{faq}, update_time = NOW() WHERE product_id = #{productId}")
	int update(ServiceItem item);
}
