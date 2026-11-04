package com.coolskyai.modules.community.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.community.entity.LikeRecord;

/**
 * @Description: 点赞持久层
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Mapper
public interface LikeRecordMapper {

	@Select("SELECT COUNT(*) FROM like_record WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
	int exists(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

	@Insert("INSERT IGNORE INTO like_record (user_id, target_type, target_id, create_time) " +
			"VALUES (#{userId}, #{targetType}, #{targetId}, NOW())")
	int insert(LikeRecord likeRecord);

	@Delete("DELETE FROM like_record WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
	int delete(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("targetId") Long targetId);

	@Select("SELECT COUNT(*) FROM like_record WHERE target_type = #{targetType} AND target_id = #{targetId}")
	int countByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

	/**
	 * 动态点赞数仍保留在 moment.likes 上，保证列表页无需子查询
	 */
	@Update("UPDATE moment SET likes = GREATEST(likes + #{delta}, 0) WHERE id = #{id}")
	int changeMomentLikes(@Param("id") Long id, @Param("delta") int delta);
}
