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

import java.util.List;

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

	@Insert("INSERT INTO ai_message (conversation_id, role, content, citations, model, create_time) " +
			"VALUES (#{conversationId}, #{role}, #{content}, #{citations}, #{model}, NOW())")
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

	@Update("UPDATE ai_message SET share_token = #{token} WHERE id = #{id}")
	int updateShareToken(@Param("id") Long id, @Param("token") String token);

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
