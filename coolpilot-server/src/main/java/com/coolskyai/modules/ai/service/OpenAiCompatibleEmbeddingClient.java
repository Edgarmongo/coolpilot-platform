package com.coolskyai.modules.ai.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.coolskyai.util.JacksonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @Description: OpenAI 兼容协议的 embedding 客户端（语义向量）
 *
 * 与 LLM 客户端同一套思路：只依赖 /embeddings 一个端点，
 * Ollama、vLLM、DeepSeek、通义、OpenAI 都是改配置而不是改代码。
 *
 *   Ollama 本地   ai.embedding.base-url=http://host.docker.internal:11434/v1  model=bge-m3  dim=1024
 *   第三方        ai.embedding.base-url=https://api.xxx.com/v1  api-key=sk-xxx  model=...  dim=...
 *
 * 三个容易踩的点：
 *   1. **维度必须显式配置**。索引侧靠 dim 判断「这块向量是不是当前模型生成的」，
 *      配错会让检索静默返回空结果，所以启动时就把配置打出来。
 *   2. 返回顺序**不保证**与输入一致，按 index 排序后再取（OpenAI 兼容实现普遍如此）。
 *   3. 空文本不发给模型：部分服务会直接 400，索引里的空块本来就该跳过。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
@ConditionalOnProperty(name = "ai.embedding.provider", havingValue = "openai")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);

	@Value("${ai.embedding.base-url:}")
	private String baseUrl;

	@Value("${ai.embedding.api-key:}")
	private String apiKey;

	@Value("${ai.embedding.model:}")
	private String model;

	@Value("${ai.embedding.dim:1024}")
	private int dim;

	@Value("${ai.embedding.timeout-seconds:30}")
	private int timeoutSeconds;

	private RestClient restClient;

	@PostConstruct
	void init() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Math.min(10, Math.max(3, timeoutSeconds / 5)) * 1000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		this.restClient = RestClient.builder().requestFactory(factory).build();
		if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
			log.warn("embedding 选择了 openai 协议但未配置 base-url / model，检索会持续失败");
			return;
		}
		log.info("embedding 已启用远程模型：provider=openai model={} dim={} baseUrl={}", model, dim, baseUrl);
	}

	@Override
	public String provider() {
		return "openai";
	}

	@Override
	public int dim() {
		return dim;
	}

	@Override
	public String model() {
		return model == null ? "" : model;
	}

	@Override
	public float[] embed(String text) {
		return embedBatch(List.of(text == null ? "" : text)).get(0);
	}

	/**
	 * 一次请求向量化多条：重建索引时能把「每块一次 HTTP」压到「每批一次」
	 */
	@Override
	public List<float[]> embedBatch(List<String> texts) {
		List<float[]> vectors = new ArrayList<>(texts.size());
		if (texts.isEmpty()) {
			return vectors;
		}
		Map<String, Object> body = Map.of("model", model(), "input", texts);
		String response;
		try {
			response = restClient.post()
					.uri(trimTrailingSlash(baseUrl) + "/embeddings")
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						if (apiKey != null && !apiKey.isBlank()) {
							headers.setBearerAuth(apiKey);
						}
					})
					.body(JacksonUtils.writeValueAsString(body))
					.retrieve()
					.body(String.class);
		} catch (Exception e) {
			throw new IllegalStateException("embedding 请求失败：" + e.getMessage(), e);
		}

		Map<String, Object> parsed = JacksonUtils.readValue(response, Map.class);
		List<Map<String, Object>> data = parsed == null ? List.of() : (List<Map<String, Object>>) parsed.get("data");
		if (data == null || data.size() != texts.size()) {
			throw new IllegalStateException("embedding 返回条数与输入不一致：" + (data == null ? 0 : data.size())
					+ " / " + texts.size());
		}
		// 顺序不保证一致，按 index 归位
		List<Map<String, Object>> ordered = new ArrayList<>(data);
		ordered.sort(Comparator.comparingInt(item -> ((Number) item.getOrDefault("index", 0)).intValue()));
		for (Map<String, Object> item : ordered) {
			Object raw = item.get("embedding");
			if (!(raw instanceof List<?> values)) {
				throw new IllegalStateException("embedding 返回格式异常");
			}
			if (values.size() != dim) {
				throw new IllegalStateException("embedding 维度不匹配：模型返回 " + values.size()
						+ "，配置 ai.embedding.dim=" + dim);
			}
			float[] vector = new float[dim];
			for (int i = 0; i < dim; i++) {
				vector[i] = ((Number) values.get(i)).floatValue();
			}
			vectors.add(normalize(vector));
		}
		return vectors;
	}

	private float[] normalize(float[] vector) {
		double sum = 0;
		for (float value : vector) {
			sum += value * value;
		}
		if (sum == 0) {
			return vector;
		}
		float norm = (float) Math.sqrt(sum);
		for (int i = 0; i < vector.length; i++) {
			vector[i] /= norm;
		}
		return vector;
	}

	private String trimTrailingSlash(String url) {
		return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
