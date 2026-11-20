package com.coolskyai.modules.trade.service;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * 订单服务。
 *
 * <p>三条硬规则，改这个类之前请先看 [docs/TRADE-STATE-MACHINE.md]：
 * <ol>
 *   <li><b>金额只由服务端算</b>：取商品表里的当前售价，前端传的金额一律不看。</li>
 *   <li><b>数字商品一人一份</b>：已持有权益不允许重复下单。</li>
 *   <li><b>状态流转走带原状态条件的 UPDATE</b>：靠影响行数判断谁抢到了这次流转，
 *       不能改成「先查状态再更新」—— 那是竞态漏洞。</li>
 * </ol>
 *
 * @author CoolSkyAI
 */
@Service
public class OrderService {

	/** 待支付订单有效期（分钟） */
	private static final int PAY_EXPIRE_MINUTES = 30;

	/** 同商品防连点窗口（秒） */
	private static final int SUBMIT_GUARD_SECONDS = 10;

	/** 列表分页上限，避免一次拉全表 */
	private static final int MAX_PAGE_SIZE = 100;

	private static final String PENDING_PAY = "PENDING_PAY";
	private static final String PENDING_CONFIRM = "PENDING_CONFIRM";
	private static final String PAID = "PAID";
	private static final String COMPLETED = "COMPLETED";
	private static final String CANCELLED = "CANCELLED";
	private static final String CLOSED = "CLOSED";
	private static final String REFUNDING = "REFUNDING";
	private static final String REFUNDED = "REFUNDED";

	/** 后台按状态筛选时只接受这些值，防止拼接非法条件 */
	private static final Set<String> KNOWN_STATUSES =
			Set.of(PENDING_PAY, PENDING_CONFIRM, PAID, COMPLETED, CANCELLED, CLOSED, REFUNDING, REFUNDED);

	/** 状态码 → 中文。前端直接展示，改文案要同步通知前端 */
	private static final Map<String, String> STATUS_TEXT = Map.of(
			PENDING_PAY, "待支付",
			PENDING_CONFIRM, "待确认收款",
			PAID, "已支付",
			COMPLETED, "已完成",
			CANCELLED, "已取消",
			CLOSED, "已关闭",
			REFUNDING, "退款中",
			REFUNDED, "已退款");

	private final OrderMapper orderMapper;
	private final OrderItemMapper orderItemMapper;
	private final PaymentMapper paymentMapper;
	private final RefundMapper refundMapper;
	private final ProductMapper productMapper;
	private final EntitlementMapper entitlementMapper;
	private final StringRedisTemplate redisTemplate;

