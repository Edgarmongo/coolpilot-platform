package com.coolskyai.constant;

/**
 * JWT常量
 *
 * token 的 subject 用前缀区分身份来源：
 *   owner:用户名  平台所有者（后台登录）
 *   user:用户ID   前台注册用户
 *
 * 历史前缀 admin: / visitor: 仍可解析（见 LEGACY_* 常量），
 * 保证角色改名那一刻不会把所有人的登录态踢掉；
 * 待旧 token 全部过期后即可删除兼容分支。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-23
 */
public final class JwtConstants {

	/**
	 * 平台所有者 token 前缀
	 */
	public static final String OWNER_PREFIX = "owner:";

	/**
	 * 前台注册用户 token 前缀
	 * 与所有者是两套身份：前台自助注册产生，不具备后台管理权限
	 */
	public static final String USER_PREFIX = "user:";

	/**
	 * 历史所有者前缀（旧称博主 admin）
	 */
	public static final String LEGACY_ADMIN_PREFIX = "admin:";

	/**
	 * 历史前台用户前缀（旧称访客 visitor）
	 */
	public static final String LEGACY_VISITOR_PREFIX = "visitor:";

	private JwtConstants() {
	}

	/**
	 * subject 是否代表登录身份
	 *
	 * 密码文章 token 的 subject 是文章ID（纯数字），不在此列，按匿名处理。
	 */
	public static boolean isAuthSubject(String subject) {
		return isOwnerSubject(subject) || isUserSubject(subject);
	}

	public static boolean isOwnerSubject(String subject) {
		return subject != null
				&& (subject.startsWith(OWNER_PREFIX) || subject.startsWith(LEGACY_ADMIN_PREFIX));
	}

	public static boolean isUserSubject(String subject) {
		return subject != null
				&& (subject.startsWith(USER_PREFIX) || subject.startsWith(LEGACY_VISITOR_PREFIX));
	}

	/**
	 * 取所有者用户名，非所有者 subject 返回 null
	 */
	public static String ownerName(String subject) {
		if (!isOwnerSubject(subject)) {
			return null;
		}
		return subject.startsWith(OWNER_PREFIX)
				? subject.substring(OWNER_PREFIX.length())
				: subject.substring(LEGACY_ADMIN_PREFIX.length());
	}

	/**
	 * 取前台用户ID，非用户 subject 返回 null
	 */
	public static Long userId(String subject) {
		if (!isUserSubject(subject)) {
			return null;
		}
		String raw = subject.startsWith(USER_PREFIX)
				? subject.substring(USER_PREFIX.length())
				: subject.substring(LEGACY_VISITOR_PREFIX.length());
		try {
			return Long.parseLong(raw);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
