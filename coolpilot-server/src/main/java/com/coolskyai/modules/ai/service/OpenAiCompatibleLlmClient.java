package com.coolskyai.modules.ai.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.coolskyai.util.JacksonUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @Description: OpenAI 兼容协议的大模型客户端
 *
 * 一个实现覆盖两类部署，靠配置切换：
 *   本地   Ollama / vLLM / LM Studio → base-url 形如 http://127.0.0.1:11434/v1，通常不需要 Key
 *   第三方 DeepSeek / 通义千问兼容模式 / 其它云厂商 → base-url 形如 https://api.deepseek.com/v1
 *
 * 只用 /chat/completions 一个端点，不做厂商特有能力，
 * 这样「换模型」的代价就是改三行配置，而不是改代码。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

	@Value("${ai.llm.base-url:}")
	private String baseUrl;

	@Value("${ai.llm.api-key:}")
	private String apiKey;

	@Value("${ai.llm.model:}")
	private String model;

	@Value("${ai.llm.timeout-seconds:60}")
	private int timeoutSeconds;

	@Value("${ai.llm.max-tokens:800}")
	private int maxTokens;

	@Value("${ai.llm.temperature:0.3}")
	private double temperature;

	private RestClient restClient;

	@PostConstruct
	void init() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		// 连接超时短一些：模型服务没起来时不该让用户等满整轮对话
		factory.setConnectTimeout(Math.min(10, Math.max(3, timeoutSeconds / 6)) * 1000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		this.restClient = RestClient.builder().requestFactory(factory).build();
		if (available()) {
			log.info("大模型已配置：model={} baseUrl={}", model, baseUrl);
		} else {
			log.info("大模型未配置（ai.llm.base-url / ai.llm.model 为空），问答将降级为仅返回检索片段");
		}
	}

	@Override
	public boolean available() {
		return notBlank(baseUrl) && notBlank(model);
	}

	@Override
	public String model() {
		return notBlank(model) ? model : "";
	}

	@Override
	public String chat(String systemPrompt, String userPrompt) {
		if (!available()) {
			throw new LlmException("未配置大模型");
		}

		Map<String, Object> body = buildBody(systemPrompt, userPrompt);
		body.put("stream", false);
		String endpoint = endpoint();
		try {
			/*
			 * 显式序列化成字符串再发：RestClient 的 body(Object) 对 Map 有歧义，
			 * 实测会被当成 URI 变量处理，最终发出空请求体（服务端收到 Content-Length: 0）。
			 */
			String payload = JacksonUtils.writeValueAsString(body);
			String raw = restClient.post()
					.uri(endpoint)
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						if (notBlank(apiKey)) {
							headers.setBearerAuth(apiKey.trim());
						}
					})
					.body(payload)
					.retrieve()
					.body(String.class);
			return extractContent(raw);
		} catch (RestClientResponseException e) {
			String detail = e.getResponseBodyAsString();
			if (detail != null && detail.length() > 200) {
				detail = detail.substring(0, 200) + "…";
			}
			// 401 多半是 Key 错了，404 多半是 base-url 少了 /v1，把线索写进日志省一次排查
			log.warn("调用大模型失败 status={} model={} endpoint={} body={}",
					e.getStatusCode().value(), model, endpoint, detail);
			throw new LlmException("大模型返回错误：" + e.getStatusCode().value()
					+ (detail == null || detail.isEmpty() ? "" : "，" + detail), e);
		} catch (ResourceAccessException e) {
			log.warn("连接大模型超时或不可达 endpoint={}: {}", endpoint, e.getMessage());
			throw new LlmException("连接大模型超时，请稍后再试", e);
		} catch (LlmException e) {
			throw e;
		} catch (Exception e) {
			log.warn("调用大模型异常 model={}: {}", model, e.getMessage());
			throw new LlmException("调用大模型失败：" + e.getMessage(), e);
		}
	}

	@Override
	public void stream(String systemPrompt, String userPrompt, Consumer<String> onDelta) {
		if (!available()) {
			throw new LlmException("未配置大模型");
		}
		Map<String, Object> body = buildBody(systemPrompt, userPrompt);
		body.put("stream", true);
		String endpoint = endpoint();
		String payload = JacksonUtils.writeValueAsString(body);

		try {
			restClient.post()
					.uri(endpoint)
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> {
						if (notBlank(apiKey)) {
							headers.setBearerAuth(apiKey.trim());
						}
					})
					.body(payload)
					.exchange((request, response) -> {
						int status = response.getStatusCode().value();
						if (status != 200) {
							String detail = readAll(response.getBody());
							throw new LlmException("大模型返回错误：" + status
									+ (detail.isEmpty() ? "" : "，" + truncate(detail)));
						}
						readStream(response.getBody(), onDelta);
						return null;
					});
		} catch (LlmException e) {
			throw e;
		} catch (ResourceAccessException e) {
			log.warn("连接大模型超时或不可达 endpoint={}: {}", endpoint, e.getMessage());
			throw new LlmException("连接大模型超时，请稍后再试", e);
		} catch (Exception e) {
			log.warn("流式调用大模型异常 model={}: {}", model, e.getMessage());
			throw new LlmException("调用大模型失败：" + e.getMessage(), e);
		}
	}

	/**
	 * 逐行读 SSE：只关心 data: 行，忽略 event:/id:/空行与 [DONE]
	 */
	private void readStream(java.io.InputStream stream, Consumer<String> onDelta) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.startsWith("data:")) {
					continue;
				}
				String data = line.substring(5).trim();
				if (data.isEmpty() || "[DONE]".equals(data)) {
					continue;
				}
				String delta = extractDelta(data);
				if (delta != null && !delta.isEmpty()) {
					onDelta.accept(delta);
				}
			}
		}
	}

	private String extractDelta(String data) {
		Map<String, Object> root = JacksonUtils.readValue(data, Map.class);
		if (root == null) {
			return null;
		}
		Object choices = root.get("choices");
		if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
			return null;
		}
		Object first = ((List<?>) choices).get(0);
		Object delta = first instanceof Map ? ((Map<?, ?>) first).get("delta") : null;
		Object content = delta instanceof Map ? ((Map<?, ?>) delta).get("content") : null;
		return content == null ? null : String.valueOf(content);
	}

	private String readAll(java.io.InputStream stream) {
		try (java.io.InputStream in = stream) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			return "";
		}
	}

	private String truncate(String text) {
		return text.length() <= 200 ? text : text.substring(0, 200) + "…";
	}

	/**
	 * 请求体：单轮对话的公共部分
	 */
	private Map<String, Object> buildBody(String systemPrompt, String userPrompt) {
		List<Map<String, String>> messages = new ArrayList<>(2);
		messages.add(Map.of("role", "system", "content", systemPrompt == null ? "" : systemPrompt));
		messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));

		Map<String, Object> body = new LinkedHashMap<>(8);
		body.put("model", model);
		body.put("messages", messages);
		body.put("temperature", temperature);
		body.put("max_tokens", maxTokens);
		return body;
	}

	private String endpoint() {
		return baseUrl.replaceAll("/+$", "") + "/chat/completions";
	}

	private String extractContent(String raw) {
		if (raw == null || raw.isEmpty()) {
			throw new LlmException("大模型返回为空");
		}
		// 用 Map 解析而不是专门的 DTO：不同厂商会在 choices 里塞各自字段，
		// 只取标准 OpenAI 结构里必需的那几层，其余忽略。
		Map<String, Object> root = JacksonUtils.readValue(raw, Map.class);
		if (root == null) {
			throw new LlmException("解析大模型返回失败");
		}
		Object choices = root.get("choices");
		if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
			throw new LlmException("大模型返回格式异常：缺少 choices");
		}
		Object first = ((List<?>) choices).get(0);
		Object message = first instanceof Map ? ((Map<?, ?>) first).get("message") : null;
		Object content = message instanceof Map ? ((Map<?, ?>) message).get("content") : null;
		String text = content == null ? "" : String.valueOf(content).trim();
		if (text.isEmpty()) {
			throw new LlmException("大模型没有返回内容");
		}
		return text;
	}

	private boolean notBlank(String value) {
		return value != null && !value.trim().isEmpty();
	}
}
