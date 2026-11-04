package top.naccl.modules.entitlement.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.annotation.OperationLogger;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.entitlement.dto.GrantDTO;
import top.naccl.modules.entitlement.dto.RevokeDTO;
import top.naccl.modules.entitlement.service.EntitlementService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 权益管理（后台）
 * @Author: Naccl
 * @Date: 2026-11-05
 */
@RestController
public class EntitlementAdminController {
	@Autowired
	private EntitlementService entitlementService;
	@Autowired
	private AccountService accountService;

	@GetMapping("/admin/entitlements")
	public Result list(@RequestParam(required = false) Long userId,
	                   @RequestParam(required = false) Long productId,
	                   @RequestParam(required = false) String status,
	                   @RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", entitlementService.pageAdmin(userId, productId, status, pageNum, pageSize));
	}

	@OperationLogger("人工发放权益")
	@PostMapping("/admin/entitlement/grant")
	public Result grant(@RequestBody GrantDTO dto) {
		Long operatorId = accountService.requireUserId();
		Map<String, Object> data = new HashMap<>(2);
		data.put("id", entitlementService.grant(dto, operatorId));
		return Result.ok("发放成功", data);
	}

	@OperationLogger("撤销权益")
	@PostMapping("/admin/entitlement/revoke")
	public Result revoke(@RequestBody RevokeDTO dto) {
		if (dto.getId() == null) {
			return Result.create(400, "权益ID不能为空");
		}
		Long operatorId = accountService.requireUserId();
		entitlementService.revoke(dto.getId(), dto.getReason(), operatorId);
		return Result.ok("已撤销");
	}

	@GetMapping("/admin/entitlement/logs")
	public Result logs(@RequestParam(required = false) Long userId,
	                   @RequestParam(required = false) Long productId,
	                   @RequestParam(required = false) String action,
	                   @RequestParam(defaultValue = "1") Integer pageNum,
	                   @RequestParam(defaultValue = "10") Integer pageSize) {
		return Result.ok("OK", entitlementService.pageLogs(userId, productId, action, pageNum, pageSize));
	}

	/**
	 * 手动触发过期扫描（无定时任务依赖，运维可随时调用）
	 */
	@OperationLogger("过期权益扫描")
	@PostMapping("/admin/entitlement/expire")
	public Result expire() {
		Map<String, Object> data = new HashMap<>(2);
		data.put("count", entitlementService.expireOutdated());
		return Result.ok("扫描完成", data);
	}
}
