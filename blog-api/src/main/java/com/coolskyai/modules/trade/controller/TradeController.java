package com.coolskyai.modules.trade.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.trade.dto.CreateOrderDTO;
import com.coolskyai.modules.trade.dto.CreatePaymentDTO;
import com.coolskyai.modules.trade.dto.ManualConfirmDTO;
import com.coolskyai.modules.trade.dto.RefundApplyDTO;
import com.coolskyai.modules.trade.entity.Order;
import com.coolskyai.modules.trade.entity.Refund;
import com.coolskyai.modules.trade.service.OrderService;
import com.coolskyai.modules.trade.service.PaymentService;
import com.coolskyai.modules.trade.service.RefundService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 交易前台接口（下单 / 支付 / 退款）
 *
 * 所有接口都要求登录，且只允许操作自己的订单。
 * 归属校验在 Service 层做，Controller 只负责取当前用户。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class TradeController {
	@Autowired
	private OrderService orderService;
	@Autowired
	private PaymentService paymentService;
	@Autowired
	private RefundService refundService;
	@Autowired
	private AccountService accountService;

	// ---------------- 订单 ----------------

	@PostMapping("/order")
	public Result create(@RequestBody CreateOrderDTO dto) {
		Long userId = accountService.requireUserId();
		Order order = orderService.create(userId, dto);
		return Result.ok("下单成功", orderService.detail(order.getOrderNo(), userId, false));
	}

	@GetMapping("/order/{orderNo}")
	public Result detail(@PathVariable String orderNo) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", orderService.detail(orderNo, userId, false));
	}

	@GetMapping("/orders")
	public Result orders(@RequestParam(required = false) String status,
	                     @RequestParam(defaultValue = "1") Integer pageNum,
	                     @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", orderService.myOrders(userId, status, pageNum, pageSize));
	}

	@PostMapping("/order/{orderNo}/cancel")
	public Result cancel(@PathVariable String orderNo) {
		Long userId = accountService.requireUserId();
		orderService.cancel(orderNo, userId);
		return Result.ok("订单已取消");
	}

	// ---------------- 支付 ----------------

	/**
	 * 可选收款渠道（前端渲染按钮用）
	 */
	@GetMapping("/payment/channels")
	public Result channels() {
		return Result.ok("OK", PaymentService.MANUAL_CHANNELS);
	}

	@PostMapping("/payment")
	public Result pay(@RequestBody CreatePaymentDTO dto) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", paymentService.create(userId, dto));
	}

	@GetMapping("/payment/{paymentNo}")
	public Result payment(@PathVariable String paymentNo) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", paymentService.query(paymentNo, userId));
	}

	/**
	 * 人工转账后提交付款凭证
	 */
	@PostMapping("/payment/{paymentNo}/manual-confirm")
	public Result manualConfirm(@PathVariable String paymentNo, @RequestBody(required = false) ManualConfirmDTO dto) {
		Long userId = accountService.requireUserId();
		paymentService.submitTransferProof(paymentNo, userId, dto);
		return Result.ok("已提交，等待确认收款");
	}

	// ---------------- 退款 ----------------

	@PostMapping("/refund")
	public Result applyRefund(@RequestBody RefundApplyDTO dto) {
		Long userId = accountService.requireUserId();
		Refund refund = refundService.apply(userId, dto);
		Map<String, Object> data = new HashMap<>(2);
		data.put("refundNo", refund.getRefundNo());
		data.put("status", refund.getStatus());
		return Result.ok("退款申请已提交", data);
	}

	@GetMapping("/refund/{refundNo}")
	public Result refund(@PathVariable String refundNo) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", refundService.detail(refundNo, userId, false));
	}

	@GetMapping("/refunds")
	public Result refunds(@RequestParam(defaultValue = "1") Integer pageNum,
	                      @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", refundService.myRefunds(userId, pageNum, pageSize));
	}
}
