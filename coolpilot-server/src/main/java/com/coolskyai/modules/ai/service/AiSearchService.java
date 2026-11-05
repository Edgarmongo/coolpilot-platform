package com.coolskyai.modules.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.modules.ai.entity.KbChunk;
import com.coolskyai.modules.ai.entity.KbDocument;
import com.coolskyai.modules.ai.mapper.KbMapper;
import com.coolskyai.modules.ai.vo.Retrieved;
import com.coolskyai.modules.entitlement.entity.Entitlement;
import com.coolskyai.modules.entitlement.mapper.EntitlementMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 知识库检索
 *
 * 向量存在 MySQL，这里在进程内维护一份快照做余弦检索：
 *   1e4 块 × 512 维约 20MB，单次查询几毫秒，比引入向量库划算（见 ADR-0001）。
 *
 * 权限过滤在打分之前完成：付费块只有「公开 或 持有该商品权益」才进入候选，
 * 从源头避免把不该出现的内容读进结果，而不是检索完再删除。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class AiSearchService {

	@Value("${ai.search.min-score:0.05}")
	private float minScore;

	@Value("${ai.search.cache-seconds:300}")
	private long cacheSeconds;

	@Value("${ai.search.max-limit:20}")
	private int maxLimit;

	@Autowired
	private KbMapper kbMapper;
	@Autowired
	private EmbeddingClient embeddingClient;
	@Autowired
	private EntitlementMapper entitlementMapper;

	private volatile Snapshot snapshot;

	/**
	 * 索引重建后调用，下次查询重新装载
	 */
	public void invalidate() {
		snapshot = null;
	}

	/**
	 * 检索入口（给前台搜索页用，返回可直接序列化的 Map）
	 */
	public Map<String, Object> search(String query, Long userId, Integer limit) {
		int size = normalizeSize(limit);
		long start = System.currentTimeMillis();
		List<Retrieved> retrieved = retrieve(query, userId, size);

		List<Map<String, Object>> results = new ArrayList<>(retrieved.size());
		for (Retrieved item : retrieved) {
			Map<String, Object> row = new HashMap<>(10);
			row.put("docId", item.getDocId());
			row.put("sourceType", item.getSourceType());
			row.put("title", item.getTitle());
			row.put("url", item.getUrl());
			row.put("snippet", item.getSnippet());
			row.put("score", item.getScore());
			row.put("productId", item.getProductId());
			row.put("chunkIndex", item.getChunkIndex());
			results.add(row);
		}

		Snapshot current = snapshot();
		Map<String, Object> data = new LinkedHashMap<>(10);
		data.put("query", normalizeQuery(query));
		data.put("model", current.model());
		data.put("dim", current.dim());
		data.put("candidates", current.entries().size());
		data.put("total", results.size());
		data.put("tookMs", System.currentTimeMillis() - start);
		data.put("results", results);
		return data;
	}

	/**
	 * 检索（内部用，带完整正文）
	 *
	 * 搜索页只要摘要，问答要拿正文拼上下文，所以两者共用这一套打分与权限过滤，
	 * 避免「搜得到但问不出」这种两套逻辑不一致的怪现象。
	 */
	public List<Retrieved> retrieve(String query, Long userId, int limit) {
		String text = normalizeQuery(query);
		int size = limit <= 0 ? 8 : Math.min(limit, maxLimit);

		Snapshot current = snapshot();
		float[] queryVector = embeddingClient.embed(text);
		Set<Integer> queryTokens = toTokenSet(embeddingClient.tokenHashes(text));
		Set<Long> allowedProducts = allowedProducts(userId);

		Map<Long, Scored> bestPerDocument = new LinkedHashMap<>();
		for (Entry entry : current.entries()) {
			if (!entry.isPublic()) {
				// 付费块：没有权益直接跳过，绝不进入打分与结果
				if (entry.productId() == null || !allowedProducts.contains(entry.productId())) {
					continue;
				}
			}
			/*
			 * 字面向量的护栏：没有任何共同词时，高分只可能来自哈希碰撞。
			 * 语义模型不提供 token（空集合），这里自动失效。
			 */
			if (!queryTokens.isEmpty() && !sharesToken(queryTokens, entry.tokenHashes())) {
				continue;
			}
			float score = VectorCodec.cosine(queryVector, entry.vector());
			if (score < minScore) {
				continue;
			}
			Scored previous = bestPerDocument.get(entry.docId());
			if (previous == null || score > previous.score()) {
				bestPerDocument.put(entry.docId(), new Scored(entry, score));
			}
		}

		List<Scored> ranked = new ArrayList<>(bestPerDocument.values());
		ranked.sort(Comparator.comparingDouble(Scored::score).reversed());

		List<Retrieved> results = new ArrayList<>(size);
		for (Scored scored : ranked) {
			if (results.size() >= size) {
				break;
			}
			KbDocument document = current.documents().get(scored.entry().docId());
			if (document == null) {
				continue;
			}
			Retrieved item = new Retrieved();
			item.setDocId(document.getId());
			item.setSourceType(document.getSourceType());
			item.setTitle(document.getTitle());
			item.setUrl(document.getUrl());
			item.setContent(scored.entry().content());
			item.setSnippet(snippet(scored.entry().content()));
			item.setScore(Math.round(scored.score() * 1000) / 1000.0);
			item.setProductId(document.getProductId());
			item.setChunkIndex(scored.entry().chunkIndex());
			results.add(item);
		}
		return results;
	}

	private String normalizeQuery(String query) {
		String text = query == null ? "" : query.trim();
		if (text.isEmpty()) {
			throw new BadRequestException("请输入检索内容");
		}
		if (text.length() > 100) {
			throw new BadRequestException("检索内容不要超过 100 个字符");
		}
		return text;
	}

	private int normalizeSize(Integer limit) {
		return limit == null || limit <= 0 ? 8 : Math.min(limit, maxLimit);
	}

	public Map<String, Object> stats() {
		Snapshot current = snapshot;
		Map<String, Object> stats = new LinkedHashMap<>(10);
		stats.put("documents", kbMapper.countDocuments());
		stats.put("chunks", kbMapper.countChunks());
		stats.put("loadedEntries", current == null ? 0 : current.entries().size());
		stats.put("model", embeddingClient.model());
		stats.put("dim", embeddingClient.dim());
		stats.put("minScore", minScore);
		stats.put("lastIndexedAt", kbMapper.lastIndexedAt());
		stats.put("cacheSeconds", cacheSeconds);
		return stats;
	}

	// ---------------- 内部 ----------------

	private Set<Long> allowedProducts(Long userId) {
		if (userId == null) {
			return Set.of();
		}
		Set<Long> products = new HashSet<>();
		for (Entitlement entitlement : entitlementMapper.listActiveByUser(userId)) {
			if (entitlement.getProductId() != null) {
				products.add(entitlement.getProductId());
			}
		}
		return products;
	}

	private Snapshot snapshot() {
		Snapshot current = snapshot;
		if (current != null && System.currentTimeMillis() - current.builtAt() < cacheSeconds * 1000L) {
			return current;
		}
		synchronized (this) {
			current = snapshot;
			if (current != null && System.currentTimeMillis() - current.builtAt() < cacheSeconds * 1000L) {
				return current;
			}
			snapshot = build();
			return snapshot;
		}
	}

	private Snapshot build() {
		Map<Long, KbDocument> documents = new HashMap<>();
		for (KbDocument document : kbMapper.listDocuments()) {
			documents.put(document.getId(), document);
		}

		List<Entry> entries = new ArrayList<>();
		for (KbChunk chunk : kbMapper.listAllChunks()) {
			// 维度或模型对不上说明索引是旧模型建的，跳过而不是拿错向量硬算
			if (chunk.getDim() == null || chunk.getDim() != embeddingClient.dim()) {
				continue;
			}
			if (chunk.getModel() != null && !chunk.getModel().isEmpty()
					&& !chunk.getModel().equals(embeddingClient.model())) {
				continue;
			}
			float[] vector = VectorCodec.decode(chunk.getEmbedding());
			if (vector.length == 0) {
				continue;
			}
			entries.add(new Entry(chunk.getId(), chunk.getDocId(), chunk.getProductId(),
					Boolean.TRUE.equals(chunk.getIsPublic()), vector, chunk.getContent(),
					chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
					embeddingClient.tokenHashes(chunk.getContent())));
		}
		return new Snapshot(entries, documents, System.currentTimeMillis(), embeddingClient.model(),
				embeddingClient.dim());
	}

	private String snippet(String content) {
		if (content == null) {
			return "";
		}
		String text = content.replaceAll("\\s+", " ").trim();
		return text.length() <= 160 ? text : text.substring(0, 160) + "…";
	}

	private Set<Integer> toTokenSet(int[] hashes) {
		Set<Integer> set = new HashSet<>(hashes.length * 2);
		for (int hash : hashes) {
			set.add(hash);
		}
		return set;
	}

	private boolean sharesToken(Set<Integer> queryTokens, int[] chunkTokens) {
		for (int token : chunkTokens) {
			if (queryTokens.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private record Entry(long chunkId, long docId, Long productId, boolean isPublic, float[] vector,
	                     String content, int chunkIndex, int[] tokenHashes) {
	}

	private record Scored(Entry entry, float score) {
	}

	private record Snapshot(List<Entry> entries, Map<Long, KbDocument> documents, long builtAt,
	                        String model, int dim) {
	}
}
