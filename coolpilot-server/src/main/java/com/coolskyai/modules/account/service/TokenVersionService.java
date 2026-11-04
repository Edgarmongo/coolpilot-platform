package com.coolskyai.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @Description: 身份令牌版本（R-11）
 *
 * 问题：JWT 是无状态的，改密码、主动登出都不会让已签发的 token 失效，
 * 有效期内谁拿到 token 谁就能用。3 天的窗口对「改密码赶人」来说太长了。
 *
 * 做法：给每个身份（owner:用户名 / user:ID）维护一个版本号，
 * 签发时写进 token 的 tv 声明，每次请求比对；版本一变，旧 token 全部作废。
 *   - 改密码 → bump，所有设备上的旧 token 立即失效
 *   - 主动登出 → bump，等于「退出所有设备」（不区分单设备，够用且简单）
 *
 * 为什么不用黑名单：那要为每个 token 存一条记录并保存到过期，
 * 而版本号每个身份只有一条、天然有 TTL，读一次 Redis 就能判断。
 *
 * 可用性取舍：Redis 异常时 fail-open（按版本 0 处理），
 * 否则一次 Redis 抖动就会把全站所有人踢下线。代价是 Redis 挂掉期间吊销失效，
 * 属于可接受的降级（此时登录、限流也都不可用了，本来就是故障状态）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class TokenVersionService {

	private static final String KEY_PREFIX = "auth:tv:";

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Value("${token.expireTime:259200000}")
	private long expireTime;

	/**
	 * 当前版本号，未设置过视为 0
	 */
	public long current(String subject) {
		if (subject == null || subject.isEmpty()) {
			return 0L;
		}
		try {
			String value = redisTemplate.opsForValue().get(KEY_PREFIX + subject);
			return value == null ? 0L : Long.parseLong(value);
		} catch (Exception e) {
			return 0L;
		}
	}

	/**
	 * 递增版本，返回新版本号。
	 * TTL 取 token 有效期：旧 token 最多活这么久，版本号没必要留更久。
	 */
	public long bump(String subject) {
		if (subject == null || subject.isEmpty()) {
			return 0L;
		}
		try {
			Long version = redisTemplate.opsForValue().increment(KEY_PREFIX + subject);
			redisTemplate.expire(KEY_PREFIX + subject, Duration.ofMillis(Math.max(expireTime, 3_600_000L)));
			return version == null ? 0L : version;
		} catch (Exception e) {
			return 0L;
		}
	}
}
