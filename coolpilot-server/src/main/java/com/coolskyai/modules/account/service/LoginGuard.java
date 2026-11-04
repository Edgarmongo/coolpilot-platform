package com.coolskyai.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.coolskyai.service.RedisService;

/**
 * @Description: 登录失败次数限制
 *
 * @AccessLimit 按「IP + 接口」计数，能挡住单机爆破，
 * 但换 IP 打同一个账号仍然打得动。这里按账号计数：
 * 连续失败 5 次锁定 15 分钟，登录成功清零。
 *
 * 计数放在 Redis 并带过期时间，服务重启不会永久锁死账号。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class LoginGuard {
	/** 连续失败多少次后锁定 */
	private static final int MAX_FAILS = 5;
	/** 锁定时长（秒） */
	private static final long LOCK_SECONDS = 15 * 60L;
	private static final String KEY_PREFIX = "login:fail:";

	@Autowired
	private RedisService redisService;

	private String key(String username) {
		String name = username == null ? "unknown" : username.trim().toLowerCase();
		return KEY_PREFIX + name;
	}

	/**
	 * 当前账号是否已被锁定
	 */
	public boolean isLocked(String username) {
		Integer fails = redisService.getObjectByValue(key(username), Integer.class);
		return fails != null && fails >= MAX_FAILS;
	}

	/**
	 * 记录一次失败，返回当前失败次数
	 */
	public int onFail(String username) {
		String k = key(username);
		Integer fails = redisService.getObjectByValue(k, Integer.class);
		if (fails == null) {
			redisService.incrementByKey(k, 1);
			redisService.expire(k, LOCK_SECONDS);
			return 1;
		}
		redisService.incrementByKey(k, 1);
		// 每次失败都续期，避免「快到 15 分钟时再试几次」把窗口拖长
		redisService.expire(k, LOCK_SECONDS);
		return fails + 1;
	}

	/**
	 * 登录成功，清空失败计数
	 */
	public void onSuccess(String username) {
		redisService.deleteCacheByKey(key(username));
	}

	/**
	 * 剩余锁定提示
	 */
	public String lockMessage() {
		return "该账号连续登录失败次数过多，已临时锁定 15 分钟";
	}
}
