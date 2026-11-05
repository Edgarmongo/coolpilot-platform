package com.coolskyai.modules.ai.service;

import java.util.List;

/**
 * @Description: 重排（rerank）
 *
 * 为什么需要它：第一轮打分是「查询向量 vs 片段向量」，两者各自独立编码，
 * 只能比较「说的是不是一回事」，无法判断「这段到底有没有回答问题」。
 * 重排模型把查询与候选**放在一起**看，判断粒度更细，代价是慢 ——
 * 所以只对前 20 个候选做，而不是全量。
 *
 * 两种实现：
 *   http —— 专门的 rerank 服务（bge-reranker-v2-m3，经 Infinity / TEI / Jina / Cohere 暴露）
 *   llm  —— 直接用对话模型做列表排序，不需要额外模型，适合只有生成模型的部署
 *
 * 接口只收字符串列表、只回下标，不依赖业务对象：
 * 换实现（本地服务、云 API、对话模型）时调用方一行不用改。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
public interface Reranker {

	/** 实现标识（http / llm），用于后台展示与排查 */
	String provider();

	/**
	 * 按相关性重排
	 *
	 * @param query     检索问题（改写后的独立问题）
	 * @param documents 候选正文，顺序即编号（下标 0 对应候选 1）
	 * @param topN      需要的结果条数，不足时按实际数量返回
	 * @return 候选下标，按相关性从高到低；实现可以只返回它认为相关的那些
	 * @throws RuntimeException 调用失败 —— 由调用方决定回退策略
	 */
	List<Integer> rerank(String query, List<String> documents, int topN);
}
