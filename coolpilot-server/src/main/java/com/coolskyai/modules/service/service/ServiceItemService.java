package com.coolskyai.modules.service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.service.entity.ServiceItem;
import com.coolskyai.modules.service.mapper.ServiceItemMapper;

/**
 * @Description: 服务商品扩展
 *
 * 只有 product_type=SERVICE 的商品才允许挂扩展信息，
 * 避免「文档商品也被填了交付周期」这类脏数据。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class ServiceItemService {

	@Autowired
	private ServiceItemMapper serviceItemMapper;
	@Autowired
	private ProductMapper productMapper;

	public ServiceItem findByProductId(Long productId) {
		return productId == null ? null : serviceItemMapper.findByProductId(productId);
	}

	/**
	 * 保存（不存在则新建），返回最新值
	 */
	@Transactional(rollbackFor = Exception.class)
	public ServiceItem save(Long productId, Integer deliveryDays, String scope, String process, String faq) {
		Product product = productMapper.findById(productId);
		if (product == null) {
			throw new BadRequestException("商品不存在");
		}
		if (!"SERVICE".equals(product.getProductType())) {
			throw new BadRequestException("只有技术服务类商品才能配置服务信息");
		}
		ServiceItem item = serviceItemMapper.findByProductId(productId);
		if (item == null) {
			item = new ServiceItem();
			item.setProductId(productId);
			item.setDeliveryDays(deliveryDays == null ? 0 : deliveryDays);
			item.setScope(scope);
			item.setProcess(process);
			item.setFaq(faq);
			serviceItemMapper.insert(item);
		} else {
			item.setDeliveryDays(deliveryDays == null ? 0 : deliveryDays);
			item.setScope(scope);
			item.setProcess(process);
			item.setFaq(faq);
			serviceItemMapper.update(item);
		}
		return serviceItemMapper.findByProductId(productId);
	}
}
