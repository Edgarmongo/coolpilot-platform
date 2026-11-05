package com.coolskyai.modules.ai.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.ai.entity.ShareComment;

import java.util.List;

/**
 * @Description: 分享页评论（复用 comment 表，target_type='SHARE'）
 *
 * 所有语句都带 target_type / target_id 条件：
 * 少了它，一个 blogId 恰好等于某条 messageId 时就会串到别人的评论区。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface ShareCommentMapper {

	/**
	 * page=3 是「分享问答评论」的标记；blog_id 留空，文章的评论仍走 blog_id
	 */
	@Insert("INSERT INTO comment (user_id, nickname, email, content, avatar, create_time, ip, " +
			"is_published, is_admin_comment, page, is_notice, blog_id, parent_comment_id, target_type, target_id) " +
			"VALUES (#{userId}, #{nickname}, #{email}, #{content}, #{avatar}, #{createTime}, #{ip}, " +
			"1, #{adminComment}, 3, 0, NULL, #{parentCommentId}, 'SHARE', #{targetId})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(ShareComment comment);

	/**
	 * 顶楼分页：新的在前
	 *
	 * 讨论区按「最新的先看到」排，配合「查看更多」翻更早的 ——
	 * 发完留言立刻能在第一屏看到，比藏在第 5 页合理。
	 */
	@Select("<script>" +
			"SELECT id, target_id, user_id, nickname, avatar, content, create_time, parent_comment_id, is_admin_comment AS adminComment " +
			"FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} AND is_published = 1 " +
			"AND parent_comment_id = -1 " +
			"ORDER BY " +
			"<choose><when test='newest'>id DESC</when><otherwise>id ASC</otherwise></choose> " +
			"LIMIT #{offset}, #{size}" +
			"</script>")
	List<ShareComment> pageRoots(@Param("targetId") Long targetId,
	                             @Param("offset") int offset, @Param("size") int size,
	                             @Param("newest") boolean newest);

	/**
	 * 这一页顶楼下的所有回复（按时间正序，读起来是对话）
	 */
	@Select("<script>" +
			"SELECT id, target_id, user_id, nickname, avatar, content, create_time, parent_comment_id, is_admin_comment AS adminComment " +
			"FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} AND is_published = 1 " +
			"AND parent_comment_id IN " +
			"<foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>" +
			" ORDER BY id ASC" +
			"</script>")
	List<ShareComment> listReplies(@Param("targetId") Long targetId, @Param("parentIds") List<Long> parentIds);

	/**
	 * 顶楼总数：决定有没有「下一页」
	 */
	@Select("SELECT COUNT(*) FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} " +
			"AND is_published = 1 AND parent_comment_id = -1")
	int countRoots(@Param("targetId") Long targetId);

	/**
	 * 回复总数：只用于展示「共 N 条留言」
	 */
	@Select("SELECT COUNT(*) FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} " +
			"AND is_published = 1 AND parent_comment_id <> -1")
	int countReplies(@Param("targetId") Long targetId);

	/**
	 * 取一条仍在展示的评论（校验父评论归属、校验删除权限都用它）
	 */
	@Select("SELECT id, target_id, user_id, nickname, avatar, content, create_time, parent_comment_id, is_admin_comment AS adminComment " +
			"FROM comment WHERE id = #{id} AND target_type = 'SHARE' AND target_id = #{targetId} AND is_published = 1")
	ShareComment findOne(@Param("id") Long id, @Param("targetId") Long targetId);

	@Select("SELECT COUNT(*) FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} AND is_published = 1")
	int countPublished(@Param("targetId") Long targetId);

	/**
	 * 软删除：保留行，后台仍能看到是谁说过什么（审核/追责需要）
	 */
	@Update("UPDATE comment SET is_published = 0 WHERE id = #{id} AND target_type = 'SHARE' AND target_id = #{targetId}")
	int hide(@Param("id") Long id, @Param("targetId") Long targetId);
}
