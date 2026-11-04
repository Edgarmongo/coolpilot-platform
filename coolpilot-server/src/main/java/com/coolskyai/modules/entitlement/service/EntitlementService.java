package com.coolskyai.modules.entitlement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.entitlement.dto.GrantDTO;
import com.coolskyai.modules.entitlement.entity.Entitlement;
import com.coolskyai.modules.entitlement.entity.EntitlementLog;
import com.coolskyai.modules.entitlement.mapper.EntitlementLogMapper;
import com.coolskyai.modules.entitlement.mapper.EntitlementMapper;
import com.coolskyai.modules.product.entity.Product;
import com.coolskyai.modules.product.entity.ProductResource;
import com.coolskyai.modules.product.mapper.ProductMapper;
import com.coolskyai.modules.product.mapper.ProductResourceMapper;
import com.coolskyai.modules.trade.entity.Order;
import com.coolskyai.modules.trade.entity.OrderItem;
import com.coolskyai.service.RedisService;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 权益服务
 *
 * 三个入口，一套落库逻辑：
 * 1. 支付成功 → grantForOrder（系统发放）
 * 2. 后台人工发放 → grant（GIFT）
 * 3. 退款成功 → revokeByOrder（按订单精确撤销）
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class EntitlementService {
	private static final Set<String> ENT_TYPES = Set.of("ACCESS", "DOWNLOAD", "LEARN");

	@Autowired
	private EntitlementMapper entitlementMapper;
	@Autowired
	private EntitlementLogMapper entitlementLogMapper;
	@Autowired
	private ProductMapper productMapper;
	@Autowired
	private ProductResourceMapper productResourceMapper;
	@Autowired
	private RedisService redisService;

	// ---------------- 鉴权 ----------------

	/**
	 * 是否可访问某商品
	 *
	 * @return {accessible, reason, product}
	 * reason：FREE / PURCHASED / NOT_PURCHASED / EXPIRED / REVOKED / NOT_FOUND
	 */
	public Map<String, Object> access(Long userId, String productNo) {
		Product product = productMapper.findByNo(productNo);
		Map<String, Object> data = new HashMap<>(4);
		if (product == null) {
			data.put("accessible", false);
			data.put("reason", "NOT_FOUND");
			return data;
		}
		data.put("product", product);
		if (isFree(product)) {
			data.put("accessible", true);
			data.put("reason", "FREE");
			return data;
		}
		if (userId == null) {
			data.put("accessible", false);
			data.put("reason", "NOT_LOGIN");
			return data;
		}
		if (product.getOwnerId() != null && product.getOwnerId().equals(userId)) {
			data.put("accessible", true);
			data.put("reason", "OWNER");
			return data;
		}
		if (entitlementMapper.countActive(userId, product.getId()) > 0) {
			data.put("accessible", true);
			data.put("reason", "PURCHASED");
			return data;
		}
		Entitlement latest = entitlementMapper.findLatest(userId, product.getId());
		if (latest == null) {
			data.put("accessible", false);
			data.put("reason", "NOT_PURCHASED");
		} else if ("REVOKED".equals(latest.getStatus())) {
			data.put("accessible", false);
			data.put("reason", "REVOKED");
		} else if (latest.getExpireTime() != null && latest.getExpireTime().before(new Date())) {
			data.put("accessible", false);
			data.put("reason", "EXPIRED");
			data.put("expireTime", latest.getExpireTime());
		} else {
			data.put("accessible", false);
			data.put("reason", "NOT_PURCHASED");
		}
		return data;
	}

	/**
	 * 是否有该商品（任一类型）的可下载权益
	 */
	public boolean canDownload(Long userId, Long productId) {
		return entitlementMapper.countActive(userId, productId) > 0;
	}

	public List<Entitlement> myEntitlements(Long userId) {
		return entitlementMapper.listActiveByUser(userId);
	}

	// ---------------- 发放 ----------------

	/**
	 * 支付成功后按订单发放权益
	 *
	 * 商品本身给 ACCESS；若商品挂了可下载资源，额外给 DOWNLOAD。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void grantForOrder(Order order, List<OrderItem> items, Long operatorId) {
		for (OrderItem item : items) {
			upsert(order.getUserId(), item.getProductId(), order.getOrderNo(), "ACCESS", "PURCHASE",
					null, operatorId, "购买订单 " + order.getOrderNo());
			List<ProductResource> resources = productResourceMapper.listByProduct(item.getProductId());
			if (!resources.isEmpty()) {
				upsert(order.getUserId(), item.getProductId(), order.getOrderNo(), "DOWNLOAD", "PURCHASE",
						null, operatorId, "购买订单 " + order.getOrderNo() + " 附资源");
			}
		}
		evict(order.getUserId());
	}

	/**
	 * 后台人工发放
	 */
	@Transactional(rollbackFor = Exception.class)
	public Long grant(GrantDTO dto, Long operatorId) {
		if (dto.getUserId() == null || dto.getProductId() == null) {
			throw new BadRequestException("用户ID与商品ID不能为空");
		}
		Product product = productMapper.findById(dto.getProductId());
		if (product == null) {
			throw new BadRequestException("商品不存在");
		}
		String entType = dto.getEntType() == null ? "ACCESS" : dto.getEntType();
		if (!ENT_TYPES.contains(entType)) {
			throw new BadRequestException("非法权益类型：" + entType);
		}
		Date startTime = dto.getStartTime() == null ? new Date() : dto.getStartTime();
		if (dto.getExpireTime() != null && dto.getExpireTime().before(startTime)) {
			throw new BadRequestException("过期时间不能早于生效时间");
		}
		String reason = dto.getReason() == null || dto.getReason().trim().isEmpty()
				? "后台人工发放" : dto.getReason().trim();
		Long id = upsert(dto.getUserId(), dto.getProductId(), null, entType, "GIFT",
				dto.getExpireTime(), operatorId, reason);
		evict(dto.getUserId());
		return id;
	}

	/**
	 * 按订单撤销（退款）
	 */
	@Transactional(rollbackFor = Exception.class)
	public int revokeByOrder(String orderNo, String reason, Long operatorId) {
		List<Entitlement> list = entitlementMapper.listByOrderNo(orderNo);
		int count = entitlementMapper.revokeByOrderNo(orderNo);
		for (Entitlement entitlement : list) {
			if (!"ACTIVE".equals(entitlement.getStatus())) {
				continue;
			}
			writeLog(entitlement.getId(), entitlement.getUserId(), entitlement.getProductId(),
					"REVOKE", reason, operatorId);
			evict(entitlement.getUserId());
		}
		return count;
	}

	/**
	 * 单条撤销（后台）
	 */
	@Transactional(rollbackFor = Exception.class)
	public void revoke(Long id, String reason, Long operatorId) {
		if (reason == null || reason.trim().isEmpty()) {
			throw new BadRequestException("撤销原因不能为空");
		}
		Entitlement entitlement = entitlementMapper.findById(id);
		if (entitlement == null) {
			throw new BadRequestException("权益不存在");
		}
		if (!"ACTIVE".equals(entitlement.getStatus())) {
			throw new BadRequestException("该权益已不是生效状态");
		}
		entitlementMapper.revokeById(id);
		writeLog(id, entitlement.getUserId(), entitlement.getProductId(), "REVOKE", reason.trim(), operatorId);
		evict(entitlement.getUserId());
	}

	/**
	 * 过期扫描（后台可手动触发）
	 */
	public int expireOutdated() {
		return entitlementMapper.expireOutdated();
	}

	// ---------------- 后台查询 ----------------

	public PageResult<Entitlement> pageAdmin(Long userId, Long productId, String status, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
		int total = entitlementMapper.countAdmin(userId, productId, status);
		List<Entitlement> list = entitlementMapper.pageAdmin(userId, productId, status, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<EntitlementLog> pageLogs(Long userId, Long productId, String action, Integer pageNum, Integer pageSize) {
		int page = (pageNum == null || pageNum < 1) ? 1 : pageNum;
		int size = (pageSize == null || pageSize < 1) ? 10 : Math.min(pageSize, 100);
		int total = entitlementLogMapper.countAdmin(userId, productId, action);
		List<EntitlementLog> list = entitlementLogMapper.pageAdmin(userId, productId, action, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ---------------- 内部 ----------------

	private Long upsert(Long userId, Long productId, String orderNo, String entType, String source,
	                    Date expireTime, Long operatorId, String reason) {
		Entitlement entitlement = new Entitlement();
		entitlement.setUserId(userId);
		entitlement.setProductId(productId);
		entitlement.setOrderNo(orderNo);
		entitlement.setEntType(entType);
		entitlement.setSource(source);
		entitlement.setStartTime(new Date());
		entitlement.setExpireTime(expireTime);
		entitlementMapper.upsert(entitlement);
		Long id = entitlementMapper.findIdByUnique(userId, productId, entType);
		if (id != null) {
			writeLog(id, userId, productId, "GRANT", reason, operatorId);
		}
		return id;
	}

	private void writeLog(Long entitlementId, Long userId, Long productId, String action, String reason, Long operatorId) {
		EntitlementLog log = new EntitlementLog();
		log.setEntitlementId(entitlementId);
		log.setUserId(userId);
		log.setProductId(productId);
		log.setAction(action);
		log.setReason(reason == null ? "" : reason);
		log.setOperatorId(operatorId);
		entitlementLogMapper.insert(log);
	}

	private boolean isFree(Product product) {
		return product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0;
	}

	/**
	 * 缓存只在写时失效，读路径直接查库（有唯一索引，成本可控）
	 */
	private void evict(Long userId) {
		if (userId == null) {
			return;
		}
		try {
			redisService.deleteCacheByKey("entitlement:" + userId);
		} catch (Exception ignored) {
			// Redis 不可用不影响主流程
		}
	}
}
