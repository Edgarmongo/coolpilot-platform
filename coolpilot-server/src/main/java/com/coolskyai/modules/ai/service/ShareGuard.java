package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @Description: 分享页的阅读/点赞去重
 *
 * 分享页是「不登录也能看」的，所以没法按用户去重，只能按 IP：
 *   - 阅读：同一 IP 30 分钟内算一次，刷新页面不会把数字刷上去
 *   - 点赞：同一 IP 记 30 天，点第二次是取消
 *
 * 为什么用 Redis 而不是落库：这两件事都是短周期、可丢失的状态，
 * 为它们建表要写清理任务，而 Redis 的 TTL 天生就是干这个的。
 * 计数本身仍写在 ai_message 上（share_views / share_likes），
 * 所以 Redis 丢数据最多是「有人能重复点一次」，统计不会归零。
 *
 * 可用性取舍：Redis 异常时 fail-open —— 阅读照算、点赞放行，
 * 否则一次抖动就让分享页的按钮全变哑巴。代价是故障期间可能被刷几次，
 * 对这两个纯展示型数字来说可以接受。
 *
 * IP 取值同样默认用 TCP 对端地址（request.getRemoteAddr），
 * 不信任 X-Forwarded-For，否则换个头就是「新访客」。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class ShareGuard {

	private static final Logger log = LoggerFactory.getLogger(ShareGuard.class);

	/** 同一 IP 的阅读去重窗口 */
	private static final Duration VIEW_WINDOW = Duration.ofMinutes(30);
	/** 点赞状态保留多久（也是「取消点赞」的有效期） */
	private static final Duration LIKE_TTL = Duration.ofDays(30);

	private static final String VIEW_PREFIX = "ai:share:view:";
	private static final String LIKE_PREFIX = "ai:share:like:";

	@Autowired
	private StringRedisTemplate redisTemplate;

	/**
	 * 记一次阅读：同一 IP 在窗口内只算一次
	 *
	 * @return true 表示这次该计数
	 */
	public boolean markView(String token, String ip) {
		try {
			Boolean first = redisTemplate.opsForValue()
					.setIfAbsent(VIEW_PREFIX + token + ":" + ip, "1", VIEW_WINDOW);
			return Boolean.TRUE.equals(first);
		} catch (Exception e) {
			log.warn("阅读去重失败，按首次阅读处理：{}", e.getMessage());
			return true;
		}
	}

	/**
	 * 该 IP 是否已经点过赞
	 */
	public boolean isLiked(String token, String ip) {
		try {
			return Boolean.TRUE.equals(redisTemplate.hasKey(LIKE_PREFIX + token + ":" + ip));
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 点赞：同一个 IP 只记一次
	 *
	 * @return true 表示这次是新点赞（调用方据此自增计数）
	 */
	public boolean like(String token, String ip) {
		try {
			Boolean first = redisTemplate.opsForValue()
					.setIfAbsent(LIKE_PREFIX + token + ":" + ip, "1", LIKE_TTL);
			return Boolean.TRUE.equals(first);
		} catch (Exception e) {
			log.warn("点赞状态写入失败，按新增点赞处理：{}", e.getMessage());
			return true;
		}
	}

	/**
	 * 取消点赞
	 *
	 * @return true 表示这次确实取消掉了（调用方据此自减计数）
	 */
	public boolean unlike(String token, String ip) {
		try {
			return Boolean.TRUE.equals(redisTemplate.delete(LIKE_PREFIX + token + ":" + ip));
		} catch (Exception e) {
			log.warn("点赞状态删除失败：{}", e.getMessage());
			return false;
		}
	}
}
