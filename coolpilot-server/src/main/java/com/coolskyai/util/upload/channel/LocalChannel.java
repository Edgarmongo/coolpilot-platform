package com.coolskyai.util.upload.channel;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.coolskyai.config.properties.BlogProperties;
import com.coolskyai.config.properties.UploadProperties;
import com.coolskyai.util.upload.UploadUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本地磁盘存储
 *
 * 文件名用 UUID，不用原始文件名：原始名可能带路径穿越（`../`）或非 ASCII 字符，
 * 拼进 URL 之后既不好排错也容易出安全问题。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-23
 */
@Lazy
@Component
public class LocalChannel implements FileUploadChannel {

	private final BlogProperties blogProperties;
	private final UploadProperties uploadProperties;

	public LocalChannel(BlogProperties blogProperties, UploadProperties uploadProperties) {
		this.blogProperties = blogProperties;
		this.uploadProperties = uploadProperties;
	}

	/**
	 * 保存到本地目录，返回 `/image/{fileName}` 形式的访问地址
	 */
	@Override
	public String upload(UploadUtils.ImageResource image) throws Exception {
		Path directory = Path.of(uploadProperties.getPath());
		Files.createDirectories(directory);

		String fileName = UUID.randomUUID() + "." + image.getType();
		Files.write(directory.resolve(fileName), image.getData());
		return blogProperties.getApi() + "/image/" + fileName;
	}
}
