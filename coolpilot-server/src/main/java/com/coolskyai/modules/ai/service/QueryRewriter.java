package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;

/**
 * @Description: 追问改写（把「那退款呢」补成独立问题）
 *
 * 为什么需要它：检索是「拿一句话去比对」，而追问天然缺主语。
 * 「那退款呢」里的「那」指向上一轮的「商品退款流程」，字面检索看不到这层关系，
 * 于是要么召回不到，要么靠「本轮 + 上一轮」硬拼 —— 拼出来的句子语义是糊的，
 * 向量也测不准。
 *
 * 做法：让模型只做一件事 —— 补全，不做回答。三条护栏保证它不会跑偏：
 *   1. 明显的独立问题不调用模型（省一次 1 秒的往返，见 {@link #suspected}）
 *   2. 输出做清洗与长度限制，异常/空/超长一律回退到原问题
 *   3. 改写结果只用于检索，回答与界面仍展示用户原话
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
			2. 用上一轮问题补全代词与省略成分（它、这个、那个、还有呢）。
			3. 专有名词、型号、版本号原样保留，不要换成同义词。
			4. 本轮问题本身已经独立时，原样输出。
			5. 不超过 40 个字，不要换行。
			""";

	/** 追问的信号词：出现任意一个，就值得让模型补全一次 */
	private static final String[] MARKERS = {"这", "那", "它", "其", "该", "上面", "刚才", "前面", "还有", "呢", "同样"};

	@Value("${ai.chat.rewrite-enabled:true}")
	private boolean enabled;

	/** 短于这个长度的问题，几乎一定是追问（「退款呢」「具体点」） */
	@Value("${ai.chat.rewrite-min-length:12}")
	private int minLength;

	/** 改写结果超过这个长度基本是模型跑偏了，直接丢弃 */
	private static final int MAX_REWRITE = 100;

	@Autowired
	private LlmClient llmClient;
	@Autowired
	private AiConversationMapper conversationMapper;

	/**
	 * 改写结果
	 *
	 * @param query    实际用于检索的查询（失败时就是原问题）
	 * @param rewritten 是否真的改写过，便于后台排查
	 * @param tookMs   改写耗时，0 表示没有调用模型
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
			return new Result(question, false, 0);
		}
		if (!suspected(question)) {
			return new Result(question, false, 0);
		}
		String previous = conversationMapper.lastUserQuestion(conversation.getId());
		if (previous == null || previous.isBlank()) {
			return new Result(question, false, 0);
		}

		long start = System.currentTimeMillis();
		try {
			String raw = llmClient.chat(SYSTEM_PROMPT,
					"上一轮问题：" + previous + "\n本轮追问：" + question);
			String cleaned = cleanup(raw);
			if (cleaned == null) {
				return new Result(question, false, System.currentTimeMillis() - start);
			}
			return new Result(cleaned, !cleaned.equals(question), System.currentTimeMillis() - start);
		} catch (Exception e) {
			// 改写是锦上添花：失败就按原问题检索，让问答继续走下去
			log.warn("追问改写失败，按原问题检索：{}", e.getMessage());
			return new Result(question, false, System.currentTimeMillis() - start);
		}
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
}
