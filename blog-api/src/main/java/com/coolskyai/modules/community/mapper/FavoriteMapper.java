package com.coolskyai.modules.community.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.coolskyai.modules.community.entity.Favorite;

import java.util.List;

/**
 * @Description: 收藏持久层
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
@Mapper
public interface FavoriteMapper {

	@Select("SELECT COUNT(*) FROM favorite WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
	int exists(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

	@Insert("INSERT IGNORE INTO favorite (user_id, target_type, target_id, create_time) " +
			"VALUES (#{userId}, #{targetType}, #{targetId}, NOW())")
	int insert(Favorite favorite);

	@Delete("DELETE FROM favorite WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
	int delete(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

	@Select("SELECT COUNT(*) FROM favorite WHERE user_id = #{userId}")
	int countByUser(@Param("userId") Long userId);

	/**
	 * 我的收藏列表：文章取标题与封面，动态取内容摘要
	 */
	@Select("SELECT f.id, f.user_id, f.target_type, f.target_id, f.create_time, " +
			"CASE f.target_type " +
			"  WHEN 'ARTICLE' THEN b.title " +
			"  WHEN 'MOMENT' THEN LEFT(m.content, 80) " +
			"  ELSE CONCAT('内容 #', f.target_id) END AS target_title, " +
			"b.first_picture AS target_cover, " +
			"COALESCE(b.create_time, m.create_time) AS target_time " +
			"FROM favorite f " +
			"LEFT JOIN blog b ON f.target_type = 'ARTICLE' AND b.id = f.target_id " +
			"LEFT JOIN moment m ON f.target_type = 'MOMENT' AND m.id = f.target_id " +
			"WHERE f.user_id = #{userId} " +
			"ORDER BY f.create_time DESC LIMIT #{offset}, #{size}")
	List<Favorite> pageByUser(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);
}
