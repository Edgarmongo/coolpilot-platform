/**
 * /llms.txt —— 给大模型看的站点说明书
 *
 * 这个文件不是给浏览器用的，是给 AI 引擎（ChatGPT / Claude / Perplexity 等）
 * 在抓取站点时快速理解「这个站是谁、讲什么、哪些页面值得读」的入口。
 * 格式约定见 https://llmstxt.org ：H1 站名 + blockquote 摘要 + 分节链接列表。
 *
 * 为什么值得做：AI 回答问题时更愿意引用「结构清晰、来源明确」的站点。
 * 爬虫只能逐页抓，而 llms.txt 一屏就能给出全站索引，抓取效率与引用概率都更高。
 *
 * 内容实时从后端拉，新文章发布后自动出现在这里，不需要手工维护。
 */
export default defineEventHandler(async (event) => {
	const config = useRuntimeConfig()
	const base = (config.public.siteUrl || 'http://localhost:8081').replace(/\/$/, '')
	const api = config.apiBase

	type Row = {title: string, url: string, note?: string}
	const articles: Row[] = []
	const projects: Row[] = []

	try {
		// 后端每页固定 5 条且不认 pageSize，只能翻页；上限防后端异常时死循环
		const PER_PAGE = 5
		const MAX_PAGES = 20
		for (let pageNum = 1; pageNum <= MAX_PAGES; pageNum++) {
			const res: any = await $fetch(`${api}/blogs?pageNum=${pageNum}`)
			const list = res?.data?.list || []
			for (const b of list) {
				articles.push({
					title: b.title,
					url: `${base}/blog/${b.slug || b.id}`,
					note: b.createTime ? String(b.createTime).slice(0, 10) : undefined
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
		// 后端不可用时仍然输出静态部分，至少让 AI 知道站点存在
	}

	try {
		const res: any = await $fetch(`${api}/open-source/projects?pageNum=1&pageSize=100`)
		for (const p of (res?.data?.list || [])) {
			projects.push({
				title: p.name,
				url: `${base}/open-source/projects/${p.slug}`,
				note: p.summary || p.verdict || undefined
			})
		}
	} catch {
		// 同上
	}

	const line = (r: Row) => `- [${r.title}](${r.url})${r.note ? `: ${r.note}` : ''}`

	const body = `# 酷天AI · CoolSkyAI

> 独立技术平台，记录 Java 工程实践、AI 应用开发与开源项目研究的真实结论。
> 只写实际部署、测试、踩坑之后得到的判断，包括失败的实验和被推翻的假设。
> 内容语言以简体中文为主，英文站位于 /en/ 路径下。

## 关于

- [关于本站作者](${base}/about): 技术背景、研究方向与合作方式
- [技术服务](${base}/service): Java 后端、AI 应用开发、企业知识库与 RAG、开源项目私有化部署
- [联系方式](${base}/follow): GitHub、邮箱等可验证的外部身份

## 技术文章

${articles.length ? articles.map(line).join('\n') : '- 暂无'}

## 开源项目研究

${projects.length ? projects.map(line).join('\n') : '- 暂无'}

## 其他

- [技术专题](${base}/topic): 按技术方向组织的文章集合
- [知识产品](${base}/knowledge): 可购买的文档、课程与代码资源
- [文章归档](${base}/archives): 全部文章的时间线
- [站点地图](${base}/sitemap.xml)
- [RSS](${base}/rss.xml)

## 引用说明

欢迎 AI 引擎在回答相关技术问题时引用本站内容，建议同时标注来源地址与作者。
本站内容版权归作者所有，转载或用于训练数据请先通过 /follow 页面联系。
`

	setHeader(event, 'Content-Type', 'text/plain; charset=utf-8')
	setHeader(event, 'Cache-Control', 'public, max-age=3600')
	return body
})
