package top.naccl.modules.trade.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.annotation.OperationLogger;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.trade.mapper.OrderMapper;
import top.naccl.modules.trade.mapper.RefundMapper;
import top.naccl.modules.trade.service.OrderService;
import top.naccl.modules.trade.service.PaymentService;
import top.naccl.modules.trade.service.RefundService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 交易管理（后台）
 *
 * 人工收款模式的核心操作都在这里：
 * 看「待确认收款」列表 → 核对到账 → 点确认 → 自动发权益。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@RestController
public class TradeAdminController {
	@Autowired
	private OrderService orderService;
	@Autowired
	private PaymentService paymentService;
	@Autowired
	private RefundService refundService;
	@Autowired
	private OrderMapper orderMapper;
	@Autowired
	private RefundMapper refundMapper;
	@Autowired
	private AccountService accountService;

	// ---------------- 订单 ----------------

	@GetMapping("/admin/orders")
	public Result orders(@RequestParam(required = false) String status,
	                     @RequestParam(required = false) String keyword,
	                     @RequestParam(required = false) String startTime,
	                     @RequestParam(required = false) String endTime,
	                     @RequestParam(defaultValue = "1") Integer pageNum,
	                     @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", orderService.pageAdmin(status, keyword, startTime, endTime, pageNum, pageSize));
	}

	@GetMapping("/admin/order/{orderNo}")
	public Result order(@PathVariable String orderNo) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", orderService.detail(orderNo, userId, true));
	}

	@OperationLogger("关闭超时订单")
	@PostMapping("/admin/order/closeExpired")
	public Result closeExpired() {
		Map<String, Object> data = new HashMap<>(2);
		data.put("count", orderService.closeExpired());
		return Result.ok("处理完成", data);
	}

	// ---------------- 支付 ----------------

	@GetMapping("/admin/payments")
	public Result payments(@RequestParam(required = false) String status,
	                       @RequestParam(required = false) String channel,
	                       @RequestParam(required = false) String keyword,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", paymentService.pageAdmin(status, channel, keyword, pageNum, pageSize));
	}

	/**
	 * 人工确认收款（唯一发权益入口）
	 */
	@OperationLogger("确认收款")
	@PostMapping("/admin/payment/{paymentNo}/confirm")
	public Result confirm(@PathVariable String paymentNo,
	                      @RequestParam(required = false) String tradeNo,
	                      @RequestParam(required = false) String remark) {
		Long operatorId = accountService.requireUserId();
		return Result.ok("OK", paymentService.confirmManual(paymentNo, operatorId, tradeNo, remark));
	}

	@OperationLogger("关闭支付单")
	@PostMapping("/admin/payment/{paymentNo}/close")
	public Result closePayment(@PathVariable String paymentNo,
	                           @RequestParam(required = false) String remark) {
		paymentService.close(paymentNo, remark);
		return Result.ok("已关闭");
	}

	// ---------------- 退款 ----------------

	@GetMapping("/admin/refunds")
	public Result refunds(@RequestParam(required = false) String status,
	                      @RequestParam(required = false) String keyword,
	                      @RequestParam(defaultValue = "1") Integer pageNum,
	                      @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", refundService.pageAdmin(status, keyword, pageNum, pageSize));
	}

	@OperationLogger("同意退款")
	@PostMapping("/admin/refund/{refundNo}/approve")
	public Result approve(@PathVariable String refundNo,
	                      @RequestParam(required = false) String remark) {
		Long operatorId = accountService.requireUserId();
		refundService.approve(refundNo, operatorId, remark);
		return Result.ok("已同意，请完成线下打款后点「确认已退款」");
	}

	@OperationLogger("拒绝退款")
	@PostMapping("/admin/refund/{refundNo}/reject")
	public Result reject(@PathVariable String refundNo,
	                     @RequestParam(required = false) String remark) {
		Long operatorId = accountService.requireUserId();
		refundService.reject(refundNo, operatorId, remark);
		return Result.ok("已拒绝");
	}

	@OperationLogger("确认退款完成")
	@PostMapping("/admin/refund/{refundNo}/finish")
	public Result finish(@PathVariable String refundNo,
	                     @RequestParam(required = false) String remark) {
		Long operatorId = accountService.requireUserId();
		refundService.finish(refundNo, operatorId, remark);
		return Result.ok("退款完成，相关权益已撤销");
	}

	// ---------------- 统计 ----------------

	@GetMapping("/admin/stats/sales")
	public Result sales(@RequestParam(required = false) String startTime,
	                    @RequestParam(required = false) String endTime) {
		BigDecimal paid = orderMapper.sumPaidAmount(startTime, endTime);
		BigDecimal refunded = refundMapper.sumRefunded(startTime, endTime);
		Map<String, Object> data = new HashMap<>(8);
		data.put("paidAmount", paid);
		data.put("refundAmount", refunded);
		data.put("netAmount", paid.subtract(refunded));
		data.put("paidOrders", orderMapper.countPaid(startTime, endTime));
		data.put("waitingConfirm", paymentService.countWaitingConfirm());
		return Result.ok("OK", data);
	}
}
