package com.coolskyai.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;

/**
 * @Description: JWT工具类
 * @Author: CoolSkyAI
 * @Date: 2020-09-02
 *
 * 升级说明：jjwt 0.9.x → 0.12.x。
 * 新版本强制校验 HMAC 密钥长度（HS512 要求 512 bit = 64 字节），
 * 而配置项 token.secretKey 允许任意长度，直接使用会抛 WeakKeyException。
 * 因此这里用 SHA-512 对配置值做一次摘要，派生出固定 64 字节的密钥，
 * 既满足长度要求，也让配置值保持可读、长度不受限制。
 */
@Component
public class JwtUtils {
	private static long expireTime;
	private static SecretKey signingKey;

	@Value("${token.secretKey}")
	public void setSecretKey(String secretKey) {
		JwtUtils.signingKey = deriveKey(secretKey);
	}

	@Value("${token.expireTime}")
	public void setExpireTime(long expireTime) {
		JwtUtils.expireTime = expireTime;
	}

	/**
	 * 由配置的原始密钥派生 512 位 HMAC 密钥
	 */
	private static SecretKey deriveKey(String rawKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			byte[] keyBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
			return Keys.hmacShaKeyFor(keyBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("无法初始化 JWT 签名密钥", e);
		}
	}

	/**
	 * 判断token是否存在
	 *
	 * @param token
	 * @return
	 */
	public static boolean judgeTokenIsExist(String token) {
		return token != null && !"".equals(token) && !"null".equals(token);
	}

	/**
	 * 生成token
	 *
	 * @param subject
	 * @return
	 */
	public static String generateToken(String subject) {
		return generateTokenWithVersion(subject, 0L);
	}

	/**
	 * 生成不带权限、只带版本的 token（/login 的平台所有者前台身份）
	 *
	 * 注意不能重载成 generateToken(String, long)：会和「自定义过期时间」的
	 * generateToken(String, long) 签名冲突（参数都是 String + long）。
	 */
	public static String generateTokenWithVersion(String subject, long version) {
		return Jwts.builder()
				.subject(subject)
				.claim("tv", version)
				.expiration(new Date(System.currentTimeMillis() + expireTime))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * 生成带角色权限的token
	 *
	 * @param subject
	 * @param authorities
	 * @return
	 */
	public static String generateToken(String subject, Collection<? extends GrantedAuthority> authorities) {
		return generateToken(subject, authorities, 0L);
	}

	/**
	 * 生成带角色权限与令牌版本的 token
	 *
	 * tv 是身份版本号（见 TokenVersionService）：版本一变，旧 token 立即失效。
	 *
	 * @param subject     身份（owner:用户名 / user:ID）
	 * @param authorities 角色
	 * @param version     当前版本号
	 */
	public static String generateToken(String subject, Collection<? extends GrantedAuthority> authorities, long version) {
		StringBuilder sb = new StringBuilder();
		for (GrantedAuthority authority : authorities) {
			sb.append(authority.getAuthority()).append(",");
		}
		return Jwts.builder()
				.subject(subject)
				.claim("authorities", sb.toString())
				.claim("tv", version)
				.expiration(new Date(System.currentTimeMillis() + expireTime))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * 读取 token 里的版本号，缺失按 0 处理
	 */
	public static long tokenVersion(io.jsonwebtoken.Claims claims) {
		Object value = claims.get("tv");
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

	/**
	 * 生成自定义过期时间token
	 *
	 * @param subject
	 * @param expireTime
	 * @return
	 */
	public static String generateToken(String subject, long expireTime) {
		return Jwts.builder()
				.subject(subject)
				.expiration(new Date(System.currentTimeMillis() + expireTime))
				.signWith(signingKey)
				.compact();
	}

	/**
	 * 获取tokenBody同时校验token是否有效（无效则会抛出异常）
	 *
	 * @param token
	 * @return
	 */
	public static Claims getTokenBody(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token.replace("Bearer", ""))
				.getPayload();
	}
}
