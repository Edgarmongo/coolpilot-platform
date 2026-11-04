package com.coolskyai.constant;

/**
 * @Description: 需要鉴权的接口路径清单
 *
 * 集中在这里的唯一目的：新增接口时只改一处。
 * 之前路径与角色散落在 SecurityConfig 的链式调用里，
 * 每加一个 /xxx 接口就要记得再写一行 hasAnyRole("admin","visitor")，
 * 漏写会被 anyRequest().permitAll() 静默放行（历史上已出现过）。
 *
 * 摆放顺序与匹配优先级无关（Spring Security 按注册顺序匹配，
 * SecurityConfig 里的顺序才是最终顺序），这里只做分组。
 *
 * @Author: CoolSkyAI
 * @Date: 2026-11-05
 */
public final class SecurityPaths {

	private SecurityPaths() {
	}

	/**
	 * 仅平台所有者可访问：后台管理接口
	 *
	 * 注意 /admin/webTitleSuffix 在 SecurityConfig 中先于本组放行。
	 */
	public static final String[] OWNER_ONLY = {
			"/admin/**"
	};

	/**
	 * 登录即可访问（平台所有者与前台用户同权）
	 *
	 * 归属校验（只能看自己的订单/消息/收藏）在 Service 层做，
	 * 这里只负责挡住未登录请求。
	 */
	public static final String[] AUTHENTICATED = {
			// 账户中心
			"/me", "/me/**",
			// 社区：收藏、点赞、站内消息
			"/favorite", "/favorite/**", "/favorites",
			"/like", "/like/**",
			"/messages", "/messages/**", "/message/**",
			// 文件：换下载凭证、用户上传凭证
			"/file/token", "/file/upload",
			// 交易与权益
			"/order", "/orders", "/order/**",
			"/payment", "/payment/**",
			"/refund", "/refunds", "/refund/**",
			"/entitlements"
	};

	/**
	 * 匿名可访问，但有意为之的路径：
	 *   /feedback        允许未登录提交问题反馈
	 *   /resource/access 未登录返回 NOT_LOGIN 而不是 401，前端据此显示"登录后购买"
	 *   /visitor/register、/visitor/login 前台自助注册与登录
	 *   /login           博主前台登录（历史接口）
	 * 其余未命中上面两组的请求一律 permitAll。
	 */
}
