package com.coolskyai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 StringUtils 的历史语义。
 *
 * 最容易改错的是 isEmpty：它的约定是「任意一项为空即 true」，
 * 调用方拿它一次校验多个必填项（`isEmpty(nickname, email)`）。
 */
class StringUtilsTest {

	@Test
	void isEmpty_任意一项为空即为空() {
		assertThat(StringUtils.isEmpty((String) null)).isTrue();
		assertThat(StringUtils.isEmpty("")).isTrue();
		assertThat(StringUtils.isEmpty("   ")).isTrue();
		assertThat(StringUtils.isEmpty("a", null)).isTrue();
		assertThat(StringUtils.isEmpty("a", "  ", "b")).isTrue();
	}

	@Test
	void isEmpty_全部有值才为假() {
		assertThat(StringUtils.isEmpty("a")).isFalse();
		assertThat(StringUtils.isEmpty("a", "b", "c")).isFalse();
	}

	@Test
	void hasSpecialChar_命中SQL通配符() {
		assertThat(StringUtils.hasSpecialChar("%")).isTrue();
		assertThat(StringUtils.hasSpecialChar("a_b")).isTrue();
		assertThat(StringUtils.hasSpecialChar("[x]")).isTrue();
		assertThat(StringUtils.hasSpecialChar("#tag")).isTrue();
		assertThat(StringUtils.hasSpecialChar("*")).isTrue();
	}

	@Test
	void hasSpecialChar_普通词与空值不误判() {
		assertThat(StringUtils.hasSpecialChar("spring boot")).isFalse();
		assertThat(StringUtils.hasSpecialChar("中文搜索")).isFalse();
		assertThat(StringUtils.hasSpecialChar((String) null)).isFalse();
	}

	@Test
	void substring_边界行为() {
		assertThat(StringUtils.substring(null, 0, 3)).isEmpty();
		assertThat(StringUtils.substring("", 0, 3)).isEmpty();
		// 负数视为不截断
		assertThat(StringUtils.substring("abcdef", -1, 3)).isEqualTo("abcdef");
		assertThat(StringUtils.substring("abcdef", 0, -1)).isEqualTo("abcdef");
		// 越界收敛
		assertThat(StringUtils.substring("abc", 0, 99)).isEqualTo("abc");
		// 起点不小于终点
		assertThat(StringUtils.substring("abc", 2, 2)).isEmpty();
		assertThat(StringUtils.substring("abc", 3, 1)).isEmpty();
		assertThat(StringUtils.substring("abcdef", 1, 4)).isEqualTo("bcd");
	}

	@Test
	void getStackTrace_包含类型与消息() {
		String trace = StringUtils.getStackTrace(new IllegalStateException("库连不上"));
		assertThat(trace).contains("IllegalStateException").contains("库连不上");
		assertThat(StringUtils.getStackTrace(null)).isEmpty();
	}
}
