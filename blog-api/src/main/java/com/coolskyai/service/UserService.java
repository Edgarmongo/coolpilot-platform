package com.coolskyai.service;

import com.coolskyai.entity.User;
import com.coolskyai.model.dto.LoginInfo;
import com.coolskyai.model.dto.RegisterInfo;

public interface UserService {
	User findUserByUsernameAndPassword(String username, String password);

	User findUserById(Long id);

	boolean changeAccount(User user, String jwt);

	/**
	 * 前台访客自助注册
	 *
	 * @param registerInfo 注册信息
	 * @return 注册成功的访客（密码已置空）
	 */
	User registerVisitor(RegisterInfo registerInfo);

	/**
	 * 前台访客登录
	 *
	 * @param loginInfo 账号密码
	 * @return 访客（密码已置空）
	 */
	User loginVisitor(LoginInfo loginInfo);
}
