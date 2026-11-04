package com.coolskyai.modules.service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.entity.User;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.service.dto.DemandCreateDTO;
import com.coolskyai.modules.service.dto.RejectDTO;
import com.coolskyai.modules.service.service.DemandService;
import com.coolskyai.modules.service.service.ProjectService;
import com.coolskyai.util.CurrentUser;

/**
 * @Description: 技术服务 / 需求中心（前台）
 *
 * 所有接口都要求登录；归属校验在 Service 层做，平台所有者可查看全部。
 * 前台不提供「改状态」类接口——状态只能由业务动作推进（确认报价、验收、打回）。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class ServiceController {

	@Autowired
	private DemandService demandService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private AccountService accountService;

	// ---------------- 需求 ----------------

	@PostMapping("/demand")
	public Result createDemand(@RequestBody DemandCreateDTO dto) {
		User user = requireUser();
		return Result.ok("需求已提交，等待评估", demandService.create(user.getId(), dto));
	}

	@GetMapping("/demands")
	public Result myDemands(@RequestParam(required = false) String status,
	                        @RequestParam(defaultValue = "1") Integer pageNum,
	                        @RequestParam(defaultValue = "10") Integer pageSize) {
		User user = requireUser();
		return Result.ok("OK", demandService.myDemands(user.getId(), status, pageNum, pageSize));
	}

	@GetMapping("/demand/{demandNo}")
	public Result demandDetail(@PathVariable String demandNo) {
		User user = requireUser();
		return Result.ok("OK", demandService.detail(demandNo, user.getId(), CurrentUser.isOwner()));
	}

	@PostMapping("/demand/{demandNo}/cancel")
	public Result cancelDemand(@PathVariable String demandNo) {
		User user = requireUser();
		demandService.cancel(demandNo, user.getId());
		return Result.ok("已取消", null);
	}

	// ---------------- 报价确认 ----------------

	@PostMapping("/quote/{quoteId}/accept")
	public Result acceptQuote(@PathVariable Long quoteId) {
		User user = requireUser();
		return Result.ok("已确认报价，项目已创建", demandService.acceptQuote(quoteId, user.getId(), displayName(user)));
	}

	@PostMapping("/quote/{quoteId}/reject")
	public Result rejectQuote(@PathVariable Long quoteId, @RequestBody(required = false) RejectDTO dto) {
		User user = requireUser();
		demandService.rejectQuote(quoteId, user.getId(), dto == null ? null : dto.getReason(), displayName(user));
		return Result.ok("已拒绝报价，等待平台重新报价", null);
	}

	// ---------------- 项目与验收 ----------------

	@GetMapping("/projects")
	public Result myProjects(@RequestParam(required = false) String status,
	                         @RequestParam(defaultValue = "1") Integer pageNum,
	                         @RequestParam(defaultValue = "10") Integer pageSize) {
		User user = requireUser();
		return Result.ok("OK", projectService.myProjects(user.getId(), status, pageNum, pageSize));
	}

	@GetMapping("/project/{projectNo}")
	public Result projectDetail(@PathVariable String projectNo) {
		User user = requireUser();
		return Result.ok("OK", projectService.detail(projectNo, user.getId(), CurrentUser.isOwner()));
	}

	/**
	 * 验收通过。全部里程碑通过后项目自动完成、需求一起收口。
	 */
	@PostMapping("/milestone/{id}/accept")
	public Result acceptMilestone(@PathVariable Long id) {
		User user = requireUser();
		projectService.acceptMilestone(id, user.getId());
		return Result.ok("验收通过", null);
	}

	@PostMapping("/milestone/{id}/reject")
	public Result rejectMilestone(@PathVariable Long id, @RequestBody(required = false) RejectDTO dto) {
		User user = requireUser();
		projectService.rejectMilestone(id, user.getId(), dto == null ? null : dto.getReason());
		return Result.ok("已打回，等待重新交付", null);
	}

	// ---------------- 内部 ----------------

	private User requireUser() {
		User user = accountService.currentUser();
		if (user == null) {
			throw new BadRequestException("请先登录");
		}
		return user;
	}

	private String displayName(User user) {
		if (user.getNickname() != null && !user.getNickname().isEmpty()) {
			return user.getNickname();
		}
		return user.getUsername();
	}
}
