package com.coolskyai.aspect;

import com.coolskyai.annotation.VisitLogger;
import com.coolskyai.constant.RedisKeyConstants;
import com.coolskyai.entity.VisitLog;
import com.coolskyai.entity.Visitor;
import com.coolskyai.enums.VisitBehavior;
import com.coolskyai.model.dto.VisitLogRemark;
import com.coolskyai.model.vo.BlogDetail;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.RedisService;
import com.coolskyai.service.VisitLogService;
import com.coolskyai.service.VisitorService;
import com.coolskyai.util.AopUtils;
import com.coolskyai.util.IpAddressUtils;
import com.coolskyai.util.JacksonUtils;
import com.coolskyai.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;

/**
 * 访问日志切面。
 *
 * <p>只挂在带 {@link VisitLogger} 的接口上。一次请求做两件事：
 * 确认访客标识（没有就签发一个），然后把这次访问写进 {@code visit_log}。
 *
 * <p><b>访客标识的设计</b>：不依赖 Cookie，而是用「小时戳 + IP + User-Agent」生成确定性 UUID，
 * 通过响应头 {@code identification} 交给前端保存。这样同一访客在一段时间内的多次访问可以归并，
 * 也不受禁用 Cookie 影响。
 *
 * @author CoolSkyAI
 */
@Component
@Aspect
public class VisitLogAspect {

	/** 访客标识的请求/响应头名，前后端约定 */
	private static final String IDENTIFICATION_HEADER = "identification";

	/** 参数序列化后的最大留存长度，防止一条日志把库撑大 */
	private static final int PARAM_MAX_LENGTH = 2000;

	private final VisitLogService visitLogService;
	private final VisitorService visitorService;
	private final RedisService redisService;

	public VisitLogAspect(VisitLogService visitLogService, VisitorService visitorService, RedisService redisService) {
		this.visitLogService = visitLogService;
		this.visitorService = visitorService;
		this.redisService = redisService;
	}

	@Pointcut("@annotation(visitLogger)")
	public void logPointcut(VisitLogger visitLogger) {
	}

	@Around("logPointcut(visitLogger)")
	public Object logAround(ProceedingJoinPoint joinPoint, VisitLogger visitLogger) throws Throwable {
		long startedAt = System.currentTimeMillis();
		Result result = (Result) joinPoint.proceed();
		int elapsedMillis = (int) (System.currentTimeMillis() - startedAt);

		HttpServletRequest request = currentRequest();
		String identification = resolveIdentification(request);
		visitLogService.saveVisitLog(buildLog(joinPoint, visitLogger, request, result, elapsedMillis, identification));
		return result;
	}

	// ------------------------------------------------------------------
	// 访客标识
	// ------------------------------------------------------------------

	/**
	 * 拿到本次请求的访客标识：先看请求头，再看 Redis，最后看数据库；都没有就签发新的。
	 */
	private String resolveIdentification(HttpServletRequest request) {
		String fromHeader = request.getHeader(IDENTIFICATION_HEADER);
		if (fromHeader == null) {
			return issueIdentification(request);
		}
		if (cachedInRedis(fromHeader)) {
			return fromHeader;
		}
		// Redis 里没有，但可能只是缓存被清过；库里还在就重新写回缓存，不重复建档
		if (visitorService.hasUUID(fromHeader)) {
			redisService.saveValueToSet(RedisKeyConstants.IDENTIFICATION_SET, fromHeader);
			return fromHeader;
		}
		return issueIdentification(request);
	}

	/**
	 * 签发新的访客标识并落地。
	 *
	 * <p>注意时间戳只精确到小时、**没有清零毫秒** —— 这是沿用下来的行为：
	 * 同一小时内不同毫秒会算出不同 UUID。也就是说实际粒度接近「一次会话一个访客」，
	 * 而不是「一小时一个访客」。改成清零会改变 UV 统计口径，需要单独评估，不要顺手改。
	 */
	private String issueIdentification(HttpServletRequest request) {
		String ip = IpAddressUtils.getIpAddress(request);
		String userAgent = request.getHeader("User-Agent");
		String uuid = UUID.nameUUIDFromBytes(
				(hourMillis() + ip + userAgent).getBytes(StandardCharsets.UTF_8)).toString();

		HttpServletResponse response = currentResponse();
		if (response != null) {
			response.addHeader(IDENTIFICATION_HEADER, uuid);
			// 自定义头默认不暴露给前端脚本，需要显式声明
			response.addHeader("Access-Control-Expose-Headers", IDENTIFICATION_HEADER);
		}

		if (!cachedInRedis(uuid)) {
			redisService.saveValueToSet(RedisKeyConstants.IDENTIFICATION_SET, uuid);
			visitorService.saveVisitor(new Visitor(uuid, ip, userAgent));
		}
		return uuid;
	}

