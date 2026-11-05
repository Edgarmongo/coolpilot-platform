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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 专用重排服务客户端（bge-reranker 一类交叉编码器）
 *
 * 走「查询 + 候选文档 → 相关性分数」这套通用协议，兼容：
 *   Infinity / TEI   POST /rerank  {"query":..,"texts":[..]} 或 {"documents":[..]}
 *   Jina / Cohere    POST /rerank  {"model":..,"query":..,"documents":[..],"top_n":..}
 *
 * 响应结构各家长得不一样（results[].relevance_score / score，或直接一个数组），
 * 这里一并兼容 —— 部署侧只需要改 base-url 与 model，不用改代码。
 *
 * 与 {@link LlmReranker} 的分工：这个准，但要多跑一个模型服务；
 * 那个不用额外部署，慢一点也粗一点。选哪个是部署决策，不是代码决策。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
@ConditionalOnProperty(name = "ai.rerank.provider", havingValue = "http")
public class HttpReranker implements Reranker {

	private static final Logger log = LoggerFactory.getLogger(HttpReranker.class);

	@Value("${ai.rerank.base-url:}")
	private String baseUrl;

	@Value("${ai.rerank.path:/rerank}")
	private String path;

	@Value("${ai.rerank.api-key:}")
	private String apiKey;

	@Value("${ai.rerank.model:bge-reranker-v2-m3}")
	private String model;

	@Value("${ai.rerank.timeout-seconds:10}")
	private int timeoutSeconds;

	/**
	 * 相关性下限：低于它的候选直接丢弃
	 *
	 * 只有专用 rerank 服务给的是「同一问题内的可比分数」，才敢定绝对阈值。
	 * 默认 0 = 不截断 —— bge-reranker 的原生 logits 范围因模型而异，
	 * 随手填一个数就误杀，比不截断更难排查。
	 */
	@Value("${ai.rerank.min-score:0}")
	private double minScore;

	private RestClient restClient;

	@PostConstruct
	void init() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(3000);
		factory.setReadTimeout(timeoutSeconds * 1000);
		this.restClient = RestClient.builder().requestFactory(factory).build();
		if (baseUrl == null || baseUrl.isBlank()) {
			log.warn("重排选择了 http 协议但未配置 ai.rerank.base-url，重排会被跳过");
			return;
		}
		log.info("重排已启用：provider=http model={} baseUrl={}", model, baseUrl);
	}

	@Override
	public String provider() {
		return "http";
	}

	@Override
	public List<Integer> rerank(String query, List<String> documents, int topN) {
		if (documents.size() <= 1) {
			List<Integer> single = new ArrayList<>();
			if (!documents.isEmpty()) {
				single.add(0);
			}
			return single;
		}
		Map<String, Object> body = new LinkedHashMap<>(8);
		body.put("model", model);
		body.put("query", query);
		body.put("documents", documents);
		// TEI 认 texts，其它家认 documents；两个都带上，多余字段会被忽略
		body.put("texts", documents);
		// 先要全量分数，截断由我们自己按 min-score 判断（服务端 top_n 会按它自己的口径截）
		body.put("top_n", documents.size());

		String response;
		try {
			response = restClient.post()
					.uri(trimTrailingSlash(baseUrl) + path)
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
			throw new IllegalStateException("重排请求失败：" + e.getMessage(), e);
		}
		return parse(response, documents.size(), topN);
	}

	@SuppressWarnings("unchecked")
	private List<Integer> parse(String response, int size, int topN) {
		Object parsed = JacksonUtils.readValue(response, Object.class);
		List<Object> rows;
		if (parsed instanceof List<?> list) {
			rows = new ArrayList<>(list);
		} else if (parsed instanceof Map<?, ?> map && map.get("results") instanceof List<?> list) {
			rows = new ArrayList<>(list);
		} else {
			throw new IllegalStateException("重排响应格式无法识别");
		}

		List<int[]> scored = new ArrayList<>(rows.size());
		for (Object row : rows) {
			if (!(row instanceof Map<?, ?> item)) {
				continue;
			}
			Object index = item.get("index");
			Object score = item.get("relevance_score") != null ? item.get("relevance_score") : item.get("score");
			if (index instanceof Number i && score instanceof Number s) {
				scored.add(new int[]{i.intValue(), (int) Math.round(s.doubleValue() * 100000)});
			}
		}
		if (scored.isEmpty()) {
			throw new IllegalStateException("重排响应里没有可用的分数");
		}
		scored.sort(Comparator.comparingInt((int[] row) -> row[1]).reversed());
		int floor = (int) Math.round(minScore * 100000);
		List<Integer> ordered = new ArrayList<>(scored.size());
		for (int[] row : scored) {
			if (row[0] < 0 || row[0] >= size || ordered.contains(row[0])) {
				continue;
			}
			if (minScore > 0 && row[1] < floor) {
				continue;
			}
			ordered.add(row[0]);
		}
		return ordered.size() > topN ? new ArrayList<>(ordered.subList(0, topN)) : ordered;
	}

	private String trimTrailingSlash(String url) {
		return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
