package com.coolskyai.util;

import cn.hutool.core.lang.hash.MurmurHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @Description: 摘要与口令哈希
 *
 * 三种用途各不一样，不要互相替代：
 * - MD5：只用于非安全场景的短标识（历史上用于生成文件名等）
 * - MurmurHash32：评论头像的稳定取模，**算法与取值不能变**，否则同一昵称的头像会换一张
 * - BCrypt：口令存储，自带盐，比对走 matches 而不是重新 encode
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-17
 */
public final class HashUtils {

	private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

	private static final char[] HEX_LOWER = "0123456789abcdef".toCharArray();

	private HashUtils() {
	}

	public static String getMd5(CharSequence text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			return toHex(digest.digest(bytesOf(text)));
		} catch (NoSuchAlgorithmException e) {
			// JDK 必须提供 MD5，走到这里说明运行环境异常
			throw new IllegalStateException("当前 JVM 不支持 MD5", e);
		}
	}

	/**
	 * 32 位 MurmurHash 转非负 long。负数用 MAX_VALUE - hash 抬正，
	 * 这是历史上定下的映射方式，改了会让已入库的头像索引全部失效。
	 */
	public static long getMurmurHash32(String text) {
		int hash = MurmurHash.hash32(text);
		return hash < 0 ? Integer.MAX_VALUE - (long) hash : hash;
	}

	public static String getBC(CharSequence rawPassword) {
		return PASSWORD_ENCODER.encode(rawPassword);
	}

	public static boolean matchBC(CharSequence rawPassword, String encodedPassword) {
		return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
	}

	private static byte[] bytesOf(CharSequence text) {
		return text.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String toHex(byte[] digest) {
		char[] out = new char[digest.length * 2];
		for (int i = 0; i < digest.length; i++) {
			int value = digest[i] & 0xFF;
			out[i * 2] = HEX_LOWER[value >>> 4];
			out[i * 2 + 1] = HEX_LOWER[value & 0x0F];
		}
		return new String(out);
	}
}
