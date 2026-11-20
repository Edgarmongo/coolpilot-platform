package com.coolskyai.util.upload.channel;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.coolskyai.config.properties.GithubProperties;
import com.coolskyai.util.upload.UploadUtils;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GitHub 仓库 + jsDelivr CDN 的图片存储
 *
 * 落盘在仓库里、由 CDN 回源，好处是不用维护对象存储；代价是每次上传都是一次
 * commit，仓库体积会一直长。适合图片量小的站点，不适合当主存储长期用。
 *
 * 上传走 Contents API：PUT /repos/{owner}/{repo}/contents/{path}/{file}，
 * 内容用 Base64；返回的地址拼 jsDelivr，而不是 raw.githubusercontent（后者没缓存且有速率限制）。
 *
 * @author: CoolSkyAI
 * @date: 2022-01-23
 */
@Lazy
@Component
public class GithubChannel implements FileUploadChannel {

	/** GitHub 内容接口，参数顺序：owner / repo / 仓库内目录 / 文件名 */
	private static final String CONTENT_API = "https://api.github.com/repos/%s/%s/contents%s/%s";

	/** jsDelivr 回源地址，参数顺序：owner / repo / 仓库内目录 / 文件名 */
	private static final String CDN_API = "https://fastly.jsdelivr.net/gh/%s/%s%s/%s";

	private static final String COMMIT_MESSAGE = "Add files via CoolSkyAI";

	private final GithubProperties githubProperties;
	private final RestTemplate restTemplate;

	public GithubChannel(GithubProperties githubProperties, RestTemplate restTemplate) {
		this.githubProperties = githubProperties;
		this.restTemplate = restTemplate;
	}

	/**
	 * 上传到仓库并返回 CDN 链接
	 */
	@Override
	public String upload(UploadUtils.ImageResource image) {
		if (isBlank(githubProperties.getToken())) {
			throw new IllegalStateException("GitHub 上传通道未配置 token（upload.github.token）");
		}
		String fileName = UUID.randomUUID() + "." + image.getType();

		Map<String, String> body = new LinkedHashMap<>(4);
		body.put("message", COMMIT_MESSAGE);
		body.put("content", Base64.getEncoder().encodeToString(image.getData()));

		restTemplate.put(String.format(CONTENT_API,
						githubProperties.getUsername(),
						githubProperties.getRepos(),
						githubProperties.getReposPath(),
						fileName),
				new HttpEntity<>(body, authHeaders()));

		return String.format(CDN_API,
				githubProperties.getUsername(),
				githubProperties.getRepos(),
				githubProperties.getReposPath(),
				fileName);
	}

	private HttpHeaders authHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		// GitHub 的 token 认证前缀是 "token "，不是 Bearer
		headers.set("Authorization", "token " + githubProperties.getToken());
		return headers;
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}
}
