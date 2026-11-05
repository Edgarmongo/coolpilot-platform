package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.modules.ai.vo.Retrieved;
import com.coolskyai.util.JacksonUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Description: 重排门面
 *
 * 调用方只关心「把候选按相关性再排一遍，取前 N 个」，不必知道背后是
 * 专用 rerank 服务还是对话模型。
 *
 * 四层保护，都是为了让重排「可有可无」而不是「一坏就全坏」：
 *   1. 没配 provider（或配置不完整）时直接跳过，一次也不调用
 *   2. 调用失败返回原顺序：排序差一点总比整个搜索/问答报错强
 *   3. 连续失败后熔断 60 秒：配置写错时不会让每个请求都白等一次超时
 *   4. **最少保留 N 条**：重排允许「只留相关的」来截断噪音，
 *      但如果模型判断过严、或候选本身就只有一两条相关，上下文被削空会让
 *      问答直接变成「站内没有资料」—— 那比多给两条低分资料更糟。
 *
 * 缓存：同一问题 + 同一批候选的排序结果短时复用（默认 15 分钟）。
 * 键里带上候选指纹，索引重建或内容更新后自然失效，不依赖主动清理。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class RerankService {

	private static final Logger log = LoggerFactory.getLogger(RerankService.class);

	/** 熔断窗口：连续失败后这段时间内直接跳过重排 */
	private static final long BREAK_MS = 60_000L;
	/**
	 * 重排策略版本
	 *
	 * 缓存键要带上它：提示词或截断规则一改，同一批候选的旧排序结果就不再适用。
	 * 改提示词 / 截断逻辑时手动 +1，比「等 15 分钟自然过期」可靠。
	 */
	private static final String STRATEGY_VERSION = "3";

	/** 送进重排的正文长度：Title 之后最多这么多字 */
	private static final int DOC_LIMIT = 240;

	private final ObjectProvider<Reranker> rerankerProvider;
	private final AtomicLong brokenUntil = new AtomicLong(0);

	@Value("${ai.rerank.enabled:true}")
	private boolean enabled;

	/** 严格度策略：min-keep 的取值由它推导（显式配置可覆盖） */
	@Autowired
	private RerankPolicy policy;

	/** 重排结果缓存时间（秒），0 表示不缓存 */
	@Value("${ai.rerank.cache-seconds:900}")
	private long cacheSeconds;

	@Autowired
	private AiCache aiCache;

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
	 * @param topN       最终最多几条
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
			List<Integer> order = cachedOrder(reranker.provider(), query, candidates);
			if (order == null) {
				order = reranker.rerank(query, documents, topN);
				remember(reranker.provider(), query, candidates, order);
			}
			if (order == null) {
				// 实现没有给结论（理论不会发生）：宁可原样返回，也不能把候选丢掉
				return trim(candidates, topN);
			}
			brokenUntil.set(0);
			List<Retrieved> ranked;
			if (order.isEmpty()) {
				/*
				 * 模型说「都不相关」：这是有效结论，不是失败。
				 * 但第一轮分数很高时保留最低限度几条 —— 模型偶尔会过于严格，
				 * 而「明明有资料却答没有」比「多给两条低分资料」更伤人。
				 */
				Double top = candidates.get(0).getScore();
				ranked = top != null && top >= policy.emptyKeepScore()
						? keepAtLeast(List.of(), candidates, policy.minKeep())
						: List.of();
			} else {
				ranked = keepAtLeast(toRanked(order, candidates), candidates, policy.minKeep());
			}
			log.debug("重排完成 provider={} {} -> {} 条，耗时 {}ms",
					reranker.provider(), candidates.size(), ranked.size(), System.currentTimeMillis() - start);
			return trim(ranked, topN);
		} catch (Exception e) {
			brokenUntil.set(System.currentTimeMillis() + BREAK_MS);
			log.warn("重排失败，保持原顺序（{} 秒内不再尝试）：{}", BREAK_MS / 1000, e.getMessage());
			return trim(candidates, topN);
		}
	}

	private List<Retrieved> toRanked(List<Integer> order, List<Retrieved> candidates) {
		List<Retrieved> ranked = new ArrayList<>(order.size());
		for (Integer index : order) {
			if (index != null && index >= 0 && index < candidates.size()) {
				ranked.add(candidates.get(index));
			}
		}
		return ranked;
	}

	/**
	 * 重排结果不足 minKeep 时，用原顺序里没被选中的条目按序补齐
	 *
	 * 注意补的是「原分数靠前但模型没选」的那些 —— 它们最可能是被误杀的相关项。
	 */
	private List<Retrieved> keepAtLeast(List<Retrieved> ranked, List<Retrieved> candidates, int atLeast) {
		if (ranked.size() >= Math.max(atLeast, 1)) {
			return ranked;
		}
		Set<Long> seen = new LinkedHashSet<>();
		for (Retrieved item : ranked) {
			seen.add(key(item));
		}
		List<Retrieved> filled = new ArrayList<>(ranked);
		for (Retrieved item : candidates) {
			if (filled.size() >= Math.max(atLeast, 1)) {
				break;
			}
			if (seen.add(key(item))) {
				filled.add(item);
			}
		}
		return filled;
	}

	/**
	 * 候选身份：docId + chunkIndex 足以区分（同一文档的不同块是不同的候选）
	 */
	private long key(Retrieved item) {
		long doc = item.getDocId() == null ? 0 : item.getDocId();
		int chunk = item.getChunkIndex() == null ? 0 : item.getChunkIndex();
		return doc * 100_000L + chunk;
	}

	@SuppressWarnings("unchecked")
	private List<Integer> cachedOrder(String provider, String query, List<Retrieved> candidates) {
		if (cacheSeconds <= 0) {
			return null;
		}
		String cached = aiCache.get("rk", fingerprint(provider, query, candidates));
		if (cached == null) {
			return null;
		}
		try {
			return (List<Integer>) JacksonUtils.readValue(cached, List.class);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 缓存键：策略版本 + 实现 + 问题 + 候选内容指纹
	 *
	 * 指纹取**正文内容的摘要**而不是 docId:chunkIndex：
	 * 文章改了内容但块划分没变时，id 完全一样，用 id 做键会命中旧排序 ——
	 * 表现是「内容更新了，搜索结果还是按老文本排的」，而且 15 分钟内反复出现。
	 * 摘要只看标题与正文，块位置变化也自然失效。
	 */
	private String fingerprint(String provider, String query, List<Retrieved> candidates) {
		StringBuilder sb = new StringBuilder(256);
		for (Retrieved item : candidates) {
			sb.append(item.getTitle()).append('\u0001')
					.append(item.getContent()).append('\u0002');
		}
		return STRATEGY_VERSION + "|" + provider + "|" + query + "|" + AiCache.shortSha1(sb.toString());
	}

	private void remember(String provider, String query, List<Retrieved> candidates, List<Integer> order) {
		if (cacheSeconds <= 0 || order == null) {
			return;
		}
		try {
			aiCache.put("rk", JacksonUtils.writeValueAsString(order), cacheSeconds,
					fingerprint(provider, query, candidates));
		} catch (Exception e) {
			log.debug("重排结果缓存失败（忽略）：{}", e.getMessage());
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
