package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.coolskyai.util.JacksonUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @Description: 用对话模型做重排（不需要额外部署 rerank 模型）
 *
 * 做法是「列表排序」：把问题与候选编号一次发给模型，让它按相关性输出编号序列。
 * 比逐条打分省调用次数，也比「模型判 yes/no」稳定 —— 排序是相对判断，
 * 模型对相对关系的判断远比绝对分数可靠。
 *
 * 三个取舍：
 *   1. 候选只发标题 + 摘要（约 160 字）：全文进 prompt 既贵又没必要，
 *      重排看的是「这段讲的是不是这件事」。
 *   2. 输出解析失败就抛异常，由 {@link RerankService} 回退到原顺序 ——
 *      排序错乱比不排序危害更大。
 *   3. 模型没有列出的编号视为「不相关」：这是截断的依据，
 *      但数量下限由 RerankService 兜住，不会把上下文削成空。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Component
@ConditionalOnProperty(name = "ai.rerank.provider", havingValue = "llm")
public class LlmReranker implements Reranker {

	private static final Logger log = LoggerFactory.getLogger(LlmReranker.class);

	private static final String SYSTEM_PROMPT = """
			你是检索结果排序器。给定一个问题与若干候选片段，按与问题的相关性从高到低排序。
			只输出与问题直接相关的候选编号，无关的不要输出；如果全都不相关，输出 []。
			格式：JSON 数组，元素是候选编号（整数），例如 [3,1,2]。不输出解释、不输出其它文字。
			""";

	/** 每个候选送进去的正文长度：够判断「讲的是不是这件事」就够了 */
	@Value("${ai.rerank.excerpt-length:160}")
	private int excerptLength;

	@Autowired
	private LlmClient llmClient;

	@Override
	public String provider() {
		return "llm";
	}

	@Override
	public List<Integer> rerank(String query, List<String> documents, int topN) {
		if (documents.size() <= 1) {
			return indexes(documents.size(), topN);
		}
		String answer = llmClient.chat(SYSTEM_PROMPT, buildPrompt(query, documents));
		List<Integer> ordered = parse(answer, documents.size());
		if (ordered == null) {
			// 解析不出来才当失败：让调用方回退原顺序，而不是把候选全丢掉
			throw new IllegalStateException("重排结果无法解析：" + brief(answer));
		}
		return ordered.size() > topN ? new ArrayList<>(ordered.subList(0, topN)) : ordered;
	}

	private String buildPrompt(String query, List<String> documents) {
		StringBuilder sb = new StringBuilder(1024);
		sb.append("问题：").append(query).append("\n\n候选：\n");
		for (int i = 0; i < documents.size(); i++) {
			String text = documents.get(i);
			String excerpt = text.length() <= excerptLength ? text : text.substring(0, excerptLength) + "…";
			sb.append(i + 1).append(". ").append(excerpt.replace('\n', ' ')).append('\n');
		}
		return sb.toString();
	}

	/**
	 * 解析编号数组：容忍 ```json 包裹、中文字符、多余空格
	 * 注意**不补全**被模型省略的编号：省略在这里等于「与问题无关」，补回来就等于让截断失效。
	 *
	 * @return null 表示解析失败（调用方回退原顺序）；空列表表示模型判断「都不相关」（调用方据此截断）
	 */
	@SuppressWarnings("unchecked")
	private List<Integer> parse(String answer, int size) {
		if (answer == null || answer.isBlank()) {
			return null;
		}
		int start = answer.indexOf('[');
		int end = answer.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return null;
		}
		List<Integer> parsed;
		try {
			parsed = (List<Integer>) JacksonUtils.readValue(answer.substring(start, end + 1), List.class);
		} catch (Exception e) {
			log.debug("重排输出不是合法 JSON：{}", brief(answer));
			return null;
		}
		if (parsed == null) {
			return null;
		}
		// 空数组是**有效结论**（都不相关），不是解析失败 —— 调用方要据此截断
		if (parsed.isEmpty()) {
			return List.of();
		}
		Set<Integer> ordered = new LinkedHashSet<>();
		for (Object value : parsed) {
			if (!(value instanceof Number number)) {
				continue;
			}
			int index = number.intValue() - 1;
			if (index >= 0 && index < size) {
				ordered.add(index);
			}
		}
		return new ArrayList<>(ordered);
	}

	private List<Integer> indexes(int size, int topN) {
		List<Integer> list = new ArrayList<>(size);
		for (int i = 0; i < Math.min(size, Math.max(topN, 1)); i++) {
			list.add(i);
		}
		return list;
	}

	private String brief(String text) {
		if (text == null) {
			return "";
		}
		return text.length() <= 60 ? text : text.substring(0, 60) + "…";
	}
}
