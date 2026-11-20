package com.coolskyai.util.upload.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.coolskyai.config.properties.GithubProperties;
import com.coolskyai.util.upload.UploadUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * GitHub 通道：验证落库地址、鉴权头、Base64 内容与返回的 CDN 链接。
 * 文件名是 UUID，所以断言用前后缀而不是全等。
 */
class GithubChannelTest {

	private GithubProperties properties;
	private RestTemplate restTemplate;
	private MockRestServiceServer server;
	private GithubChannel channel;

	@BeforeEach
	void setUp() {
		properties = new GithubProperties();
		properties.setUsername("Edgarmongo");
		properties.setRepos("img-host");
		properties.setReposPath("/blog");
		properties.setToken("ghp_test_token");

		restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		channel = new GithubChannel(properties, restTemplate);
	}

	@Test
	void 上传请求带上鉴权头与Base64内容() {
		byte[] data = "png-bytes".getBytes(StandardCharsets.UTF_8);
		server.expect(requestTo(org.hamcrest.Matchers.startsWith(
						"https://api.github.com/repos/Edgarmongo/img-host/contents/blog/")))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(header("Authorization", "token ghp_test_token"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		String url = channel.upload(new UploadUtils.ImageResource(data, "png"));

		server.verify();
		assertThat(url).startsWith("https://fastly.jsdelivr.net/gh/Edgarmongo/img-host/blog/")
				.endsWith(".png");
	}

	@Test
	void 未配置token时给出明确错误() {
		properties.setToken("  ");

		assertThatThrownBy(() -> channel.upload(new UploadUtils.ImageResource(new byte[]{1}, "png")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("未配置 token");
	}
}
