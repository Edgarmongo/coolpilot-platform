package com.coolskyai.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @Description: 字符串校验与截断
 *
 * 这里的语义沿用历史约定：`isEmpty(a, b)` 表示「任意一项为空」，
 * 调用方普遍写成 `isEmpty(x, y)` 来一次性校验多个必填项，不要改成「全部为空」。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-08-02
 */
public final class StringUtils {

	private StringUtils() {
	}

	/**
	 * 任意一个为 null 或全空白即返回 true
	 */
	public static boolean isEmpty(String... values) {
		if (values == null) {
			return true;
		}
		for (String value : values) {
			if (value == null || value.trim().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 是否包含 SQL LIKE 通配符。搜索关键词要先过这一关，否则 `%` 会把全表带出来
	 */
	public static boolean hasSpecialChar(String... values) {
		if (values == null) {
			return false;
		}
		for (String value : values) {
			if (value == null) {
				continue;
			}
			for (int i = 0; i < value.length(); i++) {
				if (isLikeWildcard(value.charAt(i))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isLikeWildcard(char c) {
		switch (c) {
			case '%':
			case '_':
			case '[':
			case '#':
			case '*':
				return true;
			default:
				return false;
		}
	}

	/**
	 * 安全截断：负数视为「不截断」，越界自动收敛，起点不小于终点时返回空串
	 */
	public static String substring(String text, int begin, int end) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		if (begin < 0 || end < 0) {
			return text;
		}
		int stop = Math.min(end, text.length());
		return begin >= stop ? "" : text.substring(begin, stop);
	}

	/**
	 * 完整堆栈，带类型与 message。异常日志切面靠它落库，不能只留一行 message
	 */
	public static String getStackTrace(Throwable throwable) {
		if (throwable == null) {
			return "";
		}
		StringWriter buffer = new StringWriter();
		try (PrintWriter writer = new PrintWriter(buffer)) {
			throwable.printStackTrace(writer);
		}
		return buffer.toString();
	}
}
