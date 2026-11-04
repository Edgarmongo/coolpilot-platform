package com.coolskyai.modules.product.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.product.entity.Lesson;

import java.util.List;

/**
 * @Description: 课时持久层
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface LessonMapper {

	@Select("SELECT * FROM lesson WHERE course_id = #{courseId} ORDER BY sort ASC, id ASC")
	List<Lesson> listByCourse(@Param("courseId") Long courseId);

	@Select("SELECT * FROM lesson WHERE id = #{id}")
	Lesson findById(@Param("id") Long id);

	@Select("SELECT * FROM lesson WHERE id = #{id} AND is_free_preview = b'1'")
	Lesson findFreePreview(@Param("id") Long id);

	@Insert("INSERT INTO lesson (course_id, title, sort, content_type, video_file_id, content, duration, is_free_preview, create_time, update_time) " +
			"VALUES (#{courseId}, #{title}, #{sort}, #{contentType}, #{videoFileId}, #{content}, #{duration}, #{isFreePreview}, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Lesson lesson);

	@Update("UPDATE lesson SET title = #{title}, sort = #{sort}, content_type = #{contentType}, " +
			"video_file_id = #{videoFileId}, content = #{content}, duration = #{duration}, " +
			"is_free_preview = #{isFreePreview}, update_time = NOW() WHERE id = #{id}")
	int update(Lesson lesson);

	@Delete("DELETE FROM lesson WHERE id = #{id}")
	int delete(@Param("id") Long id);
}
