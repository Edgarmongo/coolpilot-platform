package com.coolskyai.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.ai.dto.ChatDTO;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.entity.AiMessage;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;
import com.coolskyai.modules.ai.vo.Retrieved;
import com.coolskyai.util.JacksonUtils;

import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 知识库问答（RAG）
 *
 * 流程：检索 TopN → 拼上下文 → 调模型 → 返回答案 + 引用。
 *
 * 几个刻意的决定：
 *   1. 检索与搜索页共用一套打分和权限过滤（AiSearchService.retrieve），
 *      避免「搜得到但问不出」的两套逻辑。
 *   2. 没检索到内容就不调模型：没有资料时模型只会编，还白花一次 token。
 *   3. 模型没配 / 调不通时降级为「只回检索片段」，功能和成本都不浪费；
 *      降级原因写进 note，前端照实展示。
 *   4. 会话只在登录后落库；未登录也能问，只是不留历史 —— 不给匿名用户建垃圾数据。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class RagChatService {

	private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

	private static final int TOP_K = 6;
	private static final int MAX_QUESTION = 500;
	private static final int MAX_TITLE = 40;
	/** 检索侧只接受 100 字以内，长问题截断后再检索 */
	private static final int MAX_RETRIEVE_QUERY = 100;

	private static final String SYSTEM_PROMPT = """
			你是 CoolSkyAI 站点的技术助理，面向中文读者。
			规则：
			1. 只依据「资料」回答；资料没有覆盖的内容，直接说「站内资料没有提到」，不要补充常识或猜测。
			2. 先给结论，再给要点；要点用短句，必要时用 1. 2. 3. 编号，不要输出 Markdown 表格和代码块。
			3. 引用资料时在句末用 [编号] 标注，编号与资料列表一致；同一句可以标多个，如 [1][3]。
			4. 不要编造链接、价格、时间等具体信息。
			5. 用简体中文回答，控制在 300 字以内。
			""";

	/** 进上下文的最低相似度：低于它的片段只会给模型添乱 */
	@Value("${ai.chat.min-score:0.08}")
	private double minScore;

	@Autowired
	private AiSearchService aiSearchService;
	@Autowired
	private LlmClient llmClient;
	@Autowired
	private AiConversationMapper conversationMapper;
	@Autowired
	private TaskExecutor taskExecutor;

	/**
	 * 提问
	 */
	public Map<String, Object> chat(Long userId, ChatDTO dto) {
		String question = dto == null || dto.getQuestion() == null ? "" : dto.getQuestion().trim();
		if (question.isEmpty()) {
			throw new BadRequestException("请输入你的问题");
		}
		if (question.length() > MAX_QUESTION) {
			throw new BadRequestException("问题不要超过 " + MAX_QUESTION + " 个字符");
		}

		long start = System.currentTimeMillis();
		AiConversation conversation = resolveConversation(userId, dto.getConversationId(), question);

		List<Retrieved> retrieved = retrieve(question, userId, conversation);
		List<Map<String, Object>> citations = toCitations(retrieved);

		String answer;
		String note = "";
		boolean degraded = false;
		String model = llmClient.model();

		if (retrieved.isEmpty()) {
			answer = "站内暂时没有找到和「" + shortQuestion(question) + "」相关的内容。"
					+ "可以换个说法再问一次，或者到「意见反馈」里直接提问。";
		} else if (!llmClient.available()) {
			degraded = true;
			answer = null;
			note = "当前未配置大模型，先展示检索到的片段";
		} else {
			try {
				answer = llmClient.chat(SYSTEM_PROMPT, buildUserPrompt(question, retrieved));
			} catch (LlmException e) {
				degraded = true;
				answer = null;
				note = e.getMessage();
				log.warn("问答降级 userId={} question={}: {}", userId, shortQuestion(question), e.getMessage());
			}
		}

		if (conversation != null) {
			saveMessages(conversation, question, answer, note, citations, model);
		}

		Map<String, Object> data = new LinkedHashMap<>(10);
		data.put("conversationId", conversation == null ? null : conversation.getId());
		data.put("answer", answer);
		data.put("citations", citations);
		data.put("model", model);
		data.put("degraded", degraded);
		data.put("note", note);
		data.put("tookMs", System.currentTimeMillis() - start);
		return data;
	}

	/**
	 * 流式提问（SSE）
	 *
	 * 事件约定：
	 *   meta  → 引用、会话 id、模型、是否降级（先给前端，让来源先出现）
	 *   delta → 增量文本
	 *   done  → 完整答案与耗时（用于收尾与落库后的 id 回传）
	 *   error → 生成中出错（前端保留已收到的部分）
	 *
	 * 检索与校验在主线程同步做完（毫秒级），生成放线程池，
	 * 这样控制器能立刻返回 emitter，不至于占着请求线程等模型。
	 */
	public void chatStream(Long userId, ChatDTO dto, SseEmitter emitter) {
		String question = dto == null || dto.getQuestion() == null ? "" : dto.getQuestion().trim();
		if (question.isEmpty()) {
			throw new BadRequestException("请输入你的问题");
		}
		if (question.length() > MAX_QUESTION) {
			throw new BadRequestException("问题不要超过 " + MAX_QUESTION + " 个字符");
		}

		AiConversation conversation = resolveConversation(userId, dto.getConversationId(), question);
		List<Retrieved> retrieved = retrieve(question, userId, conversation);
		List<Map<String, Object>> citations = toCitations(retrieved);

		Map<String, Object> meta = new LinkedHashMap<>(8);
		meta.put("conversationId", conversation == null ? null : conversation.getId());
		meta.put("citations", citations);
		meta.put("model", llmClient.model());
		meta.put("degraded", !retrieved.isEmpty() && !llmClient.available());
		meta.put("note", retrieved.isEmpty() ? "" :
				(llmClient.available() ? "" : "当前未配置大模型，先展示检索到的片段"));
		send(emitter, "meta", meta);

		taskExecutor.execute(() -> runStream(emitter, conversation, question, retrieved, citations));
	}

	private void runStream(SseEmitter emitter, AiConversation conversation, String question,
	                       List<Retrieved> retrieved, List<Map<String, Object>> citations) {
		long start = System.currentTimeMillis();
		StringBuilder answer = new StringBuilder();
		String note = "";
		boolean degraded = false;
		String model = llmClient.model();

		try {
			if (retrieved.isEmpty()) {
				// 没有资料就不调模型，直接把结论当作一次增量推给前端
				String text = "站内暂时没有找到和「" + shortQuestion(question) + "」相关的内容。"
						+ "可以换个说法再问一次，或者到「意见反馈」里直接提问。";
				answer.append(text);
				send(emitter, "delta", Map.of("text", text));
			} else if (!llmClient.available()) {
				degraded = true;
				note = "当前未配置大模型，先展示检索到的片段";
			} else {
				llmClient.stream(SYSTEM_PROMPT, buildUserPrompt(question, retrieved), delta -> {
					answer.append(delta);
					send(emitter, "delta", Map.of("text", delta));
				});
			}
		} catch (LlmException e) {
			degraded = true;
			note = e.getMessage();
			// 已生成的半截答案照样保留：比清空重来更接近用户预期
			send(emitter, "error", Map.of("message", note,
					"partial", answer.length() > 0));
			log.warn("流式问答中断 question={}: {}", shortQuestion(question), e.getMessage());
		} catch (Exception e) {
			// 客户端断开时 send 会抛 IOException，这里静默结束，不当成业务错误
			log.debug("流式输出中止: {}", e.getMessage());
		}

		String finalAnswer = answer.length() > 0 ? answer.toString() : null;
		if (conversation != null) {
			saveMessages(conversation, question, finalAnswer, note, citations, model);
		}

		Map<String, Object> done = new LinkedHashMap<>(8);
		done.put("conversationId", conversation == null ? null : conversation.getId());
		done.put("answer", finalAnswer);
		done.put("degraded", degraded);
		done.put("note", note);
		done.put("tookMs", System.currentTimeMillis() - start);
		try {
			send(emitter, "done", done);
		} catch (Exception e) {
			// 客户端已断开：答案已经落库，这里静默结束即可
			log.debug("推送 done 失败（客户端可能已断开）: {}", e.getMessage());
		} finally {
			try {
				emitter.complete();
			} catch (Exception ignored) {
				// complete 在已超时/已断开时会抛，忽略
			}
		}
	}

	/**
	 * 写一个 SSE 事件；客户端已断开时抛异常，由调用方决定是否忽略
	 */
	private void send(SseEmitter emitter, String event, Object data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
		} catch (Exception e) {
			throw new IllegalStateException("SSE 发送失败", e);
		}
	}

	public PageResult<AiConversation> myConversations(Long userId, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 || pageSize > 50 ? 20 : pageSize;
		int total = conversationMapper.countConversations(userId);
		List<AiConversation> list = conversationMapper.pageConversations(userId, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 会话详情：消息按时间正序，引用解析成对象给前端直接渲染
	 */
	public Map<String, Object> conversationDetail(Long id, Long userId) {
		AiConversation conversation = conversationMapper.findConversation(id);
		// 不区分「不存在」和「不是你的」：避免用 id 探测他人会话是否存在
		if (conversation == null || !conversation.getUserId().equals(userId)) {
			throw new BadRequestException("会话不存在");
		}
		List<Map<String, Object>> messages = new ArrayList<>();
		for (AiMessage message : conversationMapper.listMessages(id, 100)) {
			Map<String, Object> row = new LinkedHashMap<>(8);
			row.put("id", message.getId());
			row.put("role", message.getRole());
			row.put("content", message.getContent());
			row.put("citations", parseCitations(message.getCitations()));
			row.put("model", message.getModel());
			row.put("createTime", message.getCreateTime());
			messages.add(row);
		}
		Map<String, Object> data = new LinkedHashMap<>(4);
		data.put("conversation", conversation);
		data.put("messages", messages);
		return data;
	}

	public void deleteConversation(Long id, Long userId) {
		AiConversation conversation = conversationMapper.findConversation(id);
		if (conversation == null || !conversation.getUserId().equals(userId)) {
			throw new BadRequestException("会话不存在");
		}
		conversationMapper.deleteMessages(id);
		conversationMapper.deleteConversation(id, userId);
	}

	/**
	 * 后台：当前模型配置（不含密钥）
	 */
	public Map<String, Object> llmStatus() {
		Map<String, Object> data = new LinkedHashMap<>(8);
		data.put("configured", llmClient.available());
		data.put("model", llmClient.model());
		data.put("embeddingModel", "见知识库索引");
		data.put("topK", TOP_K);
		data.put("maxQuestion", MAX_QUESTION);
		data.put("minScore", minScore);
		return data;
	}

	/**
	 * 后台：连通性自测，用最短的提示确认「能连上、能返回」
	 */
	public Map<String, Object> llmTest() {
		Map<String, Object> data = new LinkedHashMap<>(4);
		if (!llmClient.available()) {
			data.put("ok", false);
			data.put("message", "未配置大模型：请在环境变量里设置 AI_LLM_BASE_URL 与 AI_LLM_MODEL");
			return data;
		}
		long start = System.currentTimeMillis();
		try {
			String reply = llmClient.chat("你是连通性检查助手，只回复 OK。", "OK");
			data.put("ok", true);
			data.put("tookMs", System.currentTimeMillis() - start);
			data.put("reply", reply.length() > 60 ? reply.substring(0, 60) : reply);
		} catch (LlmException e) {
			data.put("ok", false);
			data.put("message", e.getMessage());
		}
		return data;
	}

	// ---------------- 内部 ----------------

	/**
	 * 检索上下文
	 *
	 * 追问往往只有「那退款呢」几个字，单独检索命中率极低。
	 * 这里在检索空手时把上一轮问题拼进来再试一次 ——
	 * 是「补救」而不是真正的指代消解，但成本为零，能救回大部分多轮对话。
	 */
	private List<Retrieved> retrieve(String question, Long userId, AiConversation conversation) {
		List<Retrieved> retrieved = retrieveOnce(question, userId);
		if (!retrieved.isEmpty() || conversation == null) {
			return retrieved;
		}
		String previous = conversationMapper.lastUserQuestion(conversation.getId());
		if (previous == null || previous.trim().isEmpty()) {
			return retrieved;
		}
		return retrieveOnce(question + " " + previous, userId);
	}

	private List<Retrieved> retrieveOnce(String query, Long userId) {
		String text = query.trim();
		if (text.length() > MAX_RETRIEVE_QUERY) {
			text = text.substring(0, MAX_RETRIEVE_QUERY);
		}
		List<Retrieved> all = aiSearchService.retrieve(text, userId, TOP_K);
		List<Retrieved> kept = new ArrayList<>(all.size());
		for (Retrieved item : all) {
			if (item.getScore() != null && item.getScore() >= minScore) {
				kept.add(item);
			}
		}
		return kept;
	}

	private AiConversation resolveConversation(Long userId, Long conversationId, String question) {
		if (userId == null) {
			// 匿名不落库：既能问，也不给数据库添垃圾
			return null;
		}
		if (conversationId != null) {
			AiConversation existing = conversationMapper.findConversation(conversationId);
			if (existing == null || !existing.getUserId().equals(userId)) {
				throw new BadRequestException("会话不存在");
			}
			return existing;
		}
		AiConversation created = new AiConversation();
		created.setUserId(userId);
		created.setTitle(shortQuestion(question));
		conversationMapper.insertConversation(created);
		return created;
	}

	private void saveMessages(AiConversation conversation, String question, String answer,
	                          String note, List<Map<String, Object>> citations, String model) {
		AiMessage userMessage = new AiMessage();
		userMessage.setConversationId(conversation.getId());
		userMessage.setRole("USER");
		userMessage.setContent(question);
		userMessage.setCitations(null);
		userMessage.setModel("");
		conversationMapper.insertMessage(userMessage);

		AiMessage assistant = new AiMessage();
		assistant.setConversationId(conversation.getId());
		assistant.setRole("ASSISTANT");
		assistant.setContent(answer != null ? answer : "（未生成回答：" + note + "）");
		assistant.setCitations(writeCitations(citations));
		assistant.setModel(model == null ? "" : model);
		conversationMapper.insertMessage(assistant);

		conversationMapper.touchConversation(conversation.getId(), 2);
	}

	/**
	 * 拼上下文：编号 + 标题 + 链接 + 正文，编号与引用列表一一对应
	 */
	private String buildUserPrompt(String question, List<Retrieved> retrieved) {
		StringBuilder sb = new StringBuilder(2048);
		sb.append("资料：\n");
		for (int i = 0; i < retrieved.size(); i++) {
			Retrieved item = retrieved.get(i);
			sb.append('[').append(i + 1).append("] 《").append(item.getTitle()).append("》");
			if (item.getUrl() != null && !item.getUrl().isEmpty()) {
				sb.append("（").append(item.getUrl()).append("）");
			}
			sb.append('\n').append(trimContent(item.getContent())).append("\n\n");
		}
		sb.append("问题：").append(question).append('\n');
		sb.append("请依据上面的资料回答，并在句末标注引用编号。");
		return sb.toString();
	}

	private String trimContent(String content) {
		if (content == null) {
			return "";
		}
		String text = content.trim();
		// 切块约 350 字，这里再兜一层上限，避免换更大分块后把上下文撑爆
		return text.length() <= 700 ? text : text.substring(0, 700) + "…";
	}

	private List<Map<String, Object>> toCitations(List<Retrieved> retrieved) {
		List<Map<String, Object>> list = new ArrayList<>(retrieved.size());
		for (int i = 0; i < retrieved.size(); i++) {
			Retrieved item = retrieved.get(i);
			Map<String, Object> row = new LinkedHashMap<>(8);
			row.put("index", i + 1);
			row.put("docId", item.getDocId());
			row.put("sourceType", item.getSourceType());
			row.put("title", item.getTitle());
			row.put("url", item.getUrl());
			row.put("snippet", item.getSnippet());
			row.put("score", item.getScore());
			list.add(row);
		}
		return list;
	}

	private String writeCitations(List<Map<String, Object>> citations) {
		return JacksonUtils.writeValueAsString(citations);
	}

	private List<Map<String, Object>> parseCitations(String json) {
		if (json == null || json.isEmpty()) {
			return List.of();
		}
		List<Map<String, Object>> parsed =
				JacksonUtils.readValue(json, new TypeReference<List<Map<String, Object>>>() {
				});
		return parsed == null ? List.of() : parsed;
	}

	private String shortQuestion(String question) {
		String text = question.replaceAll("\\s+", " ").trim();
		return text.length() <= MAX_TITLE ? text : text.substring(0, MAX_TITLE) + "…";
	}
}
