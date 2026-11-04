package com.coolskyai.modules.product.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.service.ProductService;
import com.coolskyai.modules.service.service.ServiceItemService;

import java.util.Map;

/**
 * @Description: 知识商品（前台公开接口）
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class ProductController {
	@Autowired
	private ProductService productService;
	@Autowired
	private ServiceItemService serviceItemService;

	@GetMapping("/products")
	public Result products(@RequestParam(required = false) String type,
	                       @RequestParam(required = false) Long categoryId,
	                       @RequestParam(required = false) String keyword,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "12") Integer pageSize) {
		return Result.ok("OK", productService.pageOnSale(type, categoryId, keyword, pageNum, pageSize));
	}

	@GetMapping("/productCategories")
	public Result categories(@RequestParam(required = false) String type) {
		return Result.ok("OK", productService.categories(type));
	}

	@GetMapping("/product/{productNo}")
	public Result detail(@PathVariable String productNo) {
		Map<String, Object> data = productService.detail(productNo);
		// 服务类商品额外带出交付周期 / 服务范围 / 流程 / FAQ
		Object product = data.get("product");
		if (product instanceof Product && "SERVICE".equals(((Product) product).getProductType())) {
			data.put("serviceItem", serviceItemService.findByProductId(((Product) product).getId()));
		}
		return Result.ok("OK", data);
	}

	@GetMapping("/product/{productNo}/catalog")
	public Result catalog(@PathVariable String productNo) {
		return Result.ok("OK", productService.catalog(productNo));
	}
}
