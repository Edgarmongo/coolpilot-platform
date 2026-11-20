package com.coolskyai.util.common;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @Description: 静态获取 Bean
 *
 * 只在「不是 Spring 管的类」里用：Quartz 的 Job 由反射创建、上传通道由配置字符串决定实现，
 * 这两处拿不到注入，只能走这里。业务代码请正常注入，不要图省事调 getBean。
 *
 * 容器还没起来时 getBean 会抛 IllegalStateException 并说清原因 ——
 * 比直接 NPE 在调用栈深处要容易定位得多。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
@Component
public class SpringContextUtils implements ApplicationContextAware {

	private static volatile ApplicationContext context;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		SpringContextUtils.context = applicationContext;
	}

	public static Object getBean(String name) {
		return requireContext().getBean(name);
	}

	public static <T> T getBean(String name, Class<T> requiredType) {
		return requireContext().getBean(name, requiredType);
	}

	public static <T> T getBean(Class<T> requiredType) {
		return requireContext().getBean(requiredType);
	}

	public static boolean containsBean(String name) {
		return requireContext().containsBean(name);
	}

	public static boolean isSingleton(String name) {
		return requireContext().isSingleton(name);
	}

	public static Class<?> getType(String name) {
		return requireContext().getType(name);
	}

	private static ApplicationContext requireContext() {
		ApplicationContext current = context;
		if (current == null) {
			throw new IllegalStateException("ApplicationContext 尚未初始化，"
					+ "确认调用发生在容器启动之后（静态初始化块里取 Bean 会踩到这个问题）");
		}
		return current;
	}
}
