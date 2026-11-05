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
	 * 分数形态：auto / logit / probability
	 *
	 * bge-reranker 这类交叉编码器返回的是 logits（可正可负、范围因模型而异），
	 * Jina / Cohere 返回的已经是 0~1 的相关度。阈值要能通用，先统一到 0~1：
	 * logits 走 sigmoid，概率原样使用。
	 * auto 的判据是「出现负数或大于 1 的值 → 判定为 logits」，实践中足够稳。
	 */
	@Value("${ai.rerank.score-mode:auto}")
	private String scoreMode;

	/**
	 * 相对下限：低于「首位分数 × 该系数」的候选丢弃
	 *
	 * 这是替代「拍脑袋定绝对阈值」的做法 —— 交叉编码器的分数量纲各不相同，
	 * 但「与最高分的比例」是同一套语义。0 表示关闭。
	 * 例：首位 0.9、系数 0.5 → 低于 0.45 的丢掉；全是 0.8~0.9 时会全部保留，
	 * 因为它们同样相关，没有可切的空档。
	 */
	@Value("${ai.rerank.relative-floor:0.5}")
	private double relativeFloor;

	/**
	 * 绝对下限（0~1 空间）：低于它的候选直接丢弃，0 表示不启用
	 *
	 * 需要标定时才用：先用日志观察同一问题的分数分布，再决定切在哪。
	 * 不确定就别填 —— 相对下限已经能处理大多数情况。
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

		List<double[]> raw = new ArrayList<>(rows.size());
		for (Object row : rows) {
			if (!(row instanceof Map<?, ?> item)) {
				continue;
			}
			Object index = item.get("index");
			Object score = item.get("relevance_score") != null ? item.get("relevance_score") : item.get("score");
			if (index instanceof Number i && score instanceof Number sc) {
				raw.add(new double[]{i.intValue(), sc.doubleValue()});
			}
		}
		if (raw.isEmpty()) {
			throw new IllegalStateException("重排响应里没有可用的分数");
		}
		// 先统一到 0~1，阈值才有意义
		boolean logits = isLogits(raw);
		List<double[]> scored = new ArrayList<>(raw.size());
		for (double[] row : raw) {
			scored.add(new double[]{row[0], logits ? sigmoid(row[1]) : clamp(row[1])});
		}
		scored.sort(Comparator.comparingDouble((double[] row) -> row[1]).reversed());

		double top = scored.get(0)[1];
		double floor = Math.max(minScore, relativeFloor > 0 ? top * relativeFloor : 0);
		List<Integer> ordered = new ArrayList<>(scored.size());
		for (double[] row : scored) {
			int index = (int) row[0];
			if (index < 0 || index >= size || ordered.contains(index)) {
				continue;
			}
			if (row[1] < floor) {
				continue;
			}
			ordered.add(index);
		}
		return ordered.size() > topN ? new ArrayList<>(ordered.subList(0, topN)) : ordered;
	}

	/**
	 * 判断返回的是不是 logits
	 *
	 * 显式配置优先；auto 时看是否有负数或超过 1 的值 —— 概率不会出现这两种情况。
	 */
	private boolean isLogits(List<double[]> raw) {
		if ("logit".equalsIgnoreCase(scoreMode)) {
			return true;
		}
		if ("probability".equalsIgnoreCase(scoreMode)) {
			return false;
		}
		for (double[] row : raw) {
			if (row[1] < 0 || row[1] > 1) {
				return true;
			}
		}
		return false;
	}

	private double sigmoid(double value) {
		return 1.0 / (1.0 + Math.exp(-value));
	}

	private double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}

	private String trimTrailingSlash(String url) {
		return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
