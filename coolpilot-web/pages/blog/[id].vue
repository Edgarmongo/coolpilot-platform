<template>
	<div class="post-page">
		<!-- 已解锁 / 无密码 -->
		<template v-if="blog">
			<div class="with-side">
				<article class="article-main">
					<nav class="crumb">
						<NuxtLink :to="localePath('/')">{{ t('post.crumbHome') }}</NuxtLink>
						<span class="crumb-sep">/</span>
						<NuxtLink :to="localePath('/archives')">{{ t('archives.listTitle') }}</NuxtLink>
						<template v-if="blog.category">
							<span class="crumb-sep">/</span>
							<NuxtLink :to="localePath(`/category/${blog.category.name}`)">{{ blog.category.name }}</NuxtLink>
						</template>
					</nav>

					<div class="tags article-tags">
						<span v-if="blog.category" class="tag field">{{ blog.category.name }}</span>
						<NuxtLink v-for="tag in (blog.tags || [])" :key="tag.id" :to="localePath(`/tag/${tag.name}`)"
						          class="tag">{{ tag.name }}</NuxtLink>
					</div>

					<h1 class="article-title">{{ blog.title }}</h1>
					<p v-if="plainText(blog.description)" class="article-sub">{{ plainText(blog.description) }}</p>

					<div class="meta article-meta">
						<span>{{ formatDate(blog.createTime, 'YYYY-MM-DD') }}</span>
						<template v-if="blog.readTime">
							<span class="sep">·</span><span>{{ t('home.minutes', {n: blog.readTime}) }}</span>
						</template>
						<span class="sep">·</span>
						<span>{{ t('post.reads', {n: blog.views || 0}) }}</span>
					</div>

					<div class="article-cover">
						<AppCover :seed="`${blog.id}-${blog.title}`" :height="220"/>
					</div>

					<!-- 正文：标题锚点由 rendered 统一注入，正文原文不改 -->
					<div class="article" v-html="rendered.html"></div>

					<!-- 本篇所属专题：从单篇回到系列阅读路径 -->
					<div v-if="topics.length" class="topic-tip">
						<span class="muted">{{ t('post.inTopic') }}</span>
						<NuxtLink v-for="item in topics" :key="item.id" :to="localePath(`/topic/${item.slug}`)" class="tag">
							{{ item.title }}
						</NuxtLink>
						<span v-if="topics[0].articleCount" class="muted">{{ t('post.topicCount', {n: topics[0].articleCount}) }}</span>
					</div>

					<!-- 本篇所属项目：与项目档案形成双向内链。未关联时不渲染，不留空白 -->
					<div v-if="ossProject" class="oss-link">
						<span class="muted oss-label">{{ t('oss.blog.inProject') }}</span>
						<NuxtLink :to="localePath(`/open-source/projects/${ossProject.slug}`)" class="oss-card">
							<span class="oss-name">{{ ossProject.name }}</span>
							<span class="oss-summary">{{ ossProject.summary }}</span>
							<span v-if="ossProject.verdict" class="oss-verdict">{{ t(`oss.verdicts.${ossProject.verdict}`) }}</span>
						</NuxtLink>
					</div>

					<!-- 关联付费内容 -->
					<div v-if="relatedProducts.length" class="related">
						<h3 class="block-title">{{ t('post.related') }}</h3>
						<div class="related-grid">
							<NuxtLink v-for="item in relatedProducts" :key="item.id"
							          :to="localePath(`/knowledge/${item.productNo}`)" class="related-item">
								<div class="ri-head">
									<span class="tag">{{ typeText(item.productType) }}</span>
									<span v-if="item.owned" class="owned">{{ t('post.owned') }}</span>
								</div>
								<strong>{{ item.title }}</strong>
								<p class="muted ri-summary">{{ item.summary }}</p>
								<span class="amount">{{ priceLabel(item) }}</span>
							</NuxtLink>
						</div>
					</div>

					<!-- 收藏与点赞：未登录时点击跳转登录 -->
					<div class="actions">
						<button :class="['act', {active: favorited}]" @click="toggleFavorite">
							{{ favorited ? t('post.favorited') : t('post.favorite') }}
						</button>
						<button :class="['act', {active: liked}]" @click="toggleLike">
							{{ liked ? t('post.liked') : t('post.like') }}<span v-if="likeCount" class="cnt">{{ likeCount }}</span>
						</button>
						<NuxtLink :to="localePath('/archives')" class="act act-link">{{ t('post.backToList') }}</NuxtLink>
					</div>

					<CommentSection :blog-id="blogId"/>
				</article>

				<aside class="side">
					<div v-if="rendered.toc.length > 1" class="side-block">
						<div class="side-title">{{ t('post.toc') }}</div>
						<ul class="toc">
							<li v-for="item in rendered.toc" :key="item.id" :class="{lv3: item.lv === 3}">
								<a :href="`#${item.id}`">{{ item.text }}</a>
							</li>
						</ul>
					</div>

					<div v-if="relatedPosts.length" class="side-block">
						<div class="side-title">{{ t('post.relatedReads') }}</div>
						<ul class="side-list">
							<li v-for="item in relatedPosts" :key="item.id">
								<NuxtLink :to="localePath(`/blog/${item.slug || item.id}`)">{{ item.title }}</NuxtLink>
							</li>
						</ul>
					</div>
				</aside>
			</div>
		</template>

		<!-- 需要密码 -->
		<div v-else-if="locked" class="locked-wrap">
			<div class="card locked">
				<h1 class="page-title">{{ t('post.lockedTitle') }}</h1>
				<p class="page-desc">{{ message || t('post.lockedDesc') }}</p>
				<div class="lock-form">
					<input v-model="password" type="password" :placeholder="t('post.pwdPlaceholder')" @keyup.enter="unlock">
					<button class="btn" :disabled="unlocking" @click="unlock">
						{{ unlocking ? t('post.verifying') : t('post.unlock') }}
					</button>
				</div>
				<p class="muted">{{ t('post.unlockHint') }}</p>
			</div>
		</div>

		<div v-else class="empty">{{ message || t('post.notFound') }}</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()
