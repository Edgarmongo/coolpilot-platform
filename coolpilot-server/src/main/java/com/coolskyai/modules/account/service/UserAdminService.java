package com.coolskyai.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.coolskyai.constant.JwtConstants;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.mapper.UserMapper;
import com.coolskyai.model.vo.PageResult;
import com.coolskyai.modules.auth.service.PasswordResetService;
import com.coolskyai.util.HashUtils;

import java.util.List;
import java.util.Map;

/**
 * @Description: 后台用户管理
 *
 * 只管前台注册用户（ROLE_user）：所有者账号不在这里出现，
 * 也不允许被禁用 —— 把唯一的后台账号停掉，等于把自己锁在门外。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@Service
public class UserAdminService {

	/** 正常 */
	private static final int STATUS_ACTIVE = 0;
	/** 已禁用 */
	private static final int STATUS_DISABLED = 1;

	@Autowired
	private UserMapper userMapper;
	@Autowired
	private TokenVersionService tokenVersionService;

	public PageResult<Map<String, Object>> list(String keyword, Integer status, Integer pageNum, Integer pageSize) {
		int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
		int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
		String kw = keyword == null ? null : keyword.trim();
		int total = userMapper.countUsers(kw, status);
		List<Map<String, Object>> list = userMapper.pageUsers(kw, status, (page - 1) * size, size);
		return new PageResult<>((total + size - 1) / size, total, list);
	}

	/**
	 * 启用 / 禁用账号
	 *
	 * 禁用后立刻递增该身份的 token 版本：已登录的设备下一次请求就失效，
	 * 不必等 token 自然过期（这一步靠的是 R-11 的版本号机制）。
	 */
	public void setStatus(Long userId, Integer status) {
		if (userId == null || status == null || (status != STATUS_ACTIVE && status != STATUS_DISABLED)) {
			throw new BadRequestException("参数有误");
		}
		int updated = userMapper.updateStatus(userId, status);
		if (updated == 0) {
			throw new BadRequestException("用户不存在，或该账号不允许被禁用");
		}
		if (status == STATUS_DISABLED) {
			tokenVersionService.bump(JwtConstants.USER_PREFIX + userId);
		}
	}

	/**
	 * 重置前台用户密码。
	 *
	 * 存在的理由：邮件服务没配好时，访客「忘记密码」走不通，
	 * 站长需要能在后台直接帮他改一个临时密码。
	 *
	 * 改完顺手递增 token 版本，把该账号已登录的会话全部踢掉 ——
	 * 否则「密码重置了但旧设备还在线」等于没重置。
	 */
	public void resetPassword(Long userId, String newPassword) {
		if (userId == null) {
			throw new BadRequestException("参数有误");
		}
		PasswordResetService.checkPassword(newPassword);
		User user = userMapper.findById(userId);
		if (user == null) {
			throw new BadRequestException("用户不存在");
		}
		if (!"ROLE_user".equals(user.getRole())) {
			throw new BadRequestException("只能重置前台访客账号的密码；站长密码请在「账号设置」里修改");
		}
		userMapper.updatePassword(userId, HashUtils.getBC(newPassword));
		tokenVersionService.bump(JwtConstants.USER_PREFIX + userId);
	}
}
