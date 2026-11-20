package com.coolskyai.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * QQ 号格式与昵称解析。
 *
 * 昵称接口是第三方服务，随时可能变；这里的断言只锁两件事：
 * 请求里确实带上了 QQ 号，以及返回结构异常时回落到 "nickname"。
 */
class QQInfoUtilsTest {

	private MockRestServiceServer server;

	private void mockServer() {
		RestTemplate restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		QQInfoUtils.setRestTemplate(restTemplate);
	}

	@AfterEach
	void tearDown() {
		QQInfoUtils.setRestTemplate(new RestTemplate());
	}

	@Test
	void isQQNumber_只认5到11位且首位非0() {
		assertThat(QQInfoUtils.isQQNumber("10001")).isTrue();
		assertThat(QQInfoUtils.isQQNumber("12345678901")).isTrue();
		assertThat(QQInfoUtils.isQQNumber("1234")).isFalse();
		assertThat(QQInfoUtils.isQQNumber("02345")).isFalse();
		assertThat(QQInfoUtils.isQQNumber("123456789012")).isFalse();
		assertThat(QQInfoUtils.isQQNumber("1000a")).isFalse();
		assertThat(QQInfoUtils.isQQNumber(null)).isFalse();
	}

	@Test
	void getQQNickname_解析正常返回() {
		mockServer();
		server.expect(requestTo("https://api.toubiec.cn/api/qqinfo_v4.php?qq=10001"))
				.andRespond(withSuccess("{\"success\":\"1\",\"data\":{\"qq\":10001,\"name\":\"小明\"}}",
						MediaType.APPLICATION_JSON));

		assertThat(QQInfoUtils.getQQNickname("10001")).isEqualTo("小明");
		server.verify();
	}

	@Test
	void getQQNickname_缺少数据时回落() {
		mockServer();
		server.expect(requestTo("https://api.toubiec.cn/api/qqinfo_v4.php?qq=10002"))
				.andRespond(withSuccess("{\"success\":\"0\",\"msg\":\"not found\"}", MediaType.APPLICATION_JSON));

		assertThat(QQInfoUtils.getQQNickname("10002")).isEqualTo("nickname");
	}
}
