package com.coolskyai.modules.topic.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.topic.entity.Topic;
import com.coolskyai.modules.topic.vo.TopicArticle;

import java.util.List;

/**
 * @Description: 专题持久层
 *
 * 文章数量用子查询算，而不是冗余一个 article_count 字段：
 * 专题数量很少、列表页也不会翻很多页，实时算比维护冗余字段更不容易错
 * （冗余字段最容易出现「解绑了但计数没减」的脏数据）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Mapper
public interface TopicMapper {

	/** 前台列表：只算已发布文章，避免「专题显示 5 篇点进去只有 2 篇」 */
	String PUBLIC_COLUMNS = "t.*, (SELECT COUNT(*) FROM topic_article ta JOIN blog b ON b.id = ta.blog_id " +
			"AND b.is_published = 1 WHERE ta.topic_id = t.id) AS article_count FROM topic t ";

	String ADMIN_COLUMNS = "t.*, (SELECT COUNT(*) FROM topic_article ta WHERE ta.topic_id = t.id) AS article_count " +
			"FROM topic t ";

	@Select("SELECT " + PUBLIC_COLUMNS + "WHERE t.is_published = 1 ORDER BY t.sort DESC, t.id DESC")
	List<Topic> listPublished();

	@Select("SELECT " + PUBLIC_COLUMNS + "WHERE t.slug = #{slug} AND t.is_published = 1")
	Topic findPublishedBySlug(@Param("slug") String slug);

	@Select("SELECT " + ADMIN_COLUMNS + "WHERE t.id = #{id}")
	Topic findById(@Param("id") Long id);

	@Select("SELECT * FROM topic WHERE slug = #{slug}")
	Topic findBySlug(@Param("slug") String slug);

	@Select("<script>" +
			"SELECT " + ADMIN_COLUMNS +
			"<where>" +
			"<if test='published != null'>AND t.is_published = #{published}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (t.title LIKE CONCAT('%', #{keyword}, '%') " +
			"OR t.slug LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"</where> ORDER BY t.sort DESC, t.id DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Topic> pageAdmin(@Param("published") Boolean published,
	                      @Param("keyword") String keyword,
	                      @Param("offset") int offset,
	                      @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM topic t " +
			"<where>" +
			"<if test='published != null'>AND t.is_published = #{published}</if>" +
			"<if test='keyword != null and keyword != &quot;&quot;'>AND (t.title LIKE CONCAT('%', #{keyword}, '%') " +
			"OR t.slug LIKE CONCAT('%', #{keyword}, '%'))</if>" +
			"</where></script>")
	int countAdmin(@Param("published") Boolean published,
	               @Param("keyword") String keyword);

	@Insert("INSERT INTO topic (slug, title, description, cover, is_published, sort, create_time, update_time) " +
			"VALUES (#{slug}, #{title}, #{description}, #{cover}, #{isPublished}, #{sort}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Topic topic);

	@Update("UPDATE topic SET slug = #{slug}, title = #{title}, description = #{description}, cover = #{cover}, " +
			"is_published = #{isPublished}, sort = #{sort}, update_time = NOW() WHERE id = #{id}")
	int update(Topic topic);

	@Update("UPDATE topic SET is_published = #{published}, update_time = NOW() WHERE id = #{id}")
	int updatePublished(@Param("id") Long id, @Param("published") Boolean published);

	@Delete("DELETE FROM topic WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

	@Delete("DELETE FROM topic_article WHERE topic_id = #{topicId}")
	int deleteArticles(@Param("topicId") Long topicId);

	@Insert("INSERT INTO topic_article (topic_id, blog_id, sort) VALUES (#{topicId}, #{blogId}, #{sort})")
	int insertArticle(@Param("topicId") Long topicId, @Param("blogId") Long blogId, @Param("sort") int sort);

	@Select("<script>" +
			"SELECT b.id, b.title, b.slug, b.description, b.create_time, b.read_time, b.views, " +
			"CASE WHEN b.password IS NULL OR b.password = '' THEN 0 ELSE 1 END AS privacy, ta.sort " +
			"FROM topic_article ta JOIN blog b ON b.id = ta.blog_id " +
			"WHERE ta.topic_id = #{topicId}" +
			"<if test='publishedOnly'>AND b.is_published = 1</if> " +
			"ORDER BY ta.sort ASC, b.id ASC" +
			"</script>")
	List<TopicArticle> listArticles(@Param("topicId") Long topicId,
	                                @Param("publishedOnly") boolean publishedOnly);

	@Select("SELECT blog_id FROM topic_article WHERE topic_id = #{topicId} ORDER BY sort ASC, blog_id ASC")
	List<Long> listArticleIds(@Param("topicId") Long topicId);

	/** 文章所属的已发布专题（文章页显示「本篇属于…」） */
	@Select("SELECT t.*, (SELECT COUNT(*) FROM topic_article ta2 JOIN blog b2 ON b2.id = ta2.blog_id " +
			"AND b2.is_published = 1 WHERE ta2.topic_id = t.id) AS article_count " +
			"FROM topic_article ta JOIN topic t ON t.id = ta.topic_id " +
			"WHERE ta.blog_id = #{blogId} AND t.is_published = 1 ORDER BY t.sort DESC, t.id DESC")
	List<Topic> listByBlogId(@Param("blogId") Long blogId);

	@Select("SELECT COUNT(*) FROM blog WHERE id = #{blogId}")
	int countBlog(@Param("blogId") Long blogId);
}
