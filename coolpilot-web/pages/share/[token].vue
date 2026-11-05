<template>
	<div class="share-page">
		<div class="ribbon">
			<span>来自 <NuxtLink to="/" class="brand">CoolSkyAI</NuxtLink> 的问答分享</span>
			<NuxtLink to="/ask" class="try">我也去问一个 →</NuxtLink>
		</div>

		<div v-if="!answer" class="empty card">
			<p>{{ errorMessage }}</p>
			<p class="muted">{{ errorHint }}</p>
			<NuxtLink to="/ask" class="btn">去站内问答</NuxtLink>
		</div>

		<template v-else>
			<div v-if="question" class="question">
				<span class="label">问</span>
				<p>{{ question }}</p>
			</div>

			<article class="answer card">
				<div class="label-row">
					<span class="label">答</span>
					<span class="muted small">{{ formatDate(createTime) }}<template v-if="model"> · {{ model }}</template></span>
				</div>
				<div class="text" @click="onCitationClick">
					<span v-html="renderAnswer(answer, citations, 'share')"/>
				</div>

				<div v-if="citations.length" class="citations">
					<div class="cite-title">来源</div>
					<a v-for="cite in citations" :key="cite.index"
					   :id="`cite-share-${cite.index}`"
					   :class="['cite', {active: highlighted === cite.index}]"
					   :href="cite.url" target="_blank" rel="noopener noreferrer">
						<span class="cite-idx">[{{ cite.index }}]</span>
						<span class="cite-name">{{ cite.title }}</span>
						<span class="tag">{{ sourceText(cite.sourceType) }}</span>
					</a>
				</div>

				<div class="share-bar">
					<button :class="['like', {on: liked}]" :disabled="liking" @click="toggleLike">
						<span class="heart">{{ liked ? '♥' : '♡' }}</span>{{ likes }}
					</button>
					<span class="stat">阅读 {{ views }}</span>
					<span class="stat">{{ expiryText }}</span>
				</div>
			</article>

			<section id="discuss" ref="talkRef" class="talk card">
				<div class="talk-head">
					<span class="talk-title">讨论</span>
					<div class="talk-tools">
						<span class="muted small">{{ totalCount ? `${totalCount} 条留言` : '还没有留言' }}</span>
						<span class="sorter">
							<!--
								这些链接只差查询串，NuxtLink 会把它们全判成「当前页」，
								于是每个链接都带上 router-link-active 和 aria-current。
								改用原生 a：href 给爬虫，点击交给我们自己跳。
							-->
							<a :class="['sort', {on: sort === 'newest'}]" :href="sortHref('newest')"
							   @click.prevent="go(sortTo('newest'), $event)">最新</a>
							<a :class="['sort', {on: sort === 'oldest'}]" :href="sortHref('oldest')"
							   @click.prevent="go(sortTo('oldest'), $event)">最早</a>
						</span>
					</div>
				</div>

				<ul v-if="topComments.length" class="talk-list">
					<li v-for="item in topComments" :key="item.id" class="talk-item">
						<img class="avatar" :src="item.avatar" alt=""/>
						<div class="body">
							<div class="meta">
								<span class="name">{{ item.nickname }}</span>
								<span v-if="item.admin" class="badge">作者</span>
								<span class="muted time">{{ formatDate(item.createTime) }}</span>
							</div>
							<p class="content">{{ item.content }}</p>
							<div class="ops">
								<button v-if="isLogin" class="link" @click="startReply(item)">回复</button>
								<button v-if="canDelete(item)" class="link" @click="removeComment(item)">删除</button>
							</div>

							<ul v-if="repliesOf(item.id).length" class="reply-list">
								<li v-for="reply in repliesOf(item.id)" :key="reply.id" class="reply-item">
									<div class="meta">
										<span class="name">{{ reply.nickname }}</span>
										<span v-if="reply.admin" class="badge">作者</span>
										<span class="muted time">{{ formatDate(reply.createTime) }}</span>
									</div>
									<p class="content">{{ reply.content }}</p>
									<div class="ops">
										<button v-if="isLogin" class="link" @click="startReply(item, reply)">回复</button>
										<button v-if="canDelete(reply)" class="link" @click="removeComment(reply)">删除</button>
									</div>
								</li>
							</ul>
						</div>
					</li>
				</ul>

				<p v-else-if="!comments.length" class="muted empty-talk">
					还没有人说话。看完有疑问或补充，留言让作者知道。
				</p>

				<div v-if="totalPages > 1" class="pager">
					<a v-if="hasPrev" class="page-btn" :href="pagerHref(currentPage - 1)" rel="prev"
					   @click.prevent="go(pagerTo(currentPage - 1), $event)">上一页</a>
					<span v-else class="page-btn disabled">上一页</span>
					<span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
					<a v-if="hasNext" class="page-btn" :href="pagerHref(currentPage + 1)" rel="next"
					   @click.prevent="go(pagerTo(currentPage + 1), $event)">下一页</a>
					<span v-else class="page-btn disabled">下一页</span>
				</div>

				<div v-if="isLogin" class="talk-form">
					<div v-if="replyTo" class="replying">
						<span>回复 {{ replyTo.nickname }}</span>
						<button class="link" @click="cancelReply">取消</button>
					</div>
					<textarea v-model="draft" rows="3" maxlength="500"
					          :placeholder="replyTo ? `回复 ${replyTo.nickname}…` : '说说你的看法，或追问一句'"
					          @keydown.ctrl.enter="submitComment"/>
					<p v-if="notice" class="notice">{{ notice }}</p>
					<div class="form-actions">
						<span class="muted counter">{{ draft.length }}/500</span>
						<button class="btn" :disabled="sending || !draft.trim()" @click="submitComment">
							{{ sending ? '发送中…' : '发送' }}
						</button>
					</div>
				</div>
				<div v-else class="login-tip">
					<NuxtLink :to="`/login?redirect=${encodeURIComponent(route.fullPath)}`" class="btn">登录后参与讨论</NuxtLink>
					<span class="muted small">留言需要登录，避免匿名刷屏</span>
				</div>
			</section>

			<p class="muted foot">
				回答由模型基于站内资料生成，可能有偏差；重要信息请点开来源核对。
			</p>
		</template>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const token = computed(() => String(route.params.token))
