package com.coolskyai.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coolskyai.annotation.AccessLimit;
import com.coolskyai.util.IpAddressUtils;

import jakarta.servlet.http.HttpServletRequest;
import com.coolskyai.annotation.VisitLogger;
import com.coolskyai.enums.VisitBehavior;
import com.coolskyai.model.vo.Friend;
import com.coolskyai.model.vo.FriendInfo;
import com.coolskyai.model.vo.Result;
import com.coolskyai.service.FriendService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 友链
 * @Author: CoolSkyAI
 * @Date: 2020-09-08
 */
@RestController
public class FriendController {
	@Autowired
	FriendService friendService;

	@Value("${security.trust-forwarded-for:false}")
	private boolean trustForwardedFor;

	/**
	 * 获取友链页面
	 *
	 * @return
	 */
	@VisitLogger(VisitBehavior.FRIEND)
	@GetMapping("/friends")
	public Result friends() {
		List<Friend> friendList = friendService.getFriendVOList();
		FriendInfo friendInfo = friendService.getFriendInfo(true, true);
		Map<String, Object> map = new HashMap<>(4);
		map.put("friendList", friendList);
		map.put("friendInfo", friendInfo);
		return Result.ok("获取成功", map);
	}

	/**
	 * 按昵称增加友链浏览次数
	 *
	 * @param nickname 友链昵称
	 * @return
	 */
	@VisitLogger(VisitBehavior.CLICK_FRIEND)
	@AccessLimit(seconds = 60, maxCount = 30, msg = "操作过于频繁，请稍后再试")
	@PostMapping("/friend")
	public Result addViews(@RequestParam String nickname, HttpServletRequest request) {
		/*
		 * 去重用的 IP 默认取 TCP 对端地址，而不是 X-Forwarded-For：
		 * 后者客户端可以随便写，换一个伪造头就换一个"新访客"，去重形同虚设。
		 * 部署在可信反代之后（且反代会覆盖该头）时，把
		 * security.trust-forwarded-for 打开即可恢复按真实客户端 IP 统计。
		 */
		String ip = trustForwardedFor ? IpAddressUtils.getIpAddress(request) : request.getRemoteAddr();
		friendService.addViewsOnce(nickname, ip);
		return Result.ok("请求成功");
	}
}
