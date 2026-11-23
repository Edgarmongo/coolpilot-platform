/**
 * RSS 2.0 订阅源
 *
 * 三个实际作用：
 *   1. 让 RSS 阅读器 / 聚合站抓取，这类抓取常带来第一批外链；
 *   2. 各类「内容监控」工具（含部分 AI 训练与索引管线）靠 feed 发现新文章；
 *   3. 搜索引擎把 feed 当作更新信号，新文章收录更快。
 *
 * 只输出摘要与链接，不灌全文——全文会让聚合站直接镜像走流量。
 */
export default defineEventHandler(async (event) => {
	const config = useRuntimeConfig()
	const base = (config.public.siteUrl || 'http://localhost:8081').replace(/\/$/, '')
	const api = config.apiBase

	const items: {title: string, link: string, date?: string, category?: string}[] = []

	try {
		const PER_PAGE = 5
		const MAX_PAGES = 4 // feed 只放最近 20 篇，老文章靠 sitemap 覆盖
		for (let pageNum = 1; pageNum <= MAX_PAGES; pageNum++) {
			const res: any = await $fetch(`${api}/blogs?pageNum=${pageNum}`)
			const list = res?.data?.list || []
			for (const b of list) {
				items.push({
					title: b.title,
					link: `${base}/blog/${b.slug || b.id}`,
					date: b.createTime,
					category: b.category?.name
				})
			}
			const totalPage = res?.data?.totalPage
			if (typeof totalPage === 'number') {
				if (pageNum >= totalPage) break
			} else if (list.length < PER_PAGE) {
				break
			}
		}
	} catch {
		// 后端不可用时返回一个只有频道信息的空 feed，不要让订阅端拿到 500
	}

	const toRfc822 = (v?: string) => {
		if (!v) return ''
		const d = new Date(v)
		return isNaN(d.getTime()) ? '' : d.toUTCString()
	}

	const body = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>酷天AI · CoolSkyAI</title>
    <link>${base}/</link>
    <description>Java 工程实践、AI 应用开发与开源项目研究的真实结论</description>
    <language>zh-CN</language>
    <atom:link href="${base}/rss.xml" rel="self" type="application/rss+xml"/>
${items.map(i => `    <item>
      <title>${escapeXml(i.title)}</title>
      <link>${escapeXml(i.link)}</link>
      <guid isPermaLink="true">${escapeXml(i.link)}</guid>${i.date ? `\n      <pubDate>${toRfc822(i.date)}</pubDate>` : ''}${i.category ? `\n      <category>${escapeXml(i.category)}</category>` : ''}
    </item>`).join('\n')}
  </channel>
</rss>`

	setHeader(event, 'Content-Type', 'application/rss+xml; charset=utf-8')
	setHeader(event, 'Cache-Control', 'public, max-age=1800')
	return body
})

function escapeXml(value: string) {
	return String(value).replace(/[<>&'"]/g, c => ({'<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;'}[c] || c))
}
