package com.coolskyai.modules.service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.model.vo.Result;
import com.coolskyai.modules.account.service.AccountService;
import com.coolskyai.modules.service.dto.DeliverDTO;
import com.coolskyai.modules.service.dto.DemandReviewDTO;
import com.coolskyai.modules.service.dto.DemandStatusDTO;
import com.coolskyai.modules.service.dto.MilestoneSaveDTO;
import com.coolskyai.modules.service.dto.ProjectCreateDTO;
import com.coolskyai.modules.service.dto.QuoteSaveDTO;
import com.coolskyai.modules.service.dto.ServiceItemDTO;
import com.coolskyai.modules.service.service.DemandService;
import com.coolskyai.modules.service.service.ProjectService;
import com.coolskyai.modules.service.service.ServiceItemService;
import com.coolskyai.util.CurrentUser;

/**
 * @Description: 技术服务后台（需求审核 / 报价 / 项目 / 交付）
 *
 * 所有 /admin/** 接口由 Spring Security 限定为 ROLE_owner，
 * 这里只负责取操作人，写进状态流水。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
@RestController
public class ServiceAdminController {

	@Autowired
	private DemandService demandService;
	@Autowired
	private ProjectService projectService;
	@Autowired
	private ServiceItemService serviceItemService;
	@Autowired
	private AccountService accountService;

	// ---------------- 服务商品扩展 ----------------

	@GetMapping("/admin/serviceItem/{productId}")
	public Result getServiceItem(@PathVariable Long productId) {
		return Result.ok("OK", serviceItemService.findByProductId(productId));
	}

	@PutMapping("/admin/serviceItem/{productId}")
	public Result saveServiceItem(@PathVariable Long productId, @RequestBody ServiceItemDTO dto) {
		return Result.ok("已保存", serviceItemService.save(productId, dto.getDeliveryDays(),
				dto.getScope(), dto.getProcess(), dto.getFaq()));
	}

	// ---------------- 需求 ----------------

	@GetMapping("/admin/demands")
	public Result demands(@RequestParam(required = false) String status,
	                      @RequestParam(required = false) String keyword,
	                      @RequestParam(defaultValue = "1") Integer pageNum,
	                      @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", demandService.pageAdmin(status, keyword, pageNum, pageSize));
	}

	@GetMapping("/admin/demand/{demandNo}")
	public Result demandDetail(@PathVariable String demandNo) {
		// 后台视角：isOwner=true，联系人字段可见
		return Result.ok("OK", demandService.detail(demandNo, null, true));
	}

	@PostMapping("/admin/demand/{demandNo}/review")
	public Result review(@PathVariable String demandNo, @RequestBody DemandReviewDTO dto) {
		demandService.review(demandNo, dto, operatorId(), operatorName());
		return Result.ok(Boolean.TRUE.equals(dto.getApprove()) ? "已通过，进入待报价" : "已拒绝", null);
	}

	@PutMapping("/admin/demand/{demandNo}/status")
	public Result moveDemand(@PathVariable String demandNo, @RequestBody DemandStatusDTO dto) {
		demandService.moveStatus(demandNo, dto.getStatus(), dto.getRemark(), operatorId(), operatorName());
		return Result.ok("状态已更新", null);
	}

	// ---------------- 报价 ----------------

	@PostMapping("/admin/quote")
	public Result createQuote(@RequestBody QuoteSaveDTO dto) {
		return Result.ok("报价草稿已创建", demandService.createQuote(dto, operatorId(), operatorName()));
	}

	@PutMapping("/admin/quote/{id}")
	public Result updateQuote(@PathVariable Long id, @RequestBody QuoteSaveDTO dto) {
		return Result.ok("已保存", demandService.updateQuote(id, dto, operatorId(), operatorName()));
	}

	@PostMapping("/admin/quote/{id}/send")
	public Result sendQuote(@PathVariable Long id) {
		demandService.sendQuote(id, operatorId(), operatorName());
		return Result.ok("报价已发出，等待客户确认", null);
	}

	// ---------------- 项目 ----------------

	@GetMapping("/admin/projects")
	public Result projects(@RequestParam(required = false) String status,
	                       @RequestParam(required = false) String keyword,
	                       @RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", projectService.pageAdmin(status, keyword, pageNum, pageSize));
	}

	@GetMapping("/admin/project/{projectNo}")
	public Result projectDetail(@PathVariable String projectNo) {
		return Result.ok("OK", projectService.detail(projectNo, null, true));
	}

	@PostMapping("/admin/project")
	public Result createProject(@RequestBody ProjectCreateDTO dto) {
		return Result.ok("项目已创建", projectService.createManual(dto, operatorId(), operatorName()));
	}

	@PutMapping("/admin/project/{projectNo}/status")
	public Result moveProject(@PathVariable String projectNo, @RequestBody DemandStatusDTO dto) {
		projectService.moveProject(projectNo, dto.getStatus(), operatorId(), operatorName());
		return Result.ok("状态已更新", null);
	}

	// ---------------- 里程碑与交付 ----------------

	@PostMapping("/admin/milestone")
	public Result addMilestone(@RequestBody MilestoneSaveDTO dto) {
		return Result.ok("里程碑已添加", projectService.addMilestone(dto, operatorId(), operatorName()));
	}

	@PutMapping("/admin/milestone/{id}")
	public Result updateMilestone(@PathVariable Long id, @RequestBody MilestoneSaveDTO dto) {
		projectService.updateMilestone(id, dto, operatorId(), operatorName());
		return Result.ok("已保存", null);
	}

	@PostMapping("/admin/milestone/{id}/deliver")
	public Result deliver(@PathVariable Long id, @RequestBody DeliverDTO dto) {
		return Result.ok("已交付，等待客户验收", projectService.deliver(id, dto.getDeliveryType(),
				dto.getFileId(), dto.getContent(), operatorId(), operatorName(), dto.getRemark()));
	}

	// ---------------- 内部 ----------------

	private Long operatorId() {
		Long id = accountService.currentUserId();
		return id == null ? 0L : id;
	}

	private String operatorName() {
		String username = CurrentUser.ownerUsername();
		return username == null ? "平台所有者" : username;
	}
}