const {call, isLogin, user} = useApi()

/*
 * 分享可能已经过期（410）或作者撤销了（400），两者对访问者的意义不同：
 * 前者还能请作者重新分享，后者基本没救，所以把后端消息透出来。
 */
const {data} = await useAsyncData(`share-${token.value}`, async () => {
	try {
		return await $fetch<any>(`/api/share/answer/${token.value}`)
	} catch (error: any) {
		return error?.data || null
	}
})

const payload = computed<any>(() => {
	const res = data.value
	return res && res.code === 200 ? res.data : null
})
const expired = computed(() => data.value?.code === 410)
const question = computed(() => payload.value?.question || '')
const answer = computed(() => payload.value?.answer || '')
const citations = computed<any[]>(() => payload.value?.citations || [])
const model = computed(() => payload.value?.model || '')
const createTime = computed(() => payload.value?.createTime || '')
const expiresAt = computed(() => payload.value?.expiresAt || null)

const errorMessage = computed(() => {
	if (payload.value) {
		return ''
	}
	return expired.value ? '这个分享链接已经过期' : '分享内容不存在或已失效'
})
const errorHint = computed(() => expired.value
	? '作者设置了有效期，到期后链接自动关闭；可以让作者重新分享一次。'
	: '作者可能取消了分享，或这段对话已被删除。')

const highlighted = ref<number | null>(null)
const views = ref(Number(payload.value?.views || 0))
const likes = ref(Number(payload.value?.likes || 0))
const liked = ref(false)
const liking = ref(false)

const expiryText = computed(() => {
	if (!expiresAt.value) {
		return '长期有效'
	}
	const date = new Date(expiresAt.value)
	const days = Math.ceil((date.getTime() - Date.now()) / 86400000)
	return days > 0 ? `${days} 天后过期` : '即将过期'
})

