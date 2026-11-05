package com.coolskyai.modules.ai.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.ai.entity.AiConversation;
import com.coolskyai.modules.ai.entity.AiMessage;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Description: AI 问答会话持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface AiConversationMapper {

	@Insert("INSERT INTO ai_conversation (user_id, title, message_count, create_time, update_time) " +
			"VALUES (#{userId}, #{title}, 0, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertConversation(AiConversation conversation);

	@Update("UPDATE ai_conversation SET message_count = message_count + #{delta}, update_time = NOW() " +
			"WHERE id = #{id}")
	int touchConversation(@Param("id") Long id, @Param("delta") int delta);

	@Select("SELECT * FROM ai_conversation WHERE id = #{id}")
	AiConversation findConversation(@Param("id") Long id);

	@Select("SELECT * FROM ai_conversation WHERE user_id = #{userId} ORDER BY update_time DESC " +
			"LIMIT #{offset}, #{size}")
	List<AiConversation> pageConversations(@Param("userId") Long userId,
	                                       @Param("offset") int offset,
	                                       @Param("size") int size);

	@Select("SELECT COUNT(*) FROM ai_conversation WHERE user_id = #{userId}")
	int countConversations(@Param("userId") Long userId);

	@Delete("DELETE FROM ai_conversation WHERE id = #{id} AND user_id = #{userId}")
	int deleteConversation(@Param("id") Long id, @Param("userId") Long userId);

	@Delete("DELETE FROM ai_message WHERE conversation_id = #{conversationId}")
	int deleteMessages(@Param("conversationId") Long conversationId);

	@Insert("INSERT INTO ai_message (conversation_id, role, content, retrieval_query, citations, model, create_time) " +
			"VALUES (#{conversationId}, #{role}, #{content}, #{retrievalQuery}, #{citations}, #{model}, NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insertMessage(AiMessage message);

	/**
	 * 取最近 N 条再倒序，保证长会话不会把全部历史拉进内存；
	 * 前端按时间正序渲染，这里返回的已翻转好。
	 */
	@Select("SELECT * FROM (" +
			"  SELECT * FROM ai_message WHERE conversation_id = #{conversationId} ORDER BY id DESC LIMIT #{limit}" +
			") t ORDER BY t.id ASC")
	List<AiMessage> listMessages(@Param("conversationId") Long conversationId, @Param("limit") int limit);

	/**
	 * 上一轮用户问题：追问常常只有「那退款呢」几个字，单靠它检索不到东西
	 */
	@Select("SELECT content FROM ai_message WHERE conversation_id = #{conversationId} AND role = 'USER' " +
			"ORDER BY id DESC LIMIT 1")
	String lastUserQuestion(@Param("conversationId") Long conversationId);

	/**
	 * 最近 N 轮「已还原成独立问题」的提问，按时间倒序
	 *
	 * 取 retrieval_query 而不是 content：上一轮如果也是追问，它的原话同样指代不清，
	 * 拿它当上下文等于让模型在模糊的基础上再猜一次；改写后的句子才是能自洽的锚点。
	 */
	@Select("SELECT COALESCE(NULLIF(retrieval_query, ''), content) FROM ai_message " +
			"WHERE conversation_id = #{conversationId} AND role = 'USER' " +
			"ORDER BY id DESC LIMIT #{limit}")
	List<String> recentResolvedQueries(@Param("conversationId") Long conversationId, @Param("limit") int limit);

	/**
	 * 会话归属人：分享页收到评论时通知他
	 */
	@Select("SELECT user_id FROM ai_conversation WHERE id = #{id}")
	Long findOwnerId(@Param("id") Long id);

	// ---------------- 后台：问答记录 ----------------

	/**
	 * 全部会话（后台）：带用户名，便于判断是谁问的
	 */
	@Select("SELECT c.id, c.user_id AS userId, COALESCE(NULLIF(u.nickname, ''), u.username) AS userName, " +
			"c.title, c.message_count AS messageCount, c.create_time AS createTime, c.update_time AS updateTime " +
			"FROM ai_conversation c LEFT JOIN user u ON u.id = c.user_id " +
			"WHERE (#{keyword} IS NULL OR #{keyword} = '' OR c.title LIKE CONCAT('%', #{keyword}, '%')) " +
			"ORDER BY c.update_time DESC LIMIT #{offset}, #{size}")
	List<Map<String, Object>> pageAll(@Param("keyword") String keyword,
	                                  @Param("offset") int offset, @Param("size") int size);

	@Select("SELECT COUNT(*) FROM ai_conversation c " +
			"WHERE (#{keyword} IS NULL OR #{keyword} = '' OR c.title LIKE CONCAT('%', #{keyword}, '%'))")
	int countAll(@Param("keyword") String keyword);

	@Select("SELECT COUNT(*) FROM ai_message WHERE conversation_id = #{conversationId}")
	int countMessages(@Param("conversationId") Long conversationId);

	// ---------------- 分享 ----------------

	/**
	 * 取一条属于该用户的助手回答
	 *
	 * 分享和收藏都只能针对自己的回答：一条 JOIN 里同时校验角色与会话归属，
	 * 避免「拿到别人 messageId 就能读内容」。
	 */
	@Select("SELECT m.* FROM ai_message m JOIN ai_conversation c ON c.id = m.conversation_id " +
			"WHERE m.id = #{id} AND m.role = 'ASSISTANT' AND c.user_id = #{userId}")
	AiMessage findAnswerOwned(@Param("id") Long id, @Param("userId") Long userId);

	/**
	 * 写分享令牌
	 *
	 * 重新分享等于换一条新链接：过期时间与两个计数一起重置，
	 * 否则新链接会继承上一条的阅读/点赞数，看起来像是「被看过」。
	 */
	@Update("UPDATE ai_message SET share_token = #{token}, share_expires_at = #{expiresAt}, " +
			"share_views = 0, share_likes = 0 WHERE id = #{id}")
	int updateShareToken(@Param("id") Long id, @Param("token") String token, @Param("expiresAt") Date expiresAt);

	/**
	 * 撤销分享：令牌与到期时间一起清掉（计数留着，重新分享时会归零）
	 */
	@Update("UPDATE ai_message SET share_token = NULL, share_expires_at = NULL WHERE id = #{id}")
	int clearShareToken(@Param("id") Long id);

	@Update("UPDATE ai_message SET share_views = share_views + 1 WHERE id = #{id}")
	int increaseShareViews(@Param("id") Long id);

	/**
	 * 点赞计数带上下界：并发或重复调用也不该出现负数
	 */
	@Update("UPDATE ai_message SET share_likes = share_likes + 1 WHERE id = #{id} AND share_likes < 100000000")
	int increaseShareLikes(@Param("id") Long id);

	@Update("UPDATE ai_message SET share_likes = share_likes - 1 WHERE id = #{id} AND share_likes > 0")
	int decreaseShareLikes(@Param("id") Long id);

	@Select("SELECT share_views, share_likes FROM ai_message WHERE id = #{id}")
	AiMessage findShareStats(@Param("id") Long id);

	@Select("SELECT * FROM ai_message WHERE share_token = #{token} AND role = 'ASSISTANT'")
	AiMessage findByShareToken(@Param("token") String token);

	/**
	 * 分享页要显示「问题是什么」，取同会话里这条回答之前最近的一条用户消息
	 */
	@Select("SELECT content FROM ai_message WHERE conversation_id = #{conversationId} " +
			"AND role = 'USER' AND id < #{beforeId} ORDER BY id DESC LIMIT 1")
	String findQuestionBefore(@Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId);

	/**
	 * 这些回答里哪些已被当前用户收藏（会话详情回显「已收藏」）
	 */
	@Select("<script>" +
			"SELECT target_id FROM favorite WHERE user_id = #{userId} AND target_type = 'AI_ANSWER' " +
			"AND target_id IN " +
			"<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
			"</script>")
	List<Long> listFavoritedAnswerIds(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
