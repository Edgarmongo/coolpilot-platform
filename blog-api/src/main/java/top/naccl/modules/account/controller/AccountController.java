package top.naccl.modules.account.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.entity.User;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.account.vo.AccountVO;

/**
 * @Description: 账户中心（当前登录用户）
 *
 * 这是新业务区（modules/*）的第一个模块，也是前台身份链路的验收点：
 * 能正确返回当前用户，说明 JwtFilter → SecurityContext → 业务层 已打通。
 *
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@RestController
public class AccountController {
	@Autowired
	private AccountService accountService;

	/**
	 * 当前登录用户信息
	 * 博主与访客共用此接口，返回体不包含密码
	 */
	@GetMapping("/me")
	public Result me() {
		User user = accountService.currentUser();
		if (user == null) {
			return Result.create(403, "请先登录");
		}
		return Result.ok("OK", AccountVO.of(user));
	}
}