const {t, te, locale} = useI18n()
const localePath = useLocalePath()
// 结构化数据里的作者名跟着品牌走（中文酷天AI / 英文CoolSkyAI）
const brand = useBrand()
const siteUrl = String(useRuntimeConfig().public.siteUrl || '').replace(/\/$/, '')

/** 摘要要从 HTML 正文里抠纯文本：直接截 HTML 会把标签也塞进 description */
function plainText(html: string) {
	return (html || '')
		.replace(/<[^>]+>/g, ' ')
		.replace(/[#>*`~]+/g, ' ')
		.replace(/\s+/g, ' ')
		.trim()
}

/** og:image 必须是绝对地址，相对路径各家抓取器都不认 */
function absoluteUrl(url: string) {
	if (!url) {
		return ''
	}
	if (/^https?:\/\//i.test(url)) {
		return url
	}
	return siteUrl + (url.startsWith('/') ? url : '/' + url)
}

/**
 * 路由参数既可能是旧链接的 id，也可能是新链接的 slug。
 * 纯数字按 id 处理，其余按 slug 查——旧链接因此不会失效。
 */
const param = String(route.params.id)
const isIdParam = /^\d+$/.test(param)
const blogQuery = isIdParam ? `id=${param}` : `slug=${encodeURIComponent(param)}`
const blog = ref<any>(null)
// 评论、收藏、点赞都需要真实 id，slug 进来时由接口返回后再填
const blogId = ref<number>(isIdParam ? Number(param) : 0)
const locked = ref(false)
// 文章确实不存在（后端 404）。这个标记只用于首次加载后抛 404，
// 解锁流程里再次拉取失败不该触发，否则输错密码会跳成 404 页。
const notFound = ref(false)
const message = ref('')
const password = ref('')
const unlocking = ref(false)
const favorited = ref(false)
const liked = ref(false)
const likeCount = ref(0)
const relatedProducts = ref<any[]>([])
const topics = ref<any[]>([])
const relatedPosts = ref<any[]>([])
const ossProject = ref<any>(null)
// 密码凭证按文章维度存，30 天有效
const pwdToken = useCookie<string | null>(`cp_blog_pwd_${param}`, {maxAge: 60 * 60 * 24 * 30, sameSite: 'lax'})

/**
 * 正文里给 h2/h3 补 id，供右侧目录跳转。
 *
 * 为什么不改后端：Markdown 转 HTML 是 CMS 契约的一部分（5 处共用），
 * 在这里按渲染结果加锚点，比给整条链路加参数影响面小得多。
 * 原文不落库、不改写，每次渲染重新生成，规则简单可预测。
 */
const rendered = computed<{html: string, toc: {id: string, text: string, lv: number}[]}>(() => {
	const toc: {id: string, text: string, lv: number}[] = []
	let index = 0
	const html = String(blog.value?.content || '').replace(
		/<h([23])([^>]*)>([\s\S]*?)<\/h\1>/g,
		(match, level, attrs, inner) => {
			// 标题里可能有行内 code、引号转义，目录要的是纯文本
			const text = decodeEntities(String(inner).replace(/<[^>]+>/g, '')).trim()
			if (!text) {
				return match
			}
			index += 1
			const id = `sec-${index}`
			toc.push({id, text, lv: Number(level)})
			// 正文里如果已经有 id，先去掉，避免同一元素出现两个 id
			const cleanAttrs = String(attrs).replace(/\sid="[^"]*"/g, '')
			return `<h${level}${cleanAttrs} id="${id}">${inner}</h${level}>`
		}
	)
	return {html, toc}
})

/** 正文由 Markdown 渲染时会带实体（&quot; 之类），目录里不能原样显示 */
function decodeEntities(text: string) {
	return text
		.replace(/&#(\d+);/g, (_, code) => String.fromCharCode(Number(code)))
		.replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCharCode(parseInt(code, 16)))
		.replace(/&quot;/g, '"')
		.replace(/&apos;|&#39;/g, "'")
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&nbsp;/g, ' ')
		.replace(/&amp;/g, '&')
}

async function fetchBlog() {
	const headers: Record<string, string> = {}
	if (pwdToken.value) {
		headers.authorization = pwdToken.value
	}
	// 密码文章只认自己的凭证，不能带访客 token（后端会把它当 blogId 解析）
	const res: any = await call(`/blog?${blogQuery}`, {headers}).catch(() => null)
	if (res && res.code === 200) {
		blog.value = res.data
		if (res.data && res.data.id) {
			blogId.value = res.data.id
			await Promise.all([loadRelated(), loadTopics(), loadRelatedPosts(), loadOssProject()])
		}
		locked.value = false
		message.value = ''
	} else if (res && res.code === 403) {
		/*
		 * 403 说明这张凭证已经不作数了（过期、换了文章、或 R-12 之前的老格式）。
		 * 清掉它，否则每次进页面都白带一个无效 token，解锁时也容易让人以为是密码错。
		 */
		pwdToken.value = null
		blog.value = null
		locked.value = true
		message.value = res.msg || '该文章受密码保护'
	} else {
		blog.value = null
		locked.value = false
		// 后端对不存在的 slug 返回 code 404，这里记下来，交给首次加载后统一抛，
		// 不直接 throw：解锁流程也会走这个分支，抛出去会把人从密码框弹到 404 页。
		notFound.value = res?.code === 404
		message.value = (res && res.msg) || '加载失败'
	}
}

async function unlock() {
	if (!password.value) {
		return
	}
	unlocking.value = true
	try {
		const res: any = await call('/checkBlogPassword', {
			method: 'POST',
			body: {blogId: blogId.value, password: password.value}
		})
		if (res.code === 200) {
			pwdToken.value = res.data
			password.value = ''
			await fetchBlog()
		} else {
			alert(res.msg || '密码错误')
		}
	} finally {
		unlocking.value = false
	}
}

await fetchBlog()

/*
 * 文章不存在 → 返回真正的 404。
 *
 * 之前这里渲染的是一个 200 的「加载失败」页面，后果是：所有拼错的、
 * 已删除的文章地址都会被搜索引擎当正常页收录，白耗抓取预算、
 * 稀释整站质量。注意必须在首次加载后判断，不能塞进 fetchBlog 里抛，
 * 那个函数解锁时也会调用。
 */
if (notFound.value) {
	throw createError({statusCode: 404, statusMessage: 'Article not found'})
}

/**
 * 收藏/点赞状态只对已登录用户查询。
 * like/status 允许匿名调用（user 为 null 时 liked=false），但仍以登录态为准。
 */
if (blog.value && isLogin.value) {
	const [f, l]: any[] = await Promise.all([
		call(`/favorite/status?targetType=ARTICLE&targetId=${blogId.value}`).catch(() => null),
		call(`/like/status?targetType=ARTICLE&targetId=${blogId.value}`).catch(() => null)
	])
	favorited.value = !!(f && f.data && f.data.favorited)
	liked.value = !!(l && l.data && l.data.liked)
	likeCount.value = (l && l.data && l.data.count) || 0
}

function requireLogin() {
	navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
	return false
}

async function toggleFavorite() {
	if (!isLogin.value) {
		requireLogin()
		return
	}
	const res: any = await call(`/favorite?targetType=ARTICLE&targetId=${blogId.value}`, {
		method: favorited.value ? 'DELETE' : 'POST'
	})
	if (res && res.code === 200) {
		favorited.value = !favorited.value
	}
}

async function toggleLike() {
	if (!isLogin.value) {
		requireLogin()
		return
	}
	const res: any = await call(`/like?targetType=ARTICLE&targetId=${blogId.value}`, {
		method: liked.value ? 'DELETE' : 'POST'
	})
	if (res && res.code === 200) {
		liked.value = !liked.value
		likeCount.value = Math.max(0, likeCount.value + (liked.value ? 1 : -1))
	}
}

async function loadTopics() {
	const res: any = await call(`/blog/${blogId.value}/topics`).catch(() => null)
	topics.value = (res && res.code === 200 && res.data) ? res.data : []
}

async function loadRelated() {
	const res: any = await call(`/blog/${blogId.value}/products`).catch(() => null)
	relatedProducts.value = (res && res.code === 200 && res.data) ? res.data : []
}

/**
 * 侧栏「相关阅读」：优先同分类，没有分类就退回最新文章。
 * 一律排除当前这篇，否则会出现「相关阅读：本文」。
 */
async function loadRelatedPosts() {
	const current = blog.value || {}
	const url = current.category?.name
		? `/category?categoryName=${encodeURIComponent(current.category.name)}&pageNum=1`
		: '/blogs?pageNum=1'
	const res: any = await call(url).catch(() => null)
	const list = (res && res.data && res.data.list) || []
	relatedPosts.value = list.filter((item: any) => item.id !== current.id).slice(0, 5)
}

/** 文章所属的开源项目档案：栏目文章才有，普通文章返回 null */
async function loadOssProject() {
	const res: any = await call(`/open-source/blog/${blogId.value}/project`).catch(() => null)
	ossProject.value = (res && res.code === 200) ? (res.data || null) : null
}

function typeText(type: string) {
	const key = `post.types.${type}`
	return te(key) ? t(key) : type
}

function priceLabel(product: any) {
	if (product.priceMode === 'CUSTOM') {
		return t('common.quote')
	}
	const value = Number(product.price || 0).toFixed(2).replace(/\.00$/, '')
	const amount = `¥${value}`
	return product.priceMode === 'STARTING' ? t('common.from', {price: amount}) : amount
}

if (blog.value) {
	/**
	 * 文章页的分享与搜索信息要用文章自己的摘要，不能沿用全站那句 description。
	 *
	 * 摘要优先取 description（后台填的简介），没有就从正文首段截一段。
	 * og:image 用封面图，没有的话微信抓到的就是空白卡片。
	 */
	const desc = (blog.value.description || plainText(blog.value.content) || '').slice(0, 150)
	const pageUrl = siteUrl + route.path
	const cover = blog.value.firstPicture ? absoluteUrl(blog.value.firstPicture) : `${siteUrl}/og-default.png`
	const tags = (blog.value.tags || []).map((x: any) => x.name).filter(Boolean)
	useHead({
		title: blog.value.title,
		meta: [
			{name: 'description', content: desc},
			{property: 'og:type', content: 'article'},
			{property: 'og:title', content: blog.value.title},
			{property: 'og:description', content: desc},
			{property: 'og:url', content: pageUrl},
			{property: 'og:image', content: cover},
			{name: 'twitter:card', content: 'summary_large_image'},
			{name: 'twitter:image', content: cover},
			...(blog.value.createTime
				? [{property: 'article:published_time', content: blog.value.createTime}]
				: []),
			...(blog.value.updateTime
				? [{property: 'article:modified_time', content: blog.value.updateTime}]
				: []),
			...(tags.length ? [{property: 'article:tag', content: tags.join(',')}] : [])
		],
		/**
		 * 文章级结构化数据：BlogPosting + 面包屑。
		 *
		 * 这是被 AI 引擎引用的前提——GPTBot / PerplexityBot 解析页面时，
		 * JSON-LD 里的作者、发布时间、正文摘要是最容易被抽取成「事实」的部分。
		 * 只靠 meta 标签它们拿不到「谁在什么时候写了什么」这层关系。
		 */
		script: [{
			type: 'application/ld+json',
			innerHTML: JSON.stringify({
				'@context': 'https://schema.org',
				'@graph': [
					{
						'@type': 'BlogPosting',
						'@id': `${pageUrl}#article`,
						headline: blog.value.title,
						description: desc,
						inLanguage: locale.value === 'en' ? 'en' : 'zh-CN',
						mainEntityOfPage: {'@type': 'WebPage', '@id': pageUrl},
						datePublished: blog.value.createTime || undefined,
						dateModified: blog.value.updateTime || blog.value.createTime || undefined,
						image: [cover],
						wordCount: blog.value.words || undefined,
						timeRequired: blog.value.readTime ? `PT${blog.value.readTime}M` : undefined,
						articleSection: blog.value.category?.name || undefined,
						keywords: tags.length ? tags.join(', ') : undefined,
						author: {
							'@type': 'Person',
							name: brand.value.brandName,
							url: `${siteUrl}/about`
						},
						publisher: {'@id': `${siteUrl}/#organization`}
					},
					{
						'@type': 'BreadcrumbList',
						itemListElement: [
							{'@type': 'ListItem', position: 1, name: t('post.crumbHome'), item: `${siteUrl}/`},
							{'@type': 'ListItem', position: 2, name: t('archives.listTitle'), item: `${siteUrl}/archives`},
							...(blog.value.category?.name
								? [{
									'@type': 'ListItem', position: 3, name: blog.value.category.name,
									item: `${siteUrl}/category/${blog.value.category.name}`
								}]
								: []),
							{
								'@type': 'ListItem',
								position: blog.value.category?.name ? 4 : 3,
								name: blog.value.title,
								item: pageUrl
							}
						]
					}
				]
			})
		}]
	})
} else {
	useHead({
		title: t('post.lockedTitle'),
		// 锁定页没有正文，收录进去只有坏处：既排不上名，又拉低整站内容质量
		meta: [{name: 'robots', content: 'noindex, follow'}]
	})
}
</script>

<style scoped>
/* ============================================================
   标题区
   ============================================================ */
.crumb {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	font-size: 12.5px;
	color: var(--muted);
	margin: 26px 0 18px;
}

.crumb a:hover {
	color: var(--teal);
}

.crumb-sep {
	color: var(--faint);
}

.article-tags {
	margin-bottom: 14px;
}

.article-title {
	font-size: 34px;
	font-weight: 800;
	line-height: 1.35;
	letter-spacing: -.8px;
	margin-bottom: 14px;
}

.article-sub {
	font-size: 15.5px;
	line-height: 1.75;
	color: var(--muted);
	margin-bottom: 18px;
	max-width: 760px;
}

.article-meta {
	padding-bottom: 18px;
	border-bottom: 1px solid var(--line);
}

.article-cover {
	margin: 26px 0 0;
	border-radius: var(--radius);
	overflow: hidden;
	border: 1px solid var(--line);
}

/* ============================================================
   正文
   ============================================================ */
.article {
	margin-top: 30px;
}

.block-title {
	font-size: 15px;
	font-weight: 700;
	margin-bottom: 12px;
}

.topic-tip {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	margin-top: 30px;
	padding: 14px 16px;
	border: 1px solid var(--line);
	border-radius: 10px;
	background: var(--teal-050);
}

/* ============================================================
   关联产品 / 操作
   ============================================================ */
.related {
	margin-top: 34px;
	padding-top: 20px;
	border-top: 1px solid var(--line);
}

.related-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
	gap: 12px;
}

.related-item {
	display: flex;
	flex-direction: column;
	gap: 6px;
	padding: 14px 16px;
	border: 1px solid var(--line);
	border-radius: 10px;
	transition: transform .18s, border-color .18s;
}

.related-item:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.ri-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.ri-head .tag {
	padding: 3px 7px;
}

.owned {
	font-size: 12px;
	color: var(--teal);
}

.related-item strong {
	font-size: 14.5px;
	line-height: 1.5;
}

.ri-summary {
	margin: 0;
	font-size: 12px;
	line-height: 1.6;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.amount {
	font-size: 14px;
	font-weight: 700;
	color: var(--teal);
}

.oss-link {
	margin-top: 26px;
	padding: 16px 18px;
	border: 1px solid var(--line);
	border-radius: 10px;
	background: var(--teal-050);
}

.oss-label {
	display: block;
	margin-bottom: 10px;
}

.oss-card {
	display: flex;
	align-items: baseline;
	flex-wrap: wrap;
	gap: 10px;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: 9px;
	padding: 12px 14px;
	transition: border-color .15s, transform .15s;
}

.oss-card:hover {
	border-color: var(--line-2);
	transform: translateY(-1px);
}

.oss-name {
	font-size: 14.5px;
	font-weight: 700;
}

.oss-card:hover .oss-name {
	color: var(--teal);
}

.oss-summary {
	flex: 1;
	min-width: 180px;
	font-size: 12.5px;
	color: var(--muted);
	line-height: 1.6;
	display: -webkit-box;
	-webkit-line-clamp: 1;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.oss-verdict {
	font-size: 11.5px;
	font-weight: 600;
	color: var(--teal-700);
	background: var(--teal-100);
	border-radius: 5px;
	padding: 3px 8px;
	white-space: nowrap;
}

.actions {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 10px;
	margin-top: 30px;
	padding-top: 20px;
	border-top: 1px solid var(--line);
}

.act {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	height: 36px;
	padding: 0 18px;
	border: 1px solid var(--line);
	border-radius: 999px;
	background: #fff;
	color: var(--muted);
	font-size: 13.5px;
	cursor: pointer;
	transition: border-color .15s, color .15s, background .15s;
}

.act:hover {
	border-color: var(--teal);
	color: var(--teal);
}

.act.active {
	border-color: var(--teal);
	background: var(--teal-050);
	color: var(--teal);
	font-weight: 600;
}

.act-link {
	margin-left: auto;
	color: var(--ink-2);
}

.cnt {
	font-size: 12px;
	opacity: .8;
}

/* ============================================================
   密码文章
   ============================================================ */
.locked-wrap {
	max-width: 520px;
	margin: 60px auto;
}

.locked {
	text-align: center;
	padding: 40px 32px;
}

.locked .page-desc {
	margin: 0 auto 22px;
}

.lock-form {
	display: flex;
	gap: 10px;
	margin-bottom: 14px;
}

.lock-form input {
	flex: 1;
	min-width: 0;
	height: 42px;
	padding: 0 14px;
	border: 1px solid var(--line);
	border-radius: var(--radius-sm);
	font-size: 14px;
	outline: none;
}

.lock-form input:focus {
	border-color: var(--teal);
}

@media (max-width: 900px) {
	.article-title {
		font-size: 27px;
	}

	.article-cover {
		margin-top: 20px;
	}
}
</style>
