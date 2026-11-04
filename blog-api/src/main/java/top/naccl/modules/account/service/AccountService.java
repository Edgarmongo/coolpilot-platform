package top.naccl.modules.account.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import top.naccl.entity.User;
import top.naccl.exception.BadRequestException;
import top.naccl.service.UserService;
import top.naccl.service.impl.UserServiceImpl;
import top.naccl.util.CurrentUser;

/**
 * @Description: 账户上下文服务
 *
 * 统一的"当前登录用户"解析入口。业务模块不要各自解析 token，
 * 统一调用这里，避免出现多套判断逻辑。
 *
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@Service
public class AccountService {
	@Autowired
	private UserService userService;
	@Autowired
	private UserServiceImpl userServiceImpl;

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
