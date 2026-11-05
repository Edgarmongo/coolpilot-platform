package com.coolskyai.modules.ai.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.entity.AiMessage;
import com.coolskyai.modules.ai.mapper.AiConversationMapper;
import com.coolskyai.util.JacksonUtils;

import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 后台问答记录
 *
 * 有了这个页面才能回答三个运营问题：
 *   1. 用户到底在问什么（写出更好的文章/商品说明）
 *   2. 哪些问题没被检索命中（索引缺内容，还是措辞对不上）
 *   3. 模型回答得怎么样（引用为空、降级回答一眼能看出来）
 *
 * 只读，不提供删除：问答记录是排查依据，删掉就查不了了。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class AiAdminService {

	/** 一次最多取这么多条消息，够看清一次会话，也不至于把响应撑爆 */
	private static final int MAX_MESSAGES = 100;

	@Autowired
	private AiConversationMapper conversationMapper;

	public PageResult<Map<String, Object>> conversations(String keyword, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
		String kw = keyword == null ? null : keyword.trim();
		int total = conversationMapper.countAll(kw);
		List<Map<String, Object>> list = conversationMapper.pageAll(kw, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 会话详情：问题与回答成对展示，附引用条数
	 */
	public Map<String, Object> detail(Long conversationId) {
		AiConversation conversation = conversationMapper.findConversation(conversationId);
		if (conversation == null) {
			throw new BadRequestException("会话不存在");
		}
		List<AiMessage> messages = conversationMapper.listMessages(conversationId, MAX_MESSAGES);
		List<Map<String, Object>> rows = new ArrayList<>(messages.size());
		for (AiMessage message : messages) {
			Map<String, Object> row = new LinkedHashMap<>(8);
			row.put("id", message.getId());
			row.put("role", message.getRole());
			row.put("content", message.getContent());
			row.put("model", message.getModel());
			// 追问改写后的检索句：为空说明是按原话检索的
			row.put("retrievalQuery", message.getRetrievalQuery());
			row.put("citationCount", citationCount(message.getCitations()));
			row.put("createTime", message.getCreateTime());
			rows.add(row);
		}
		Map<String, Object> data = new LinkedHashMap<>(8);
		data.put("id", conversation.getId());
		data.put("userId", conversation.getUserId());
		data.put("title", conversation.getTitle());
		data.put("messageCount", conversation.getMessageCount());
		data.put("createTime", conversation.getCreateTime());
		data.put("updateTime", conversation.getUpdateTime());
		data.put("messages", rows);
		return data;
	}

	/**
	 * 引用条数：空引用通常意味着「没检索到资料」或「模型没标来源」，是排查的起点
	 */
	private int citationCount(String citations) {
		if (citations == null || citations.isBlank()) {
			return 0;
		}
		try {
			List<Map<String, Object>> parsed =
					JacksonUtils.readValue(citations, new TypeReference<List<Map<String, Object>>>() {
					});
			return parsed == null ? 0 : parsed.size();
		} catch (Exception e) {
			// 历史数据格式异常时不该让整个详情页打不开
			return 0;
		}
	}
}
