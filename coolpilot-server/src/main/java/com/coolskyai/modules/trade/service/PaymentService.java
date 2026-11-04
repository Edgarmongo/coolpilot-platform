package com.coolskyai.modules.trade.service;

import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 支付服务
 *
 * 核心只有一个出口：onPaid。
 * 无论是后台「确认收款」还是未来的渠道异步回调，最终都调用它，
 * 保证发权益、加销量、转订单状态的逻辑不分叉。
 *
 * 幂等靠 payment 的条件更新：只有把状态从 INIT/WAIT_CONFIRM 改成 SUCCESS 的那一次
 * （影响行数 = 1）才继续发放，重复点击直接返回"已处理"。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class PaymentService {
	/** 人工收款的三个渠道：都不需要商户资质，线下转账后由 OWNER 确认 */
	public static final Map<String, String> MANUAL_CHANNELS = new LinkedHashMap<>();

	static {
		MANUAL_CHANNELS.put("MANUAL_WECHAT", "微信转账");
		MANUAL_CHANNELS.put("MANUAL_ALIPAY", "支付宝转账");
		MANUAL_CHANNELS.put("MANUAL_PAYPAL", "PayPal");
	}

	@Autowired
	private PaymentMapper paymentMapper;
	@Autowired
	private OrderMapper orderMapper;
	@Autowired
	private OrderItemMapper orderItemMapper;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private SiteSettingLookupMapper siteSettingLookupMapper;
	@Autowired
	private OrderService orderService;
	@Autowired
	private EntitlementService entitlementService;
	@Autowired
	private NotifyService notifyService;

	// ---------------- 创建支付 ----------------

	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> create(Long userId, CreatePaymentDTO dto) {
		if (dto.getOrderNo() == null || dto.getOrderNo().trim().isEmpty()) {
			throw new BadRequestException("订单号不能为空");
		}
		String channel = normalizeChannel(dto.getChannel());
		Order order = orderService.requirePayable(dto.getOrderNo().trim(), userId);
		if ("PENDING_CONFIRM".equals(order.getStatus())) {
			throw new BadRequestException("已提交付款凭证，请等待确认收款");
		}

		Payment payment = paymentMapper.findLatestByBizNo(order.getOrderNo());
		if (payment != null && "WAIT_CONFIRM".equals(payment.getStatus())) {
			throw new BadRequestException("已提交付款凭证，请等待确认收款");
		}
		if (payment != null && "INIT".equals(payment.getStatus()) && channel.equals(payment.getChannel())) {
			// 复用未完成的支付单，避免用户刷新页面生成一堆支付单
			return buildPayInfo(order, payment);
		}
		if (payment != null && "INIT".equals(payment.getStatus())) {
			paymentMapper.close(payment.getPaymentNo(), "切换收款渠道：" + channel);
		}

		Payment created = new Payment();
		created.setPaymentNo(generatePaymentNo());
		created.setBizType(order.getBizType());
		created.setBizNo(order.getOrderNo());
		created.setUserId(userId);
		created.setChannel(channel);
		created.setAmount(order.getPayAmount());
		created.setStatus("INIT");
		paymentMapper.insert(created);
		return buildPayInfo(order, created);
	}

	/**
	 * 用户提交转账凭证：支付单转 WAIT_CONFIRM，订单转 PENDING_CONFIRM
	 */
	@Transactional(rollbackFor = Exception.class)
	public void submitTransferProof(String paymentNo, Long userId, ManualConfirmDTO dto) {
		Payment payment = paymentMapper.findByNo(paymentNo);
		if (payment == null) {
			throw new BadRequestException("支付单不存在");
		}
		if (!payment.getUserId().equals(userId)) {
			throw new BadRequestException("无权操作该支付单");
		}
		if ("SUCCESS".equals(payment.getStatus())) {
			throw new BadRequestException("该笔支付已确认收款");
		}
		if (!"INIT".equals(payment.getStatus())) {
			throw new BadRequestException("当前状态不可提交凭证");
		}
		String payerName = dto == null || dto.getPayerName() == null ? "" : dto.getPayerName().trim();
		if (payerName.isEmpty()) {
			throw new BadRequestException("请填写付款人姓名");
		}
		Date transferTime = parseTime(dto.getTransferTime());
		if (paymentMapper.markWaitConfirm(paymentNo, userId, payerName, transferTime, dto.getProofFileId()) == 0) {
			throw new BadRequestException("提交失败，请刷新后重试");
		}
		orderMapper.markPendingConfirm(payment.getBizNo());

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
		Payment payment = paymentMapper.findByNo(paymentNo);
		if (payment == null) {
			throw new BadRequestException("支付单不存在");
		}
		if ("SUCCESS".equals(payment.getStatus())) {
			return false;
		}
		if (!"PRODUCT".equals(payment.getBizType())) {
			throw new BadRequestException("暂不支持的支付业务类型：" + payment.getBizType());
		}
		String finalTradeNo = (tradeNo == null || tradeNo.trim().isEmpty())
				? "MANUAL-" + paymentNo : tradeNo.trim();
		int updated = paymentMapper.markSuccess(paymentNo, finalTradeNo, operatorId,
				remark == null ? "" : remark, raw);
		if (updated == 0) {
			// 已被其它请求抢先处理
			return false;
		}

		Order order = orderMapper.findByNo(payment.getBizNo());
		if (order == null) {
			throw new BadRequestException("支付单关联的订单不存在：" + payment.getBizNo());
		}
		if (!"PAID".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
			if (orderMapper.markPaid(order.getOrderNo()) == 0) {
				throw new BadRequestException("订单当前状态为「" + OrderService.statusText(order.getStatus())
						+ "」，无法确认收款，请先处理订单");
			}
		} else {
			// 订单已是支付态（例如重复确认另一笔支付单），不再重复发权益
			return true;
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
	 * 关闭支付单
	 */
	public void close(String paymentNo, String remark) {
		Payment payment = paymentMapper.findByNo(paymentNo);
		if (payment == null) {
			throw new BadRequestException("支付单不存在");
		}
		if (paymentMapper.close(paymentNo, remark == null ? "后台关闭" : remark) == 0) {
			throw new BadRequestException("当前状态不可关闭（已成功或已关闭）");
		}
	}

	// ---------------- 查询 ----------------

	public Map<String, Object> query(String paymentNo, Long userId) {
		Payment payment = paymentMapper.findByNo(paymentNo);
		if (payment == null) {
			throw new BadRequestException("支付单不存在");
		}
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
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
		int total = paymentMapper.countAdmin(status, channel, keyword);
		List<Payment> list = paymentMapper.pageAdmin(status, channel, keyword, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public int countWaitingConfirm() {
		return paymentMapper.countWaitingConfirm();
	}

	// ---------------- 内部 ----------------

	private Map<String, Object> buildPayInfo(Order order, Payment payment) {
		Map<String, Object> data = new HashMap<>(16);
		data.put("paymentNo", payment.getPaymentNo());
		data.put("orderNo", order.getOrderNo());
		data.put("channel", payment.getChannel());
		data.put("channelName", MANUAL_CHANNELS.getOrDefault(payment.getChannel(), payment.getChannel()));
		data.put("amount", payment.getAmount());
		data.put("expireTime", order.getPayExpireTime());
		data.put("payeeName", setting("payeeName", ""));
		data.put("transferRemark", order.getOrderNo().substring(Math.max(0, order.getOrderNo().length() - 6)));
		data.put("status", payment.getStatus());

		switch (payment.getChannel()) {
			case "MANUAL_WECHAT":
				data.put("qrCodeUrl", setting("wechatQr", ""));
				data.put("tip", "请使用微信扫码转账，金额与备注必须一致，转账后回到本页提交付款人姓名与截图");
				break;
			case "MANUAL_ALIPAY":
				data.put("qrCodeUrl", setting("alipayQr", ""));
				data.put("tip", "请使用支付宝扫码转账，金额与备注必须一致，转账后回到本页提交付款人姓名与截图");
				break;
			case "MANUAL_PAYPAL":
				data.put("qrCodeUrl", "");
				data.put("payUrl", setting("paypalUrl", ""));
				data.put("tip", "请通过 PayPal 链接付款，付款后在 PayPal 备注中填写订单尾号，并回到本页提交付款信息");
				break;
			default:
				data.put("qrCodeUrl", "");
				data.put("tip", "请联系站点管理员确认收款方式");
				break;
		}
		return data;
	}

	private String setting(String key, String defaultValue) {
		try {
			String value = siteSettingLookupMapper.findValue(key);
			return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private String normalizeChannel(String channel) {
		if (channel == null || channel.trim().isEmpty()) {
			return "MANUAL_WECHAT";
		}
		String value = channel.trim().toUpperCase();
		if ("MANUAL".equals(value)) {
			return "MANUAL_WECHAT";
		}
		if (!MANUAL_CHANNELS.containsKey(value)) {
			throw new BadRequestException("暂不支持的收款渠道：" + channel);
		}
		return value;
	}

	private Date parseTime(String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		try {
			return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(text.trim());
		} catch (java.text.ParseException e) {
			try {
				return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(text.trim());
			} catch (java.text.ParseException ex) {
				throw new BadRequestException("转账时间格式应为 yyyy-MM-dd HH:mm:ss");
			}
		}
	}

	private String generatePaymentNo() {
		String prefix = "PAY" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
		for (int i = 0; i < 20; i++) {
			String no = prefix + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));
			if (paymentMapper.findByNo(no) == null) {
				return no;
			}
		}
		throw new IllegalStateException("生成支付单号失败，请重试");
	}
}
