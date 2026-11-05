package com.coolskyai.modules.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @Description: 轻量分词（中英混排，无外部依赖）
 *
 * 中文没有空格，业界做法要么上词典/模型，要么按字切。这里选第三条路：
 * **单字 + 相邻二字**。二者都有用 ——
 *   二字给精度（「退款」比「退」「款」更能定位）
 *   单字给召回（「退款流程」与「退钱流程」共享「退」）
 *
 * 它同时服务两个地方：
 *   1. 哈希向量（HashingEmbeddingClient）的维度投递
 *   2. 混合检索里的字面分（bigram 覆盖率）
 *
 * 刻意不引 jieba/HanLP：多一个 40MB 词典和一套加载逻辑，
 * 而我们真正想要的是「字面召回」，不是词性分析。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public final class TextTokenizer {

	private TextTokenizer() {
	}

	/**
	 * 全量词元（含单字，供向量投递用）
	 */
	public static List<String> tokens(String text) {
		List<String> tokens = new ArrayList<>(text == null ? 0 : text.length());
		if (text == null || text.isEmpty()) {
			return tokens;
		}
		StringBuilder ascii = new StringBuilder(16);
		char previousCjk = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (isAsciiWord(c)) {
				ascii.append(Character.toLowerCase(c));
				previousCjk = 0;
				continue;
			}
			if (ascii.length() > 0) {
				tokens.add(ascii.toString());
				ascii.setLength(0);
			}
			if (isCjk(c)) {
				tokens.add(String.valueOf(c));
				if (previousCjk != 0) {
					tokens.add(new String(new char[]{previousCjk, c}));
				}
				previousCjk = c;
			} else {
				previousCjk = 0;
			}
		}
		if (ascii.length() > 0) {
			tokens.add(ascii.toString());
		}
		return tokens;
	}

	/**
	 * 检索用词元：二字词优先，单字只在「整句没有二字词」时兜底
	 *
	 * 单字太容易命中（「的」「是」到处都有），参与字面分会把噪音顶上来。
	 */
	public static Set<String> terms(String text) {
		List<String> tokens = tokens(text);
		Set<String> bigrams = new LinkedHashSet<>();
		Set<String> words = new LinkedHashSet<>();
		Set<String> singles = new LinkedHashSet<>();
		for (String token : tokens) {
			if (token.length() == 1 && isCjk(token.charAt(0))) {
				singles.add(token);
			} else if (isCjk(token.charAt(0))) {
				bigrams.add(token);
			} else if (token.length() >= 2) {
				words.add(token);
			}
		}
		Set<String> result = new LinkedHashSet<>(bigrams);
		result.addAll(words);
		if (result.isEmpty()) {
			result.addAll(singles);
		}
		return result;
	}

	private static boolean isAsciiWord(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
	}

	/** 是否 CJK 汉字（供向量实现复用） */
	public static boolean isCjk(char c) {
		return c >= '\u4e00' && c <= '\u9fff';
	}
}
