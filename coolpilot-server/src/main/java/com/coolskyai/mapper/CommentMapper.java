package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
import com.coolskyai.entity.Comment;
import com.coolskyai.model.vo.PageComment;

import java.util.List;
import java.util.Map;

/**
 * @Description: 博客评论持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-08-03
 */
@Mapper
@Repository
public interface CommentMapper {
	List<Comment> getListByPageAndParentCommentId(Integer page, Long blogId, Long parentCommentId);

	/**
	 * 我的评论：联文章标题，page=1（关于页）的评论没有 blogId，标题为空
	 */
	@Select("SELECT c.id, c.content, c.create_time AS createTime, c.blog_id AS blogId, c.page, " +
			"c.is_published AS published, c.parent_comment_id AS parentCommentId, b.title AS blogTitle " +
			"FROM comment c LEFT JOIN blog b ON c.blog_id = b.id " +
			"WHERE c.user_id = #{userId} ORDER BY c.create_time DESC LIMIT #{offset}, #{size}")
	List<Map<String, Object>> pageByUser(@Param("userId") Long userId,
	                                     @Param("offset") int offset, @Param("size") int size);

	@Select("SELECT COUNT(*) FROM comment WHERE user_id = #{userId}")
	int countByUser(@Param("userId") Long userId);

	List<Comment> getListByParentCommentId(Long parentCommentId);

	List<PageComment> getPageCommentListByPageAndParentCommentId(Integer page, Long blogId, Long parentCommentId);

	Comment getCommentById(Long id);

	int updateCommentPublishedById(Long commentId, Boolean published);

	int updateCommentNoticeById(Long commentId, Boolean notice);

	int deleteCommentById(Long commentId);

	int deleteCommentsByBlogId(Long blogId);

	int updateComment(Comment comment);

	int countByPageAndIsPublished(Integer page, Long blogId, Boolean isPublished);

	int countComment();

	int saveComment(com.coolskyai.model.dto.Comment comment);
}
