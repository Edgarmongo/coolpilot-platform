package com.coolskyai.util.upload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.coolskyai.constant.UploadConstants;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.util.upload.channel.ChannelFactory;
import com.coolskyai.util.upload.channel.FileUploadChannel;

/**
 * @Description: 图片抓取与落盘
 *
 * 用静态方法是被历史接口逼的：调用方（Markdown 渲染、评论头像下载）是非 Spring 组件，
 * 所以这里退化成「由容器把依赖灌进静态字段」。灌入发生在启动阶段，
 * 靠 @DependsOn 保证 SpringContextUtils 先就绪（ChannelFactory 要静态取 Bean）。
 *
 * @Author: CoolSkyAI
 * @Date: 2021-11-11
 */
@Component
@DependsOn("springContextUtils")
public class UploadUtils {

	private static RestTemplate restTemplate;

	private static FileUploadChannel channel;

	@Autowired
	public void setRestTemplate(RestTemplate restTemplate) {
		UploadUtils.restTemplate = restTemplate;
	}

	@Value("${upload.channel}")
	public void setUploadChannel(String channelName) {
		UploadUtils.channel = ChannelFactory.getChannel(channelName);
	}

	@AllArgsConstructor
	@Getter
	public static class ImageResource {
		byte[] data;
		/** 图片扩展名，如 jpg / png */
		String type;
	}

	/**
	 * 按配置的方式存储图片
	 *
	 * @param image 图片数据与扩展名
	 * @return 可访问的图片 URL
	 */
	public static String upload(ImageResource image) throws Exception {
		if (channel == null) {
			throw new IllegalStateException("上传通道未初始化，检查 upload.channel 配置");
		}
		return channel.upload(image);
	}

	/**
	 * 从网络抓取图片。只接受 image/*，其它类型直接拒绝 ——
	 * 这个入口会被 Markdown 里的外链调用，不能变成任意 URL 下载器。
	 */
	public static ImageResource getImageByRequest(String url) {
		ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
		MediaType contentType = response.getHeaders().getContentType();
		if (contentType == null || !UploadConstants.IMAGE.equalsIgnoreCase(contentType.getType())) {
			throw new BadRequestException("response contentType unlike image");
		}
		return new ImageResource(response.getBody(), contentType.getSubtype());
	}
}
