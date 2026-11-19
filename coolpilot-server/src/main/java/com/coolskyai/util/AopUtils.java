package com.coolskyai.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 切面用的入参提取工具。
 *
 * <p>目的是把「可以安全写进日志的参数」挑出来：请求、响应、上传文件写进日志没有意义，
 * 还会把整个请求体序列化进去；令牌类参数属于敏感信息，不能落库。
 */
public final class AopUtils {

	/** 不记录到日志的参数名，小写比较 */
	private static final Set<String> SENSITIVE_PARAMS = Set.of("jwt", "password", "token");

	private AopUtils() {
	}

	/**
	 * 按参数名收集入参，跳过敏感字段与不可序列化对象。
	 *
	 * <p>用 LinkedHashMap 保持声明顺序 —— 日志里参数顺序和代码一致，排查时不用来回对照。
	 */
	public static Map<String, Object> getRequestParams(JoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] names = signature.getParameterNames();
		Object[] values = joinPoint.getArgs();

		Map<String, Object> collected = new LinkedHashMap<>();
		if (names == null || values == null) {
			return collected;
		}

		// 参数名可能拿不到（未开 -parameters 时签名信息缺失），此时按下标兜底，不能直接越界
		int limit = Math.min(names.length, values.length);
		for (int i = 0; i < limit; i++) {
			String name = names[i];
			Object value = values[i];
			if (name == null || SENSITIVE_PARAMS.contains(name.toLowerCase())) {
				continue;
			}
			if (isContextObject(value)) {
				continue;
			}
			collected.put(name, value);
		}
		return collected;
	}

	/**
	 * 这些对象要么是框架注入的、要么体积不可控，都不该出现在日志里。
	 */
	private static boolean isContextObject(Object value) {
		return value == null
				? false
				: value instanceof HttpServletRequest
				|| value instanceof HttpServletResponse
				|| value instanceof MultipartFile
				|| (value instanceof MultipartFile[] files)
				|| (value instanceof byte[]);
	}

	/** 供切面在需要时判断某个参数名是否敏感 */
	public static boolean isSensitive(String name) {
		return name != null && SENSITIVE_PARAMS.contains(name.toLowerCase());
	}

	/** 调试用：把参数表转成一行文本，方便直接写进日志字段 */
	public static String describe(Map<String, Object> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		params.forEach((k, v) -> {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(k).append('=');
			if (v instanceof Object[] arr) {
				sb.append(Arrays.toString(arr));
			} else {
				sb.append(v);
			}
		});
		return sb.toString();
	}
}
