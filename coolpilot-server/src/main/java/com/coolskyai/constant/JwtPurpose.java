package com.coolskyai.constant;

import io.jsonwebtoken.Claims;

/**
 * @Description: token 的用途声明（R-12）
 *
 * 问题：以前所有 token 共用一把签名密钥，只靠 subject 长什么样来区分身份 ——
 * 登录态是 owner:用户名 / user:ID，密码文章凭证是一串纯数字（文章ID）。
 * 这条规则太细：任何读 token 的地方都得自己判断 subject 形态，
 * 新加一个功能忘了判断，就等于把「文章密码凭证」当成了登录态，
 * 或者反过来把登录 token 当成了某篇文章的通行证。
 *
 * 做法：签发时写死 purpose，校验时按用途比对。
 *   login —— 登录态（后台所有者、前台注册用户）
 *   blog  —— 密码保护文章的访问凭证，subject 为 blog:{id}
 *
 * 兼容：历史登录 token 没有 purpose，仍按 login 处理（否则改这一版会把所有人踢下线）；
 * 历史文章凭证同样是「没有 purpose + 纯数字 subject」，**不再接受** ——
 * 它的有效期只有 30 天且存在浏览器 cookie 里，重新输一次密码即可，
 * 这个代价换来的是「用途」不再靠猜。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public final class JwtPurpose {

	/** 登录态 */
	public static final String LOGIN = "login";

	/** 密码保护文章的访问凭证 */
	public static final String BLOG = "blog";

	/** 文章凭证的 subject 前缀 */
	public static final String BLOG_SUBJECT_PREFIX = "blog:";

	private JwtPurpose() {
	}

	/**
	 * 取 token 的用途；历史 token 没有这个声明，返回 null
	 */
	public static String of(Claims claims) {
		Object value = claims == null ? null : claims.get("purpose");
		return value instanceof String ? (String) value : null;
	}

	/**
	 * 是否可作为登录态使用
	 *
	 * 没有 purpose 的历史 token 视为登录态（兼容期），但必须带身份前缀才作数，
	 * 由调用方用 JwtConstants.isAuthSubject 再判一次。
	 */
	public static boolean isLogin(Claims claims) {
		String purpose = of(claims);
		return purpose == null || LOGIN.equals(purpose);
	}

	/**
	 * 是否是该文章的访问凭证
	 */
	public static boolean isBlogAccess(Claims claims, Long blogId) {
		if (claims == null || blogId == null || !BLOG.equals(of(claims))) {
			return false;
		}
		// 用途对了还不够，必须是这一篇：否则拿 A 文章的密码能看 B 文章
		Object bid = claims.get("bid");
		return bid instanceof Number && ((Number) bid).longValue() == blogId;
	}
}
