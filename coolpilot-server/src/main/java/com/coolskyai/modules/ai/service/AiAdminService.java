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
	@Autowired
	private RerankPolicy rerankPolicy;

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
	 * 检索质量：用真实数据回答「严格度该设哪一档」
	 *
	 * 严格度的默认值不是拍出来的：0 引用占比太高说明截断过狠或语料不够，
	 * 引用数常年顶格说明弱相关片段被大量放进来。这里把两个信号摆出来，并给一句建议。
	 *
	 * 刻意只做「建议」不做「自动改」：严格度影响答不出来还是答偏，属于运营判断，
	 * 让系统自动切会出现「今天松明天紧」的不可预期行为。
	 */
	public Map<String, Object> quality(Integer days, Integer limit) {
		int window = days == null || days < 1 ? 7 : Math.min(days, 90);
		int cap = limit == null || limit < 1 ? 500 : Math.min(limit, 2000);

		List<String> citations = conversationMapper.recentCitations(window, cap);
		int answers = citations.size();
		int zero = 0;
		int one = 0;
		int two = 0;
		int many = 0;
		int total = 0;
		for (String snapshot : citations) {
			int count = citationCount(snapshot);
			total += count;
			if (count == 0) {
				zero++;
			} else if (count == 1) {
				one++;
			} else if (count == 2) {
				two++;
			} else {
				many++;
			}
		}
		int questions = conversationMapper.countQuestions(window);
		int rewritten = conversationMapper.countRewritten(window);

		double zeroRate = answers == 0 ? 0 : round(zero * 1.0 / answers, 3);
		double avg = answers == 0 ? 0 : round(total * 1.0 / answers, 2);
		double rewriteRate = questions == 0 ? 0 : round(rewritten * 1.0 / questions, 3);

		String suggestion;
		String reason;
		if (answers < 5) {
			suggestion = "insufficient";
			reason = "近 " + window + " 天只有 " + answers + " 次回答，样本太少，先把内容做起来再调严格度";
		} else if (zeroRate >= 0.30) {
			suggestion = "loose";
			reason = "有 " + percent(zeroRate) + " 的回答没引用到任何资料：要么截断过严，要么站内确实缺内容。"
					+ "如果这些提问本身是站内该有的，先调宽松（loose）或补内容";
		} else if (avg >= 5) {
			suggestion = "strict";
			reason = "平均引用 " + avg + " 条、几乎每次都能凑满，说明弱相关片段被大量放进上下文；"
					+ "严格一档可以让答案更聚焦";
		} else {
			suggestion = "keep";
			reason = "0 引用占比 " + percent(zeroRate) + "、平均引用 " + avg + " 条，处于合理区间，保持当前档位";
		}

		Map<String, Object> data = new LinkedHashMap<>(16);
		data.put("days", window);
		data.put("questions", questions);
		data.put("answers", answers);
		data.put("rewritten", rewritten);
		data.put("rewriteRate", rewriteRate);
		data.put("zeroCitation", zero);
		data.put("zeroRate", zeroRate);
		data.put("avgCitations", avg);
		Map<String, Object> distribution = new LinkedHashMap<>(4);
		distribution.put("0", zero);
		distribution.put("1", one);
		distribution.put("2", two);
		distribution.put("3+", many);
		data.put("distribution", distribution);
		data.put("strictness", rerankPolicy.strictness());
		data.put("suggestion", suggestion);
		data.put("reason", reason);
		return data;
	}

	private double round(double value, int scale) {
		double factor = Math.pow(10, scale);
		return Math.round(value * factor) / factor;
	}

	private String percent(double rate) {
		return Math.round(rate * 100) + "%";
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
