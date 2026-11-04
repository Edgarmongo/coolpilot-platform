package com.coolskyai.modules.service.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.service.entity.Quote;

import java.util.List;

/**
 * @Description: 报价单持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface QuoteMapper {

	@Select("SELECT * FROM quote WHERE demand_id = #{demandId} ORDER BY version DESC")
	List<Quote> listByDemand(@Param("demandId") Long demandId);

	@Select("SELECT * FROM quote WHERE id = #{id}")
	Quote findById(@Param("id") Long id);

	@Select("SELECT * FROM quote WHERE quote_no = #{quoteNo}")
	Quote findByNo(@Param("quoteNo") String quoteNo);

	@Select("SELECT COALESCE(MAX(version), 0) FROM quote WHERE demand_id = #{demandId}")
	int maxVersion(@Param("demandId") Long demandId);

	@Insert("INSERT INTO quote (quote_no, demand_id, version, amount, currency, scope, valid_until, status, " +
			"create_time, update_time) VALUES (#{quoteNo}, #{demandId}, #{version}, #{amount}, #{currency}, " +
			"#{scope}, #{validUntil}, #{status}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Quote quote);

	@Update("UPDATE quote SET amount = #{amount}, scope = #{scope}, valid_until = #{validUntil}, update_time = NOW() " +
			"WHERE id = #{id} AND status = 'DRAFT'")
	int updateDraft(Quote quote);

	@Update("UPDATE quote SET status = #{to}, update_time = NOW() WHERE id = #{id} AND status = #{from}")
	int move(@Param("id") Long id, @Param("from") String from, @Param("to") String to);

	/**
	 * 同一需求的其他报价置为失效（用户确认某一份后）
	 */
	@Update("UPDATE quote SET status = 'EXPIRED', update_time = NOW() " +
			"WHERE demand_id = #{demandId} AND id <> #{keepId} AND status = 'SENT'")
	int expireOthers(@Param("demandId") Long demandId, @Param("keepId") Long keepId);
}
