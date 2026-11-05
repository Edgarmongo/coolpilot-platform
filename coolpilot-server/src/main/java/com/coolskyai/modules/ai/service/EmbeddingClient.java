package com.coolskyai.modules.ai.service;

/**
 * @Description: 文本向量化接口
 *
 * 换模型只需要换实现：本地哈希（dev/离线）→ 远程 embedding API。
 * 调用方只依赖 dim() / model() / embed()，不关心背后是谁。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public interface EmbeddingClient {

	/** 向量维度；更换实现后必须重建索引 */
	int dim();

	/** 模型标识，存进 kb_chunk.model，便于判断索引是否过期 */
	String model();

	/** 把文本转成 L2 归一化后的向量 */
	float[] embed(String text);

	/**
	 * 批量向量化
	 *
	 * 真实模型一次请求几十毫秒，逐条发在建索引时会慢到不可接受；
	 * 默认实现退化为逐条调用，远程实现覆盖它走一次批量请求。
	 */
	default java.util.List<float[]> embedBatch(java.util.List<String> texts) {
		java.util.List<float[]> vectors = new java.util.ArrayList<>(texts.size());
		for (String text : texts) {
			vectors.add(embed(text));
		}
		return vectors;
	}

	/** 实现标识（hashing / openai），用于后台展示与排查 */
	default String provider() {
		return "custom";
	}

	/**
	 * 词元哈希（去重）。
	 *
	 * 只有字面向量（哈希/BM25 这类）才需要：用它做「必须有共同词」的护栏，
	 * 避免哈希碰撞把无关内容顶上来。真正的语义模型返回空数组即可，
	 * 检索侧会自动跳过这道判断。
	 */
	default int[] tokenHashes(String text) {
		return new int[0];
	}
}
