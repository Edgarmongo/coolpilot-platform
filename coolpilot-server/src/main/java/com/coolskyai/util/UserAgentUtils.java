package com.coolskyai.util;

import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;
import com.coolskyai.model.dto.UserAgentDTO;

/**
 * @Description: User-Agent 解析
 *
 * 只取两个字段（系统、浏览器），不开全字段解析 —— yauaa 的规则集会全部加载，
 * 字段越少启动越快、内存越省。缓存 1 万条足够覆盖单站点的访问分布。
 *
 * 解析失败不影响日志落库：拿不到就给 Unknown，访问日志仍然完整。
 *
 * @Author: CoolSkyAI
 * @Date: 2020-11-30
 */
@Component
public class UserAgentUtils {

	private static final String UNKNOWN = "Unknown";

	private static final String OS_FIELD = UserAgent.OPERATING_SYSTEM_NAME_VERSION_MAJOR;
	private static final String BROWSER_FIELD = UserAgent.AGENT_NAME_VERSION;

	private final UserAgentAnalyzer analyzer;

	public UserAgentUtils() {
		this.analyzer = UserAgentAnalyzer
				.newBuilder()
				.useJava8CompatibleCaching()
				.withCache(10000)
				.hideMatcherLoadStats()
				.withField(OS_FIELD)
				.withField(BROWSER_FIELD)
				.build();
	}

	/**
	 * 从 User-Agent 解析客户端操作系统与浏览器版本
	 */
	public UserAgentDTO parseOsAndBrowser(String userAgent) {
		if (userAgent == null || userAgent.trim().isEmpty()) {
			return new UserAgentDTO(UNKNOWN, UNKNOWN);
		}
		try {
			UserAgent agent = analyzer.parse(userAgent);
			return new UserAgentDTO(readField(agent, OS_FIELD), readField(agent, BROWSER_FIELD));
		} catch (Exception e) {
			// 畸形 UA 不该让日志写入失败
			return new UserAgentDTO(UNKNOWN, UNKNOWN);
		}
	}

	private String readField(UserAgent agent, String field) {
		String value = agent.getValue(field);
		return value == null || value.isEmpty() ? UNKNOWN : value;
	}
}
