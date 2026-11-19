package com.coolskyai.util;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;

/**
 * 客户端 IP 解析与归属地查询。
 *
 * <p>两件事分开看：{@link #getIpAddress} 只负责在代理链中找出真实客户端地址，
 * {@link #getCityInfo} 把地址交给离线库换成「国家|区域|省份|城市|运营商」。
 *
 * <p>离线库在启动时整块读进内存，换取查询零 IO；代价是启动时多一次读盘。
 */
@Slf4j
@Component
public class IpAddressUtils {

	/**
	 * 代理转发头的尝试顺序。
	 *
	 * <p>顺序有意义：越靠前的头越接近真实客户端。nginx 会写 X-Real-IP，
	 * 多级代理时 X-Forwarded-For 是逗号分隔的链路；不同厂商的网关用各自的头名，
	 * 所以要把常见几种都试一遍。
	 */
	private static final List<String> PROXY_HEADERS = List.of(
			"X-Real-IP",
			"x-forwarded-for",
			"Proxy-Client-IP",
			"WL-Proxy-Client-IP",
			"HTTP_CLIENT_IP",
			"HTTP_X_FORWARDED_FOR");

	/** 回环地址：出现在本地开发或未配置代理的场景，需要换成机器自身的出口地址 */
	private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1");

	private static final String DB_PATH = "/ipdb/ip2region.xdb";

	/** 只读句柄，启动时赋值；为空说明离线库加载失败，查询直接返回空串 */
	private static Searcher searcher;

	@PostConstruct
	void loadOfflineDatabase() {
		try (InputStream in = new ClassPathResource(DB_PATH).getInputStream()) {
			// 一次性读入内存，避免每次查询都触碰文件系统，也绕开了 jar 内资源无法随机读取的问题
			searcher = new Searcher(null, null, in.readAllBytes());
			log.info("IP 离线库已加载: {}", DB_PATH);
		} catch (Exception e) {
			// 归属地是展示信息，缺了不该让服务起不来
			log.error("IP 离线库加载失败，归属地查询将返回空值: {}", DB_PATH, e);
		}
	}

	/**
	 * 取客户端真实 IP。
	 *
	 * @return 永不返回 null；代理链形如 {@code "1.2.3.4, 10.0.0.1"} 时只取第一段
	 */
	public static String getIpAddress(HttpServletRequest request) {
		String ip = PROXY_HEADERS.stream()
				.map(request::getHeader)
				.filter(IpAddressUtils::hasValue)
				.findFirst()
				.orElse(null);

		if (ip == null) {
			ip = request.getRemoteAddr();
			if (LOOPBACK.contains(ip)) {
				ip = resolveLocalHost(ip);
			}
		}

		int comma = ip.indexOf(',');
		return comma < 0 ? ip : ip.substring(0, comma).trim();
	}

	/**
	 * 查离线库拿归属地。
	 *
	 * @return 形如 {@code "中国|广东省|深圳市|电信"}，未命中返回空串
	 */
	public static String getCityInfo(String ip) {
		if (searcher == null || ip == null || ip.isEmpty()) {
			return "";
		}
		try {
			String region = searcher.search(ip);
			if (region == null || region.isEmpty()) {
				return "";
			}
			// 库里用 0 表示该级未知，展示时把占位去掉，避免出现「中国|0|0|0|0」
			return region.replace("|0", "").replace("0|", "");
		} catch (Exception e) {
			log.error("归属地查询失败: {}", ip, e);
			return "";
		}
	}

	private static boolean hasValue(String v) {
		return v != null && !v.isEmpty() && !"unknown".equalsIgnoreCase(v);
	}

	/**
	 * 回环地址换成本机地址。取不到就保留原值 —— 丢掉整个请求比拿到 127.0.0.1 更糟。
	 */
	private static String resolveLocalHost(String loopback) {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			log.warn("无法解析本机地址，保留 {}", loopback);
			return loopback;
		}
	}
}
