package com.coolskyai.util.upload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.coolskyai.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 图片抓取：只放行 image/*，扩展名从 Content-Type 的子类型取。
 * 用 MockRestServiceServer 拦截，不出网。
 */
class UploadUtilsTest {

	private MockRestServiceServer server;
	private RestTemplate restTemplate;

	@BeforeEach
	void setUp() {
		restTemplate = new RestTemplate();
		server = MockRestServiceServer.bindTo(restTemplate).build();
		// UploadUtils 的历史形态是静态字段由容器注入，测试里手动灌入
		new UploadUtils().setRestTemplate(restTemplate);
	}

	@AfterEach
	void tearDown() {
		server.verify();
	}

	@Test
	void getImageByRequest_放行图片并带出扩展名() {
		byte[] bytes = {1, 2, 3, 4};
		server.expect(requestTo("http://cdn.example.com/a.png"))
				.andRespond(withSuccess(bytes, MediaType.IMAGE_PNG));

		UploadUtils.ImageResource image = UploadUtils.getImageByRequest("http://cdn.example.com/a.png");

		assertThat(image.getData()).isEqualTo(bytes);
		assertThat(image.getType()).isEqualTo("png");
	}

	@Test
	void getImageByRequest_非图片类型被拒绝() {
		server.expect(requestTo("http://cdn.example.com/page.html"))
				.andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

		assertThatThrownBy(() -> UploadUtils.getImageByRequest("http://cdn.example.com/page.html"))
				.isInstanceOf(BadRequestException.class)
				.hasMessage("response contentType unlike image");
	}

	@Test
	void getImageByRequest_缺少ContentType时不放行() {
		server.expect(requestTo("http://cdn.example.com/raw"))
				.andRespond(withSuccess(new byte[]{9}, null));

		assertThatThrownBy(() -> UploadUtils.getImageByRequest("http://cdn.example.com/raw"))
				.isInstanceOf(BadRequestException.class);
	}
}
