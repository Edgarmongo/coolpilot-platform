package com.coolskyai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.model.dto.LoginInfo;
import com.coolskyai.model.dto.RegisterInfo;
import com.coolskyai.service.UserService;
import com.coolskyai.util.HashUtils;
import com.coolskyai.util.JwtUtils;

import java.util.Date;
import java.util.regex.Pattern;

/**
 * @Description: 用户业务层接口实现类
 * @Author: CoolSkyAI
 * @Date: 2020-07-19
 */
@Service
public class UserServiceImpl implements UserService, UserDetailsService {
	/**
	 * 前台访客角色，与博主的 ROLE_admin 严格区分
	 */
	private static final String VISITOR_ROLE = "ROLE_visitor";
	/**
	 * 用户名：2-20 位字母、数字、下划线、点或中文
	 */
	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[\\w\\u4e00-\\u9fa5.-]{2,20}$");
	/**
	 * 邮箱：宽松校验，仅拦截明显不合法的格式
	 */
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

	@Autowired
	private UserMapper userMapper;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		User user = userMapper.findByUsername(username);
		if (user == null) {
			throw new UsernameNotFoundException("用户不存在");
		}
		return user;
	}

	@Override
	public User findUserByUsernameAndPassword(String username, String password) {
		User user = userMapper.findByUsername(username);
		if (user == null) {
			throw new UsernameNotFoundException("用户不存在");
		}
		if (!HashUtils.matchBC(password, user.getPassword())) {
			throw new UsernameNotFoundException("密码错误");
		}
		return user;
	}

	@Override
	public User findUserById(Long id) {
		User user = userMapper.findById(id);
		if (user == null) {
			throw new NotFoundException("用户不存在");
		}
		return user;
	}

	@Override
	public boolean changeAccount(User user, String jwt) {
		String username = JwtUtils.getTokenBody(jwt).getSubject();
		user.setPassword(HashUtils.getBC(user.getPassword()));
		if (userMapper.updateUserByUsername(username, user) != 1) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return false;
		}
		return true;
	}

	@Override
	public User registerVisitor(RegisterInfo registerInfo) {
		String username = registerInfo.getUsername() == null ? "" : registerInfo.getUsername().trim();
		String password = registerInfo.getPassword() == null ? "" : registerInfo.getPassword();

		if (!USERNAME_PATTERN.matcher(username).matches()) {
			throw new BadRequestException("用户名需为 2-20 位字母、数字、下划线、点或中文");
		}
		if (password.length() < 6 || password.length() > 32) {
			throw new BadRequestException("密码长度需为 6-32 位");
		}
		if (userMapper.findByUsername(username) != null) {
			throw new BadRequestException("该用户名已被占用");
		}

		String nickname = registerInfo.getNickname() == null ? "" : registerInfo.getNickname().trim();
		if (nickname.isEmpty()) {
			nickname = username;
		} else if (nickname.length() > 20) {
			throw new BadRequestException("昵称不能超过 20 个字符");
		}

		String email = registerInfo.getEmail() == null ? "" : registerInfo.getEmail().trim();
		if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
			throw new BadRequestException("邮箱格式不正确");
		}

		Date now = new Date();
		User user = new User();
		user.setUsername(username);
		user.setPassword(HashUtils.getBC(password));
		user.setNickname(nickname);
		user.setAvatar("/img/avatar.jpg");
		user.setEmail(email);
		user.setCreateTime(now);
		user.setUpdateTime(now);
		user.setRole(VISITOR_ROLE);
		if (userMapper.saveUser(user) != 1) {
			throw new PersistenceException("注册失败，请稍后重试");
		}
		user.setPassword(null);
		return user;
	}

	@Override
	public User loginVisitor(LoginInfo loginInfo) {
		String username = loginInfo.getUsername() == null ? "" : loginInfo.getUsername().trim();
		User user = findUserByUsernameAndPassword(username, loginInfo.getPassword());
		// 博主账号不允许从访客入口登录，避免两套身份混用
		if (!VISITOR_ROLE.equals(user.getRole())) {
			throw new BadRequestException("该账号不是访客账号，请前往后台管理登录");
		}
		user.setPassword(null);
		return user;
	}
}
