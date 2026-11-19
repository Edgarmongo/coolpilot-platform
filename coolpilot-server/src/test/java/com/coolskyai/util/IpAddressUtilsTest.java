package com.coolskyai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户端 IP 解析的行为契约。
 *
 * <p>这些用例锁定的是「代理链怎么取第一段」「unknown 要不要跳过」「回环怎么处理」，
 * 换实现时只要这几条不变，上层（访问日志、限流）就不会受影响。
 */
class IpAddressUtilsTest {

	@Test
	@DisplayName("优先取 X-Real-IP")
	void prefersRealIpHeader() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Real-IP", "203.0.113.7");
		req.addHeader("X-Forwarded-For", "198.51.100.2");

		assertThat(IpAddressUtils.getIpAddress(req)).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("多级代理取 X-Forwarded-For 的第一段")
	void takesFirstHopFromForwardedChain() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Forwarded-For", "198.51.100.2, 10.0.0.1, 10.0.0.2");

		assertThat(IpAddressUtils.getIpAddress(req)).isEqualTo("198.51.100.2");
	}

	@Test
	@DisplayName("unknown 视为无效，继续尝试下一个头")
	void skipsUnknownPlaceholder() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Real-IP", "unknown");
		req.addHeader("x-forwarded-for", "203.0.113.9");

		assertThat(IpAddressUtils.getIpAddress(req)).isEqualTo("203.0.113.9");
	}

	@Test
	@DisplayName("没有代理头时回落到 remoteAddr")
	void fallsBackToRemoteAddr() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRemoteAddr("203.0.113.11");

		assertThat(IpAddressUtils.getIpAddress(req)).isEqualTo("203.0.113.11");
	}

	@Test
	@DisplayName("回环地址换成本机地址，而不是原样返回 127.0.0.1")
	void replacesLoopbackWithLocalAddress() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRemoteAddr("127.0.0.1");

		String ip = IpAddressUtils.getIpAddress(req);

		assertThat(ip).isNotBlank().isNotEqualTo("127.0.0.1");
	}

	@Test
	@DisplayName("归属地查询对空输入与未初始化句柄都返回空串，不抛异常")
	void cityInfoIsSafeOnEmptyInput() {
		assertThat(IpAddressUtils.getCityInfo(null)).isEmpty();
		assertThat(IpAddressUtils.getCityInfo("")).isEmpty();
	}

	@Test
	@DisplayName("私网地址能查到归属地或返回空串，不会抛异常")
	void cityInfoNeverThrows() {
		// 离线库未初始化时返回空串，初始化后返回区域串，两种都算通过
		String info = IpAddressUtils.getCityInfo("127.0.0.1");
		assertThat(info).isNotNull();
	}
}