/*
 * 阅读与点赞都由浏览器上报：SSR 请求的源 IP 是 Nuxt 服务器，
 * 所有人都一样，按它去重会把整站访问压成一条。
 */
onMounted(async () => {
	try {
		const res: any = await $fetch(`/api/share/answer/${token.value}/view`, {method: 'POST'})
		if (res && res.code === 200 && res.data) {
			views.value = Number(res.data.views || 0)
			likes.value = Number(res.data.likes || 0)
			liked.value = !!res.data.liked
		}
	} catch (e) {
		// 统计失败不影响阅读
	}
})

async function toggleLike() {
	if (liking.value) {
		return
	}
	liking.value = true
	const next = !liked.value
	try {
		const res: any = await $fetch(`/api/share/answer/${token.value}/like`, {
			method: next ? 'POST' : 'DELETE'
		})
		if (res && res.code === 200 && res.data) {
			liked.value = !!res.data.liked
			likes.value = Number(res.data.likes || 0)
		}
	} catch (e) {
		// 失败就保持原样，不弹窗打断阅读
	} finally {
		liking.value = false
	}
}

/* ---------------- 讨论 ---------------- */

/** 分页只切顶楼：一页顶楼 + 它们的全部回复 */
const PAGE_SIZE = 20

/*
 * 页码和排序都放在 URL 上（?page=2&sort=oldest）：
 * 可以直接把某一页发给人，也让爬虫能沿着链接把留言逐页抓完 ——
 * 只靠「加载更多」的话，第 2 页往后对搜索引擎是不存在的。
 */
const pageNum = computed(() => {
	const raw = Number(route.query.page)
	return Number.isFinite(raw) && raw >= 1 ? Math.floor(raw) : 1
})
const sort = computed(() => (route.query.sort === 'oldest' ? 'oldest' : 'newest'))

const {data: commentPage, refresh: refreshComments} = await useAsyncData(
	() => `share-comments-${token.value}-${pageNum.value}-${sort.value}`,
	async () => {
		try {
			return await call<any>(`/share/answer/${token.value}/comments?pageNum=${pageNum.value}&pageSize=${PAGE_SIZE}&sort=${sort.value}`)
		} catch (error) {
			return null
		}
	},
	// 页码/排序变化时重新取，不整页刷新
	{watch: [pageNum, sort, token]}
)

const pageMeta = computed<any>(() => (commentPage.value && commentPage.value.code === 200 ? commentPage.value.data : null))
const comments = computed<any[]>(() => pageMeta.value?.comments || [])
const totalTop = computed(() => Number(pageMeta.value?.totalTop || 0))
const totalReplies = computed(() => Number(pageMeta.value?.totalReplies || 0))
const totalPages = computed(() => Number(pageMeta.value?.totalPages || 0))
/** 实际渲染的页码：地址写 ?page=99 时后端会夹到最后一页，这里以返回值为准 */
const currentPage = computed(() => Number(pageMeta.value?.pageNum || pageNum.value))
const hasPrev = computed(() => currentPage.value > 1)
const hasNext = computed(() => currentPage.value < totalPages.value)

/** 展示用总数：顶楼 + 回复 */
const totalCount = computed(() => totalTop.value + totalReplies.value)

const topComments = computed(() => comments.value.filter(item => !item.parentId || item.parentId === -1))

function repliesOf(id: number) {
	return comments.value.filter(item => item.parentId === id)
}

// 页码越界（比如 ?page=99，或最后一页的留言被删光）：退到最后一页
watch([pageNum, currentPage], ([wanted, actual]) => {
	if (actual >= 1 && wanted !== actual) {
		navigateTo(pagerTo(actual), {replace: true})
	}
})

/** 翻页链接：带 #discuss，落地后直接停在讨论区而不是页首 */
function pagerTo(target: number) {
	return {
		path: route.path,
		hash: '#discuss',
		query: {...route.query, page: target <= 1 ? undefined : target}
	}
}

