package com.coolskyai.modules.opensource.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.coolskyai.modules.opensource.entity.OssProject;
import com.coolskyai.modules.opensource.vo.OssArticleItem;
import com.coolskyai.modules.opensource.vo.OssProjectCard;

import java.util.List;
import java.util.Map;

/**
 * 开源项目档案持久层。
 *
 * 复杂查询（文章流、相关项目、领域计数）放 XML：
 * 这些语句带动态条件与聚合，写成注解会难以阅读，
 * 也与 BlogMapper.xml 的既有风格一致。
 *
 * 一条贯穿全部公开查询的硬规则：只认 publish_status = 'published'。
 * 不提供任何「带状态参数」的公开方法，避免某天有人传个 archived 把草稿放出去。
 */
@Mapper
public interface OssProjectMapper {

	// ---------------- 前台 ----------------

	List<OssProjectCard> pagePublished(@Param("category") String category,
	                                   @Param("tag") String tag,
	                                   @Param("keyword") String keyword,
	                                   @Param("featured") Boolean featured,
	                                   @Param("sort") String sort,
	                                   @Param("offset") int offset,
	                                   @Param("size") int size);

	int countPublished(@Param("category") String category,
	                   @Param("tag") String tag,
	                   @Param("keyword") String keyword,
	                   @Param("featured") Boolean featured);

	OssProject findPublishedBySlug(@Param("slug") String slug);

	List<OssArticleItem> listArticlesOfProject(@Param("projectId") Long projectId);

	List<OssArticleItem> pageArticles(@Param("category") String category,
	                                  @Param("tag") String tag,
	                                  @Param("keyword") String keyword,
	                                  @Param("offset") int offset,
	                                  @Param("size") int size);

	int countArticles(@Param("category") String category,
	                  @Param("tag") String tag,
	                  @Param("keyword") String keyword);

	/** 领域 -> 项目数，含 0；service 层补齐 5 个枚举 */
	List<Map<String, Object>> countByCategory();

	List<Map<String, Object>> listTags();

	OssProjectCard findFeaturedProject();

	OssArticleItem findLatestArticleOfProject(@Param("projectId") Long projectId,
	                                          @Param("relationType") String relationType);

	List<OssProjectCard> listRelated(@Param("projectId") Long projectId, @Param("limit") int limit);

	OssArticleItem.ProjectBrief findProjectOfBlog(@Param("blogId") Long blogId);

	// ---------------- 后台 ----------------

	List<OssProject> pageAdmin(@Param("publishStatus") String publishStatus,
	                           @Param("researchStatus") String researchStatus,
	                           @Param("category") String category,
	                           @Param("keyword") String keyword,
	                           @Param("offset") int offset,
	                           @Param("size") int size);

	int countAdmin(@Param("publishStatus") String publishStatus,
	               @Param("researchStatus") String researchStatus,
	               @Param("category") String category,
	               @Param("keyword") String keyword);

	OssProject findById(@Param("id") Long id);

	/** 唯一性检查要连草稿一起查，否则会和未发布的 slug 撞库 */
	OssProject findBySlugAny(@Param("slug") String slug);

	int insert(OssProject project);

	int update(OssProject project);

	int updatePublishStatus(@Param("id") Long id, @Param("status") String status);

	int deleteTags(@Param("projectId") Long projectId);

	int insertTag(@Param("projectId") Long projectId, @Param("tagId") Long tagId);

	List<Long> listTagIds(@Param("projectId") Long projectId);

	int deleteArticles(@Param("projectId") Long projectId);

	int insertArticle(@Param("projectId") Long projectId,
	                  @Param("blogId") Long blogId,
	                  @Param("relationType") String relationType,
	                  @Param("sort") int sort);

	/** 校验关联的文章是否都存在且已发布：返回其中「可用」的 id */
	List<Long> filterUsableBlogIds(@Param("blogIds") List<Long> blogIds);

	List<OssArticleItem> listAllArticlesOfProject(@Param("projectId") Long projectId);
}
