package com.coolskyai.constant;

/**
 * @Description: 需要鉴权的接口路径清单
 *
 * 集中在这里的唯一目的：新增接口时只改一处。
 * 之前路径与角色散落在 SecurityConfig 的链式调用里，
 * 每加一个 /xxx 接口就要记得再写一行 hasAnyRole("admin","visitor")，
 * 漏写会被默认规则静默放行（历史上已出现过）。
 *
 * 2026-11-05 起默认规则改为 denyAll：漏归类 = 403，不再是匿名可读。
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
			"/entitlements",
			// V1.2 技术服务：需求提交/报价确认/项目验收
			"/demand", "/demands", "/demand/**",
			"/quote/**",
			"/projects", "/project/**",
			"/milestone/**"
	};

	/**
	 * 匿名可访问的路径清单（显式白名单）
	 *
	 * R-03 的收口方式：从「默认放行 + 需要鉴权的写规则」改成「默认拒绝 + 显式放行」。
	 * 新增接口如果忘了归类，会得到 403 而不是静默匿名可达 —— 这是刻意的：
	 * 宁可上线前发现缺配置，也不要把用户数据裸露出去。
	 *
	 * 每一项都要能说清为什么公开，说不清的就该进 AUTHENTICATED。
	 */
	public static final String[] PUBLIC = {
			// 内容浏览
			"/blogs", "/blog", "/archives", "/searchBlog", "/category", "/tag",
			"/moments", "/about", "/site",
			// 评论：匿名可读；匿名可提交（密码文章另有 token 校验，见 CommentController）
			"/comments", "/comment",
			// 友链：匿名可看、可申请；浏览量计数在 Service 层按 IP 去重
			"/friends", "/friend",
			// 身份
			"/login", "/visitor/register", "/visitor/login",
			// 互动：点赞与文章密码校验不需要登录
			"/moment/like/**", "/checkBlogPassword",
			// 商品与服务展示
			"/products", "/productCategories", "/product/**",
			// 文件：下载靠一次性 token 自身校验；图片是公开静态资源
			"/file/download", "/image/**",
			// 权益探测：未登录返回 NOT_LOGIN，而不是 401
			"/resource/access",
			// 知识库检索：公开内容可匿名搜，付费块在服务层按权益过滤
			"/ai/search",
			// 问题反馈：允许未登录提交
			"/feedback",
			// Telegram webhook：路径里带 bot token，本身就是凭证
			"/tg/**",
			// 健康检查：给容器 healthcheck / 负载均衡用，只返回组件通断
			"/health", "/health/live",
			// Spring Boot 默认错误页，不放行会把 404 变成 403
			"/error"
	};
}
