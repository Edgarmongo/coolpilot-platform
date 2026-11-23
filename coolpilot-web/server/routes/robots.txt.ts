/**
 * robots.txt
 *
 * 后台与接口不需要被收录；其余全放行，并在末尾指向 sitemap。
 */
export default defineEventHandler((event) => {
	const config = useRuntimeConfig()
	const base = (config.public.siteUrl || 'http://localhost:8081').replace(/\/$/, '')

	setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
	return `User-agent: *
Allow: /
Disallow: /api/
Disallow: /cms/
Disallow: /me
Disallow: /order/
Disallow: /login
Disallow: /register

# ---- AI 引擎（GEO）----
# 显式放行而不是只靠上面的通配：一是表明「欢迎引用」的意图，
# 二是将来若有人加了全局 Disallow，这几条能让主流 AI 爬虫不被误伤。
# 注意：Cloudflare 的 Bot Fight Mode 会绕过 robots.txt 直接拦，
# 需要在 CF 后台确认它是关闭的，否则这里写得再好也没用。
User-agent: GPTBot
Allow: /

User-agent: OAI-SearchBot
Allow: /

User-agent: ChatGPT-User
Allow: /

User-agent: ClaudeBot
Allow: /

User-agent: PerplexityBot
Allow: /

User-agent: Google-Extended
Allow: /

# 给 AI 的站点说明书，见 https://llmstxt.org
# /llms.txt

Sitemap: ${base}/sitemap.xml
`
})
