package com.coolskyai.modules.entitlement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.entitlement.service.EntitlementService;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.mapper.ProductMapper;

/**
 * @Description: 权益前台接口
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class EntitlementController {
	@Autowired
	private EntitlementService entitlementService;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private AccountService accountService;

	/**
	 * 我的权益（我的课程 / 已购文档 / 已购代码）
	 */
	@GetMapping("/entitlements")
	public Result mine() {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", entitlementService.myEntitlements(userId));
	}

	/**
	 * 是否可访问
	 *
	 * 优先用 productNo；传 productId 亦可。不返回 403，而是返回可解释的 reason，
	 * 前端据此显示"立即购买""续期""联系客服"，比弹错误框体验好。
	 */
	@GetMapping("/resource/access")
	public Result access(@RequestParam(required = false) String productNo,
	                     @RequestParam(required = false) Long productId) {
		Long userId = accountService.currentUserId();
		if (productNo == null && productId != null) {
			Product product = productMapper.findById(productId);
			if (product == null) {
				return Result.ok("OK", java.util.Map.of("accessible", false, "reason", "NOT_FOUND"));
			}
			productNo = product.getProductNo();
		}
		return Result.ok("OK", entitlementService.access(userId, productNo));
	}
}
