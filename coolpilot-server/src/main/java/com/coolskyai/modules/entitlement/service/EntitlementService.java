package com.coolskyai.modules.entitlement.service;

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
 * 三个写入入口，一套落库逻辑：
 * 1. 支付成功 → grantForOrder（按订单发，系统调用）
 * 2. 后台人工发放 → grant（GIFT，带生效与过期时间）
 * 3. 退款完成 → revokeByOrder（按订单精确撤销，不影响人工赠送的权益）
 *
 * 读路径直接查库：entitlement 上有 (user_id, product_id, ent_type) 唯一索引，
 * 命中成本可控；缓存只在写时失效，避免「发完权益还要等缓存过期」。
 *
 * 幂等依据是 countActive / 唯一键 upsert，不是「先查后插」——
 * 后者在并发确认支付时会重复发放。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class EntitlementService {

	private static final Set<String> ENT_TYPES = Set.of("ACCESS", "DOWNLOAD", "LEARN");

	private static final String TYPE_ACCESS = "ACCESS";
	private static final String TYPE_DOWNLOAD = "DOWNLOAD";

	private static final String SOURCE_PURCHASE = "PURCHASE";
	private static final String SOURCE_GIFT = "GIFT";

	private static final String STATUS_ACTIVE = "ACTIVE";
	private static final String STATUS_REVOKED = "REVOKED";

	private static final String ACTION_GRANT = "GRANT";
	private static final String ACTION_REVOKE = "REVOKE";

	private static final int PAGE_SIZE_DEFAULT = 10;
	private static final int PAGE_SIZE_MAX = 100;

	private final EntitlementMapper entitlementMapper;
	private final EntitlementLogMapper entitlementLogMapper;
	private final ProductMapper productMapper;
	private final ProductResourceMapper productResourceMapper;
	private final RedisService redisService;

	public EntitlementService(EntitlementMapper entitlementMapper,
	                          EntitlementLogMapper entitlementLogMapper,
	                          ProductMapper productMapper,
	                          ProductResourceMapper productResourceMapper,
	                          RedisService redisService) {
		this.entitlementMapper = entitlementMapper;
		this.entitlementLogMapper = entitlementLogMapper;
		this.productMapper = productMapper;
		this.productResourceMapper = productResourceMapper;
		this.redisService = redisService;
	}

	// ---------------- 鉴权 ----------------

	/**
	 * 是否可访问某商品
	 *
	 * @return {accessible, reason, product}
	 * reason：FREE / PURCHASED / OWNER / NOT_LOGIN / NOT_PURCHASED / EXPIRED / REVOKED / NOT_FOUND
	 */
	public Map<String, Object> access(Long userId, String productNo) {
		Product product = productMapper.findByNo(productNo);
		if (product == null) {
			return verdict(false, "NOT_FOUND");
		}
		Map<String, Object> data = verdict(true, "FREE");
		data.put("product", product);
		if (!isFree(product)) {
			applyPaidAccess(data, product, userId);
		}
		return data;
	}

	/**
	 * 付费商品的判定顺序：未登录 → 作者本人 → 有效权益 → 过期/已撤销
	 */
	private void applyPaidAccess(Map<String, Object> data, Product product, Long userId) {
		if (userId == null) {
			deny(data, "NOT_LOGIN");
			return;
		}
		if (product.getOwnerId() != null && product.getOwnerId().equals(userId)) {
			allow(data, "OWNER");
			return;
		}
		if (entitlementMapper.countActive(userId, product.getId()) > 0) {
			allow(data, "PURCHASED");
			return;
		}
		// 没有生效中的权益，再看历史记录，好让用户知道是过期了还是被撤销了
		Entitlement latest = entitlementMapper.findLatest(userId, product.getId());
		if (latest == null) {
			deny(data, "NOT_PURCHASED");
			return;
		}
		if (STATUS_REVOKED.equals(latest.getStatus())) {
			deny(data, "REVOKED");
			return;
		}
		if (isExpired(latest)) {
			deny(data, "EXPIRED");
			data.put("expireTime", latest.getExpireTime());
			return;
		}
		deny(data, "NOT_PURCHASED");
	}

	/**
	 * 是否持有该商品的可下载权益（文件服务用）
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
	 * 商品本身给 ACCESS；若挂了可下载资源，再补一条 DOWNLOAD。
	 * 同类型权益走唯一键 upsert，重复确认支付不会产生两条记录。
	 */
	@Transactional(rollbackFor = Exception.class)
	public void grantForOrder(Order order, List<OrderItem> items, Long operatorId) {
		for (OrderItem item : items) {
			Long productId = item.getProductId();
			String reason = "购买订单 " + order.getOrderNo();
			upsert(order.getUserId(), productId, order.getOrderNo(), TYPE_ACCESS, SOURCE_PURCHASE,
					null, operatorId, reason);

			List<ProductResource> resources = productResourceMapper.listByProduct(productId);
			if (!resources.isEmpty()) {
				upsert(order.getUserId(), productId, order.getOrderNo(), TYPE_DOWNLOAD, SOURCE_PURCHASE,
						null, operatorId, reason + " 附资源");
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
		if (productMapper.findById(dto.getProductId()) == null) {
			throw new BadRequestException("商品不存在");
		}
		String entType = dto.getEntType() == null ? TYPE_ACCESS : dto.getEntType();
		if (!ENT_TYPES.contains(entType)) {
			throw new BadRequestException("非法权益类型：" + entType);
		}
		Date startTime = dto.getStartTime() == null ? new Date() : dto.getStartTime();
		if (dto.getExpireTime() != null && dto.getExpireTime().before(startTime)) {
			throw new BadRequestException("过期时间不能早于生效时间");
		}
		String reason = isBlank(dto.getReason()) ? "后台人工发放" : dto.getReason().trim();
		Long id = upsert(dto.getUserId(), dto.getProductId(), null, entType, SOURCE_GIFT,
				dto.getExpireTime(), operatorId, reason);
		evict(dto.getUserId());
		return id;
	}

	// ---------------- 撤销 ----------------

	/**
	 * 按订单撤销（退款完成时调用）
	 *
	 * 先查出该订单名下的权益，再批量置为 REVOKED，最后逐条写日志 ——
	 * 顺序不能反：撤销之后状态已经变了，就看不出哪些原本是生效中的。
	 */
	@Transactional(rollbackFor = Exception.class)
	public int revokeByOrder(String orderNo, String reason, Long operatorId) {
		List<Entitlement> owned = entitlementMapper.listByOrderNo(orderNo);
		int count = entitlementMapper.revokeByOrderNo(orderNo);
		for (Entitlement entitlement : owned) {
			if (!STATUS_ACTIVE.equals(entitlement.getStatus())) {
				continue;
			}
			writeLog(entitlement.getId(), entitlement.getUserId(), entitlement.getProductId(),
					ACTION_REVOKE, reason, operatorId);
			evict(entitlement.getUserId());
		}
		return count;
	}

	/**
	 * 单条撤销（后台）
	 */
	@Transactional(rollbackFor = Exception.class)
	public void revoke(Long id, String reason, Long operatorId) {
		if (isBlank(reason)) {
			throw new BadRequestException("撤销原因不能为空");
		}
		Entitlement entitlement = entitlementMapper.findById(id);
		if (entitlement == null) {
			throw new BadRequestException("权益不存在");
		}
		if (!STATUS_ACTIVE.equals(entitlement.getStatus())) {
			throw new BadRequestException("该权益已不是生效状态");
		}
		entitlementMapper.revokeById(id);
		writeLog(id, entitlement.getUserId(), entitlement.getProductId(), ACTION_REVOKE, reason.trim(), operatorId);
		evict(entitlement.getUserId());
	}

	/**
	 * 过期扫描（定时任务或后台手动触发）
	 */
	public int expireOutdated() {
		return entitlementMapper.expireOutdated();
	}

	// ---------------- 后台查询 ----------------

	public PageResult<Entitlement> pageAdmin(Long userId, Long productId, String status, Integer pageNum, Integer pageSize) {
		int size = normalizePageSize(pageSize);
		int page = normalizePageNum(pageNum);
		int total = entitlementMapper.countAdmin(userId, productId, status);
		List<Entitlement> list = entitlementMapper.pageAdmin(userId, productId, status, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	public PageResult<EntitlementLog> pageLogs(Long userId, Long productId, String action, Integer pageNum, Integer pageSize) {
		int size = normalizePageSize(pageSize);
		int page = normalizePageNum(pageNum);
		int total = entitlementLogMapper.countAdmin(userId, productId, action);
		List<EntitlementLog> list = entitlementLogMapper.pageAdmin(userId, productId, action, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	// ---------------- 内部 ----------------

	/**
	 * 落库走唯一键 upsert，返回权益 id 以便写日志。
	 * 拿不到 id 不抛异常：权益已经写进去了，日志缺一条比整笔失败更可接受。
	 */
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
			writeLog(id, userId, productId, ACTION_GRANT, reason, operatorId);
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

	private Map<String, Object> verdict(boolean accessible, String reason) {
		Map<String, Object> data = new HashMap<>(4);
		data.put("accessible", accessible);
		data.put("reason", reason);
		return data;
	}

	private void allow(Map<String, Object> data, String reason) {
		data.put("accessible", true);
		data.put("reason", reason);
	}

	private void deny(Map<String, Object> data, String reason) {
		data.put("accessible", false);
		data.put("reason", reason);
	}

	private boolean isFree(Product product) {
		return product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0;
	}

	private boolean isExpired(Entitlement entitlement) {
		return entitlement.getExpireTime() != null && entitlement.getExpireTime().before(new Date());
	}

	/**
	 * 缓存只在写时失效。Redis 挂了也不能影响发权益 —— 读路径本来就是查库。
	 */
	private void evict(Long userId) {
		if (userId == null) {
			return;
		}
		try {
			redisService.deleteCacheByKey("entitlement:" + userId);
		} catch (Exception ignored) {
			// 缓存清理失败不影响主流程
		}
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
}
