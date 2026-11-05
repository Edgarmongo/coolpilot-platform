package com.coolskyai.modules.ai.service;

import com.coolskyai.modules.ai.entity.AiUsage;
import com.coolskyai.modules.ai.mapper.AiUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: AI 用量记录与统计
 *
 * 记录点放在 LlmClient 内部：所有调用（问答 / 改写 / 重排 / 测试）都经过那里，
 * 漏记的可能最小。写库失败只打日志，绝不影响对话本身 —— 统计是附带功能，
 * 不能因为它把主流程弄挂。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Service
public class AiUsageService {

	private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

	private final AiUsageMapper usageMapper;

	public AiUsageService(AiUsageMapper usageMapper) {
		this.usageMapper = usageMapper;
	}

	/**
	 * @param estimated true 表示 token 是按字符估的（接口没返回 usage）
	 */
	public void record(String scene, String model, Integer promptTokens, Integer completionTokens,
					   long durationMs, boolean success, boolean estimated, Long userId) {
		try {
			AiUsage usage = new AiUsage();
			usage.setScene(scene);
			usage.setModel(model == null ? "" : model);
			int prompt = promptTokens == null ? 0 : Math.max(0, promptTokens);
			int completion = completionTokens == null ? 0 : Math.max(0, completionTokens);
			usage.setPromptTokens(prompt);
			usage.setCompletionTokens(completion);
			usage.setTotalTokens(prompt + completion);
			usage.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
			usage.setSuccess(success);
			usage.setEstimated(estimated);
			usage.setUserId(userId);
			usage.setCreateTime(LocalDateTime.now());
			usageMapper.insert(usage);
		} catch (Exception e) {
			log.warn("记录 AI 用量失败（不影响调用）：{}", e.getMessage());
		}
	}

	/**
	 * 接口没返回 usage 时的兜底估算。
	 *
	 * 粗略但方向对：中日韩字符约 1.5 字/token，其余按 4 字符/token。
	 * 只用于让统计页有个量级，不用于对账 —— 所以记录里会标 estimated=1。
	 */
	public static int estimateTokens(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		int cjk = 0;
		int other = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= 0x4E00 && c <= 0x9FFF) {
				cjk++;
			} else if (!Character.isWhitespace(c)) {
				other++;
			}
		}
		return (int) Math.ceil(cjk / 1.5 + other / 4.0);
	}

	/**
	 * 汇总：今日 + 区间 + 按天 / 按模型 / 按场景
	 *
	 * @param days 统计区间（含今天），1~90
	 */
	public Map<String, Object> stats(int days) {
		int range = Math.min(Math.max(days, 1), 90);
		LocalDateTime from = LocalDate.now().minusDays(range - 1L).atStartOfDay();
		LocalDateTime todayStart = LocalDate.now().atStartOfDay();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("days", range);
		result.put("today", normalizeSummary(usageMapper.summary(todayStart)));
		result.put("summary", normalizeSummary(usageMapper.summary(from)));
		result.put("byDay", fillMissingDays(usageMapper.byDay(from), range));
		result.put("byModel", usageMapper.byModel(from));
		result.put("byScene", usageMapper.byScene(from));
		return result;
	}

	/**
	 * MySQL 的 COUNT/SUM 在驱动里是 Long 或 BigDecimal，这里统一成数字，
	 * 顺便算出平均耗时与成功率 —— 让前端少算一点，口径也统一。
	 */
	private Map<String, Object> normalizeSummary(Map<String, Object> raw) {
		Map<String, Object> out = new LinkedHashMap<>();
		long calls = longOf(raw, "calls");
		long promptTokens = longOf(raw, "promptTokens");
		long completionTokens = longOf(raw, "completionTokens");
		long totalTokens = longOf(raw, "totalTokens");
		long successCalls = longOf(raw, "successCalls");
		long estimatedCalls = longOf(raw, "estimatedCalls");
		long durationMs = longOf(raw, "durationMs");

		out.put("calls", calls);
		out.put("promptTokens", promptTokens);
		out.put("completionTokens", completionTokens);
		out.put("totalTokens", totalTokens);
		out.put("successCalls", successCalls);
		out.put("estimatedCalls", estimatedCalls);
		out.put("durationMs", durationMs);
		out.put("avgDurationMs", calls == 0 ? 0 : Math.round((double) durationMs / calls));
		// 保留三位小数，前端直接乘 100 显示百分比
		out.put("successRate", calls == 0 ? 0 : Math.round((double) successCalls / calls * 1000) / 1000.0);
		return out;
	}

	private long longOf(Map<String, Object> map, String key) {
		Object value = map == null ? null : map.get(key);
		return value instanceof Number ? ((Number) value).longValue() : 0L;
	}

	/**
	 * 最近 N 天补零：没有调用的日子也要在列表里出现，
	 * 否则前端画折线会把「零使用的一天」直接跳过，看起来像连续使用。
	 */
	public List<Map<String, Object>> fillMissingDays(List<Map<String, Object>> rows, int days) {
		Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
		if (rows != null) {
			for (Map<String, Object> row : rows) {
				existing.put(String.valueOf(row.get("day")), row);
			}
		}
		List<Map<String, Object>> filled = new ArrayList<>(days);
		LocalDate start = LocalDate.now().minusDays(days - 1L);
		for (int i = 0; i < days; i++) {
			String day = start.plusDays(i).toString();
			Map<String, Object> row = existing.get(day);
			if (row == null) {
				row = new LinkedHashMap<>();
				row.put("day", day);
				row.put("calls", 0);
				row.put("promptTokens", 0);
				row.put("completionTokens", 0);
				row.put("totalTokens", 0);
			}
			filled.add(row);
		}
		return filled;
	}
}
