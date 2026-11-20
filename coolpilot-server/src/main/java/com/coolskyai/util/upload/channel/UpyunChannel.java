package com.coolskyai.util.upload.channel;

import com.upyun.RestManager;
import okhttp3.Response;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import com.coolskyai.config.properties.UpyunProperties;
import com.coolskyai.util.upload.UploadUtils;

import java.util.UUID;

/**
 * 又拍云对象存储
 *
 * RestManager 是有状态的（内部持有 okhttp 客户端），所以做成实例字段、
 * 与通道同生命周期，不要每次上传都新建 —— 新建等于每次泄漏一个连接池。
 *
 * Response 必须关：okhttp 的响应体不关会把连接挂在连接池里，传几次之后
 * 线程就卡在等空闲连接上，表现是「上传越来越慢，最后不动了」。
 *
 * @author: CoolSkyAI
 * @date: 2022-05-26
 */
@Lazy
@Component
public class UpyunChannel implements FileUploadChannel {

	private final UpyunProperties upyunProperties;
	private final RestManager manager;

	public UpyunChannel(UpyunProperties upyunProperties) {
		this.upyunProperties = upyunProperties;
		this.manager = new RestManager(upyunProperties.getBucketName(),
				upyunProperties.getUsername(),
				upyunProperties.getPassword());
	}

	@Override
	public String upload(UploadUtils.ImageResource image) throws Exception {
		String remotePath = buildRemotePath(image.getType());
		try (Response response = write(remotePath, image.getData())) {
			if (!response.isSuccessful()) {
				throw new RuntimeException("又拍云上传失败");
			}
		}
		return upyunProperties.getDomain() + remotePath;
	}

	/**
	 * 对象路径：配置的目录 + 随机文件名。随机名是为了避开中文、空格与同名覆盖
	 */
	String buildRemotePath(String fileType) {
		return upyunProperties.getPath() + "/" + UUID.randomUUID() + "." + fileType;
	}

	/**
	 * 真正落盘的一步，单独拎出来便于测试时替换（不打真实网络）
	 */
	Response write(String remotePath, byte[] data) throws Exception {
		return manager.writeFile(remotePath, data, null);
	}
}
