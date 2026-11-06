package com.coolskyai.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.constant.Role;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.modules.account.dto.PasswordDTO;
import com.coolskyai.modules.account.dto.ProfileDTO;
import com.coolskyai.modules.auth.service.PasswordResetService;
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
	@Autowired
	private TokenVersionService tokenVersionService;
	@Autowired
	private PasswordResetService passwordResetService;

	/**
	 * 当前登录用户，未登录返回 null
	 */
	public User currentUser() {
		try {
			String ownerUsername = CurrentUser.ownerUsername();
			if (ownerUsername != null) {
				return (User) userServiceImpl.loadUserByUsername(ownerUsername);
			}
			Long userId = CurrentUser.userId();
			if (userId != null) {
				return userService.findUserById(userId);
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
		/**
		 * 邮箱是找回密码的唯一凭证，改它必须证明「这个新邮箱确实是你的」，
		 * 否则填错一个字符就永久失去找回能力，也可能占用了别人的邮箱。
		 * 邮箱没变（比如只改昵称）时不要求验证码。
		 */
		String currentEmail = user.getEmail() == null ? "" : user.getEmail().trim();
		if (!email.isEmpty() && !email.equalsIgnoreCase(currentEmail)) {
			passwordResetService.verifyCode(email, dto.getCode());
			if (userMapper.countByEmail(email, userId) > 0) {
				throw new BadRequestException("该邮箱已被其他账号使用");
			}
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
		/*
		 * 改完密码立刻吊销该身份的旧 token（R-11）：
		 * 否则别人拿到的 token 还能继续用满 3 天，"改密码"就只是心理安慰。
		 * 所有设备（含后台 CMS）都会因此掉线，属于预期行为。
		 */
		tokenVersionService.bump(subjectOf(user));
	}

	/**
	 * 主动登出：吊销该身份的全部 token
	 *
	 * 无状态 JWT 本来没有"登出"概念，前端删掉本地 token 只是眼不见为净。
	 * 这里递增版本号，让服务端真正不认这张 token。
	 */
	public void logoutCurrent() {
		User user = currentUser();
		if (user != null) {
			tokenVersionService.bump(subjectOf(user));
		}
	}

	/**
	 * 身份对应的 token subject
	 */
	private String subjectOf(User user) {
		return Role.isOwner(user.getRole())
				? JwtConstants.OWNER_PREFIX + user.getUsername()
				: JwtConstants.USER_PREFIX + user.getId();
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
