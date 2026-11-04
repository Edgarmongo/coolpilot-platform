package com.coolskyai.constant;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @Description: 平台身份（角色）常量与归一化工具
 *
 * 命名语义（T-103）：
 *   ROLE_owner —— 平台所有者，可访问 /admin/** 全部管理接口
 *   ROLE_user  —— 前台注册用户，只能访问自己的数据（/me、订单、收藏等）
 *
 * 为什么改名：
 *   1. admin 既表示"管理员"又暗示"后台路径"，与 /admin 耦合，
 *      新增接口时四处硬编码 hasRole("admin")，语义也说不清"到底是谁"。
 *   2. visitor 容易被理解为"匿名访客"，但它实际是已注册并登录的用户，
 *      与真正的匿名访问混淆。
 *
 * 兼容策略：
 *   数据库与旧 token 中可能仍是 ROLE_admin / ROLE_visitor，
 *   读取时统一走 {@link #normalize(String)}，数据库由 Flyway V5 一次性收敛。
 *   新增代码只允许引用 {@link #OWNER} / {@link #USER}。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public final class Role {

	/** 平台所有者 */
	public static final String OWNER = "ROLE_owner";

	/** 前台注册用户 */
	public static final String USER = "ROLE_user";

	/** 历史角色值，仅用于读取兼容，禁止在新代码中直接引用 */
	private static final String LEGACY_OWNER = "ROLE_admin";
	private static final String LEGACY_USER = "ROLE_visitor";

	private Role() {
	}

	/**
	 * 把历史角色值归一化为当前语义，其余值原样返回
	 */
	public static String normalize(String role) {
		if (role == null || role.isEmpty()) {
			return role;
		}
		return switch (role) {
			case LEGACY_OWNER -> OWNER;
			case LEGACY_USER -> USER;
			default -> role;
		};
	}

	public static boolean isOwner(String role) {
		return OWNER.equals(normalize(role));
	}

	public static boolean isUser(String role) {
		return USER.equals(normalize(role));
	}

	/**
	 * 归一化 JWT authorities 声明（逗号分隔），让旧 token 不必重新登录
	 *
	 * 旧 token 里写的是 ROLE_admin / ROLE_visitor，
	 * 不转换的话 hasAuthority("ROLE_owner") 会判定失败，导致升级瞬间所有人掉线。
	 */
	public static String normalizeAuthorities(String authorities) {
		if (authorities == null || authorities.isBlank()) {
			return "";
		}
		return Arrays.stream(authorities.split(","))
				.map(String::trim)
				.filter(authority -> !authority.isEmpty())
				.map(Role::normalize)
				.distinct()
				.collect(Collectors.joining(","));
	}
}
