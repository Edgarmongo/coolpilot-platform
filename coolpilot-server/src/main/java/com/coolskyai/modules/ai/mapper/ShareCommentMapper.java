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
	 * 公开读取：只取展示需要的列，邮箱和 IP 不出库
	 */
	@Select("SELECT id, target_id, user_id, nickname, avatar, content, create_time, parent_comment_id, is_admin_comment AS adminComment " +
			"FROM comment WHERE target_type = 'SHARE' AND target_id = #{targetId} AND is_published = 1 " +
			"ORDER BY id ASC LIMIT #{limit}")
	List<ShareComment> listPublished(@Param("targetId") Long targetId, @Param("limit") int limit);

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