/** 排序是同一份内容的另一个视图：切完回到第一页，免得停在一个不存在的页码上 */
function sortTo(next: string) {
	return {
		path: route.path,
		hash: '#discuss',
		query: {...route.query, page: undefined, sort: next === 'newest' ? undefined : next}
	}
}

/** 页面里的 href 由我们自己拼：原生 a 需要真实地址，爬虫才跟得下去 */
function hrefOf(loc: { query: Record<string, any> }) {
	const parts = Object.entries(loc.query)
		.filter(([, value]) => value !== undefined && value !== null && value !== '')
		.map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
	return `${route.path}${parts.length ? `?${parts.join('&')}` : ''}#discuss`
}

function pagerHref(target: number) {
	return hrefOf(pagerTo(target))
}

function sortHref(next: string) {
	return hrefOf(sortTo(next))
}

/**
 * 自己跳（原生 a 默认整页刷新）：
 * 留出 ctrl/cmd/中键在新标签打开的常规行为
 */
function go(loc: any, event: MouseEvent) {
	if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) {
		return
	}
	navigateTo(loc)
}

const draft = ref('')
const sending = ref(false)
const replyTo = ref<any | null>(null)
/** 失败原因就地提示，比弹窗打断阅读友好 */
const notice = ref('')

function startReply(top: any, reply?: any) {
	// 回复「回复」时仍然挂在顶楼下，和后端的两层规则保持一致
	replyTo.value = reply || top
}

function cancelReply() {
	replyTo.value = null
}

/** 只能删自己的；平台所有者删别人的由后端放行，前端不额外显示入口 */
function canDelete(item: any) {
	return isLogin.value && user.value && Number(user.value.id) === Number(item.userId)
}

async function submitComment() {
	const text = draft.value.trim()
	if (!text || sending.value) {
		return
	}
	sending.value = true
	try {
		const res: any = await call(`/share/answer/${token.value}/comments`, {
			method: 'POST',
			body: {content: text, parentId: replyTo.value?.id ?? -1}
		})
		if (res && res.code === 200) {
			const wasReply = !!replyTo.value
			draft.value = ''
			replyTo.value = null
			notice.value = ''
			if (!wasReply && (pageNum.value !== 1 || sort.value !== 'newest')) {
				// 新留言落在「最新」的第一页，把作者带过去，否则发完看不到自己那条
				await navigateTo(sortTo('newest'))
			} else {
				await refreshComments()
				talkRef.value?.scrollIntoView({behavior: 'smooth', block: 'start'})
			}
		} else {
			notice.value = res?.msg || '留言失败，请稍后再试'
		}
	} catch (e) {
		notice.value = '留言失败，请稍后再试'
	} finally {
		sending.value = false
	}
}

async function removeComment(item: any) {
	if (!confirm('删除这条留言？')) {
		return
	}
	const res: any = await call(`/share/answer/${token.value}/comments/${item.id}`, {method: 'DELETE'})
	if (res && res.code === 200) {
		notice.value = ''
		await refreshComments()
	} else {
		notice.value = res?.msg || '删除失败，请稍后再试'
	}
}

/** 正文里的 [n]：滚到对应来源并高亮，不跳走 */
function onCitationClick(event: MouseEvent) {
	const target = (event.target as HTMLElement | null)?.closest('a[data-cite]')
	if (!target) {
		return
	}
	event.preventDefault()
	const index = Number((target as HTMLElement).dataset.cite)
	const card = document.getElementById(`cite-share-${index}`)
	if (!card) {
		return
	}
	card.scrollIntoView({behavior: 'smooth', block: 'center'})
	highlighted.value = index
	window.setTimeout(() => {
		if (highlighted.value === index) {
			highlighted.value = null
		}
	}, 1600)
}

function sourceText(type: string) {
	return {BLOG: '文章', PRODUCT: '商品', LESSON: '课时'}[type] || '内容'
}

