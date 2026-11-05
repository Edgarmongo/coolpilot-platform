package com.coolskyai.modules.ai.service;

import com.coolskyai.modules.ai.entity.AiUsage;

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
 * 做法：让模型只做一件事 —— 补全，不做回答。护栏有五层：
 *   1. 明显的独立问题不调用模型（省一次往返，见 {@link #suspected}）
 *   2. 上下文给最近 N 轮**已还原**的问题（不是原话）：上一轮若也是追问，
 *      拿它的原话当锚点等于在模糊上再猜一次
 *   3. 再给最近两轮**回答**的节选：「第一篇讲什么」「那第二篇呢」这类序号指代
 *      只存在于回答里，而且常常指向更早的那条
 *   4. 输出做清洗与长度限制，异常/空/超长一律回退到原问题
 *   5. 结果进 Redis，键里带模型名与策略版本 —— 换模型后旧改写不再适用
 *
 * 失败不影响问答：改不出来就按原问题检索，最差退化成改造前的行为。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class QueryRewriter {

	private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

	/**
	 * 改写策略版本
	 *
	 * 提示词或上下文拼法一改，缓存里的旧结果就不该再用。改规则时手动 +1。
	 */
	private static final String STRATEGY_VERSION = "2";

	private static final String SYSTEM_PROMPT = """
			你是检索查询改写器，唯一任务是把「追问」补全成不依赖上下文也能看懂的独立问题。
			规则：
			1. 只输出改写后的问题本身，不要解释、不要回答问题、不要加引号或「改写：」之类的前缀。
			2. 「那 X 呢」有两种意思，必须分清：
			   - X 是新对象（「那 RAG 手册呢」）→ 换话题，改写成围绕 X 的问题，不要把上一轮的对象塞进去；
			   - X 是上一轮对象的某个方面（「那成本呢」「那分块呢」）→ 补成「上一轮对象 + X」。
			3. 代词（它、这个、那个）先指向最近提问里明确提到的对象；提问里找不到时，
			   再看「最近回答」里提到的对象，不要凭空编。
			4. 「第一篇」「第二个」「上面那个」这类序号指代，回到最近回答里那个列表去还原。
			5. 最近回答按时间从早到晚排列：越靠后越接近当前话题，但指代明确指向更早的内容时以它为准。
			6. 不要引入对话里没出现过的新话题，也不要把两个不相干的对象拼在一起。
			7. 专有名词、型号、版本号原样保留；本轮已独立时原样输出；不超过 40 字，不换行。
			示例：
			最近提问：1. Spring Boot 4 工程脚手架包含什么？
			本轮追问：那 RAG 手册呢
			RAG 手册包含什么？
			最近提问：1. Spring Boot 4 工程脚手架包含什么？ 2. RAG 手册包含什么？
			本轮追问：那分块呢
			RAG 手册的分块怎么讲？
			最近回答：1. 站内技术方案有：1.《企业级 RAG 系统设计手册》… 2.《Java 工程师的 AI 应用开发实战》…
			本轮追问：第二篇讲什么
			《Java 工程师的 AI 应用开发实战》讲什么？
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

	/** 单条历史问题截断长度：长问题进 prompt 只会淹没本轮追问 */
	@Value("${ai.chat.rewrite-history-length:60}")
	private int historyLength;

	/**
	 * 带进 prompt 的回答轮数
	 *
	 * 2 轮是「够用且不吵」的值：序号指代常常指向**上一条**回答 ——
	 * 上一轮已经在回答「第一篇」，那个六条列表存在于更早那条回答里。
	 * 再多带会让 prompt 里堆三个以上话题，模型反而更容易挑错。
	 */
	@Value("${ai.chat.rewrite-answer-rounds:2}")
	private int answerRounds;

	/**
	 * 单条回答进 prompt 的节选长度
	 *
	 * 300 字是「能看全一个前三条列表」的量：180 字只覆盖到第二条，
	 * 用户问「第三个方案是什么」时就找不到依据了。整段回答既不必要也太贵。
	 * 0 表示完全不用回答内容（退回只看提问的旧行为）。
	 */
	@Value("${ai.chat.rewrite-answer-length:300}")
	private int answerLength;

	/**
	 * 回答节选的总预算
	 *
	 * 轮数 × 单条长度可能超预算，超了就按轮数均分 ——
	 * 宁可每条看到的内容少一点，也不要让 prompt 被回答内容占满。
	 */
	@Value("${ai.chat.rewrite-answer-budget:600}")
	private int answerBudget;

	/**
	 * 回溯上限：首次改写「没解开」时，最多往回看这么多轮回答
	 *
	 * 第 5 轮问「第一份呢」时，那份列表在 4 轮之前 —— 固定 2 轮永远够不到。
	 * 但每次都带 5 轮又会把 prompt 堆满、模型挑错对象，
	 * 所以做成「先按 2 轮试，解不开再回溯」：多数追问只花一次调用。
	 */
	@Value("${ai.chat.rewrite-max-rounds:5}")
	private int maxRounds;

	/** 是否允许回溯升级（关掉就永远是固定轮数） */
	@Value("${ai.chat.rewrite-escalate:true}")
	private boolean escalate;

	/** 改写结果缓存时间（秒） */
	@Value("${ai.chat.rewrite-cache-seconds:3600}")
	private long cacheSeconds;

	/** 序号扫描的回答条数上限：再多也不会有人在第 30 条之后再问「第三份」 */
	private static final int MAX_SCAN_ANSWERS = 20;

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

		long start = System.currentTimeMillis();

		/*
		 * 序号指代（「第一份呢」「第二个」）不走模型：模型会根据最近的话题**编**一个对象出来，
		 * 而且编得很像真的 —— 实测把「第一份呢」改成了上一轮的缓存问题，我们无从察觉。
		 * 这里改成确定性解析：按出现顺序收集窗口内回答提到的《资料名》，取第 N 个。
		 * 收集方向是从早到晚并去重，这样重复提到的旧对象不会打乱顺序。
		 */
		Ordinal ordinal = parseOrdinal(question);
		if (ordinal != null) {
			List<String> titles = titlesFromStart(conversation.getId(), ordinal.index());
			if (ordinal.index() <= titles.size()) {
				String query = buildOrdinalQuery(question, ordinal.phrase(), titles.get(ordinal.index() - 1));
				log.debug("序号指代确定性解析：{} -> {}", question, query);
				return new Result(query, true, System.currentTimeMillis() - start);
			}
			/*
			 * 序号超出对话里提到的份数：**不交给模型**。
			 * 实测它会把「第四份呢」编成「《某手册》的第四部分」—— 听着合理，其实不存在。
			 * 这里直接按原问题返回，让答案老实说「资料里只提到 N 份」，比编一个强。
			 */
			log.info("序号指代超出范围：{}（对话里只提到 {} 份）", question, titles.size());
			return unchanged(question, System.currentTimeMillis() - start);
		}

		Result first = attempt(question, history, answers(conversation.getId(), answerRounds));
		/*
		 * 回退升级：第一次没解开（结果里还留着序号/代词，或者干脆没改）时，
		 * 再把窗口拉宽到 maxRounds 试一次。
		 * 只在「确实没解开」时多花一次调用 —— 多数追问一次就够。
		 */
		if (escalate && maxRounds > answerRounds && unresolved(first.query(), question)) {
			Result wider = attempt(question, history, answers(conversation.getId(), maxRounds));
			if (!wider.query().equals(first.query())) {
				log.debug("追问改写回溯升级：{} 轮 -> {} 轮，结果：{}", answerRounds, maxRounds, wider.query());
				return new Result(wider.query(), wider.rewritten(), System.currentTimeMillis() - start);
			}
		}
		return new Result(first.query(), first.rewritten(), System.currentTimeMillis() - start);
	}

	/**
	 * 一次改写尝试：先查缓存，再调模型
	 *
	 * 缓存的键里带着这次实际用到的上下文，所以窄窗口与宽窗口天然是两条缓存，
	 * 不会互相污染。
	 */
	private Result attempt(String question, List<String> history, List<String> answers) {
		String[] keyParts = {STRATEGY_VERSION, llmClient.model(),
				String.join("\n", history), String.join("\n", answers), question};
		String cached = aiCache.get("rw", keyParts);
		if (cached != null) {
			if (CACHE_NONE.equals(cached)) {
				return unchanged(question, 0);
			}
			return new Result(cached, !cached.equals(question), 0);
		}
		try {
			String cleaned = cleanup(llmClient.chat(AiUsage.SCENE_REWRITE, SYSTEM_PROMPT,
					buildPrompt(history, answers, question)));
			if (cleaned == null) {
				// 记下「不需要改写」，同一个问题不再白花一次调用
				aiCache.put("rw", CACHE_NONE, cacheSeconds, keyParts);
				return unchanged(question, 0);
			}
			aiCache.put("rw", cleaned, cacheSeconds, keyParts);
			return new Result(cleaned, !cleaned.equals(question), 0);
		} catch (Exception e) {
			// 改写是锦上添花：失败就按原问题检索，让问答继续走下去（也不缓存，下次还能再试）
			log.warn("追问改写失败，按原问题检索：{}", e.getMessage());
			return unchanged(question, 0);
		}
	}

	/**
	 * 从会话开头收集资料名，够 need 个就停
	 *
	 * 序号指的是「用户最早看到的那个列表」，所以从第一条回答开始扫，
	 * 而不是从最近几轮 —— 问过几轮之后，那个列表早就滑出窗口了。
	 * 最多扫 20 条回答，避免超长会话把这里拖慢（每条只做一次正则）。
	 */
	private List<String> titlesFromStart(Long conversationId, int need) {
		List<String> rows;
		try {
			rows = conversationMapper.answersFromStart(conversationId, MAX_SCAN_ANSWERS);
		} catch (Exception e) {
			log.debug("读取会话回答失败：{}", e.getMessage());
			return List.of();
		}
		return titlesOf(rows, need);
	}

	/**
	 * 序号指代：第 N 份 / 第 N 篇，记下序号与原始短语
	 */
	private record Ordinal(int index, String phrase) {
	}

	/**
	 * 识别「第 + 数字 + 量词」
	 *
	 * 返回 null 表示不是序号指代（或数字解析不出来），交给模型的语义路径。
	 */
	private Ordinal parseOrdinal(String question) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("第\\s*([0-9]+|[一二三四五六七八九十]+)\\s*(份|篇|个|条|本|节|章)").matcher(question);
		if (!matcher.find()) {
			return null;
		}
		int index = ordinal(matcher.group(1));
		return index <= 0 ? null : new Ordinal(index, matcher.group());
	}

	/**
	 * 把序号短语换成具体资料名，剩下的部分当作对这篇资料的追问
	 *
	 * 「第三份的评估指标呢」→《X》评估指标
	 */
	private String buildOrdinalQuery(String question, String phrase, String title) {
		String rest = question.replace(phrase, "")
				.replaceAll("^(那|那么|还有|这些|其中)", "")
				.replaceAll("^[的\\s，,。]*", "")
				.replaceAll("[呢吗][？?]?$", "")
				.trim();
		String query = "《" + title + "》" + (rest.isEmpty() ? "讲什么？" : rest);
		return query.length() <= MAX_REWRITE ? query : query.substring(0, MAX_REWRITE);
	}

	/**
	 * 中文/阿拉伯数字序号转 int（只支持到 99，够用）
	 */
	private int ordinal(String raw) {
		if (raw == null || raw.isEmpty()) {
			return -1;
		}
		if (raw.matches("[0-9]+")) {
			return Integer.parseInt(raw);
		}
		String digits = "零一二三四五六七八九";
		if (raw.length() == 1) {
			int value = digits.indexOf(raw.charAt(0));
			return value < 0 ? -1 : value;
		}
		// 十 / 十几 / 二十几
		if (raw.charAt(0) == '十') {
			int tail = raw.length() == 1 ? 0 : digits.indexOf(raw.charAt(1));
			return 10 + Math.max(tail, 0);
		}
		int head = digits.indexOf(raw.charAt(0));
		if (head < 0) {
			return -1;
		}
		if (raw.charAt(1) == '十') {
			int tail = raw.length() > 2 ? digits.indexOf(raw.charAt(2)) : 0;
			return head * 10 + Math.max(tail, 0);
		}
		return head;
	}

	/**
	 * 按出现顺序收集回答里提到的《资料名》，去重
	 *
	 * 入参是按时间正序排好的回答节选，所以收集出来的顺序就是用户看到的编号顺序。
	 */
	private List<String> titlesOf(List<String> answers, int need) {
		// 用「去空白与标点」的形式做去重键：同一个标题在不同回答里常被写成
		// 《Java 工程师的 AI 应用开发实战》与《Java工程师的AI应用开发实战》，
		// 不归一化就会被当成两份，序号整体错位。
		java.util.Map<String, String> unique = new java.util.LinkedHashMap<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("《([^》]{2,40})》").matcher("");
		for (String answer : answers) {
			matcher.reset(answer);
			while (matcher.find()) {
				String title = matcher.group(1).trim();
				String key = title.replaceAll("[\\s\\u3000·・:：，,。.、\\-—_]", "");
				unique.putIfAbsent(key, title);
				if (unique.size() >= need) {
					return new ArrayList<>(unique.values());
				}
			}
		}
		return new ArrayList<>(unique.values());
	}

	/**
	 * 改写结果是否「还没解开」
	 *
	 * 判据是「里面还留着需要上下文才能懂的词」：
	 * 序号（第一篇 / 第 2 个）、代词（它 / 这个 / 那个）、以及「上面 / 刚才」这类位置指代。
	 * 刻意不含「其」：它在「其他」「其中」里太常见，会频繁触发多余的回溯。
	 * 另外「原样返回」也算没解开 —— 追问本该被补全，不动就是没做到。
	 */
	private boolean unresolved(String resolved, String question) {
		if (resolved == null || resolved.isBlank()) {
			return true;
		}
		if (resolved.equals(question)) {
			return true;
		}
		if (resolved.matches(".*第\\s*[0-9一二三四五六七八九十]+\\s*(篇|个|份|条|章|讲|节).*")) {
			return true;
		}
		for (String marker : new String[]{"它", "这个", "那个", "上面", "刚才"}) {
			if (resolved.contains(marker)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 取最近 N 轮已还原的问题，按时间正序（老 -> 新）
	 */
	private List<String> history(Long conversationId) {
		List<String> rows;
		try {
			rows = conversationMapper.recentResolvedQueries(conversationId, Math.max(1, historyRounds));
		} catch (Exception e) {
			log.warn("读取会话历史失败，跳过改写：{}", e.getMessage());
			return List.of();
		}
		return excerpts(rows, historyLength);
	}

	/**
	 * 取最近 N 轮回答的节选，按时间正序（老 -> 新）
	 *
	 * 回答里常出现「《某文档》」这类具体对象与列表，追问的代词/序号可能只指向它们。
	 */
	private List<String> answers(Long conversationId, int rounds) {
		if (answerLength <= 0 || rounds <= 0) {
			return List.of();
		}
		List<String> rows;
		try {
			rows = conversationMapper.recentAnswers(conversationId, rounds);
		} catch (Exception e) {
			log.debug("读取历史回答失败，忽略回答上下文：{}", e.getMessage());
			return List.of();
		}
		int perAnswer = Math.min(answerLength, Math.max(60, answerBudget / Math.max(1, rounds)));
		return excerpts(rows, perAnswer);
	}

	/**
	 * 倒序入参 → 正序输出，并做空串过滤与单条截断
	 *
	 * 去掉换行很关键：回答是分段的，带换行会让 prompt 看起来像多个对话，
	 * 模型容易把某一行当成新的问题。
	 */
	private List<String> excerpts(List<String> rows, int limit) {
		if (rows == null || rows.isEmpty() || limit <= 0) {
			return List.of();
		}
		List<String> ordered = new ArrayList<>(rows);
		Collections.reverse(ordered);
		List<String> kept = new ArrayList<>(ordered.size());
		for (String row : ordered) {
			if (row == null || row.isBlank()) {
				continue;
			}
			String text = row.replace('\n', ' ').trim();
			kept.add(text.length() <= limit ? text : text.substring(0, limit) + "…");
		}
		return kept;
	}

	private String buildPrompt(List<String> history, List<String> answers, String question) {
		StringBuilder sb = new StringBuilder(480);
		if (history.size() == 1) {
			sb.append("上一轮问题：").append(history.get(0)).append('\n');
		} else {
			sb.append("最近提问（从早到晚）：\n");
			for (int i = 0; i < history.size(); i++) {
				sb.append(i + 1).append(". ").append(history.get(i)).append('\n');
			}
		}
		if (answers.size() == 1) {
			sb.append("上一轮回答（节选）：").append(answers.get(0)).append('\n');
		} else if (answers.size() > 1) {
			sb.append("最近回答（节选，从早到晚）：\n");
			for (int i = 0; i < answers.size(); i++) {
				sb.append(i + 1).append(". ").append(answers.get(i)).append('\n');
			}
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
