package top.naccl.modules.community.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.naccl.model.vo.PageResult;
import top.naccl.model.vo.Result;
import top.naccl.modules.account.service.AccountService;
import top.naccl.modules.community.entity.Favorite;
import top.naccl.modules.community.service.CommunityService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description: 收藏与点赞接口
 *
 * 语义：POST = 添加（幂等），DELETE = 取消（幂等）。
 * 这两个接口属于"用户级"，已在 SecurityConfig 中要求登录。
 *
 * @Author: Naccl
 * @Date: 2026-11-04
 */
@RestController
public class CommunityController {
	@Autowired
	private CommunityService communityService;
	@Autowired
	private AccountService accountService;

	@PostMapping("/favorite")
	public Result addFavorite(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.requireUserId();
		boolean favorited = communityService.addFavorite(userId, targetType, targetId);
		return Result.ok("操作成功", single("favorited", favorited));
	}

	@DeleteMapping("/favorite")
	public Result removeFavorite(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.requireUserId();
		boolean favorited = communityService.removeFavorite(userId, targetType, targetId);
		return Result.ok("操作成功", single("favorited", favorited));
	}

	@GetMapping("/favorites")
	public Result favorites(@RequestParam(defaultValue = "1") Integer pageNum,
	                        @RequestParam(defaultValue = "10") Integer pageSize) {
		Long userId = accountService.requireUserId();
		PageResult<Favorite> page = communityService.pageFavorites(userId, pageNum, pageSize);
		return Result.ok("OK", page);
	}

	@GetMapping("/favorite/status")
	public Result favoriteStatus(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.requireUserId();
		return Result.ok("OK", single("favorited", communityService.isFavorited(userId, targetType, targetId)));
	}

	@PostMapping("/like")
	public Result like(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.requireUserId();
		return Result.ok("操作成功", communityService.addLike(userId, targetType, targetId));
	}

	@DeleteMapping("/like")
	public Result unlike(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.requireUserId();
		return Result.ok("操作成功", communityService.removeLike(userId, targetType, targetId));
	}

	@GetMapping("/like/status")
	public Result likeStatus(@RequestParam String targetType, @RequestParam Long targetId) {
		Long userId = accountService.currentUserId();
		return Result.ok("OK", communityService.likeStatus(userId, targetType, targetId));
	}

	private Map<String, Object> single(String key, Object value) {
		Map<String, Object> data = new HashMap<>(2);
		data.put(key, value);
		return data;
	}
}
