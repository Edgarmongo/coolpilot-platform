package com.coolskyai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.function.Supplier;

/**
 * @Description: JSON 读写（Jackson 3）
 *
 * Spring Boot 4 默认 Jackson 3，序列化器包名从 com.fasterxml.jackson 变为 tools.jackson，
 * ObjectMapper 也改成不可变构建，所以这里用 JsonMapper.builder() 建单例。
 * 实体上的注解仍来自 com.fasterxml.jackson.annotation，无需改动。
 *
 * 所有方法都是「失败不抛」：日志、监控这类旁路调用不该因为一段坏 JSON 把主流程打断，
 * 因此读失败返回 null、写失败返回空串，同时留一条 warn 方便排查。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-07
 */
public final class JacksonUtils {

	private static final Logger log = LoggerFactory.getLogger(JacksonUtils.class);

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private JacksonUtils() {
	}

	public static String writeValueAsString(Object value) {
		try {
			return MAPPER.writeValueAsString(value);
		} catch (Exception e) {
			log.warn("JSON 序列化失败：{}", e.toString());
			return "";
		}
	}

	public static <T> T readValue(String content, Class<T> valueType) {
		return quietly(() -> MAPPER.readValue(content, valueType));
	}

	public static <T> T readValue(String content, TypeReference<T> valueTypeRef) {
		return quietly(() -> MAPPER.readValue(content, valueTypeRef));
	}

	public static <T> T readValue(InputStream src, Class<T> valueType) {
		return quietly(() -> MAPPER.readValue(src, valueType));
	}

	/** 类型转换不吞异常：调用方传错类型属于编码错误，应该当场暴露 */
	public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
		return MAPPER.convertValue(fromValue, toValueType);
	}

	private static <T> T quietly(Supplier<T> reader) {
		try {
			return reader.get();
		} catch (Exception e) {
			log.warn("JSON 解析失败：{}", e.toString());
			return null;
		}
	}
}
