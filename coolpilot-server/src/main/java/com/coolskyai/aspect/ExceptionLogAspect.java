package com.coolskyai.aspect;

import com.coolskyai.annotation.OperationLogger;
import com.coolskyai.annotation.VisitLogger;
import com.coolskyai.entity.ExceptionLog;
import com.coolskyai.service.ExceptionLogService;
import com.coolskyai.util.AopUtils;
import com.coolskyai.util.IpAddressUtils;
import com.coolskyai.util.JacksonUtils;
import com.coolskyai.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 异常日志切面。
 *
 * <p>挂在所有 controller 方法上，只在抛异常时触发。记录「哪个接口、什么操作、谁来的、错在哪」。
 *
 * <p>切面自身**不允许抛异常** —— 它是在原始异常处理链上执行的，
 * 如果记录日志时再抛一次，会把真正的错误盖掉，排查时只能看到一串反射栈。
 *
 * @author CoolSkyAI
 */
@Component
@Aspect
public class ExceptionLogAspect {

	/** 参数序列化后的最大留存长度 */
	private static final int PARAM_MAX_LENGTH = 2000;

	private final ExceptionLogService exceptionLogService;

	public ExceptionLogAspect(ExceptionLogService exceptionLogService) {
		this.exceptionLogService = exceptionLogService;
	}

	@Pointcut("execution(* com.coolskyai.controller..*.*(..))")
	public void logPointcut() {
	}

	@AfterThrowing(value = "logPointcut()", throwing = "e")
	public void logAfterThrowing(JoinPoint joinPoint, Exception e) {
		HttpServletRequest request = currentRequest();
		// 拿不到请求上下文（非 Web 线程）时直接跳过：此时记一条残缺日志没有意义，
		// 反而可能因为 NPE 把原始异常吞掉
		if (request == null) {
			return;
		}
		exceptionLogService.saveExceptionLog(buildLog(joinPoint, e, request));
	}

	private ExceptionLog buildLog(JoinPoint joinPoint, Exception e, HttpServletRequest request) {
		ExceptionLog log = new ExceptionLog(
				request.getRequestURI(),
				request.getMethod(),
				describeOperation(joinPoint),
				StringUtils.getStackTrace(e),
				IpAddressUtils.getIpAddress(request),
				request.getHeader("User-Agent"));

		Map<String, Object> params = AopUtils.getRequestParams(joinPoint);
		log.setParam(StringUtils.substring(JacksonUtils.writeValueAsString(params), 0, PARAM_MAX_LENGTH));
		return log;
	}

	/**
	 * 从方法上的注解取一句人话描述，便于在后台按「在做什么」翻日志。
	 * 操作日志优先于访问日志 —— 写操作出错时，前者信息量更大。
	 */
	private String describeOperation(JoinPoint joinPoint) {
		Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

		OperationLogger operation = method.getAnnotation(OperationLogger.class);
		if (operation != null) {
			return operation.value();
		}
		VisitLogger visit = method.getAnnotation(VisitLogger.class);
		if (visit != null) {
			return visit.value().getBehavior();
		}
		return "";
	}

	private HttpServletRequest currentRequest() {
		ServletRequestAttributes attributes =
				(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes == null ? null : attributes.getRequest();
	}
}
