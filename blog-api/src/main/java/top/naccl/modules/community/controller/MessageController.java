package top.naccl.modules.community.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.model.vo.PageResult;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.community.entity.Message;
import top.naccl.modules.community.service.MessageService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 站内消息接口
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@RestController
public class MessageController {
	@Autowired
	private MessageService messageService;
	@Autowired
	private AccountService accountService;

	@GetMapping("/messages")
	public Result messages(@RequestParam(defaultValue = "1") Integer pageNum,
	                       @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		PageResult<Message> page = messageService.page(userId, pageNum, pageSize);
		return Result.ok("OK", page);
	}

	@GetMapping("/messages/unread-count")
	public Result unreadCount() {
		Long userId = accountService.requireUserId();
		Map<String, Object> data = new HashMap<>(2);
		data.put("count", messageService.unreadCount(userId));
		return Result.ok("OK", data);
	}

	@PutMapping("/message/{id}/read")
	public Result markRead(@PathVariable Long id) {
		Long userId = accountService.requireUserId();
		messageService.markRead(id, userId);
		return Result.ok("已读");
	}
}
