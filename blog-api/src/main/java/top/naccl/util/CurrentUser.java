package top.naccl.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import top.naccl.constant.JwtConstants;

/**
 * @Description: 当前登录用户上下文
 *
 * 身份来源：JwtFilter 解析 token 后写入 SecurityContext，principal 为完整 subject
 * （博主：admin:用户名；访客：visitor:用户ID）。
 *
 * 注意：不要用 ThreadLocal 自行保存用户名。
 * 历史上 JwtLoginFilter 曾用 ThreadLocal 记录登录名，异常分支未 remove 导致线程复用串号，已修复。
 *
 * @Author: Naccl
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
		if (subject.startsWith(JwtConstants.ADMIN_PREFIX) || subject.startsWith(JwtConstants.VISITOR_PREFIX)) {
			return subject;
		}
		return null;
	}

	/**
	 * 是否已登录（博主或访客）
	 */
	public static boolean isLogin() {
		return subject() != null;
	}

	/**
	 * 是否平台所有者
	 */
	public static boolean isOwner() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_admin".equals(authority.getAuthority())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 博主用户名，非博主登录返回 null
	 */
	public static String ownerUsername() {
		String subject = subject();
		if (subject != null && subject.startsWith(JwtConstants.ADMIN_PREFIX)) {
			return subject.substring(JwtConstants.ADMIN_PREFIX.length());
		}
		return null;
	}

	/**
	 * 访客ID，非访客登录返回 null
	 */
	public static Long visitorId() {
		String subject = subject();
		if (subject != null && subject.startsWith(JwtConstants.VISITOR_PREFIX)) {
			try {
				return Long.parseLong(subject.substring(JwtConstants.VISITOR_PREFIX.length()));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * 当前用户ID：访客直接取 token 中的ID；博主需要通过用户名查库
	 * 统一的取用户方法放在业务层，避免工具类依赖 Mapper
	 */
	public static Long userIdOrNull() {
		return visitorId();
	}
}
