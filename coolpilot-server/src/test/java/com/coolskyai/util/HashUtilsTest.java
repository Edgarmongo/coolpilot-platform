package com.coolskyai.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 摘要与口令哈希的行为锁定。
 *
 * getMurmurHash32 参与评论头像的取模，值一变头像就换人；
 * getMd5 的十六进制格式要与历史上入库的值一致（小写、无前缀）。
 */
class HashUtilsTest {

	@Test
	void md5_已知向量() {
		assertThat(HashUtils.getMd5("")).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
		assertThat(HashUtils.getMd5("abc")).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
	}

	@Test
	void md5_固定32位小写十六进制() {
		String value = HashUtils.getMd5("中文昵称");
		assertThat(value).hasSize(32).matches("[0-9a-f]{32}");
		// 同一输入必须稳定
		assertThat(HashUtils.getMd5("中文昵称")).isEqualTo(value);
	}

	@Test
	void murmur_非负且稳定() {
		Set<Long> seen = new HashSet<>();
		for (String nickname : new String[]{"CoolSky", "张三", "a", "很长很长的中文昵称用来测试", ""}) {
			long first = HashUtils.getMurmurHash32(nickname);
			assertThat(first).isGreaterThanOrEqualTo(0);
			assertThat(HashUtils.getMurmurHash32(nickname)).isEqualTo(first);
			seen.add(first);
		}
		// 不同昵称不应撞到同一个值（这组样本下）
		assertThat(seen).hasSize(5);
	}

	@Test
	void bcrypt_生成与比对() {
		String encoded = HashUtils.getBC("520134ssx");
		assertThat(encoded).startsWith("$2");
		assertThat(HashUtils.matchBC("520134ssx", encoded)).isTrue();
		assertThat(HashUtils.matchBC("wrong-password", encoded)).isFalse();
	}
}
