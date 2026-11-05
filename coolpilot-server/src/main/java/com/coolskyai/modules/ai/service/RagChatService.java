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
import com.coolskyai.exception.ShareExpiredException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.ai.dto.ChatDTO;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.entity.AiMessage;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;
import com.coolskyai.modules.ai.vo.Retrieved;
import com.coolskyai.util.JacksonUtils;

import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
	/** 分享默认有效期（天）与上限 */
	private static final int DEFAULT_SHARE_DAYS = 30;
	private static final int MAX_SHARE_DAYS = 365;

	private static final java.security.SecureRandom random = new java.security.SecureRandom();

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
	@Autowired
	private ShareGuard shareGuard;

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

		Long answerMessageId = null;
		if (conversation != null) {
			answerMessageId = saveMessages(conversation, question, answer, note, citations, model);
		}

		Map<String, Object> data = new LinkedHashMap<>(12);
		data.put("conversationId", conversation == null ? null : conversation.getId());
		data.put("answerMessageId", answerMessageId);
		data.put("favorited", false);
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

		/*
		 * 客户端关页面 / 点「停止」时，容器会回调 onError 或 onTimeout。
		 * 用它标记取消，生成线程看到标记就中断读取上游 ——
		 * 不这么做的话，用户已经走了，模型还在自顾自地烧 token。
		 */
		AtomicBoolean cancelled = new AtomicBoolean(false);
		emitter.onError(e -> cancelled.set(true));
		emitter.onTimeout(() -> cancelled.set(true));

		taskExecutor.execute(() -> runStream(emitter, conversation, question, retrieved, citations, cancelled));
	}

	private void runStream(SseEmitter emitter, AiConversation conversation, String question,
	                       List<Retrieved> retrieved, List<Map<String, Object>> citations,
	                       AtomicBoolean cancelled) {
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
					if (cancelled.get()) {
						// 抛出去中断上游读取（Consumer 不允许受检异常，用非受检的自定义异常）
						throw new StreamCancelledException("客户端已断开");
					}
					answer.append(delta);
					send(emitter, "delta", Map.of("text", delta));
				});
			}
		} catch (StreamCancelledException e) {
			// 用户主动停止或关闭页面：已生成的部分仍然落库，方便下次接着看
			cancelled.set(true);
			log.debug("客户端断开，停止生成 question={} 已生成 {} 字", shortQuestion(question), answer.length());
		} catch (LlmException e) {
			degraded = true;
			note = e.getMessage();
			// 已生成的半截答案照样保留：比清空重来更接近用户预期
			sendQuietly(emitter, "error", Map.of("message", note,
					"partial", answer.length() > 0));
			log.warn("流式问答中断 question={}: {}", shortQuestion(question), e.getMessage());
		} catch (Exception e) {
			// 兜底：任何没预料到的异常都不该吞掉已经生成的内容
			log.warn("流式输出异常 question={}: {}", shortQuestion(question), e.getMessage());
		}

		String finalAnswer = answer.length() > 0 ? answer.toString() : null;
		Long answerMessageId = null;
		if (conversation != null) {
			// 断连时也保存：用户下次打开历史能看到已经生成的部分
			answerMessageId = saveMessages(conversation, question, finalAnswer, note, citations, model);
		}

		if (cancelled.get()) {
			// 连接已断，发什么都没人收；落库后直接结束
			completeQuietly(emitter);
			return;
		}

		Map<String, Object> done = new LinkedHashMap<>(8);
		done.put("conversationId", conversation == null ? null : conversation.getId());
		done.put("answerMessageId", answerMessageId);
		done.put("favorited", false);
		done.put("answer", finalAnswer);
		done.put("degraded", degraded);
		done.put("note", note);
		done.put("tookMs", System.currentTimeMillis() - start);
		try {
			sendQuietly(emitter, "done", done);
		} catch (Exception e) {
			// 客户端已断开：答案已经落库，这里静默结束即可
			log.debug("推送 done 失败（客户端可能已断开）: {}", e.getMessage());
		} finally {
			completeQuietly(emitter);
		}
	}

	private void completeQuietly(SseEmitter emitter) {
		try {
			emitter.complete();
		} catch (Exception ignored) {
			// complete 在已超时 / 已断开时会抛，忽略即可
		}
	}

	/**
	 * 写一个 SSE 事件；客户端已断开时抛异常，由调用方决定是否忽略
	 */
	private void send(SseEmitter emitter, String event, Object data) {
		try {
			emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
		} catch (Exception e) {
			/*
			 * 客户端断开后这里必失败（ResponseBodyEmitter has already completed）。
			 * 抛专用异常而不是 IllegalStateException：
			 * 前者会被识别为「用户不看了」，后者会被当成故障记日志、甚至包成模型错误。
			 */
			throw new StreamCancelledException("客户端已断开", e);
		}
	}

	/**
	 * 发事件但忽略失败：用于已经准备收尾的场景（错误提示、done）
	 */
	private void sendQuietly(SseEmitter emitter, String event, Object data) {
		try {
			send(emitter, event, data);
		} catch (Exception ignored) {
			// 连接已断，发不出去是预期行为
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
		List<AiMessage> rows = conversationMapper.listMessages(id, 100);
		Set<Long> favorited = favoritedIds(userId, rows);
		List<Map<String, Object>> messages = new ArrayList<>();
		for (AiMessage message : rows) {
			Map<String, Object> row = new LinkedHashMap<>(10);
			row.put("id", message.getId());
			row.put("role", message.getRole());
			row.put("content", message.getContent());
			row.put("citations", parseCitations(message.getCitations()));
			row.put("model", message.getModel());
			row.put("createTime", message.getCreateTime());
			row.put("favorited", favorited.contains(message.getId()));
			row.put("shareToken", message.getShareToken());
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

	// ---------------- 收藏与分享 ----------------

	/**
	 * 这一批回答里哪些被当前用户收藏了（一次查询，避免每条消息打一次接口）
	 */
	private Set<Long> favoritedIds(Long userId, List<AiMessage> messages) {
		if (userId == null || messages.isEmpty()) {
			return Set.of();
		}
		List<Long> ids = new ArrayList<>();
		for (AiMessage message : messages) {
			if ("ASSISTANT".equals(message.getRole())) {
				ids.add(message.getId());
			}
		}
		if (ids.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(conversationMapper.listFavoritedAnswerIds(userId, ids));
	}

	/**
	 * 分享某条回答
	 *
	 * 链接是有寿命的：默认 30 天，最长 365 天，也可以选「长期有效」。
	 * 没有有效期时，分享出去的链接等于永久公开，用户迟早会忘 ——
	 * 到期自动失效比指望用户回来点「取消分享」现实得多。
	 *
	 * 未过期就复用原链接（重复点分享不会换链接，也不会重置计数）；
	 * 已过期则重新生成一个，计数归零。
	 *
	 * @param days 有效天数，null 用默认值；小于等于 0 表示长期有效
	 */
	public Map<String, Object> shareAnswer(Long messageId, Long userId, Integer days) {
		AiMessage message = conversationMapper.findAnswerOwned(messageId, userId);
		if (message == null) {
			throw new BadRequestException("回答不存在");
		}
		Integer validDays = normalizeShareDays(days);
		String existing = message.getShareToken();
		boolean alive = existing != null && !existing.isEmpty()
				&& (message.getShareExpiresAt() == null || message.getShareExpiresAt().after(new Date()));
		if (alive) {
			return shareInfo(existing, message.getShareExpiresAt(),
					message.getShareViews(), message.getShareLikes());
		}
		Date expiresAt = validDays == null ? null
				: new Date(System.currentTimeMillis() + validDays * 24L * 3600 * 1000);
		for (int i = 0; i < 5; i++) {
			String token = randomToken();
			try {
				conversationMapper.updateShareToken(messageId, token, expiresAt);
				return shareInfo(token, expiresAt, 0, 0);
			} catch (Exception e) {
				// 撞上唯一索引（概率极低）就换一个
				log.debug("分享 token 冲突，重试：{}", e.getMessage());
			}
		}
		throw new BadRequestException("生成分享链接失败，请稍后再试");
	}

	/**
	 * 撤销分享：链接立即失效
	 */
	public void revokeShare(Long messageId, Long userId) {
		AiMessage message = conversationMapper.findAnswerOwned(messageId, userId);
		if (message == null) {
			throw new BadRequestException("回答不存在");
		}
		conversationMapper.clearShareToken(messageId);
	}

	/**
	 * 公开读取分享的答案（不含任何用户信息）
	 */
	public Map<String, Object> sharedAnswer(String token) {
		AiMessage message = requireShare(token);
		Map<String, Object> data = new LinkedHashMap<>(16);
		data.put("question", conversationMapper.findQuestionBefore(message.getConversationId(), message.getId()));
		data.put("answer", message.getContent());
		data.put("citations", parseCitations(message.getCitations()));
		data.put("model", message.getModel());
		data.put("createTime", message.getCreateTime());
		data.put("expiresAt", message.getShareExpiresAt());
		data.put("views", nullToZero(message.getShareViews()));
		data.put("likes", nullToZero(message.getShareLikes()));
		return data;
	}

	/**
	 * 记一次阅读
	 *
	 * 放在独立的 POST 里而不是 GET 详情里：详情要能被 SSR 和爬虫安全地取，
	 * 而且 SSR 请求的源 IP 是 Nuxt 服务器，所有人共用一个，
	 * 按 IP 去重会把整站访问压成一条记录。由浏览器上报才拿得到真实访客 IP。
	 */
	public Map<String, Object> viewShare(String token, String ip) {
		AiMessage message = requireShare(token);
		if (shareGuard.markView(token.trim(), ip)) {
			conversationMapper.increaseShareViews(message.getId());
		}
		return shareStats(message.getId(), token.trim(), ip);
	}

	/**
	 * 点赞 / 取消点赞（同一 IP 只算一次，状态存在 Redis）
	 */
	public Map<String, Object> likeShare(String token, String ip, boolean liked) {
		AiMessage message = requireShare(token);
		if (liked) {
			if (shareGuard.like(token.trim(), ip)) {
				conversationMapper.increaseShareLikes(message.getId());
			}
		} else if (shareGuard.unlike(token.trim(), ip)) {
			conversationMapper.decreaseShareLikes(message.getId());
		}
		return shareStats(message.getId(), token.trim(), ip);
	}

	/**
	 * 取分享内容并校验有效期
	 */
	private AiMessage requireShare(String token) {
		if (token == null || token.trim().isEmpty() || token.length() > 32) {
			throw new BadRequestException("分享链接无效");
		}
		AiMessage message = conversationMapper.findByShareToken(token.trim());
		if (message == null) {
			throw new BadRequestException("分享内容不存在或已失效");
		}
		if (message.getShareExpiresAt() != null && !message.getShareExpiresAt().after(new Date())) {
			throw new ShareExpiredException("该分享链接已过期，请让作者重新分享");
		}
		return message;
	}

	/**
	 * 重新读一次计数，避免返回自增前的旧值
	 */
	private Map<String, Object> shareStats(Long messageId, String token, String ip) {
		AiMessage stats = conversationMapper.findShareStats(messageId);
		Map<String, Object> data = new LinkedHashMap<>(4);
		data.put("views", stats == null ? 0 : nullToZero(stats.getShareViews()));
		data.put("likes", stats == null ? 0 : nullToZero(stats.getShareLikes()));
		data.put("liked", shareGuard.isLiked(token, ip));
		return data;
	}

	private Map<String, Object> shareInfo(String token, Date expiresAt, Integer views, Integer likes) {
		Map<String, Object> data = new LinkedHashMap<>(4);
		data.put("token", token);
		data.put("path", "/share/" + token);
		data.put("expiresAt", expiresAt);
		data.put("views", nullToZero(views));
		data.put("likes", nullToZero(likes));
		return data;
	}

	/**
	 * 有效期归一化：默认 30 天，1~365 天之间取原值，<=0 视为长期有效
	 */
	private Integer normalizeShareDays(Integer days) {
		if (days == null) {
			return DEFAULT_SHARE_DAYS;
		}
		if (days <= 0) {
			return null;
		}
		return Math.min(days, MAX_SHARE_DAYS);
	}

	private int nullToZero(Integer value) {
		return value == null ? 0 : value;
	}

	/**
	 * 12 位小写字母数字，够用且短到能贴进聊天框
	 */
	private String randomToken() {
		String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder sb = new StringBuilder(12);
		for (int i = 0; i < 12; i++) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return sb.toString();
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

	private Long saveMessages(AiConversation conversation, String question, String answer,
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
		// 返回 id：前端拿到它才能收藏 / 分享这一条回答
		return assistant.getId();
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