const siteUrl = String(useRuntimeConfig().public.siteUrl || '').replace(/\/$/, '')

/** 分页地址：canonical 与 rel=prev/next 都要绝对 URL，爬虫才认 */
function absUrl(target: number) {
	const query = target > 1 ? `?page=${target}` : ''
	return `${siteUrl}${route.path}${query}`
}

useHead(() => {
	const links: Record<string, string>[] = [{rel: 'canonical', href: absUrl(currentPage.value)}]
	if (hasPrev.value) {
		links.push({rel: 'prev', href: absUrl(currentPage.value - 1)})
	}
	if (hasNext.value) {
		links.push({rel: 'next', href: absUrl(currentPage.value + 1)})
	}
	return {
		title: question.value ? `${question.value} - 问答分享 - CoolSkyAI` : '问答分享 - CoolSkyAI',
		meta: [
			...(answer.value ? [{name: 'description', content: answer.value.slice(0, 120)}] : []),
			{property: 'og:type', content: 'article'},
			{property: 'og:title', content: question.value || 'CoolSkyAI 问答分享'},
			{property: 'og:description', content: answer.value.slice(0, 120) || '来自 CoolSkyAI 的站内问答'},
			{name: 'robots', content: expired.value ? 'noindex' : 'index'}
		],
		link: links
	}
})
</script>

<style scoped>
.share-page {
	max-width: 760px;
	margin: 0 auto;
}

.ribbon {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	flex-wrap: wrap;
	font-size: 12.5px;
	color: var(--muted);
	margin-bottom: 14px;
}

.brand {
	color: var(--teal);
	font-weight: 600;
}

.try {
	color: var(--teal);
}

.try:hover {
	text-decoration: underline;
}

.question {
	display: flex;
	gap: 10px;
	align-items: flex-start;
	margin-bottom: 12px;
}

.question p {
	margin: 0;
	font-size: 16px;
	font-weight: 600;
	line-height: 1.7;
}

.label {
	flex: none;
	display: inline-block;
	min-width: 22px;
	height: 22px;
	line-height: 22px;
	text-align: center;
	border-radius: 6px;
	background: #eef7f5;
	color: var(--teal);
	font-size: 12px;
	font-weight: 600;
	margin-top: 3px;
}

.label-row {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 8px;
}

.small {
	font-size: 12px;
}

.text {
	font-size: 15px;
	line-height: 1.9;
	white-space: pre-wrap;
	word-break: break-word;
}

.citations {
	margin-top: 14px;
	padding-top: 10px;
	border-top: 1px dashed var(--line);
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.cite-title {
	font-size: 12px;
	color: var(--muted);
}

.cite {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 13px;
}

.cite:hover .cite-name {
	color: var(--teal);
}

.cite-idx {
	color: var(--teal);
	font-size: 12px;
}

.cite-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.cite.active {
	background: #fff7ed;
	box-shadow: 0 0 0 2px #fed7aa;
	border-radius: 6px;
}

.share-bar {
	display: flex;
	align-items: center;
	gap: 14px;
	margin-top: 16px;
	padding-top: 12px;
	border-top: 1px dashed var(--line);
	font-size: 12.5px;
	color: var(--muted);
}

.like {
	display: inline-flex;
	align-items: center;
	gap: 5px;
	border: 1px solid var(--line);
	background: #fff;
	color: var(--muted);
	border-radius: 999px;
	padding: 4px 12px;
	font-size: 12.5px;
	cursor: pointer;
	transition: all .15s;
}

.like:hover {
	border-color: #fca5a5;
	color: #e11d48;
}

.like.on {
	border-color: #fca5a5;
	background: #fff1f2;
	color: #e11d48;
}

.like:disabled {
	cursor: default;
	opacity: .7;
}

.heart {
	font-size: 14px;
	line-height: 1;
}

.stat {
	white-space: nowrap;
}

.empty {
	text-align: center;
	color: var(--muted);
}

.empty .btn {
	margin-top: 12px;
}

.foot {
	margin-top: 12px;
	text-align: center;
}

.talk {
	margin-top: 14px;
}

.talk-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 8px;
	margin-bottom: 12px;
}

