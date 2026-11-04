package com.coolskyai.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.modules.account.dto.PasswordDTO;
import com.coolskyai.modules.account.dto.ProfileDTO;
import com.coolskyai.service.UserService;
import com.coolskyai.service.impl.UserServiceImpl;
import com.coolskyai.util.CurrentUser;

/**
 * @Description: 账户上下文服务
 *
 * 统一的"当前登录用户"解析入口。业务模块不要各自解析 token，
 * 统一调用这里，避免出现多套判断逻辑。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-04
 */
@Service
public class AccountService {
	@Autowired
	private UserService userService;
	@Autowired
	private UserServiceImpl userServiceImpl;
	@Autowired
	private UserMapper userMapper;
	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	/**
	 * 当前登录用户，未登录返回 null
	 */
	public User currentUser() {
		try {
			String ownerUsername = CurrentUser.ownerUsername();
			if (ownerUsername != null) {
				return (User) userServiceImpl.loadUserByUsername(ownerUsername);
			}
			Long visitorId = CurrentUser.visitorId();
			if (visitorId != null) {
				return userService.findUserById(visitorId);
			}
		} catch (UsernameNotFoundException | IllegalArgumentException e) {
			return null;
		}
		return null;
	}

	/**
	 * 当前登录用户ID，未登录返回 null
	 */
	public Long currentUserId() {
		User user = currentUser();
		return user == null ? null : user.getId();
	}

	/**
	 * 修改昵称/头像/邮箱。
	 *
	 * 不碰密码——密码走独立的 changePassword，且必须验证原密码。
	 * 上游 UserServiceImpl.changeAccount 直接把新密码 encode 后覆盖，
	 * 不校验原密码，前台不能复用。
	 */
	public User updateProfile(Long userId, ProfileDTO dto) {
		if (userId == null) {
			throw new BadRequestException("请先登录");
		}
		User user = userMapper.findById(userId);
		if (user == null) {
			throw new BadRequestException("用户不存在");
		}
		String nickname = dto.getNickname() == null ? user.getNickname() : dto.getNickname().trim();
		if (nickname == null || nickname.isEmpty() || nickname.length() > 20) {
			throw new BadRequestException("昵称需为 1-20 个字符");
		}
		String avatar = dto.getAvatar() == null ? user.getAvatar() : dto.getAvatar().trim();
		String email = dto.getEmail() == null ? user.getEmail() : dto.getEmail().trim();
		if (!email.isEmpty() && (!email.contains("@") || email.length() > 100)) {
			throw new BadRequestException("邮箱格式不正确");
		}
		userMapper.updateProfile(userId, nickname, avatar == null ? "" : avatar, email);
		user.setNickname(nickname);
		user.setAvatar(avatar);
		user.setEmail(email);
		return user;
	}

	/**
	 * 修改密码：先校验原密码，再检查新密码强度。
	 */
	public void changePassword(Long userId, PasswordDTO dto) {
		if (userId == null) {
			throw new BadRequestException("请先登录");
		}
		User user = userMapper.findById(userId);
		if (user == null) {
			throw new BadRequestException("用户不存在");
		}
		String oldPassword = dto.getOldPassword() == null ? "" : dto.getOldPassword();
		String newPassword = dto.getNewPassword() == null ? "" : dto.getNewPassword();
		if (oldPassword.isEmpty()) {
			throw new BadRequestException("请输入原密码");
		}
		if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
			throw new BadRequestException("原密码不正确");
		}
		if (newPassword.length() < 6 || newPassword.length() > 32) {
			throw new BadRequestException("新密码长度需为 6-32 位");
		}
		if (newPassword.equals(oldPassword)) {
			throw new BadRequestException("新密码不能与原密码相同");
		}
		userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
	}

	/**
	 * 当前登录用户ID，未登录抛业务异常
	 */
	public Long requireUserId() {
		Long userId = currentUserId();
		if (userId == null) {
			throw new BadRequestException("请先登录");
		}
		return userId;
	}
}
