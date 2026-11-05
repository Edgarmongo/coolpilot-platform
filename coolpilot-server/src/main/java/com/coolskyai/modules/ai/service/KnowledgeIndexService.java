package com.coolskyai.modules.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.coolskyai.modules.ai.entity.KbChunk;
import com.coolskyai.modules.ai.entity.KbDocument;
import com.coolskyai.modules.ai.mapper.KbMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 知识库索引重建
 *
 * 索引三类内容：
 *   1. 已发布文章（跳过密码文章 —— 正文不能进公开索引）
 *   2. 上架商品的文案（summary + detail，本身就是公开展示内容）
 *   3. 课时正文（付费内容，带 product_id，检索时按权益过滤）
 *
 * 重建是全量替换：先清空再写入，个人站点量级（千级块）几秒内完成，
 * 比维护增量 diff 简单得多，也不会出现「删了文章索引还留着」的脏数据。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class KnowledgeIndexService {

	/** 单块目标长度（字符） */
	private static final int CHUNK_SIZE = 350;
	/** 相邻块重叠，避免答案被切在边界上 */
	private static final int OVERLAP = 60;
	/** 太短的块没有检索价值 */
	private static final int MIN_CHUNK = 20;
	private static final long KB_ID = 1L;

	/** 每批向量化的块数：远程模型下一次请求比发 N 次快得多 */
	@org.springframework.beans.factory.annotation.Value("${ai.embedding.batch-size:16}")
	private int embeddingBatchSize;

	@Autowired
	private KbMapper kbMapper;
	@Autowired
	private EmbeddingClient embeddingClient;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private AiSearchService aiSearchService;

	/**
	 * 全量重建索引
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> rebuild() {
		kbMapper.deleteAllChunks();
		kbMapper.deleteAllDocuments();

		int blogs = indexBlogs();
		int products = indexProducts();
		int lessons = indexLessons();

		// 让检索侧丢掉内存快照，下一次查询重新装载
		aiSearchService.invalidate();

		Map<String, Object> stats = new LinkedHashMap<>(8);
		stats.put("blogs", blogs);
		stats.put("products", products);
		stats.put("lessons", lessons);
		stats.put("documents", kbMapper.countDocuments());
		stats.put("chunks", kbMapper.countChunks());
		stats.put("model", embeddingClient.model());
		stats.put("dim", embeddingClient.dim());
		stats.put("lastIndexedAt", kbMapper.lastIndexedAt());
		return stats;
	}

	private int indexBlogs() {
		String sql = "SELECT id, title, content, slug FROM blog " +
				"WHERE is_published = 1 AND (password IS NULL OR password = '')";
		int[] count = {0};
		jdbcTemplate.query(sql, (RowCallbackHandler) rs -> {
			Long id = rs.getLong("id");
			String slug = rs.getString("slug");
			String url = slug == null || slug.isEmpty() ? "/blog/" + id : "/blog/" + slug;
			if (indexOne("BLOG", id, null, rs.getString("title"), url, true, rs.getString("content"))) {
				count[0]++;
			}
		});
		return count[0];
	}

	private int indexProducts() {
		String sql = "SELECT id, product_no, title, summary, detail FROM product WHERE status = 'ON_SALE'";
		int[] count = {0};
		jdbcTemplate.query(sql, (RowCallbackHandler) rs -> {
			Long id = rs.getLong("id");
			String content = nullToEmpty(rs.getString("summary")) + "\\n" + nullToEmpty(rs.getString("detail"));
			if (indexOne("PRODUCT", id, null, rs.getString("title"), "/knowledge/" + rs.getString("product_no"),
					true, content)) {
				count[0]++;
			}
		});
		return count[0];
	}

	private int indexLessons() {
		String sql = "SELECT l.id, l.title, l.content, p.id AS product_id, p.product_no " +
				"FROM lesson l JOIN course c ON c.id = l.course_id JOIN product p ON p.id = c.product_id " +
				"WHERE p.status = 'ON_SALE' AND l.content IS NOT NULL AND l.content <> ''";
		int[] count = {0};
		jdbcTemplate.query(sql, (RowCallbackHandler) rs -> {
			Long lessonId = rs.getLong("id");
			String title = rs.getString("title");
			// 课时是付费内容：is_public=false + product_id，检索时按权益过滤
			if (indexOne("LESSON", lessonId, rs.getLong("product_id"), title,
					"/knowledge/" + rs.getString("product_no"), false, rs.getString("content"))) {
				count[0]++;
			}
		});
		return count[0];
	}

	/**
	 * 写入一个来源：先切块，再插文档（带块数），最后插块
	 */
	private boolean indexOne(String sourceType, Long sourceId, Long productId, String title, String url,
	                         boolean isPublic, String rawContent) {
		List<String> chunks = split(rawContent);
		if (chunks.isEmpty()) {
			return false;
		}
		KbDocument document = new KbDocument();
		document.setKbId(KB_ID);
		document.setSourceType(sourceType);
		document.setSourceId(sourceId);
		document.setProductId(productId);
		document.setTitle(title == null || title.isEmpty() ? sourceType + "#" + sourceId : title);
		document.setUrl(url);
		document.setIsPublic(isPublic);
		document.setStatus("INDEXED");
		document.setChunkCount(chunks.size());
		kbMapper.insertDocument(document);

		int batchSize = Math.max(1, embeddingBatchSize);
		for (int start = 0; start < chunks.size(); start += batchSize) {
			int end = Math.min(chunks.size(), start + batchSize);
			List<String> batch = chunks.subList(start, end);
			List<float[]> vectors = embeddingClient.embedBatch(batch);
			for (int i = 0; i < batch.size(); i++) {
				String content = batch.get(i);
				KbChunk chunk = new KbChunk();
				chunk.setDocId(document.getId());
				chunk.setKbId(KB_ID);
				chunk.setChunkIndex(start + i);
				chunk.setContent(content);
				chunk.setProductId(productId);
				chunk.setIsPublic(isPublic);
				chunk.setEmbedding(VectorCodec.encode(vectors.get(i)));
				chunk.setDim(embeddingClient.dim());
				chunk.setModel(embeddingClient.model());
				kbMapper.insertChunk(chunk);
			}
		}
		return true;
	}

	/**
	 * 切块：按标点尽量切在句子边界，块间保留重叠
	 */
	private List<String> split(String raw) {
		String text = clean(raw);
		List<String> chunks = new ArrayList<>();
		if (text.isEmpty()) {
			return chunks;
		}
		int length = text.length();
		int start = 0;
		while (start < length) {
			int end = Math.min(length, start + CHUNK_SIZE);
			if (end < length) {
				int cut = lastSentenceEnd(text, end);
				if (cut > start + MIN_CHUNK) {
					end = cut;
				}
			}
			String piece = text.substring(start, end).trim();
			if (piece.length() >= MIN_CHUNK) {
				chunks.add(piece);
			}
			if (end >= length) {
				break;
			}
			start = Math.max(end - OVERLAP, start + 1);
		}
		return chunks;
	}

	private int lastSentenceEnd(String text, int around) {
		for (int i = around; i > around - 80 && i > 0; i--) {
			char c = text.charAt(i - 1);
			if (c == '。' || c == '！' || c == '？' || c == '\n' || c == '.' || c == '；' || c == ';') {
				return i;
			}
		}
		return around;
	}

	/**
	 * 去掉 Markdown/HTML 标记：这些符号会把向量带偏，且对检索没有意义
	 */
	private String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String text = raw
				.replaceAll("```[a-zA-Z]*", " ")
				.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
				.replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
				.replaceAll("<[^>]+>", " ")
				.replaceAll("(?m)^[ \\t]*[#>*+-]+[ \\t]*", " ")
				.replace("`", " ")
				.replace("*", " ")
				.replace("_", " ");
		return text.replaceAll("\\s+", " ").trim();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
