package com.coolskyai.util.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.Set;

/**
 * @Description: Bean 校验入口
 *
 * Validator 走 holder 方式懒加载：既保证单例，也避免类初始化时就触发
 * Validation 工厂构建（否则单测里静态引用这个类也会连带初始化校验框架）。
 *
 * 只抛第一条违规信息 —— 表单场景下用户一次看一条更容易改，全部抛出来反而没人读。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
public final class ValidatorUtils {

	private ValidatorUtils() {
	}

	private static final class Holder {
		private static final Validator INSTANCE = Validation.buildDefaultValidatorFactory().getValidator();
	}

	/**
	 * 校验对象，不通过时抛 RuntimeException（历史上就是这个类型，调用方按它兜错）
	 *
	 * @param target 待校验对象
	 * @param groups 校验分组
	 */
	public static void validateEntity(Object target, Class<?>... groups) {
		Set<ConstraintViolation<Object>> violations = Holder.INSTANCE.validate(target, groups);
		if (violations.isEmpty()) {
			return;
		}
		throw new RuntimeException(violations.iterator().next().getMessage());
	}
}
