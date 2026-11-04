package com.coolskyai.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.constant.Role;

/**
 * @Description: 当前登录用户上下文
 *
 * 身份来源：JwtFilter 解析 token 后写入 SecurityContext，principal 为完整 subject
 * （平台所有者：owner:用户名；前台用户：user:用户ID），
 * 历史 token 的 admin: / visitor: 前缀同样可用。
 *
 * 注意：不要用 ThreadLocal 自行保存用户名。
 * 历史上 JwtLoginFilter 曾用 ThreadLocal 记录登录名，异常分支未 remove 导致线程复用串号，已修复。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	private static String subject() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return null;
		}
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof String)) {
			return null;
		}
		String subject = (String) principal;
		// 匿名过滤器会写入 anonymousUser，必须排除，否则会被误判为已登录
		return JwtConstants.isAuthSubject(subject) ? subject : null;
	}

	/**
	 * 是否已登录（所有者或前台用户）
	 */
	public static boolean isLogin() {
		return subject() != null;
	}

	/**
	 * 是否平台所有者
	 *
	 * 兼容旧 token 里的 ROLE_admin，读入时归一化，不再直接比较历史字面量。
	 */
	public static boolean isOwner() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if (Role.isOwner(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 平台所有者用户名，非所有者登录返回 null
	 */
	public static String ownerUsername() {
		return JwtConstants.ownerName(subject());
	}

	/**
	 * 前台注册用户ID，非前台用户登录返回 null
	 */
	public static Long userId() {
		return JwtConstants.userId(subject());
	}

	/**
	 * 当前用户ID：前台用户直接取 token 中的ID；所有者需要通过用户名查库
	 * 统一的取用户方法放在业务层，避免工具类依赖 Mapper
	 */
	public static Long userIdOrNull() {
		return userId();
	}
}
