package com.coolskyai.modules.trade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.entitlement.service.EntitlementService;
import com.coolskyai.modules.notify.service.NotifyService;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.trade.dto.CreatePaymentDTO;
import com.coolskyai.modules.trade.dto.ManualConfirmDTO;
import com.coolskyai.modules.trade.entity.Order;
import com.coolskyai.modules.trade.entity.OrderItem;
import com.coolskyai.modules.trade.entity.Payment;
import com.coolskyai.modules.trade.mapper.OrderItemMapper;
import com.coolskyai.modules.trade.mapper.OrderMapper;
import com.coolskyai.modules.trade.mapper.PaymentMapper;
import com.coolskyai.modules.trade.mapper.SiteSettingLookupMapper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description: 支付服务
 *
 * 全站只有一个「收款成功」的出口：onPaid。
 * 后台确认收款、将来的渠道异步回调，都必须走它，
 * 否则「发权益 / 加销量 / 转订单状态」这三件事迟早分叉。
 *
 * 幂等的落点是 payment 的条件更新：只有把状态从 INIT 或 WAIT_CONFIRM
 * 改成 SUCCESS 的那一次（影响行数 = 1）才继续往下发权益，
 * 重复点击、并发确认都只会有一条路径真正执行。
 *
 * 已实现的收款渠道全部是线下转账 + 人工确认，不需要商户资质。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class PaymentService {

	/** 人工收款渠道：编码 → 展示名。顺序即前端下拉顺序，不可改成无序 Map */
	public static final Map<String, String> MANUAL_CHANNELS;

	static {
		Map<String, String> channels = new LinkedHashMap<>();
		channels.put("MANUAL_WECHAT", "微信转账");
		channels.put("MANUAL_ALIPAY", "支付宝转账");
		channels.put("MANUAL_PAYPAL", "PayPal");
		MANUAL_CHANNELS = Collections.unmodifiableMap(channels);
	}

	private static final String DEFAULT_CHANNEL = "MANUAL_WECHAT";
	/** 前端传 MANUAL 时统一收敛到微信转账 */
	private static final String CHANNEL_ALIAS = "MANUAL";

	private static final String STATUS_INIT = "INIT";
	private static final String STATUS_WAIT_CONFIRM = "WAIT_CONFIRM";
	private static final String STATUS_SUCCESS = "SUCCESS";

	private static final String BIZ_PRODUCT = "PRODUCT";
	private static final String WAIT_PROOF_TIP = "已提交付款凭证，请等待确认收款";

	private static final int PAGE_SIZE_DEFAULT = 10;
	private static final int PAGE_SIZE_MAX = 100;
	private static final int PAY_NO_RETRY = 20;

	private final PaymentMapper paymentMapper;
	private final OrderMapper orderMapper;
	private final OrderItemMapper orderItemMapper;
	private final ProductMapper productMapper;
	private final SiteSettingLookupMapper siteSettingLookupMapper;
	private final OrderService orderService;
	private final EntitlementService entitlementService;
	private final NotifyService notifyService;

	public PaymentService(PaymentMapper paymentMapper,
	                      OrderMapper orderMapper,
	                      OrderItemMapper orderItemMapper,
	                      ProductMapper productMapper,
	                      SiteSettingLookupMapper siteSettingLookupMapper,
	                      OrderService orderService,
	                      EntitlementService entitlementService,
	                      NotifyService notifyService) {
		this.paymentMapper = paymentMapper;
		this.orderMapper = orderMapper;
		this.orderItemMapper = orderItemMapper;
		this.productMapper = productMapper;
		this.siteSettingLookupMapper = siteSettingLookupMapper;
		this.orderService = orderService;
		this.entitlementService = entitlementService;
		this.notifyService = notifyService;
	}

	// ---------------- 创建支付 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> create(Long userId, CreatePaymentDTO dto) {
		String orderNo = requireOrderNo(dto);
		String channel = normalizeChannel(dto.getChannel());
		Order order = orderService.requirePayable(orderNo, userId);

		Payment latest = paymentMapper.findLatestByBizNo(orderNo);
		rejectIfWaitingProof(order.getStatus(), latest);

		// 同渠道的 INIT 支付单直接复用，避免用户刷新页面生成一堆支付单
		if (latest != null && STATUS_INIT.equals(latest.getStatus())) {
			if (channel.equals(latest.getChannel())) {
				return buildPayInfo(order, latest);
			}
			paymentMapper.close(latest.getPaymentNo(), "切换收款渠道：" + channel);
		}

		Payment created = new Payment();
		created.setPaymentNo(nextPaymentNo());
		created.setBizType(order.getBizType());
		created.setBizNo(order.getOrderNo());
		created.setUserId(userId);
		created.setChannel(channel);
		created.setAmount(order.getPayAmount());
		created.setStatus(STATUS_INIT);
		paymentMapper.insert(created);
		return buildPayInfo(order, created);
	}

	/**
	 * 用户提交转账凭证：支付单 INIT → WAIT_CONFIRM，订单 PENDING_PAY → PENDING_CONFIRM
	 *
	 * 两个状态必须一起推进。原实现没检查订单那一侧的更新结果，
	 * 订单已关闭时会出现「支付单等确认、订单已关闭」的错位，所以这里改成：
	 * 订单没推进成功就整体回滚。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void submitTransferProof(String paymentNo, Long userId, ManualConfirmDTO dto) {
		Payment payment = requirePayment(paymentNo);
		if (!payment.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该支付单");
		}
		if (STATUS_SUCCESS.equals(payment.getStatus())) {
			throw new BadRequestException("该笔支付已确认收款");
		}
		if (!STATUS_INIT.equals(payment.getStatus())) {
			throw new BadRequestException("当前状态不可提交凭证");
		}
		String payerName = trimToEmpty(dto == null ? null : dto.getPayerName());
		if (payerName.isEmpty()) {
			throw new BadRequestException("请填写付款人姓名");
		}
		Date transferTime = parseTransferTime(dto.getTransferTime());

		Order order = orderMapper.findByNo(payment.getBizNo());
		if (order == null) {
			throw new BadRequestException("支付单关联的订单不存在：" + payment.getBizNo());
		}

		if (paymentMapper.markWaitConfirm(paymentNo, userId, payerName, transferTime, dto.getProofFileId()) == 0) {
			throw new BadRequestException("提交失败，请刷新后重试");
		}
		if (orderMapper.markPendingConfirm(order.getOrderNo()) == 0) {
			throw new BadRequestException("订单当前状态为「" + OrderService.statusText(order.getStatus())
					+ "」，无法提交转账凭证");
		}

		notifyService.toOwner("ORDER", "有新的转账凭证待确认",
				"订单 " + payment.getBizNo() + " 的支付单 " + paymentNo
						+ " 已提交转账凭证，付款人：" + payerName
						+ "，金额 ¥" + payment.getAmount() + "。请到后台确认收款后发放权益。",
				"/trade/order", "PAYMENT", payment.getId());
	}

	// ---------------- 确认收款（唯一发权益出口） ----------------

	/**
	 * @return true 表示本次真的完成了收款处理；false 表示此前已处理过
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean onPaid(String paymentNo, String tradeNo, Long operatorId, String remark, String raw) {
		Payment payment = requirePayment(paymentNo);
		if (STATUS_SUCCESS.equals(payment.getStatus())) {
			return false;
		}
		if (!BIZ_PRODUCT.equals(payment.getBizType())) {
			throw new BadRequestException("暂不支持的支付业务类型：" + payment.getBizType());
		}

		String finalTradeNo = isBlank(tradeNo) ? "MANUAL-" + paymentNo : tradeNo.trim();
		// 第一道闸：只有抢到状态变更的请求才继续
		if (paymentMapper.markSuccess(paymentNo, finalTradeNo, operatorId,
				remark == null ? "" : remark, raw) == 0) {
			return false;
		}

		Order order = orderMapper.findByNo(payment.getBizNo());
		if (order == null) {
			throw new BadRequestException("支付单关联的订单不存在：" + payment.getBizNo());
		}
		if (isAlreadyPaid(order.getStatus())) {
			// 订单已被另一笔支付单推进过，不再重复发权益
			return true;
		}
		// 第二道闸：订单必须从待支付/待确认跳过来，否则说明状态已被改过
		if (orderMapper.markPaid(order.getOrderNo()) == 0) {
			throw new BadRequestException("订单当前状态为「" + OrderService.statusText(order.getStatus())
					+ "」，无法确认收款，请先处理订单");
		}

		List<OrderItem> items = orderItemMapper.listByOrderId(order.getId());
		entitlementService.grantForOrder(order, items, operatorId);
		for (OrderItem item : items) {
			productMapper.addSales(item.getProductId(), item.getQuantity() == null ? 1 : item.getQuantity());
		}
		orderMapper.markCompleted(order.getOrderNo());

		notifyService.toUser(order.getUserId(), "ORDER", "支付已确认，权益已开通",
				"订单 " + order.getOrderNo() + " 已确认收款，金额 ¥" + order.getPayAmount()
						+ "。权益已发放到你的账户，可在「我的 - 我的权益」查看。",
				"/me?tab=ent", "ORDER", order.getId());
		return true;
	}

	/**
	 * 后台人工确认收款
	 */
	public Map<String, Object> confirmManual(String paymentNo, Long operatorId, String tradeNo, String remark) {
		boolean done = onPaid(paymentNo, tradeNo, operatorId, remark, null);
		Map<String, Object> data = new HashMap<>(2);
		data.put("confirmed", done);
		data.put("message", done ? "已确认收款并发放权益" : "该支付单此前已确认，未重复发放");
		return data;
	}

	/**
	 * 关闭支付单。只改状态，不动订单 —— 订单是否关闭由订单侧自己的条件更新决定。
	 */
	public void close(String paymentNo, String remark) {
		requirePayment(paymentNo);
		if (paymentMapper.close(paymentNo, isBlank(remark) ? "后台关闭" : remark) == 0) {
			throw new BadRequestException("当前状态不可关闭（已成功或已关闭）");
		}
	}

	// ---------------- 查询 ----------------

	public Map<String, Object> query(String paymentNo, Long userId) {
		Payment payment = requirePayment(paymentNo);
		if (!payment.getUserId().equals(userId)) {
			throw new BadRequestException("无权查看该支付单");
		}
		Order order = orderMapper.findByNo(payment.getBizNo());
		Map<String, Object> data = new HashMap<>(4);
		data.put("payment", payment);
		data.put("orderStatus", order == null ? null : order.getStatus());
		data.put("orderStatusText", order == null ? "" : OrderService.statusText(order.getStatus()));
		return data;
	}

	public PageResult<Payment> pageAdmin(String status, String channel, String keyword, Integer pageNum, Integer pageSize) {
		int size = normalizePageSize(pageSize);
		int page = normalizePageNum(pageNum);
		int total = paymentMapper.countAdmin(status, channel, keyword);
		List<Payment> list = paymentMapper.pageAdmin(status, channel, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public int countWaitingConfirm() {
		return paymentMapper.countWaitingConfirm();
	}

	// ---------------- 内部：校验 ----------------

	private Payment requirePayment(String paymentNo) {
		Payment payment = paymentMapper.findByNo(paymentNo);
		if (payment == null) {
			throw new BadRequestException("支付单不存在");
		}
		return payment;
	}

	private String requireOrderNo(CreatePaymentDTO dto) {
		if (dto == null || isBlank(dto.getOrderNo())) {
			throw new BadRequestException("订单号不能为空");
		}
		return dto.getOrderNo().trim();
	}

	/**
	 * 订单或支付单任一已进入「等确认」状态，都不允许再发起支付，
	 * 否则用户会拿到第二个转账入口，后台也分不清该确认哪一笔。
	 */
	private void rejectIfWaitingProof(String orderStatus, Payment latest) {
		boolean orderWaiting = "PENDING_CONFIRM".equals(orderStatus);
		boolean paymentWaiting = latest != null && STATUS_WAIT_CONFIRM.equals(latest.getStatus());
		if (orderWaiting || paymentWaiting) {
			throw new BadRequestException(WAIT_PROOF_TIP);
		}
	}

	private boolean isAlreadyPaid(String orderStatus) {
		return "PAID".equals(orderStatus) || "COMPLETED".equals(orderStatus);
	}

	private String normalizeChannel(String channel) {
		if (isBlank(channel)) {
			return DEFAULT_CHANNEL;
		}
		String value = channel.trim().toUpperCase();
		if (CHANNEL_ALIAS.equals(value)) {
			return DEFAULT_CHANNEL;
		}
		if (!MANUAL_CHANNELS.containsKey(value)) {
			throw new BadRequestException("暂不支持的收款渠道：" + channel);
		}
		return value;
	}

	/**
	 * 严格解析：宽松模式下 2026-02-31 会被悄悄修正成 3 月 3 日，
	 * 转账时间是要和银行流水对账的，不能自动改。
	 */
	private Date parseTransferTime(String text) {
		if (isBlank(text)) {
			return null;
		}
		String value = text.trim();
		for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"}) {
			SimpleDateFormat format = new SimpleDateFormat(pattern);
			format.setLenient(false);
			try {
				return format.parse(value);
			} catch (ParseException ignored) {
				// 换下一个格式
			}
		}
		throw new BadRequestException("转账时间格式应为 yyyy-MM-dd HH:mm:ss");
	}

	// ---------------- 内部：组装 ----------------

	private Map<String, Object> buildPayInfo(Order order, Payment payment) {
		Map<String, Object> data = new HashMap<>(16);
		data.put("paymentNo", payment.getPaymentNo());
		data.put("orderNo", order.getOrderNo());
		data.put("channel", payment.getChannel());
		data.put("channelName", MANUAL_CHANNELS.getOrDefault(payment.getChannel(), payment.getChannel()));
		data.put("amount", payment.getAmount());
		data.put("expireTime", order.getPayExpireTime());
		data.put("payeeName", setting("payeeName", ""));
		data.put("transferRemark", tailOfOrderNo(order.getOrderNo()));
		data.put("status", payment.getStatus());
		applyChannelGuide(data, payment.getChannel());
		return data;
	}

	/**
	 * 每个渠道要展示的东西不一样：微信/支付宝给收款码，PayPal 给跳转链接，其余让用户联系站长。
	 */
	private void applyChannelGuide(Map<String, Object> data, String channel) {
		switch (channel) {
			case "MANUAL_WECHAT" -> {
				data.put("qrCodeUrl", setting("wechatQr", ""));
				data.put("tip", "请使用微信扫码转账，金额与备注必须一致，转账后回到本页提交付款人姓名与截图");
			}
			case "MANUAL_ALIPAY" -> {
				data.put("qrCodeUrl", setting("alipayQr", ""));
				data.put("tip", "请使用支付宝扫码转账，金额与备注必须一致，转账后回到本页提交付款人姓名与截图");
			}
			case "MANUAL_PAYPAL" -> {
				data.put("qrCodeUrl", "");
				data.put("payUrl", setting("paypalUrl", ""));
				data.put("tip", "请通过 PayPal 链接付款，付款后在 PayPal 备注中填写订单尾号，并回到本页提交付款信息");
			}
			default -> {
				data.put("qrCodeUrl", "");
				data.put("tip", "请联系站点管理员确认收款方式");
			}
		}
	}

	/** 订单尾号 6 位，用作转账备注，方便对账时匹配 */
	private String tailOfOrderNo(String orderNo) {
		int start = Math.max(0, orderNo.length() - 6);
		return orderNo.substring(start);
	}

	/**
	 * 站点设置随便读，读不到就用默认值 —— 收款页不应该因为某个配置项缺失而打不开
	 */
	private String setting(String key, String defaultValue) {
		try {
			String value = siteSettingLookupMapper.findValue(key);
			return isBlank(value) ? defaultValue : value.trim();
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/**
	 * 支付单号：时间戳 + 4 位随机，冲突就重试，最多 20 次
	 */
	private String nextPaymentNo() {
		String prefix = "PAY" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		for (int i = 0; i < PAY_NO_RETRY; i++) {
			String no = prefix + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
			if (paymentMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成支付单号失败，请重试");
	}

	private int normalizePageNum(Integer pageNum) {
		return (pageNum == null || pageNum < 1) ? 1 : pageNum;
	}

	private int normalizePageSize(Integer pageSize) {
		if (pageSize == null || pageSize < 1) {
			return PAGE_SIZE_DEFAULT;
		}
		return Math.min(pageSize, PAGE_SIZE_MAX);
	}

	private boolean isBlank(String text) {
		return text == null || text.trim().isEmpty();
	}

	private String trimToEmpty(String text) {
		return text == null ? "" : text.trim();
	}
}
