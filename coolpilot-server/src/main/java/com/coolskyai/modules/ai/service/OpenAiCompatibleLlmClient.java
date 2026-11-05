package com.coolskyai.modules.ai.service;

import com.coolskyai.util.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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
 * 这样「换模型」的代价就是改几行配置，而不是改代码。
 *
 * 配置一律通过 {@link AiRuntimeConfig} 现取，不在字段上缓存：
 * 后台改完保存要立刻生效，如果这里还是启动时注入的值，就得重启才认。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

	private final AiRuntimeConfig config;

	private final AiUsageService usageService;

	/**
	 * RestClient 按超时时间缓存：超时是连接池级别参数，不能每次调用改，
	 * 但配置可能被改，所以记住当前超时值，变了才重建。
	 */
	private volatile RestClient restClient;

	private volatile int clientTimeoutSeconds;

	public OpenAiCompatibleLlmClient(AiRuntimeConfig config, AiUsageService usageService) {
		this.config = config;
		this.usageService = usageService;
	}

	@Override
	public boolean available() {
		return !config.getRaw(AiRuntimeConfig.LLM_BASE_URL).isBlank()
				&& !config.getRaw(AiRuntimeConfig.LLM_MODEL).isBlank();
	}

	@Override
	public String model() {
		return config.getRaw(AiRuntimeConfig.LLM_MODEL);
	}

	@Override
	public String chat(String scene, String systemPrompt, String userPrompt) {
		if (!available()) {
			throw new LlmException("未配置大模型");
		}

		long start = System.currentTimeMillis();
		Map<String, Object> body = buildBody(systemPrompt, userPrompt);
		body.put("stream", false);
		String endpoint = endpoint();
		try {
			/*
			 * 显式序列化成字符串再发：RestClient 的 body(Object) 对 Map 有歧义，
			 * 实测会被当成 URI 变量处理，最终发出空请求体（服务端收到 Content-Length: 0）。
			 */
			String payload = JacksonUtils.writeValueAsString(body);
			String raw = client().post()
					.uri(endpoint)
					.contentType(MediaType.APPLICATION_JSON)
					.headers(headers -> {
						String key = config.getRaw(AiRuntimeConfig.LLM_API_KEY);
						if (!key.isBlank()) {
							headers.setBearerAuth(key.trim());
						}
					})
					.body(payload)
					.retrieve()
					.body(String.class);

			Map<String, Object> root = parse(raw);
			String content = extractContent(root);
			record(scene, usageOf(root), systemPrompt, userPrompt, content, start, true);
			return content;
		} catch (RestClientResponseException e) {
			String detail = e.getResponseBodyAsString();
			if (detail != null && detail.length() > 200) {
				detail = detail.substring(0, 200) + "…";
			}
			// 401 多半是 Key 错了，404 多半是 base-url 少了 /v1，把线索写进日志省一次排查
			log.warn("调用大模型失败 status={} model={} endpoint={} body={}",
					e.getStatusCode().value(), model(), endpoint, detail);
			record(scene, null, systemPrompt, userPrompt, null, start, false);
			throw new LlmException("大模型返回错误：" + e.getStatusCode().value()
					+ (detail == null || detail.isEmpty() ? "" : "，" + detail), e);
		} catch (ResourceAccessException e) {
			log.warn("连接大模型超时或不可达 endpoint={}: {}", endpoint, e.getMessage());
			record(scene, null, systemPrompt, userPrompt, null, start, false);
			throw new LlmException("连接大模型超时，请稍后再试", e);
		} catch (LlmException e) {
			record(scene, null, systemPrompt, userPrompt, null, start, false);
			throw e;
		} catch (Exception e) {
			log.warn("调用大模型异常 model={}: {}", model(), e.getMessage());
			record(scene, null, systemPrompt, userPrompt, null, start, false);
			throw new LlmException("调用大模型失败：" + e.getMessage(), e);
		}
	}

	@Override
	public void stream(String scene, String systemPrompt, String userPrompt, Consumer<String> onDelta) {
		if (!available()) {
			throw new LlmException("未配置大模型");
		}
		long start = System.currentTimeMillis();
		Map<String, Object> body = buildBody(systemPrompt, userPrompt);
		body.put("stream", true);
		/*
		 * 让服务在最后一条 chunk 里带上 usage。OpenAI / DeepSeek / Ollama / vLLM
		 * 都支持这个参数，不支持的会忽略而不是报错；真拿不到就按字符估算，
		 * 统计页会标出「估算」。
		 */
		body.put("stream_options", Map.of("include_usage", true));
		String endpoint = endpoint();
		String payload = JacksonUtils.writeValueAsString(body);
		StringBuilder answer = new StringBuilder();

		try {
			int[] usage = client().post()
					.uri(endpoint)
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.headers(headers -> {
						String key = config.getRaw(AiRuntimeConfig.LLM_API_KEY);
						if (!key.isBlank()) {
							headers.setBearerAuth(key.trim());
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
						return readStream(response.getBody(), delta -> {
							answer.append(delta);
							onDelta.accept(delta);
						});
					});
			record(scene, usage, systemPrompt, userPrompt, answer.toString(), start, true);
		} catch (LlmException e) {
			record(scene, null, systemPrompt, userPrompt, answer.toString(), start, false);
			throw e;
		} catch (StreamCancelledException e) {
			// 上层要求停止（客户端断开）：已经产出的部分照常记账，别当成失败
			record(scene, null, systemPrompt, userPrompt, answer.toString(), start, true);
			throw e;
		} catch (ResourceAccessException e) {
			log.warn("连接大模型超时或不可达 endpoint={}: {}", endpoint, e.getMessage());
			record(scene, null, systemPrompt, userPrompt, answer.toString(), start, false);
			throw new LlmException("连接大模型超时，请稍后再试", e);
		} catch (Exception e) {
			log.warn("流式调用大模型异常 model={}: {}", model(), e.getMessage());
			record(scene, null, systemPrompt, userPrompt, answer.toString(), start, false);
			throw new LlmException("调用大模型失败：" + e.getMessage(), e);
		}
	}

	/**
	 * 逐行读 SSE：只关心 data: 行，忽略 event:/id:/空行与 [DONE]
	 *
	 * @return 最后一条带 usage 的 chunk 里的 {prompt, completion}；没有则 null
	 */
	private int[] readStream(java.io.InputStream stream, Consumer<String> onDelta) throws IOException {
		int[] usage = null;
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
				Map<String, Object> root = JacksonUtils.readValue(data, Map.class);
				if (root == null) {
					continue;
				}
				int[] current = usageOf(root);
				if (current != null) {
					usage = current;
				}
				String delta = extractDelta(root);
				if (delta != null && !delta.isEmpty()) {
					onDelta.accept(delta);
				}
			}
		}
		return usage;
	}

	private String extractDelta(Map<String, Object> root) {
		Object choices = root.get("choices");
		if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
			return null;
		}
		Object first = ((List<?>) choices).get(0);
		Object delta = first instanceof Map ? ((Map<?, ?>) first).get("delta") : null;
		Object content = delta instanceof Map ? ((Map<?, ?>) delta).get("content") : null;
		return content == null ? null : String.valueOf(content);
	}

	/**
	 * 从响应里取 usage；没有就返回 null（调用方按字符估算并标 estimated）
	 */
	private int[] usageOf(Map<String, Object> root) {
		if (root == null) {
			return null;
		}
		Object usage = root.get("usage");
		if (!(usage instanceof Map)) {
			return null;
		}
		Map<?, ?> map = (Map<?, ?>) usage;
		Object prompt = map.get("prompt_tokens");
		Object completion = map.get("completion_tokens");
		if (!(prompt instanceof Number) && !(completion instanceof Number)) {
			return null;
		}
		return new int[]{intOf(prompt), intOf(completion)};
	}

	private int intOf(Object value) {
		return value instanceof Number ? ((Number) value).intValue() : 0;
	}

	/**
	 * 记账。统计失败不能影响对话，AiUsageService 内部已经吞掉异常。
	 */
	private void record(String scene, int[] usage, String systemPrompt, String userPrompt,
						String answer, long start, boolean success) {
		boolean estimated = usage == null;
		int promptTokens = estimated
				? AiUsageService.estimateTokens((systemPrompt == null ? "" : systemPrompt)
				+ (userPrompt == null ? "" : userPrompt))
				: usage[0];
		int completionTokens = estimated ? AiUsageService.estimateTokens(answer) : usage[1];
		usageService.record(scene, model(), promptTokens, completionTokens,
				System.currentTimeMillis() - start, success, estimated, null);
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
		body.put("model", model());
		body.put("messages", messages);
		body.put("temperature", config.getDouble(AiRuntimeConfig.LLM_TEMPERATURE, 0.3));
		body.put("max_tokens", config.getInt(AiRuntimeConfig.LLM_MAX_TOKENS, 800));
		mergeExtraBody(body);
		return body;
	}

	/**
	 * 合并 extra-body，不动核心字段
	 */
	private void mergeExtraBody(Map<String, Object> body) {
		String extraBody = config.getRaw(AiRuntimeConfig.LLM_EXTRA_BODY);
		if (extraBody.isBlank()) {
			return;
		}
		try {
			Map<String, Object> extra = JacksonUtils.readValue(extraBody, Map.class);
			if (extra != null) {
				// putIfAbsent：厂商参数不能盖掉 model / messages / stream
				extra.forEach(body::putIfAbsent);
			}
		} catch (Exception e) {
			log.warn("ai.llm.extra-body 不是合法 JSON，已忽略：{}", e.getMessage());
		}
	}

	private String endpoint() {
		return config.getRaw(AiRuntimeConfig.LLM_BASE_URL).replaceAll("/+$", "") + "/chat/completions";
	}

	private Map<String, Object> parse(String raw) {
		if (raw == null || raw.isEmpty()) {
			throw new LlmException("大模型返回为空");
		}
		// 用 Map 解析而不是专门的 DTO：不同厂商会在 choices 里塞各自字段，
		// 只取标准 OpenAI 结构里必需的那几层，其余忽略。
		Map<String, Object> root = JacksonUtils.readValue(raw, Map.class);
		if (root == null) {
			throw new LlmException("解析大模型返回失败");
		}
		return root;
	}

	private String extractContent(Map<String, Object> root) {
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

	/**
	 * 按当前超时配置取客户端；配置改了会重建一次，之后复用
	 */
	private RestClient client() {
		int timeout = config.getInt(AiRuntimeConfig.LLM_TIMEOUT, 60);
		RestClient current = restClient;
		if (current != null && clientTimeoutSeconds == timeout) {
			return current;
		}
		synchronized (this) {
			if (restClient == null || clientTimeoutSeconds != timeout) {
				SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
				// 连接超时短一些：模型服务没起来时不该让用户等满整轮对话
				factory.setConnectTimeout(Math.min(10, Math.max(3, timeout / 6)) * 1000);
				factory.setReadTimeout(timeout * 1000);
				restClient = RestClient.builder().requestFactory(factory).build();
				clientTimeoutSeconds = timeout;
				log.info("大模型客户端已就绪：model={} timeout={}s", model(), timeout);
			}
			return restClient;
		}
	}
}
