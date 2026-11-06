/**
 * 站点地图
 *
 * 直接从后端拉数据生成，不需要额外维护一份列表。
 * 文章优先用 slug 生成地址，没有 slug 的回落到 id，保证每条都能打开。
 */
export default defineEventHandler(async (event) => {
	const config = useRuntimeConfig()
	const base = (config.public.siteUrl || 'http://localhost:8081').replace(/\/$/, '')
	const api = config.apiBase

	const staticPaths = ['/', '/archives', '/topic', '/projects', '/knowledge', '/service', '/follow', '/about', '/friends', '/search']

	const urls: {loc: string, lastmod?: string, changefreq?: string, priority?: string}[] = staticPaths.map(p => ({
		loc: base + p,
		changefreq: 'weekly',
		priority: p === '/' ? '1.0' : '0.6'
	}))

	try {
		const blogs: any = await $fetch(`${api}/blogs?pageNum=1&pageSize=1000`)
		for (const b of (blogs?.data?.list || [])) {
			urls.push({
				loc: `${base}/blog/${b.slug || b.id}`,
				lastmod: b.updateTime ? new Date(b.updateTime).toISOString().slice(0, 10) : undefined,
				changefreq: 'monthly',
				priority: '0.8'
			})
		}
	} catch {
		// 后端不可用时也要能返回一份可用的 sitemap（至少含静态页）
	}

	try {
		// 专题是长尾入口，单独提交；未发布的专题后端不会返回
		const topics: any = await $fetch(`${api}/topics`)
		for (const t of (topics?.data || [])) {
			urls.push({
				loc: `${base}/topic/${t.slug}`,
				lastmod: t.updateTime ? new Date(t.updateTime).toISOString().slice(0, 10) : undefined,
				changefreq: 'weekly',
				priority: '0.7'
			})
		}
	} catch {
		// 同上
	}

	try {
		const products: any = await $fetch(`${api}/products?pageNum=1&pageSize=500`)
		for (const p of (products?.data?.list || [])) {
			urls.push({
				loc: `${base}/knowledge/${p.productNo}`,
				lastmod: p.updateTime ? new Date(p.updateTime).toISOString().slice(0, 10) : undefined,
				changefreq: 'weekly',
				priority: '0.7'
			})
		}
	} catch {
		// 同上
	}

	try {
		// 项目展示：只有 enabled = 1 的才会被接口返回，未公开的不进 sitemap
		const portfolios: any = await $fetch(`${api}/portfolios`)
		for (const p of (portfolios?.data || [])) {
			urls.push({
				loc: `${base}/projects/${p.slug}`,
				lastmod: p.updateTime ? new Date(p.updateTime).toISOString().slice(0, 10) : undefined,
				changefreq: 'monthly',
				priority: '0.7'
			})
		}
	} catch {
		// 同上
	}

	const body = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls.map(u => `  <url>
    <loc>${escapeXml(u.loc)}</loc>${u.lastmod ? `\n    <lastmod>${u.lastmod}</lastmod>` : ''}${u.changefreq ? `\n    <changefreq>${u.changefreq}</changefreq>` : ''}${u.priority ? `\n    <priority>${u.priority}</priority>` : ''}
  </url>`).join('\n')}
</urlset>`

	setHeader(event, 'Content-Type', 'application/xml; charset=utf-8')
	setHeader(event, 'Cache-Control', 'public, max-age=3600')
	return body
})

function escapeXml(value: string) {
	return value.replace(/[<>&'"]/g, c => ({'<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;'}[c] || c))
}
