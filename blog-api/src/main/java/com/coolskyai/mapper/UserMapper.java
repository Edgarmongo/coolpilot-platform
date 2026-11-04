package com.coolskyai.mapper;

import org.apache.ibatis.annotations.Mapper;
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
	 * 新增用户（前台访客注册）
	 *
	 * @param user 用户信息，需已设置 role
	 * @return 影响行数
	 */
	int saveUser(User user);
}
