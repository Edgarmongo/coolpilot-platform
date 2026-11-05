package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * @Description: AI 链路的轻量缓存（Redis）
 *
 * 追问改写与重排都是一次额外的模型调用，同一句话问两遍就该只花一次钱。
 * 缓存键用参数摘要而不是原串：中文与长提示放进 key 既不美观也容易超长，
 * 摘要把「改了几个字就当新键」的误命中挡在外面，代价是可读性 —— 需要排查时看日志里的摘要即可。
 *
 * 三条原则：
 *   1. 只缓存「结果稳定」的东西（改写、排序），不缓存会随索引变化的内容本身
 *   2. Redis 异常一律当作未命中：缓存是省钱手段，不该成为故障点
 *   3. TTL 短（分钟到小时级）：索引重建、模型切换后自然失效，不依赖主动清理
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
public class AiCache {

	private static final Logger log = LoggerFactory.getLogger(AiCache.class);

	@Autowired(required = false)
	private StringRedisTemplate redisTemplate;

	/**
	 * 取缓存，未命中或异常返回 null
	 */
	public String get(String category, String... parts) {
		if (redisTemplate == null) {
			return null;
		}
		try {
			return redisTemplate.opsForValue().get(key(category, parts));
		} catch (Exception e) {
			log.debug("读取缓存失败（按未命中处理）：{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 写缓存；ttlSeconds ≤ 0 表示不缓存
	 */
	public void put(String category, String value, long ttlSeconds, String... parts) {
		if (redisTemplate == null || value == null || ttlSeconds <= 0) {
			return;
		}
		try {
			redisTemplate.opsForValue().set(key(category, parts), value, Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			log.debug("写入缓存失败（忽略）：{}", e.getMessage());
		}
	}

	public void evict(String category, String... parts) {
		if (redisTemplate == null) {
			return;
		}
		try {
			redisTemplate.delete(key(category, parts));
		} catch (Exception e) {
			log.debug("删除缓存失败（忽略）：{}", e.getMessage());
		}
	}

	/**
	 * 键 = 类别 + 参数摘要，参数之间用 \u0000 分隔，避免「a|b」与「a|b」这类拼接歧义
	 */
	private String key(String category, String... parts) {
		StringBuilder raw = new StringBuilder();
		for (String part : parts) {
			raw.append(part == null ? "" : part).append('\u0000');
		}
		return "ai:" + category + ":" + digest(raw.toString());
	}

	private String digest(String text) {
		try {
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			byte[] bytes = sha1.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes).substring(0, 32);
		} catch (Exception e) {
			// 拿不到摘要就退化成 hashCode：命中率差一点，但不影响正确性
			return Integer.toHexString(text.hashCode());
		}
	}
}
