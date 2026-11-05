package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(AiSearchService.class);

	@Value("${ai.search.min-score:0.05}")
	private float minScore;

	@Value("${ai.search.cache-seconds:300}")
	private long cacheSeconds;

	@Value("${ai.search.max-limit:20}")
	private int maxLimit;

	/**
	 * 每个文档最多取几块内容
	 *
	 * 1 就是旧行为（只取最像的那块）。长文里的问题常常分散在多块：
	 * 问「状态管理用什么」，最像的一块可能是「性能」，而答案在相邻块里。
	 * 取 2 块并合并，代价是 prompt 变长一点，换来「答得全」。
	 */
	@Value("${ai.search.chunks-per-doc:2}")
	private int chunksPerDoc;

	/** 合并多块时的正文上限，避免把上下文撑爆 */
	@Value("${ai.search.merge-limit:650}")
	private int mergeLimit;

	/**
	 * 重排候选数
	 *
	 * 第一轮打分只负责「别漏」，重排负责「别乱」。多取几倍候选再让重排挑，
	 * 才能纠正「关键词命中但答非所问」的排序 —— 只给最终条数的话，重排无从选择。
	 */
	@Value("${ai.rerank.candidates:20}")
	private int rerankCandidates;

	/** 混合检索里向量的权重；剩下的给字面分 */
	@Value("${ai.search.hybrid-alpha:0.65}")
	private double hybridAlpha;

	/**
	 * 纯语义命中的门槛
	 *
	 * bge-m3 这类模型对任意中文短句的余弦都在 0.3~0.5 之间（各向异性），
	 * 用统一的低阈值会把「今天天气怎么样」也检出 8 条。
	 * 所以：完全没有字面重合时，要求向量相似度更高才认。
	 */
	@Value("${ai.search.semantic-floor:0.5}")
	private double semanticFloor;


	/**
	 * 标题加成
	 *
	 * 标题是「这段讲什么」的最强信号，但打分主体仍是正文。
	 * 单列一个加成，而不是并进正文覆盖率：并进去会稀释正文信号。
	 */
	@Value("${ai.search.title-boost:0.15}")
	private double titleBoost;

	@Autowired
	private KbMapper kbMapper;
	@Autowired
	private EmbeddingClient embeddingClient;
	@Autowired
	private RerankService rerankService;
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
		/*
		 * 向量拿不到（模型服务挂了 / 配置错）时降级为纯字面检索：
		 * 搜索是基础能力，不该因为 embedding 不可用就整页报错。
		 */
		float[] queryVector = null;
		try {
			queryVector = embeddingClient.embed(text);
		} catch (Exception e) {
			log.warn("查询向量化失败，本次降级为字面检索：{}", e.getMessage());
		}
		Set<Integer> queryTokens = toTokenSet(embeddingClient.tokenHashes(text));
		Set<String> queryTerms = TextTokenizer.terms(text);
		Set<Long> allowedProducts = allowedProducts(userId);

		// 每个文档保留得分最高的若干块（再按 chunkIndex 拼回原文顺序）
		Map<Long, List<Scored>> perDocument = new LinkedHashMap<>();
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
			/*
			 * 混合打分：语义相似度 + 字面覆盖率。
			 * 语义模型擅长「换个说法也能找到」，但对型号、专有名词这类精确匹配偏弱；
			 * 字面分正好补上这一块，两者加权后既不会跑题，也不会漏掉关键词命中。
			 */
			double vectorScore = queryVector == null ? 0 : VectorCodec.cosine(queryVector, entry.vector());
			double lexicalScore = lexicalScore(queryTerms, text, entry.content());
			KbDocument doc = current.documents().get(entry.docId());
			double titleCoverage = doc == null ? 0 : coverage(queryTerms, doc.getTitle());
			/*
			 * 字面证据取「正文覆盖率」与「标题覆盖率」的较大者：
			 * 有些文档正文里只写「本手册」，真正的关键词在标题上，
			 * 只看正文会把它判成纯语义命中，反而被语义门槛挡掉。
			 */
			double evidence = Math.max(lexicalScore, titleCoverage);
			if (evidence == 0 && vectorScore < semanticFloor) {
				continue;
			}
			double mixed = queryVector == null
					? evidence
					: hybridAlpha * vectorScore + (1 - hybridAlpha) * evidence;
			mixed += titleBoost * titleCoverage;
			float score = (float) Math.min(1.0, mixed);
			if (score < minScore) {
				continue;
			}
			perDocument.computeIfAbsent(entry.docId(), key -> new ArrayList<>(2))
					.add(new Scored(entry, score));
		}

		List<Scored> ranked = new ArrayList<>(perDocument.size());
		for (List<Scored> chunks : perDocument.values()) {
			chunks.sort(Comparator.comparingDouble(Scored::score).reversed());
			if (chunks.size() > Math.max(1, chunksPerDoc)) {
				chunks.subList(Math.max(1, chunksPerDoc), chunks.size()).clear();
			}
			ranked.add(chunks.get(0));
		}
		ranked.sort(Comparator.comparingDouble(Scored::score).reversed());

		/*
		 * 取候选：启用重排时多捞一些（默认 20），让它有得挑；
		 * 没启用就只取需要的条数，不白算。
		 */
		boolean rerank = rerankService.available();
		int candidateSize = rerank ? Math.min(Math.max(size * 3, rerankCandidates), ranked.size()) : size;
		List<Retrieved> results = new ArrayList<>(candidateSize);
		for (Scored scored : ranked) {
			if (results.size() >= candidateSize) {
				break;
			}
			KbDocument document = current.documents().get(scored.entry().docId());
			if (document == null) {
				continue;
			}
			List<Scored> chunks = perDocument.getOrDefault(scored.entry().docId(), List.of(scored));
			Retrieved item = new Retrieved();
			item.setDocId(document.getId());
			item.setSourceType(document.getSourceType());
			item.setTitle(document.getTitle());
			item.setUrl(document.getUrl());
			item.setContent(mergeChunks(chunks));
			item.setSnippet(snippet(scored.entry().content()));
			item.setScore(Math.round(scored.score() * 1000) / 1000.0);
			item.setProductId(document.getProductId());
			item.setChunkIndex(scored.entry().chunkIndex());
			results.add(item);
		}
		// 重排失败/未启用时原样返回：它只改顺序，不改内容
		return rerank ? rerankService.rerank(text, results, size) : results;
	}

	/**
	 * 把同一文档的若干块按原文顺序拼起来，受 mergeLimit 约束
	 *
	 * 按 chunkIndex 而不是分数排：拼出来的段落要能顺读，
	 * 分数顺序会让上下文跳来跳去，模型更难判断「哪句在回答什么」。
	 */
	private String mergeChunks(List<Scored> chunks) {
		if (chunks.size() == 1) {
			return chunks.get(0).entry().content();
		}
		List<Scored> ordered = new ArrayList<>(chunks);
		ordered.sort(Comparator.comparingInt(item -> item.entry().chunkIndex()));
		StringBuilder sb = new StringBuilder(mergeLimit + 80);
		for (Scored chunk : ordered) {
			String content = chunk.entry().content() == null ? "" : chunk.entry().content().trim();
			if (content.isEmpty()) {
				continue;
			}
			if (sb.length() > 0 && sb.length() + content.length() > mergeLimit) {
				// 剩余预算够就截一段，不够就停：宁可少给，也不要拼出半句话
				int room = mergeLimit - sb.length();
				if (room > 80) {
					sb.append('\n').append(content, 0, room - 1).append('…');
				}
				break;
			}
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(content);
		}
		return sb.toString();
	}

	/**
	 * 字面覆盖率：查询词有多少出现在这段正文里
	 *
	 * 不做 TF-IDF：语料只有几百块，IDF 估不准，反而让常见词权重失真。
	 * 覆盖率 + 整句命中加成，方向足够正确。
	 */
	private double lexicalScore(Set<String> terms, String query, String content) {
		double coverage = coverage(terms, content);
		if (coverage > 0 && query.length() >= 4 && content != null
				&& content.toLowerCase().contains(query.toLowerCase())) {
			// 整句原样出现，说明这段就是讲这件事的
			coverage = Math.min(1.0, coverage + 0.2);
		}
		return coverage;
	}

	private double coverage(Set<String> terms, String text) {
		if (terms.isEmpty() || text == null || text.isEmpty()) {
			return 0;
		}
		String lower = text.toLowerCase();
		int matched = 0;
		for (String term : terms) {
			if (lower.contains(term)) {
				matched++;
			}
		}
		return (double) matched / terms.size();
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
		/*
		 * 用 snapshot() 而不是直接读字段：进程刚起来时快照还是 null，
		 * 后台会显示「已加载 0 块」，看起来像索引丢了 —— 其实只是没人搜过。
		 */
		Snapshot current = snapshot();
		Map<String, Object> stats = new LinkedHashMap<>(10);
		stats.put("documents", kbMapper.countDocuments());
		stats.put("chunks", kbMapper.countChunks());
		stats.put("loadedEntries", current == null ? 0 : current.entries().size());
		stats.put("model", embeddingClient.model());
		stats.put("dim", embeddingClient.dim());
		stats.put("provider", embeddingClient.provider());
		stats.put("rerank", rerankService.provider());
		stats.put("hybridAlpha", hybridAlpha);
		stats.put("ignoredChunks", current == null ? 0 : current.ignored());
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
		int ignored = 0;
		for (KbChunk chunk : kbMapper.listAllChunks()) {
			// 维度或模型对不上说明索引是旧模型建的，跳过而不是拿错向量硬算
			if (chunk.getDim() == null || chunk.getDim() != embeddingClient.dim()) {
				ignored++;
				continue;
			}
			if (chunk.getModel() != null && !chunk.getModel().isEmpty()
					&& !chunk.getModel().equals(embeddingClient.model())) {
				ignored++;
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
				embeddingClient.dim(), ignored);
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
	                        String model, int dim, int ignored) {
	}
}
