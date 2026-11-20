package com.coolskyai.util.upload.channel;

import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.coolskyai.config.properties.UpyunProperties;
import com.coolskyai.util.upload.UploadUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 又拍云通道：把真正发请求的那一步换成假实现，验证路径拼接、返回地址与失败分支。
 */
class UpyunChannelTest {

	/** 用假 write 顶替 RestManager，避免测试依赖真实又拍云账号 */
	static class FakeChannel extends UpyunChannel {
		String writtenPath;
		byte[] writtenData;
		Response response;

		FakeChannel(UpyunProperties properties, Response response) {
			super(properties);
			this.response = response;
		}

		@Override
		Response write(String remotePath, byte[] data) {
			this.writtenPath = remotePath;
			this.writtenData = data;
			return response;
		}
	}

	private UpyunProperties properties() {
		UpyunProperties properties = new UpyunProperties();
		properties.setBucketName("bucket");
		properties.setUsername("user");
		properties.setPassword("pwd");
		properties.setPath("/blog");
		properties.setDomain("https://cdn.example.com");
		return properties;
	}

	@Test
	void 上传成功时返回域名加对象路径() throws Exception {
		Response response = Mockito.mock(Response.class);
		when(response.isSuccessful()).thenReturn(true);

		byte[] data = new byte[]{1, 2, 3};
		FakeChannel channel = new FakeChannel(properties(), response);
		String url = channel.upload(new UploadUtils.ImageResource(data, "jpg"));

		assertThat(channel.writtenPath).startsWith("/blog/").endsWith(".jpg");
		assertThat(channel.writtenData).isEqualTo(data);
		assertThat(url).isEqualTo("https://cdn.example.com" + channel.writtenPath);
	}

	@Test
	void 上传失败时抛异常() {
		Response response = Mockito.mock(Response.class);
		when(response.isSuccessful()).thenReturn(false);

		FakeChannel channel = new FakeChannel(properties(), response);

		assertThatThrownBy(() -> channel.upload(new UploadUtils.ImageResource(new byte[]{1}, "png")))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("又拍云上传失败");
	}
}
