package com.coolskyai.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.constant.Role;
import com.coolskyai.modules.account.service.TokenVersionService;
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
	 * 前台用户角色，与所有者 ROLE_owner 严格区分
	 */
	private static final String USER_ROLE = Role.USER;
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
	@Autowired
	private TokenVersionService tokenVersionService;

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

	/**
	 * 后台「账号设置」保存。
	 *
	 * 修过的两个老问题：
	 *   1. token 的 subject 是 owner:用户名，直接拿去 where username 查库永远匹配不到，
	 *      所以这个接口之前必然返回「修改失败」。现在用 JwtConstants.ownerName 拆前缀。
	 *   2. 原 SQL 只更新 username/password，昵称/头像/邮箱改了不生效；
	 *      且 password 传空时会把密码覆盖成 null 的哈希。
	 *      现在密码留空 = 不改，其它资料留空 = 保留原值。
	 */
	@Override
	public boolean changeAccount(User user, String jwt) {
		String oldUsername = JwtConstants.ownerName(JwtUtils.getTokenBody(jwt).getSubject());
		if (oldUsername == null) {
			return false;
		}
		User existing = userMapper.findByUsername(oldUsername);
		if (existing == null) {
			return false;
		}

		String newUsername = user.getUsername() == null ? "" : user.getUsername().trim();
		if (newUsername.isEmpty()) {
			newUsername = oldUsername;
		}
		if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
			throw new BadRequestException("用户名需为 2-20 位字母、数字、下划线、点或中文");
		}
		boolean usernameChanged = !newUsername.equals(oldUsername);
		if (usernameChanged && userMapper.findByUsername(newUsername) != null) {
			throw new BadRequestException("该用户名已被占用");
		}

		boolean passwordChanged = user.getPassword() != null && !user.getPassword().isEmpty();
		if (passwordChanged && (user.getPassword().length() < 6 || user.getPassword().length() > 32)) {
			throw new BadRequestException("密码长度需为 6-32 位");
		}

		user.setUsername(newUsername);
		user.setPassword(passwordChanged ? HashUtils.getBC(user.getPassword()) : existing.getPassword());
		if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
			user.setNickname(existing.getNickname());
		}
		if (user.getAvatar() == null || user.getAvatar().trim().isEmpty()) {
			user.setAvatar(existing.getAvatar());
		}
		if (user.getEmail() == null) {
			user.setEmail(existing.getEmail());
		}

		if (userMapper.updateUserByUsername(oldUsername, user) != 1) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return false;
		}

		// 只改资料不动登录态；改了密码或用户名才吊销
		if (passwordChanged || usernameChanged) {
			tokenVersionService.bump(JwtConstants.OWNER_PREFIX + oldUsername);
			if (usernameChanged) {
				tokenVersionService.bump(JwtConstants.OWNER_PREFIX + newUsername);
			}
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
		user.setRole(USER_ROLE);
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
		// 平台所有者账号不允许从前台入口登录，避免两套身份混用
		if (!Role.isUser(user.getRole())) {
			throw new BadRequestException("该账号是平台所有者账号，请前往后台登录");
		}
		user.setPassword(null);
		return user;
	}
}
