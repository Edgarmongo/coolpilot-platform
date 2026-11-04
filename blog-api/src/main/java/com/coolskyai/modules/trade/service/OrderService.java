package com.coolskyai.modules.trade.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.entitlement.entity.Entitlement;
import com.coolskyai.modules.entitlement.mapper.EntitlementMapper;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.trade.dto.CreateOrderDTO;
import com.coolskyai.modules.trade.entity.Order;
import com.coolskyai.modules.trade.entity.OrderItem;
import com.coolskyai.modules.trade.entity.Payment;
import com.coolskyai.modules.trade.mapper.OrderItemMapper;
import com.coolskyai.modules.trade.mapper.OrderMapper;
import com.coolskyai.modules.trade.mapper.PaymentMapper;
import com.coolskyai.modules.trade.mapper.RefundMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * @Description: 订单服务
 *
 * 三条硬规则：
 * 1. 金额只由服务端按商品当前售价计算，前端传什么都不看。
 * 2. 数字商品一人一份，已持有权益时不允许重复下单。
 * 3. 状态流转全部走带原状态条件的 UPDATE，谁抢到算谁的。
 *
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Service
public class OrderService {
	/** 待支付订单有效期 */
	private static final int PAY_EXPIRE_MINUTES = 30;
	private static final Set<String> STATUSES = Set.of(
			"PENDING_PAY", "PENDING_CONFIRM", "PAID", "COMPLETED", "CANCELLED", "CLOSED", "REFUNDING", "REFUNDED");

	@Autowired
	private OrderMapper orderMapper;
	@Autowired
	private OrderItemMapper orderItemMapper;
	@Autowired
	private PaymentMapper paymentMapper;
	@Autowired
	private RefundMapper refundMapper;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private EntitlementMapper entitlementMapper;
	@Autowired
	private StringRedisTemplate redisTemplate;

