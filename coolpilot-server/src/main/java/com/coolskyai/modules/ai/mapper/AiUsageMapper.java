package com.coolskyai.modules.ai.mapper;

import com.coolskyai.modules.ai.entity.AiUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Description: AI 用量写入与聚合
 *
 * 聚合直接交给 MySQL 做：数据量按每天几十到几百条算，加索引后
 * 一个月的数据一次 group by 是毫秒级，没必要上缓存或预聚合表。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface AiUsageMapper {

	@Insert("INSERT INTO ai_usage (scene, model, prompt_tokens, completion_tokens, total_tokens, "
			+ "duration_ms, success, estimated, user_id, create_time) "
			+ "VALUES (#{scene}, #{model}, #{promptTokens}, #{completionTokens}, #{totalTokens}, "
			+ "#{durationMs}, #{success}, #{estimated}, #{userId}, #{createTime})")
	int insert(AiUsage usage);

	/**
	 * 区间汇总：调用次数、token 三项、成功数、总耗时
	 */
	@Select("SELECT COUNT(*) AS calls, "
			+ "COALESCE(SUM(prompt_tokens), 0) AS promptTokens, "
			+ "COALESCE(SUM(completion_tokens), 0) AS completionTokens, "
			+ "COALESCE(SUM(total_tokens), 0) AS totalTokens, "
			+ "COALESCE(SUM(success), 0) AS successCalls, "
			+ "COALESCE(SUM(estimated), 0) AS estimatedCalls, "
			+ "COALESCE(SUM(duration_ms), 0) AS durationMs "
			+ "FROM ai_usage WHERE create_time >= #{from}")
	Map<String, Object> summary(@Param("from") LocalDateTime from);

	/**
	 * 按天：图表与表格的主线
	 */
	@Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS calls, "
			+ "COALESCE(SUM(prompt_tokens), 0) AS promptTokens, "
			+ "COALESCE(SUM(completion_tokens), 0) AS completionTokens, "
			+ "COALESCE(SUM(total_tokens), 0) AS totalTokens "
			+ "FROM ai_usage WHERE create_time >= #{from} "
			+ "GROUP BY day ORDER BY day")
	List<Map<String, Object>> byDay(@Param("from") LocalDateTime from);

	/**
	 * 按模型：回答「哪个模型最费」
	 */
	@Select("SELECT model, COUNT(*) AS calls, COALESCE(SUM(total_tokens), 0) AS totalTokens "
			+ "FROM ai_usage WHERE create_time >= #{from} "
			+ "GROUP BY model ORDER BY totalTokens DESC")
	List<Map<String, Object>> byModel(@Param("from") LocalDateTime from);

	/**
	 * 按场景：区分「问答」与「改写/重排」的开销 —— 后者是隐形成本，
	 * 用户看不到，但确实在消耗 token
	 */
	@Select("SELECT scene, COUNT(*) AS calls, COALESCE(SUM(total_tokens), 0) AS totalTokens "
			+ "FROM ai_usage WHERE create_time >= #{from} "
			+ "GROUP BY scene ORDER BY totalTokens DESC")
	List<Map<String, Object>> byScene(@Param("from") LocalDateTime from);
}
