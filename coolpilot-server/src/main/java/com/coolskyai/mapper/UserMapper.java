package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;
import com.coolskyai.entity.User;

/**
 * @Description: 用户持久层接口
 * @Author: CoolSkyAI
 * @Date: 2020-07-19
 */
@Mapper
@Repository
public interface UserMapper {
	User findByUsername(String username);

	User findById(Long id);

	int updateUserByUsername(String username, User user);

	/**
	 * 按主键更新资料。用 id 而不是 username，是为了让访客 token
	 * （subject 为 visitor:{userId}）也能定位到自己。
	 */
	@Update("UPDATE user SET nickname = #{nickname}, avatar = #{avatar}, email = #{email}, update_time = NOW() " +
			"WHERE id = #{id}")
	int updateProfile(@Param("id") Long id, @Param("nickname") String nickname,
	                  @Param("avatar") String avatar, @Param("email") String email);

	@Update("UPDATE user SET password = #{password}, update_time = NOW() WHERE id = #{id}")
	int updatePassword(@Param("id") Long id, @Param("password") String password);

	/**
	 * 新增用户（前台访客注册）
	 *
	 * @param user 用户信息，需已设置 role
	 * @return 影响行数
	 */
	int saveUser(User user);
}