	// ---------------- 下单 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Order create(Long userId, CreateOrderDTO dto) {
		if (dto.getProductNo() == null || dto.getProductNo().trim().isEmpty()) {
			throw new BadRequestException("商品编号不能为空");
		}
		Product product = productMapper.findOnSaleByNo(dto.getProductNo().trim());
		if (product == null) {
			throw new BadRequestException("商品不存在或已下架");
		}
		if (!"FIXED".equals(product.getPriceMode())) {
			throw new BadRequestException("该商品需人工报价，请通过「联系我」沟通后再下单");
		}
		if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("免费内容无需下单，可直接访问");
		}
		if (entitlementMapper.countActive(userId, product.getId()) > 0) {
			throw new BadRequestException("你已拥有该商品，可直接查看");
		}

		// 防连点：同一商品 10 秒内只允许提交一次
		guardRepeatedSubmit(userId, product.getId());

		// 已有未过期的待支付订单直接复用，避免用户重复付款
		Order pending = orderMapper.findPending(userId, product.getId());
		if (pending != null) {
			return pending;
		}

		BigDecimal price = product.getPrice();
		int quantity = 1;

		Order order = new Order();
		order.setOrderNo(generateOrderNo());
		order.setUserId(userId);
		order.setBizType("PRODUCT");
		order.setTotalAmount(price);
		order.setDiscountAmount(BigDecimal.ZERO);
		order.setPayAmount(price);
		order.setStatus("PENDING_PAY");
		order.setPayExpireTime(new Date(System.currentTimeMillis() + PAY_EXPIRE_MINUTES * 60_000L));
		order.setRemark(dto.getRemark() == null ? "" : dto.getRemark().trim());
		orderMapper.insert(order);

		OrderItem item = new OrderItem();
		item.setOrderId(order.getId());
		item.setProductId(product.getId());
		item.setProductNo(product.getProductNo());
		item.setProductType(product.getProductType());
		item.setTitleSnapshot(product.getTitle());
		item.setCoverSnapshot(product.getCover() == null ? "" : product.getCover());
		item.setPriceSnapshot(price);
		item.setQuantity(quantity);
		item.setAmount(price.multiply(BigDecimal.valueOf(quantity)));
		orderItemMapper.insert(item);

		return order;
	}

	// ---------------- 查询 ----------------

	public Map<String, Object> detail(String orderNo, Long userId, boolean isOwner) {
		Order order = orderMapper.findByNo(orderNo);
		if (order == null) {
			throw new BadRequestException("订单不存在");
		}
		if (!isOwner && !order.getUserId().equals(userId)) {
			throw new BadRequestException("无权查看该订单");
		}
		Map<String, Object> data = new HashMap<>(8);
		data.put("order", order);
		data.put("items", orderItemMapper.listByOrderId(order.getId()));
		List<Payment> payments = paymentMapper.listByBizNo(orderNo);
		data.put("payments", payments);
		data.put("refunds", refundMapper.listByBizNo(orderNo));
		List<Entitlement> entitlements = entitlementMapper.listByOrderNo(orderNo);
		data.put("entitlements", entitlements);
		return data;
	}

	public PageResult<Order> myOrders(Long userId, String status, Integer pageNum, Integer pageSize) {
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = orderMapper.countByUser(userId, status);
		List<Order> list = orderMapper.pageByUser(userId, status, (page - 1) * size, size);
		fillItems(list);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<Order> pageAdmin(String status, String keyword, String startTime, String endTime,
	                                   Integer pageNum, Integer pageSize) {
		if (status != null && !status.isEmpty() && !STATUSES.contains(status)) {
			throw new BadRequestException("非法订单状态：" + status);
		}
		int page = normalizePage(pageNum);
		int size = normalizeSize(pageSize);
		int total = orderMapper.countAdmin(status, keyword, startTime, endTime);
		List<Order> list = orderMapper.pageAdmin(status, keyword, startTime, endTime, (page - 1) * size, size);
		fillItems(list);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ---------------- 状态流转 ----------------

	/**
	 * 用户取消（仅待支付）
	 */
	@Transactional(rollbackFor = Exception.class)
	public void cancel(String orderNo, Long userId) {
		Order order = orderMapper.findByNo(orderNo);
		if (order == null) {
			throw new BadRequestException("订单不存在");
		}
		if (!order.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该订单");
		}
		if (!"PENDING_PAY".equals(order.getStatus())) {
			throw new BadRequestException("当前状态不可取消");
		}
		if (orderMapper.cancel(orderNo, userId) == 0) {
			throw new BadRequestException("订单状态已变化，请刷新后重试");
		}
		Payment payment = paymentMapper.findLatestByBizNo(orderNo);
		if (payment != null && ("INIT".equals(payment.getStatus()) || "WAIT_CONFIRM".equals(payment.getStatus()))) {
			paymentMapper.close(payment.getPaymentNo(), "用户取消订单");
		}
	}

	/**
	 * 关闭超时未支付订单（后台可手动触发，也可由运维定时调用）
	 */
	public int closeExpired() {
		return orderMapper.closeExpired();
	}

	/**
	 * 校验订单可用于付款
	 */
	public Order requirePayable(String orderNo, Long userId) {
		Order order = orderMapper.findByNo(orderNo);
		if (order == null) {
			throw new BadRequestException("订单不存在");
		}
		if (!order.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该订单");
		}
		if ("PENDING_CONFIRM".equals(order.getStatus())) {
			return order;
		}
		if (!"PENDING_PAY".equals(order.getStatus())) {
			throw new BadRequestException("订单当前状态为「" + statusText(order.getStatus()) + "」，不可付款");
		}
		if (order.getPayExpireTime() != null && order.getPayExpireTime().before(new Date())) {
			orderMapper.close(orderNo);
			throw new BadRequestException("订单已超时关闭，请重新下单");
		}
		return order;
	}

	// ---------------- 内部 ----------------

	private void guardRepeatedSubmit(Long userId, Long productId) {
		try {
			String key = "order:submit:" + userId + ":" + productId;
			Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(10));
			if (Boolean.FALSE.equals(ok)) {
				throw new BadRequestException("操作过于频繁，请稍后再试");
			}
		} catch (BadRequestException e) {
			throw e;
		} catch (Exception ignored) {
			// Redis 不可用时退化为数据库判断（findPending）
		}
	}

	private void fillItems(List<Order> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		List<Long> ids = orders.stream().map(Order::getId).collect(Collectors.toList());
		List<OrderItem> items = orderItemMapper.listByOrderIds(ids);
		Map<Long, List<OrderItem>> grouped = new HashMap<>(ids.size());
		for (OrderItem item : items) {
			grouped.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
		}
		for (Order order : orders) {
			List<OrderItem> own = grouped.getOrDefault(order.getId(), List.of());
			order.setItems(own);
			if (!own.isEmpty()) {
				String titles = own.stream().map(OrderItem::getTitleSnapshot).collect(Collectors.joining("、"));
				order.setTitleSummary(own.size() > 1 ? titles + " 等" + own.size() + " 件" : titles);
			}
		}
	}

	private String generateOrderNo() {
		String prefix = "O" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
			if (orderMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成订单号失败，请重试");
	}

	public static String statusText(String status) {
		if (status == null) {
			return "";
		}
		switch (status) {
			case "PENDING_PAY":
				return "待支付";
			case "PENDING_CONFIRM":
				return "待确认收款";
			case "PAID":
				return "已支付";
			case "COMPLETED":
				return "已完成";
			case "CANCELLED":
				return "已取消";
			case "CLOSED":
				return "已关闭";
			case "REFUNDING":
				return "退款中";
			case "REFUNDED":
				return "已退款";
			default:
				return status;
		}
	}

	private int normalizePage(Integer pageNum) {
		return (pageNum == null || pageNum < 1) ? 1 : pageNum;
	}

	private int normalizeSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return 10;
		}
		return Math.min(pageSize, 100);
	}
}
