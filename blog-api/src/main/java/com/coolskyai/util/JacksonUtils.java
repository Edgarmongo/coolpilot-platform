package com.coolskyai.util;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

/**
 * @Description: Jackson Object Mapper
 * @Author: Naccl
 * @Date: 2020-11-07
 *
 * 升级说明：Spring Boot 4 默认使用 Jackson 3，序列化器包名由 com.fasterxml.jackson 迁至 tools.jackson，
 * 且 ObjectMapper 改为不可变构建（JsonMapper.builder()），不再直接 new。
 * 异常体系也改为非受检的 JacksonException，因此这里统一 catch Exception。
 * 注：jackson-annotations 模块仍保留 com.fasterxml.jackson.annotation 包，实体上的注解无需改动。
 */
public class JacksonUtils {
	private static final JsonMapper objectMapper = JsonMapper.builder().build();

	public static String writeValueAsString(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	public static <T> T readValue(String content, Class<T> valueType) {
		try {
			return objectMapper.readValue(content, valueType);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <T> T readValue(String content, TypeReference<T> valueTypeRef) {
		try {
			return objectMapper.readValue(content, valueTypeRef);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <T> T readValue(InputStream src, Class<T> valueType) {
		try {
			return objectMapper.readValue(src, valueType);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
		return objectMapper.convertValue(fromValue, toValueType);
	}
}
