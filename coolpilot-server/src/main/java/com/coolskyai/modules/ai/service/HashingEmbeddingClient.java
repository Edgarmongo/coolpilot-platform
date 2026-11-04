package com.coolskyai.modules.ai.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description: 本地哈希向量（离线可用的兜底实现）
 *
 * 做法：中文取「单字 + 相邻二字」，英文/数字取整词，带符号哈希到 512 维后 L2 归一化。
 *   - 不需要任何外部依赖与 API Key，开发、CI、断网环境都能跑
 *   - 效果接近「字面召回」：同义改写召回弱，但关键词命中比 LIKE 强得多
 *
 * 局限要说清楚：它不是语义模型，不能替代真实 embedding。
 * 接上远程模型后（实现同一个接口），把 provider 换掉并重建索引即可。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Component
public class HashingEmbeddingClient implements EmbeddingClient {

	private static final int DIM = 512;
	private static final String MODEL = "hash-ngram-512";

	@Override
	public int dim() {
		return DIM;
	}

	@Override
	public String model() {
		return MODEL;
	}

	@Override
	public float[] embed(String text) {
		float[] vector = new float[DIM];
		if (text == null || text.isEmpty()) {
			return vector;
		}
		for (String token : tokens(text)) {
			int hash = token.hashCode();
			int index = Math.floorMod(hash, DIM);
			// 带符号哈希：同样的词落在同一维，不同词相互抵消，减少碰撞带来的虚高相似度
			float sign = ((hash >>> 15) & 1) == 0 ? 1f : -1f;
			vector[index] += sign * weight(token);
		}
		return normalize(vector);
	}

	@Override
	public int[] tokenHashes(String text) {
		if (text == null || text.isEmpty()) {
			return new int[0];
		}
		return tokens(text).stream().mapToInt(String::hashCode).distinct().toArray();
	}

	/**
	 * 中文单字权重 0.5、二字词 1.0；英文整词 1.0
	 */
	private float weight(String token) {
		return token.length() == 1 && isCjk(token.charAt(0)) ? 0.5f : 1.0f;
	}

	private List<String> tokens(String text) {
		List<String> tokens = new ArrayList<>(text.length());
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

	private boolean isAsciiWord(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
	}

	private boolean isCjk(char c) {
		return c >= '\u4e00' && c <= '\u9fff';
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
}
