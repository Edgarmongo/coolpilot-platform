package com.coolskyai.modules.trade.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.entitlement.service.EntitlementService;
import com.coolskyai.modules.notify.service.NotifyService;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.trade.dto.RefundApplyDTO;
import com.coolskyai.modules.trade.entity.Order;
import com.coolskyai.modules.trade.entity.OrderItem;
import com.coolskyai.modules.trade.entity.Payment;
import com.coolskyai.modules.trade.entity.Refund;
import com.coolskyai.modules.trade.mapper.OrderItemMapper;
import com.coolskyai.modules.trade.mapper.OrderMapper;
import com.coolskyai.modules.trade.mapper.PaymentMapper;
import com.coolskyai.modules.trade.mapper.RefundMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description: 退款服务
 *
 * 数字商品退款是「先审核、后打款、再撤销权益」的三段式：
 * APPLIED（用户申请）→ APPROVED（同意，待打款）→ SUCCESS（已打款，撤销权益）
 * 之所以不一步到位，是因为人工渠道的退款要真的去微信/支付宝/PayPal 操作，
 * 中途失败可以停在 APPROVED 重试，不会出现"钱没退但权益没了"。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class RefundService {
	@Autowired
	private RefundMapper refundMapper;
	@Autowired
	private OrderMapper orderMapper;
	@Autowired
	private OrderItemMapper orderItemMapper;
	@Autowired
	private PaymentMapper paymentMapper;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private EntitlementService entitlementService;
	@Autowired
	private NotifyService notifyService;

	@Transactional(rollbackFor = Exception.class)
	public Refund apply(Long userId, RefundApplyDTO dto) {
		if (dto.getOrderNo() == null || dto.getOrderNo().trim().isEmpty()) {
			throw new BadRequestException("订单号不能为空");
		}
		if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
			throw new BadRequestException("请填写退款原因");
		}
		String orderNo = dto.getOrderNo().trim();
		Order order = orderMapper.findByNo(orderNo);
		if (order == null) {
			throw new BadRequestException("订单不存在");
		}
		if (!order.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该订单");
		}
		if (!"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
			throw new BadRequestException("订单当前状态为「" + OrderService.statusText(order.getStatus()) + "」，不可申请退款");
		}
		if (refundMapper.findActiveByBizNo(orderNo) != null) {
			throw new BadRequestException("该订单已有处理中的退款申请");
		}
		Payment payment = paymentMapper.findLatestByBizNo(orderNo);
		if (payment == null || !"SUCCESS".equals(payment.getStatus())) {
			throw new BadRequestException("未找到成功的支付记录，无法退款");
		}

		Refund refund = new Refund();
		refund.setRefundNo(generateRefundNo());
		refund.setBizType(order.getBizType());
		refund.setBizNo(orderNo);
		refund.setPaymentNo(payment.getPaymentNo());
		refund.setUserId(userId);
		refund.setAmount(order.getPayAmount());
		refund.setReason(dto.getReason().trim());
		refund.setStatus("APPLIED");
		refundMapper.insert(refund);

		if (orderMapper.markRefunding(orderNo) == 0) {
			throw new BadRequestException("订单状态已变化，请刷新后重试");
		}

		notifyService.toOwner("ORDER", "收到退款申请",
				"订单 " + orderNo + " 申请退款，金额 ¥" + refund.getAmount()
						+ "，原因：" + refund.getReason() + "。请到后台处理。",
				"/trade/refund", "REFUND", refund.getId());
		return refund;
	}

	/**
	 * 同意退款（进入待打款）
	 */
	public void approve(String refundNo, Long operatorId, String remark) {
		Refund refund = requireRefund(refundNo);
		if (!"APPLIED".equals(refund.getStatus())) {
			throw new BadRequestException("当前退款单状态不可审核");
		}
		if (refundMapper.approve(refundNo, operatorId, remark == null ? "" : remark) == 0) {
			throw new BadRequestException("退款单状态已变化，请刷新后重试");
		}

		notifyService.toUser(refund.getUserId(), "ORDER", "退款申请已通过",
				"订单 " + refund.getBizNo() + " 的退款申请已通过，金额 ¥" + refund.getAmount()
						+ "，平台将尽快安排打款。",
				"/me?tab=order", "REFUND", refund.getId());
	}

	/**
	 * 拒绝退款：订单退回已完成
	 */
	@Transactional(rollbackFor = Exception.class)
	public void reject(String refundNo, Long operatorId, String remark) {
		Refund refund = requireRefund(refundNo);
		if (!"APPLIED".equals(refund.getStatus())) {
			throw new BadRequestException("当前退款单状态不可审核");
		}
		String text = remark == null || remark.trim().isEmpty() ? "不符合退款条件" : remark.trim();
		if (refundMapper.reject(refundNo, operatorId, text) == 0) {
			throw new BadRequestException("退款单状态已变化，请刷新后重试");
		}
		orderMapper.restoreAfterRefundRejected(refund.getBizNo());

		notifyService.toUser(refund.getUserId(), "ORDER", "退款申请未通过",
				"订单 " + refund.getBizNo() + " 的退款申请未通过，原因：" + text
						+ "。如有疑问可以直接提交意见反馈。",
				"/me?tab=order", "REFUND", refund.getId());
	}

	/**
	 * 确认已打款：撤销该订单发放的权益，回滚销量
	 */
	@Transactional(rollbackFor = Exception.class)
	public void finish(String refundNo, Long operatorId, String remark) {
		Refund refund = requireRefund(refundNo);
		if (!"APPROVED".equals(refund.getStatus())) {
			throw new BadRequestException("请先审核同意该退款申请");
		}
		if (refundMapper.finish(refundNo, operatorId, remark == null ? "" : remark) == 0) {
			throw new BadRequestException("退款单状态已变化，请刷新后重试");
		}
		orderMapper.markRefunded(refund.getBizNo());
		entitlementService.revokeByOrder(refund.getBizNo(), "退款完成：" + refundNo, operatorId);

		Order order = orderMapper.findByNo(refund.getBizNo());
		if (order != null) {
			List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
			for (OrderItem item : items) {
				productMapper.addSales(item.getProductId(), -(item.getQuantity() == null ? 1 : item.getQuantity()));
			}
		}

		notifyService.toUser(refund.getUserId(), "ORDER", "退款已完成",
				"订单 " + refund.getBizNo() + " 已退款 ¥" + refund.getAmount()
						+ "，对应的权益已收回。到账时间以支付渠道为准。",
				"/me?tab=order", "REFUND", refund.getId());
	}

	// ---------------- 查询 ----------------

	public Refund detail(String refundNo, Long userId, boolean isOwner) {
		Refund refund = requireRefund(refundNo);
		if (!isOwner && !refund.getUserId().equals(userId)) {
			throw new BadRequestException("无权查看该退款单");
		}
		return refund;
	}

	public PageResult<Refund> myRefunds(Long userId, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
		int total = refundMapper.countByUser(userId);
		List<Refund> list = refundMapper.pageByUser(userId, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<Refund> pageAdmin(String status, String keyword, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
		int total = refundMapper.countAdmin(status, keyword);
		List<Refund> list = refundMapper.pageAdmin(status, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ---------------- 内部 ----------------

	private Refund requireRefund(String refundNo) {
		Refund refund = refundMapper.findByNo(refundNo);
		if (refund == null) {
			throw new BadRequestException("退款单不存在");
		}
		return refund;
	}

	private String generateRefundNo() {
		String prefix = "R" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
			if (refundMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成退款单号失败，请重试");
	}
}
