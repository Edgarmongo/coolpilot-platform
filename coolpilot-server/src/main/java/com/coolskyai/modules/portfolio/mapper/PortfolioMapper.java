package com.coolskyai.modules.portfolio.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.portfolio.entity.Portfolio;

import java.util.List;

/**
 * @Description: 项目展示持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-06
 */
@Mapper
public interface PortfolioMapper {

	/**
	 * 列表页不取 content：详情正文可能有几十 KB 的 Markdown，
	 * 列表只需要封面、简介和技术栈，全查会把响应体撑大好几倍。
	 */
	@Select("SELECT id, slug, name, summary, cover_url, tech_stack, repo_url, demo_url, status, sort, enabled, " +
			"create_time, update_time FROM portfolio WHERE enabled = 1 ORDER BY sort ASC, id ASC")
	List<Portfolio> listEnabled();

	@Select("SELECT id, slug, name, summary, cover_url, tech_stack, repo_url, demo_url, status, sort, enabled, " +
			"create_time, update_time FROM portfolio ORDER BY sort ASC, id ASC")
	List<Portfolio> listAll();

	@Select("SELECT * FROM portfolio WHERE slug = #{slug} AND enabled = 1")
	Portfolio findEnabledBySlug(@Param("slug") String slug);

	@Select("SELECT * FROM portfolio WHERE id = #{id}")
	Portfolio findById(@Param("id") Long id);

	@Select("SELECT COUNT(*) FROM portfolio WHERE slug = #{slug} AND id <> #{excludeId}")
	int countBySlug(@Param("slug") String slug, @Param("excludeId") Long excludeId);

	@Insert("INSERT INTO portfolio (slug, name, summary, cover_url, tech_stack, repo_url, demo_url, status, content, sort, enabled) " +
			"VALUES (#{slug}, #{name}, #{summary}, #{coverUrl}, #{techStack}, #{repoUrl}, #{demoUrl}, #{status}, #{content}, #{sort}, #{enabled})")
	@Options(useGeneratedKeys = true, keyProperty = "id")
	int insert(Portfolio portfolio);

	@Update("UPDATE portfolio SET slug = #{slug}, name = #{name}, summary = #{summary}, cover_url = #{coverUrl}, " +
			"tech_stack = #{techStack}, repo_url = #{repoUrl}, demo_url = #{demoUrl}, status = #{status}, " +
			"content = #{content}, sort = #{sort}, enabled = #{enabled} WHERE id = #{id}")
	int update(Portfolio portfolio);

	@Update("UPDATE portfolio SET sort = #{sort} WHERE id = #{id}")
	int updateSort(@Param("id") Long id, @Param("sort") Integer sort);

	@Update("UPDATE portfolio SET enabled = #{enabled} WHERE id = #{id}")
	int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

	@Delete("DELETE FROM portfolio WHERE id = #{id}")
	int delete(@Param("id") Long id);
}
