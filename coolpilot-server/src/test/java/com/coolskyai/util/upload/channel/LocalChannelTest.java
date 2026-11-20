package com.coolskyai.util.upload.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.config.properties.UploadProperties;
import com.coolskyai.util.upload.UploadUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地存储通道：只验证「文件真的写到磁盘、URL 拼装正确」这两件事。
 * 不启 Spring 容器，直接 new 出来喂配置。
 */
class LocalChannelTest {

	@Test
	void upload_写入文件并返回可访问地址(@TempDir Path tempDir) throws Exception {
		BlogProperties blog = new BlogProperties();
		blog.setApi("https://coolskyai.com/api");
		UploadProperties upload = new UploadProperties();
		// 目录故意带结尾分隔符，验证 resolve 不受影响
		upload.setPath(tempDir.toString() + "/");

		LocalChannel channel = new LocalChannel(blog, upload);
		byte[] data = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
		String url = channel.upload(new UploadUtils.ImageResource(data, "png"));

		assertThat(url).startsWith("https://coolskyai.com/api/image/").endsWith(".png");

		String fileName = url.substring(url.lastIndexOf('/') + 1);
		Path saved = tempDir.resolve(fileName);
		assertThat(saved).exists();
		assertThat(Files.readAllBytes(saved)).isEqualTo(data);
	}

	@Test
	void upload_目录不存在时自动创建(@TempDir Path tempDir) throws Exception {
		Path nested = tempDir.resolve("a/b/c");
		UploadProperties upload = new UploadProperties();
		upload.setPath(nested.toString());
		BlogProperties blog = new BlogProperties();
		blog.setApi("http://localhost:8091");

		String url = new LocalChannel(blog, upload)
				.upload(new UploadUtils.ImageResource(new byte[]{1, 2, 3}, "jpg"));

		assertThat(nested).exists();
		assertThat(url).startsWith("http://localhost:8091/image/");
	}
}
