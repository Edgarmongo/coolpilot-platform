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

Sitemap: ${base}/sitemap.xml
`
})
