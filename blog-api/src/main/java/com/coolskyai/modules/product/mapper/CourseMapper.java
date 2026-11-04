package com.coolskyai.modules.product.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.coolskyai.modules.product.entity.Course;

/**
 * @Description: 课程扩展持久层
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@Mapper
public interface CourseMapper {

	@Select("SELECT * FROM course WHERE product_id = #{productId}")
	Course findByProductId(@Param("productId") Long productId);

	@Insert("INSERT INTO course (product_id, description, lesson_count, total_duration, create_time, update_time) " +
			"VALUES (#{productId}, #{description}, 0, 0, NOW(), NOW())")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Course course);

	@Update("UPDATE course SET description = #{description}, update_time = NOW() WHERE product_id = #{productId}")
	int updateDescription(@Param("productId") Long productId, @Param("description") String description);

	/**
	 * 同步课时数与总时长，避免列表页实时聚合
	 */
	@Update("UPDATE course c SET " +
			"c.lesson_count = (SELECT COUNT(*) FROM lesson l WHERE l.course_id = c.id), " +
			"c.total_duration = (SELECT IFNULL(SUM(l.duration), 0) FROM lesson l WHERE l.course_id = c.id), " +
			"c.update_time = NOW() " +
			"WHERE c.id = #{courseId}")
	int refreshStats(@Param("courseId") Long courseId);
}
