package com.coolskyai.util.quartz;

import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import com.coolskyai.util.common.SpringContextUtils;

import java.lang.reflect.Method;

/**
 * @Description: 把「bean 名 + 方法名 + 参数」包装成一次可执行的任务
 *
 * 每次触发都新建一个实例：Quartz 的 Job 是无状态的，任务定义来自数据库，
 * 实例里只持有本次要调用的目标与方法，不缓存 Bean 引用。
 *
 * 方法在构造时定位、运行时再放开访问权限：Constructor 里做 makeAccessible
 * 会让反射异常提前到调度线程之外，串行化排查更麻烦。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-01
 */
public class ScheduleRunnable implements Runnable {

	/** 无参调用时传给 Method.invoke 的空数组，避免两条分支各写一遍 */
	private static final Object[] NO_ARGS = new Object[0];

	private final Object target;
	private final Method method;
	private final String params;

	public ScheduleRunnable(String beanName, String methodName, String params) throws NoSuchMethodException {
		if (!StringUtils.hasText(beanName) || !StringUtils.hasText(methodName)) {
			throw new IllegalArgumentException("定时任务缺少 beanName 或 methodName");
		}
		this.target = SpringContextUtils.getBean(beanName);
		this.params = params;
		// 有参数就找 (String)，没有就找无参方法 —— 与数据库里配置的签名一一对应
		this.method = StringUtils.hasText(params)
				? target.getClass().getDeclaredMethod(methodName, String.class)
				: target.getClass().getDeclaredMethod(methodName);
	}

	@Override
	public void run() {
		try {
			ReflectionUtils.makeAccessible(method);
			method.invoke(target, StringUtils.hasText(params) ? new Object[]{params} : NO_ARGS);
		} catch (Exception e) {
			// 统一包一层，调用方（ScheduleJob）只需按一种异常处理，根因保留在 cause 里
			throw new RuntimeException("执行定时任务失败", e);
		}
	}
}
