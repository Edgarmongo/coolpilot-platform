package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.modules.ai.vo.Retrieved;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description: 重排门面
 *
 * 调用方只关心「把候选按相关性再排一遍，取前 N 个」，不必知道背后是
 * 专用 rerank 服务还是对话模型。
 *
 * 三条保护，都是为了让重排「可有可无」而不是「一坏就全坏」：
 *   1. 没配 provider（或配置不完整）时直接跳过，一次也不调用
 *   2. 调用失败返回原顺序：排序差一点总比整个搜索/问答报错强
 *   3. 连续失败后熔断 60 秒：配置写错时不会让每个请求都白等一次超时
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class RerankService {

	private static final Logger log = LoggerFactory.getLogger(RerankService.class);

	/** 熔断窗口：连续失败后这段时间内直接跳过重排 */
	private static final long BREAK_MS = 60_000L;
	/** 送进重排的正文长度：Title 之后最多这么多字 */
	private static final int DOC_LIMIT = 240;

	private final ObjectProvider<Reranker> rerankerProvider;
	private final AtomicLong brokenUntil = new AtomicLong(0);

	@Value("${ai.rerank.enabled:true}")
	private boolean enabled;

	public RerankService(ObjectProvider<Reranker> rerankerProvider) {
		this.rerankerProvider = rerankerProvider;
	}

	/**
	 * 是否真的会重排（决定调用方要不要多取候选）
	 */
	public boolean available() {
		return enabled && rerankerProvider.getIfAvailable() != null && System.currentTimeMillis() >= brokenUntil.get();
	}

	public String provider() {
		Reranker reranker = rerankerProvider.getIfAvailable();
		return available() ? reranker.provider() : "none";
	}

	/**
	 * 重排并截断
	 *
	 * @param query      检索问题
	 * @param candidates 第一轮打分结果（已按分数降序）
	 * @param topN       最终要几条
	 * @return 新顺序的列表；未启用或失败时返回原顺序的前 topN 条
	 */
	public List<Retrieved> rerank(String query, List<Retrieved> candidates, int topN) {
		if (candidates.size() <= 1 || topN <= 0) {
			return trim(candidates, topN);
		}
		if (!available()) {
			return trim(candidates, topN);
		}
		Reranker reranker = rerankerProvider.getIfAvailable();
		long start = System.currentTimeMillis();
		try {
			List<String> documents = new ArrayList<>(candidates.size());
			for (Retrieved item : candidates) {
				documents.add(toDocument(item));
			}
			List<Integer> order = reranker.rerank(query, documents, topN);
			if (order == null || order.isEmpty()) {
				return trim(candidates, topN);
			}
			List<Retrieved> ranked = new ArrayList<>(order.size());
			for (Integer index : order) {
				if (index != null && index >= 0 && index < candidates.size()) {
					ranked.add(candidates.get(index));
				}
			}
			brokenUntil.set(0);
			log.debug("重排完成 provider={} {} -> {} 条，耗时 {}ms",
					reranker.provider(), candidates.size(), ranked.size(), System.currentTimeMillis() - start);
			return trim(ranked, topN);
		} catch (Exception e) {
			brokenUntil.set(System.currentTimeMillis() + BREAK_MS);
			log.warn("重排失败，保持原顺序（{} 秒内不再尝试）：{}", BREAK_MS / 1000, e.getMessage());
			return trim(candidates, topN);
		}
	}

	/**
	 * 送进重排的文本：标题在前，正文截断
	 *
	 * 标题几乎总是判断「讲的是不是这件事」的最短路径，
	 * 所以即使正文很长，也要先把标题放进前 40 个字里。
	 */
	private String toDocument(Retrieved item) {
		StringBuilder sb = new StringBuilder(DOC_LIMIT + 32);
		sb.append('《').append(item.getTitle() == null ? "" : item.getTitle()).append('》');
		String content = item.getContent() == null ? "" : item.getContent().replace('\n', ' ').trim();
		int room = DOC_LIMIT - sb.length();
		if (room > 0 && !content.isEmpty()) {
			sb.append(content, 0, Math.min(room, content.length()));
		}
		return sb.toString();
	}

	private List<Retrieved> trim(List<Retrieved> list, int topN) {
		if (topN <= 0 || list.size() <= topN) {
			return list;
		}
		return new ArrayList<>(list.subList(0, topN));
	}
}
