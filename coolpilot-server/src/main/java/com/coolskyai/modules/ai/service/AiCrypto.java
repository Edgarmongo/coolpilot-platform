package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * @Description: AI 密钥的对称加解密
 *
 * API Key 存在数据库里，明文存等于把密钥摊在备份文件、只读账号和误发的 SQL 里。
 * 用 AES-GCM 加密，密钥从 token.secretKey（JWT 密钥）派生 —— 不额外引入一个
 * 必须保管的秘密，也就不会出现「配了加密但密钥忘了放哪」的情况。
 *
 * 代价：换 JWT_SECRET 后旧密钥解不开。解密失败返回 null，界面提示重新填一次即可，
 * 不影响其它配置。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
public class AiCrypto {

	private static final Logger log = LoggerFactory.getLogger(AiCrypto.class);

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_LENGTH = 12;

	private static final int TAG_BITS = 128;

	private final SecretKeySpec keySpec;

	private final SecureRandom random = new SecureRandom();

	public AiCrypto(@Value("${token.secretKey}") String secret) {
		try {
			// 直接拿 JWT 密钥当 AES 密钥不合适（长度不定），先走一次 SHA-256 拉齐成 128 位
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(secret.getBytes(StandardCharsets.UTF_8));
			this.keySpec = new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
		} catch (Exception e) {
			throw new IllegalStateException("初始化 AI 密钥加密失败", e);
		}
	}

	/**
	 * @return Base64(iv + 密文)；入参为空时返回空串
	 */
	public String encrypt(String plain) {
		if (plain == null || plain.isEmpty()) {
			return "";
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(out);
		} catch (Exception e) {
			log.warn("加密 AI 密钥失败：{}", e.getMessage());
			return "";
		}
	}

	/**
	 * @return 明文；密文损坏或密钥已更换时返回 null（调用方按「未配置」处理）
	 */
	public String decrypt(String cipherText) {
		if (cipherText == null || cipherText.isEmpty()) {
			return "";
		}
		try {
			byte[] all = Base64.getDecoder().decode(cipherText);
			if (all.length <= IV_LENGTH) {
				return null;
			}
			byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
			byte[] body = Arrays.copyOfRange(all, IV_LENGTH, all.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
			return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.warn("解密 AI 密钥失败（JWT_SECRET 可能已更换），请到后台重新填写：{}", e.getMessage());
			return null;
		}
	}

	/**
	 * 掩码展示：保留头尾各 4 位，中间固定星号。
	 * 太短的（本地模型常见空 Key）直接给空串，不要泄露长度。
	 */
	public String mask(String plain) {
		if (plain == null || plain.isBlank()) {
			return "";
		}
		String value = plain.trim();
		if (value.length() <= 8) {
			return "****";
		}
		return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
	}
}