	private boolean cachedInRedis(String uuid) {
		return redisService.hasValueInSet(RedisKeyConstants.IDENTIFICATION_SET, uuid);
	}

	private String hourMillis() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		return Long.toString(calendar.getTimeInMillis() / 1000);
	}

	// ------------------------------------------------------------------
	// 日志内容
	// ------------------------------------------------------------------

	private VisitLog buildLog(ProceedingJoinPoint joinPoint, VisitLogger visitLogger, HttpServletRequest request,
	                          Result result, int elapsedMillis, String identification) {
		Map<String, Object> params = AopUtils.getRequestParams(joinPoint);
		VisitLogRemark remark = judgeBehavior(visitLogger.value(), params, result);

		VisitLog log = new VisitLog(identification,
				request.getRequestURI(),
				request.getMethod(),
				visitLogger.value().getBehavior(),
				remark.getContent(),
				remark.getRemark(),
				IpAddressUtils.getIpAddress(request),
				elapsedMillis,
				request.getHeader("User-Agent"));
		log.setParam(StringUtils.substring(JacksonUtils.writeValueAsString(params), 0, PARAM_MAX_LENGTH));
		return log;
	}

	/**
	 * 按访问行为补充「访问内容」和「备注」。
	 *
	 * <p>默认值取自 {@link VisitBehavior}，只有能确定具体对象时才覆盖。
	 */
	private VisitLogRemark judgeBehavior(VisitBehavior behavior, Map<String, Object> params, Result result) {
		String fallback = behavior.getContent();
		return switch (behavior) {
			case INDEX, MOMENT -> new VisitLogRemark(fallback, "第" + params.get("pageNum") + "页");
			case BLOG -> blogRemark(result, fallback);
			case SEARCH -> searchRemark(params, result, fallback);
			case CATEGORY -> new VisitLogRemark(
					asText(params.get("categoryName")),
					"分类名称：" + params.get("categoryName") + "，第" + params.get("pageNum") + "页");
			case TAG -> new VisitLogRemark(
					asText(params.get("tagName")),
					"标签名称：" + params.get("tagName") + "，第" + params.get("pageNum") + "页");
			case CLICK_FRIEND -> new VisitLogRemark(
					asText(params.get("nickname")),
					"友链名称：" + params.get("nickname"));
			// 归档 / 友链页 / 关于我 / 点赞动态 / UNKNOWN 不需要补充内容，沿用枚举里的默认值
			default -> new VisitLogRemark(fallback, "");
		};
	}

	/**
	 * 文章页：只有查到了文章才记标题。查不到（404）保持默认内容，不硬转类型。
	 */
	private VisitLogRemark blogRemark(Result result, String fallback) {
		if (result.getCode() != 200 || !(result.getData() instanceof BlogDetail blog)) {
			return new VisitLogRemark(fallback, "");
		}
		return new VisitLogRemark(blog.getTitle(), "文章标题：" + blog.getTitle());
	}

	private VisitLogRemark searchRemark(Map<String, Object> params, Result result, String fallback) {
		if (result.getCode() != 200) {
			return new VisitLogRemark(fallback, "");
		}
		String query = asText(params.get("query"));
		return new VisitLogRemark(query, "搜索内容：" + query);
	}

	private String asText(Object value) {
		return value == null ? "" : value.toString();
	}

	// ------------------------------------------------------------------
	// 请求上下文
	// ------------------------------------------------------------------

	private HttpServletRequest currentRequest() {
		ServletRequestAttributes attributes =
				(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes == null ? null : attributes.getRequest();
	}

	private HttpServletResponse currentResponse() {
		ServletRequestAttributes attributes =
				(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes == null ? null : attributes.getResponse();
	}
}
