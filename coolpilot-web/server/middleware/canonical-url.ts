/**
 * 规范 URL 中间件：www 归一到裸域、去掉多余的尾斜杠
 *
 * 两个问题都真实存在：
 *   1. www.coolskyai.com 和 coolskyai.com 都能打开且都返回 200，
 *      Google 会把它们当成两个站点分别积累权重，等于自己跟自己竞争。
 *   2. /blog/xxx 与 /blog/xxx/ 也是两份 200。canonical 能兜住，
 *      但那只是「建议」，301 才是「结论」——用 301 更干净。
 *
 * 只处理 www 前缀，不按 host 强制跳域名：这样用 IP 或内网名做健康检查、
 * 或者以后换域名，都不会被这条规则误伤。
 */
export default defineEventHandler((event) => {
	const url = getRequestURL(event)
	const path = url.pathname

	// 静态资源、接口、后台不走这套规则（它们由 gateway 直接分发，这里只是兜底）
	if (
		path.startsWith('/_nuxt') ||
		path.startsWith('/__nuxt') ||
		path.startsWith('/api/') ||
		path.startsWith('/cms') ||
		path.startsWith('/image/')
	) {
		return
	}

	const config = useRuntimeConfig()
	const siteHost = (() => {
		try {
			return new URL(String(config.public.siteUrl || '')).host
		} catch {
			return ''
		}
	})()

	// 1) www → 裸域。只在这个站自己的规范域名不带 www 时才跳，
	//    否则会把「本来就该用 www」的站点跳坏。
	if (siteHost && !siteHost.startsWith('www.') && url.hostname.startsWith('www.')) {
		return sendRedirect(event, `${url.protocol}//${siteHost}${path}${url.search}`, 301)
	}

	// 2) 尾斜杠归一。/ 本身除外；带扩展名的文件路径不动（正常不会带斜杠）
	if (path.length > 1 && path.endsWith('/')) {
		const trimmed = path.replace(/\/+$/, '')
		if (trimmed) {
			return sendRedirect(event, `${trimmed}${url.search}`, 301)
		}
	}
})