.talk-title {
	font-size: 14px;
	font-weight: 600;
}

.talk-list {
	list-style: none;
	margin: 0;
	padding: 0;
	display: flex;
	flex-direction: column;
	gap: 14px;
}

.talk-item {
	display: flex;
	gap: 10px;
}

.avatar {
	flex: none;
	width: 32px;
	height: 32px;
	border-radius: 50%;
	object-fit: cover;
	background: #f1f5f4;
}

.body {
	flex: 1;
	min-width: 0;
}

.meta {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 12.5px;
}

.name {
	font-weight: 600;
	color: var(--ink);
}

.badge {
	font-size: 11px;
	line-height: 16px;
	padding: 0 4px;
	border-radius: 4px;
	background: #eef7f5;
	color: var(--teal);
}

.time {
	font-size: 11.5px;
}

.content {
	margin: 3px 0 0;
	font-size: 13.5px;
	line-height: 1.75;
	white-space: pre-wrap;
	word-break: break-word;
}

.ops {
	display: flex;
	gap: 12px;
	margin-top: 2px;
}

.link {
	background: none;
	border: none;
	padding: 0;
	color: var(--muted);
	font-size: 12px;
	cursor: pointer;
}

.link:hover {
	color: var(--teal);
}

.reply-list {
	list-style: none;
	margin: 8px 0 0;
	padding: 8px 0 0 10px;
	border-left: 2px solid var(--line);
	display: flex;
	flex-direction: column;
	gap: 10px;
}

.reply-item .content {
	font-size: 13px;
}

.empty-talk {
	margin: 4px 0 0;
	font-size: 13px;
}

.talk-form {
	margin-top: 14px;
	padding-top: 12px;
	border-top: 1px dashed var(--line);
}

.replying {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-bottom: 6px;
	font-size: 12px;
	color: var(--muted);
}

.talk-form textarea {
	width: 100%;
	box-sizing: border-box;
	border: 1px solid var(--line);
	border-radius: 8px;
	padding: 9px 10px;
	font-size: 13.5px;
	line-height: 1.7;
	font-family: inherit;
	color: var(--ink);
	background: #f9fbfa;
	resize: vertical;
}

.talk-form textarea:focus {
	outline: none;
	border-color: var(--teal);
	background: #fff;
}

.form-actions {
	display: flex;
	align-items: center;
	justify-content: flex-end;
	gap: 12px;
	margin-top: 8px;
}

.counter {
	font-size: 11.5px;
}

.login-tip {
	display: flex;
	align-items: center;
	gap: 10px;
	flex-wrap: wrap;
	margin-top: 14px;
	padding-top: 12px;
	border-top: 1px dashed var(--line);
}

.login-tip .btn,
.talk .btn {
	padding: 6px 16px;
	font-size: 13px;
}

.notice {
	margin: 8px 0 0;
	font-size: 12.5px;
	color: #b91c1c;
}

.talk-tools {
	display: flex;
	align-items: center;
	gap: 12px;
}

.sorter {
	display: inline-flex;
	gap: 2px;
	padding: 2px;
	border: 1px solid var(--line);
	border-radius: 999px;
}

.sort {
	padding: 2px 10px;
	border-radius: 999px;
	font-size: 12px;
	color: var(--muted);
}

.sort:hover {
	color: var(--teal);
}

.sort.on {
	background: #eef7f5;
	color: var(--teal);
	font-weight: 600;
}

.pager {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 16px;
	font-size: 12.5px;
}

.page-btn {
	padding: 4px 12px;
	border: 1px solid var(--line);
	border-radius: 999px;
	color: var(--muted);
}

.page-btn:hover {
	border-color: var(--teal);
	color: var(--teal);
}

.page-btn.disabled {
	opacity: .45;
	cursor: default;
	pointer-events: none;
}

.page-info {
	color: var(--muted);
}
</style>