	public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper, PaymentMapper paymentMapper,
	                    RefundMapper refundMapper, ProductMapper productMapper,
	                    EntitlementMapper entitlementMapper, StringRedisTemplate redisTemplate) {
		this.orderMapper = orderMapper;
		this.orderItemMapper = orderItemMapper;
		this.paymentMapper = paymentMapper;
		this.refundMapper = refundMapper;
		this.productMapper = productMapper;
		this.entitlementMapper = entitlementMapper;
		this.redisTemplate = redisTemplate;
	}

	// ------------------------------------------------------------------
	// 下单
	// ------------------------------------------------------------------

	@Transactional(rollbackFor = Exception.class)
	public Order create(Long userId, CreateOrderDTO dto) {
		Product product = requireOrderable(userId, dto.getProductNo());

		// 防连点。Redis 挂掉时退化依赖下面的 findPending
		guardRepeatedSubmit(userId, product.getId());

		// 已有未过期的待支付订单直接复用，避免同一商品被付两次
		Order pending = orderMapper.findPending(userId, product.getId());
		if (pending != null) {
			return pending;
		}

		Order order = newOrder(userId, product, dto.getRemark());
		orderMapper.insert(order);
		orderItemMapper.insert(snapshotOf(order, product));
		return order;
	}

	/**
	 * 校验商品可下单，顺带确认用户还没持有。
	 *
	 * @return 可用的商品
	 */
	private Product requireOrderable(Long userId, String productNo) {
		if (productNo == null || productNo.trim().isEmpty()) {
			throw new BadRequestException("商品编号不能为空");
		}
		Product product = productMapper.findOnSaleByNo(productNo.trim());
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
		return product;
	}

	private Order newOrder(Long userId, Product product, String remark) {
		BigDecimal price = product.getPrice();
		Order order = new Order();
		order.setOrderNo(generateOrderNo());
		order.setUserId(userId);
		order.setBizType("PRODUCT");
		order.setTotalAmount(price);
		order.setDiscountAmount(BigDecimal.ZERO);
		order.setPayAmount(price);
		order.setStatus(PENDING_PAY);
		order.setPayExpireTime(new Date(System.currentTimeMillis() + PAY_EXPIRE_MINUTES * 60_000L));
		order.setRemark(remark == null ? "" : remark.trim());
		return order;
	}

	/**
	 * 下单那一刻把标题、封面、单价都存一份快照 —— 商品后续改价改名，历史订单不能被改写。
	 */
	private OrderItem snapshotOf(Order order, Product product) {
		OrderItem item = new OrderItem();
		item.setOrderId(order.getId());
		item.setProductId(product.getId());
		item.setProductNo(product.getProductNo());
		item.setProductType(product.getProductType());
		item.setTitleSnapshot(product.getTitle());
		item.setCoverSnapshot(product.getCover() == null ? "" : product.getCover());
		item.setPriceSnapshot(product.getPrice());
		item.setQuantity(1);
		item.setAmount(product.getPrice());
		return item;
	}

	// ------------------------------------------------------------------
	// 查询
	// ------------------------------------------------------------------

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
		data.put("payments", paymentMapper.listByBizNo(orderNo));
		data.put("refunds", refundMapper.listByBizNo(orderNo));
		data.put("entitlements", entitlementMapper.listByOrderNo(orderNo));
		return data;
	}

	public PageResult<Order> myOrders(Long userId, String status, Integer pageNum, Integer pageSize) {
		int size = normalizeSize(pageSize);
		int page = normalizePage(pageNum);
		int total = orderMapper.countByUser(userId, status);
		List<Order> list = orderMapper.pageByUser(userId, status, (page - 1) * size, size);
		fillItems(list);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<Order> pageAdmin(String status, String keyword, String startTime, String endTime,
	                                   Integer pageNum, Integer pageSize) {
		if (status != null && !status.isEmpty() && !KNOWN_STATUSES.contains(status)) {
			throw new BadRequestException("非法订单状态：" + status);
		}
		int size = normalizeSize(pageSize);
		int page = normalizePage(pageNum);
		int total = orderMapper.countAdmin(status, keyword, startTime, endTime);
		List<Order> list = orderMapper.pageAdmin(status, keyword, startTime, endTime, (page - 1) * size, size);
		fillItems(list);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ------------------------------------------------------------------
	// 状态流转
	// ------------------------------------------------------------------

	/**
	 * 用户取消，仅限待支付。同时把还在等确认的支付单关掉，避免后台继续看到待确认记录。
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
		if (!PENDING_PAY.equals(order.getStatus())) {
			throw new BadRequestException("当前状态不可取消");
		}
		// 条件更新：影响行数为 0 说明状态已被并发改掉
		if (orderMapper.cancel(orderNo, userId) == 0) {
			throw new BadRequestException("订单状态已变化，请刷新后重试");
		}
		Payment payment = paymentMapper.findLatestByBizNo(orderNo);
		if (payment != null && ("INIT".equals(payment.getStatus()) || "WAIT_CONFIRM".equals(payment.getStatus()))) {
			paymentMapper.close(payment.getPaymentNo(), "用户取消订单");
		}
	}

	/**
	 * 关闭超时未支付订单。后台可手动触发，也由定时任务调用。
	 */
	public int closeExpired() {
		return orderMapper.closeExpired();
	}

	/**
	 * 校验订单可用于付款。已处于「待确认收款」时直接放行 —— 用户可能在补传凭证。
	 */
	public Order requirePayable(String orderNo, Long userId) {
		Order order = orderMapper.findByNo(orderNo);
		if (order == null) {
			throw new BadRequestException("订单不存在");
		}
		if (!order.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该订单");
		}
		if (PENDING_CONFIRM.equals(order.getStatus())) {
			return order;
		}
		if (!PENDING_PAY.equals(order.getStatus())) {
			throw new BadRequestException("订单当前状态为「" + statusText(order.getStatus()) + "」，不可付款");
		}
		if (order.getPayExpireTime() != null && order.getPayExpireTime().before(new Date())) {
			// 过期了顺手关掉，省得用户反复点进来看到一张僵尸单
			orderMapper.close(orderNo);
			throw new BadRequestException("订单已超时关闭，请重新下单");
		}
		return order;
	}

	// ------------------------------------------------------------------
	// 内部
	// ------------------------------------------------------------------

	/**
	 * 防连点。Redis 不可用时静默放行 —— 宁可退化到数据库判断，也不能因为缓存故障拒绝下单。
	 */
	private void guardRepeatedSubmit(Long userId, Long productId) {
		String key = "order:submit:" + userId + ":" + productId;
		try {
			Boolean acquired = redisTemplate.opsForValue()
					.setIfAbsent(key, "1", Duration.ofSeconds(SUBMIT_GUARD_SECONDS));
			if (Boolean.FALSE.equals(acquired)) {
				throw new BadRequestException("操作过于频繁，请稍后再试");
			}
		} catch (BadRequestException e) {
			throw e;
		} catch (Exception ignored) {
			// Redis 故障：交给 findPending 兜底
		}
	}

	/**
	 * 批量补订单明细，避免列表页 N+1 查询。
	 */
	private void fillItems(List<Order> orders) {
		if (orders == null || orders.isEmpty()) {
			return;
		}
		List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
		Map<Long, List<OrderItem>> grouped = new HashMap<>(orderIds.size());
		for (OrderItem item : orderItemMapper.listByOrderIds(orderIds)) {
			grouped.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
		}

		for (Order order : orders) {
			List<OrderItem> own = grouped.getOrDefault(order.getId(), List.of());
			order.setItems(own);
			if (own.isEmpty()) {
				continue;
			}
			String titles = own.stream().map(OrderItem::getTitleSnapshot).collect(Collectors.joining("、"));
			order.setTitleSummary(own.size() > 1 ? titles + " 等" + own.size() + " 件" : titles);
		}
	}

	/**
	 * 订单号：秒级时间戳 + 4 位随机数。撞号就重试，最多 20 次。
	 */
	private String generateOrderNo() {
		String prefix = "O" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
		for (int i = 0; i < 20; i++) {
			String candidate = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
			if (orderMapper.findByNo(candidate) == null) {
				return candidate;
			}
		}
		throw new IllegalStateException("生成订单号失败，请重试");
	}

	/**
	 * 状态码转中文。未知状态原样返回，不吞掉信息。
	 */
	public static String statusText(String status) {
		if (status == null) {
			return "";
		}
		return STATUS_TEXT.getOrDefault(status, status);
	}

	private int normalizePage(Integer pageNum) {
		return (pageNum == null || pageNum < 1) ? 1 : pageNum;
	}

	private int normalizeSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return 10;
		}
		return Math.min(pageSize, MAX_PAGE_SIZE);
	}
}
