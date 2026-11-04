package com.coolskyai.modules.community.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.community.entity.Feedback;

import java.util.List;

/**
 * @Description: 反馈持久层
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Mapper
public interface FeedbackMapper {

	@Insert("INSERT INTO feedback (user_id, content, images, contact, status, create_time, update_time) " +
			"VALUES (#{userId}, #{content}, #{images}, #{contact}, 'PENDING', NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Feedback feedback);

	@Select("<script>" +
			"SELECT * FROM feedback " +
			"<if test='status != null and status != \"\"'>WHERE status = #{status}</if> " +
			"ORDER BY create_time DESC LIMIT #{offset}, #{size}" +
			"</script>")
	List<Feedback> page(@Param("status") String status, @Param("offset") int offset, @Param("size") int size);

	@Select("<script>" +
			"SELECT COUNT(*) FROM feedback " +
			"<if test='status != null and status != \"\"'>WHERE status = #{status}</if>" +
			"</script>")
	int count(@Param("status") String status);

	@Update("UPDATE feedback SET reply = #{reply}, status = #{status}, reply_time = NOW(), update_time = NOW() WHERE id = #{id}")
	int reply(@Param("id") Long id, @Param("reply") String reply, @Param("status") String status);
}
