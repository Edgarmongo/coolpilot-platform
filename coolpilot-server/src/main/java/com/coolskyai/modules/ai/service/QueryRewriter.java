package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Description: 追问改写（把「那退款呢」补成独立问题）
 *
 * 为什么需要它：检索是「拿一句话去比对」，而追问天然缺主语。
 * 「那退款呢」里的「那」指向上一轮的话题，字面检索看不到这层关系，
 * 于是要么召回不到，要么靠「本轮 + 上一轮」硬拼 —— 拼出来的句子语义是糊的，
 * 向量也测不准。
 *
 * 做法：让模型只做一件事 —— 补全，不做回答。护栏有四层：
 *   1. 明显的独立问题不调用模型（省一次往返，见 {@link #suspected}）
 *   2. 上下文给最近 N 轮**已还原**的问题（不是原话）：上一轮若也是追问，
 *      拿它的原话当锚点等于在模糊上再猜一次
 *   3. 输出做清洗与长度限制，异常/空/超长一律回退到原问题
 *   4. 结果进 Redis：同一句话 + 同一段历史只花一次钱
 *
 * 失败不影响问答：改不出来就按原问题检索，最差退化成改造前的行为。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class QueryRewriter {

	private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

	private static final String SYSTEM_PROMPT = """
			你是检索查询改写器，唯一任务是把「追问」补全成不依赖上下文也能看懂的独立问题。
			规则：
			1. 只输出改写后的问题本身，不要解释、不要回答问题、不要加引号或「改写：」之类的前缀。
			2. 「那 X 呢」有两种意思，必须分清：
			   - X 是新对象（「那 RAG 手册呢」）→ 换话题，改写成围绕 X 的问题，不要把上一轮的对象塞进去；
			   - X 是上一轮对象的某个方面（「那成本呢」「那分块呢」）→ 补成「上一轮对象 + X」。
			3. 代词（它、这个、那个）先指向最近提问里明确提到的对象；提问里找不到时，
			   再看「上一轮回答（节选）」里提到的对象，不要凭空编。
			4. 不要引入对话里没出现过的新话题，也不要把两个不相干的对象拼在一起。
			5. 专有名词、型号、版本号原样保留；本轮已独立时原样输出；不超过 40 字，不换行。
			示例：
			最近提问：1. Spring Boot 4 工程脚手架包含什么？
			本轮追问：那 RAG 手册呢
			RAG 手册包含什么？
			最近提问：1. Spring Boot 4 工程脚手架包含什么？ 2. RAG 手册包含什么？
			本轮追问：那分块呢
			RAG 手册的分块怎么讲？
			最近提问：1. 站内有哪些 RAG 检索调优的实践记录？
			本轮追问：它的评估指标有哪些
			RAG 检索调优的评估指标有哪些
			""";

	/** 追问的信号词：出现任意一个，就值得让模型补全一次 */
	private static final String[] MARKERS = {"这", "那", "它", "其", "该", "上面", "刚才", "前面", "还有", "呢", "同样"};

	/** 缓存里表示「上一次也判断为不需要改写」，避免同一个问题反复调模型 */
	private static final String CACHE_NONE = "__NONE__";

	@Value("${ai.chat.rewrite-enabled:true}")
	private boolean enabled;

	/** 短于这个长度的问题，几乎一定是追问（「退款呢」「具体点」） */
	@Value("${ai.chat.rewrite-min-length:12}")
	private int minLength;

	/**
	 * 带进 prompt 的历史轮数
	 *
	 * 3 轮是折中：再少会出现「跨一轮就断」的指代，再多则 prompt 变长、
	 * 模型也容易把更早的话题误当成当前的。历史只是补全的线索，不是主角。
	 */
	@Value("${ai.chat.rewrite-history:3}")
	private int historyRounds;

	/**
	 * 上一轮回答进 prompt 的节选长度
	 *
	 * 只要够看清「回答了哪几个对象」即可，整段回答进 prompt 既贵又会把追问淹没。
	 * 0 表示完全不用回答内容（退回只看提问的旧行为）。
	 */
	@Value("${ai.chat.rewrite-answer-length:180}")
	private int answerLength;

	/** 单条历史问题截断长度：长问题进 prompt 只会淹没本轮追问 */
	@Value("${ai.chat.rewrite-history-length:60}")
	private int historyLength;

	/** 改写结果缓存时间（秒） */
	@Value("${ai.chat.rewrite-cache-seconds:3600}")
	private long cacheSeconds;

	/** 改写结果超过这个长度基本是模型跑偏了，直接丢弃 */
	private static final int MAX_REWRITE = 100;

	@Autowired
	private LlmClient llmClient;
	@Autowired
	private AiConversationMapper conversationMapper;
	@Autowired
	private AiCache aiCache;

	/**
	 * 改写结果
	 *
	 * @param query     实际用于检索的查询（失败时就是原问题）
	 * @param rewritten 是否真的改写过，便于后台排查
	 * @param tookMs    耗时，缓存命中时接近 0
	 */
	public record Result(String query, boolean rewritten, long tookMs) {
	}

	/**
	 * 把本轮问题改写成独立问题
	 *
	 * @param question     用户本轮原话
	 * @param conversation 当前会话，为空（匿名或首轮）时不可能有追问
	 */
	public Result rewrite(String question, AiConversation conversation) {
		if (!enabled || conversation == null || !llmClient.available()) {
			return unchanged(question, 0);
		}
		if (!suspected(question)) {
			return unchanged(question, 0);
		}
		List<String> history = history(conversation.getId());
		if (history.isEmpty()) {
			return unchanged(question, 0);
		}
		String answerExcerpt = answerExcerpt(conversation.getId());

		long start = System.currentTimeMillis();
		String cached = aiCache.get("rw", String.join("\n", history), answerExcerpt, question);
		if (cached != null) {
			if (CACHE_NONE.equals(cached)) {
				return unchanged(question, System.currentTimeMillis() - start);
			}
			return new Result(cached, !cached.equals(question), System.currentTimeMillis() - start);
		}

		try {
			String cleaned = cleanup(llmClient.chat(SYSTEM_PROMPT,
					buildPrompt(history, answerExcerpt, question)));
			if (cleaned == null) {
				// 记下「不需要改写」，同一个问题不再白花一次调用
				aiCache.put("rw", CACHE_NONE, cacheSeconds, String.join("\n", history), answerExcerpt, question);
				return unchanged(question, System.currentTimeMillis() - start);
			}
			aiCache.put("rw", cleaned, cacheSeconds, String.join("\n", history), answerExcerpt, question);
			return new Result(cleaned, !cleaned.equals(question), System.currentTimeMillis() - start);
		} catch (Exception e) {
			// 改写是锦上添花：失败就按原问题检索，让问答继续走下去（也不缓存，下次还能再试）
			log.warn("追问改写失败，按原问题检索：{}", e.getMessage());
			return unchanged(question, System.currentTimeMillis() - start);
		}
	}

	/**
	 * 取最近 N 轮已还原的问题，按时间正序（老 -> 新）
	 *
	 * 顺便做两件事：去掉空串、单条截断 —— 历史是线索，不是正文。
	 */
	private List<String> history(Long conversationId) {
		List<String> rows;
		try {
			rows = conversationMapper.recentResolvedQueries(conversationId, Math.max(1, historyRounds));
		} catch (Exception e) {
			log.warn("读取会话历史失败，跳过改写：{}", e.getMessage());
			return List.of();
		}
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		List<String> ordered = new ArrayList<>(rows);
		Collections.reverse(ordered);
		List<String> kept = new ArrayList<>(ordered.size());
		for (String row : ordered) {
			if (row == null || row.isBlank()) {
				continue;
			}
			String text = row.trim();
			kept.add(text.length() <= historyLength ? text : text.substring(0, historyLength) + "…");
		}
		return kept;
	}

	/**
	 * 上一轮回答的节选：去掉换行、截断
	 *
	 * 回答里常出现「《某文档》」这类具体对象，追问的代词可能只指向它们。
	 */
	private String answerExcerpt(Long conversationId) {
		if (answerLength <= 0) {
			return "";
		}
		try {
			String answer = conversationMapper.lastAnswer(conversationId);
			if (answer == null || answer.isBlank()) {
				return "";
			}
			String text = answer.replace('\n', ' ').trim();
			return text.length() <= answerLength ? text : text.substring(0, answerLength) + "…";
		} catch (Exception e) {
			log.debug("读取上一轮回答失败，忽略回答上下文：{}", e.getMessage());
			return "";
		}
	}

	private String buildPrompt(List<String> history, String answerExcerpt, String question) {
		StringBuilder sb = new StringBuilder(320);
		if (history.size() == 1) {
			sb.append("上一轮问题：").append(history.get(0)).append('\n');
		} else {
			sb.append("最近提问（从早到晚）：\n");
			for (int i = 0; i < history.size(); i++) {
				sb.append(i + 1).append(". ").append(history.get(i)).append('\n');
			}
		}
		if (answerExcerpt != null && !answerExcerpt.isEmpty()) {
			sb.append("上一轮回答（节选）：").append(answerExcerpt).append('\n');
		}
		sb.append("本轮追问：").append(question);
		return sb.toString();
	}

	/**
	 * 是否值得改写
	 *
	 * 判断标准是「像不像追问」而不是「对不对」：宁可多调用一次模型，
	 * 也不要把「那这类方案的成本呢」这种短追问漏掉。
	 */
	private boolean suspected(String question) {
		if (question.length() <= minLength) {
			return true;
		}
		for (String marker : MARKERS) {
			if (question.contains(marker)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 清洗模型输出：去掉常见前缀/引号、只取第一行、限制长度
	 */
	private String cleanup(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return null;
		}
		// 只取第一行：模型偶尔会先来一句「改写后：」再换行给正文
		int lineBreak = text.indexOf('\n');
		if (lineBreak > 0) {
			text = text.substring(0, lineBreak).trim();
		}
		for (String prefix : new String[]{"改写后：", "改写：", "独立问题：", "问题："}) {
			if (text.startsWith(prefix)) {
				text = text.substring(prefix.length()).trim();
			}
		}
		if (text.length() >= 2 && (text.startsWith("\"") && text.endsWith("\""))
				|| (text.startsWith("「") && text.endsWith("」"))) {
			text = text.substring(1, text.length() - 1).trim();
		}
		if (text.isEmpty() || text.length() > MAX_REWRITE) {
			return null;
		}
		// 模型偶尔会把「无法改写」当答案返回，这类内容不能拿去检索
		if (text.contains("无法") || text.contains("抱歉")) {
			return null;
		}
		return text;
	}

	private Result unchanged(String question, long tookMs) {
		return new Result(question, false, tookMs);
	}
}
