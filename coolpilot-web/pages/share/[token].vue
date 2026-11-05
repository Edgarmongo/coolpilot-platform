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

			<p class="muted foot">
				回答由模型基于站内资料生成，可能有偏差；重要信息请点开来源核对。
			</p>
		</template>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const token = computed(() => String(route.params.token))

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

useHead(() => ({
	title: question.value ? `${question.value} - 问答分享 - CoolSkyAI` : '问答分享 - CoolSkyAI',
	meta: [
		...(answer.value ? [{name: 'description', content: answer.value.slice(0, 120)}] : []),
		{property: 'og:type', content: 'article'},
		{property: 'og:title', content: question.value || 'CoolSkyAI 问答分享'},
		{property: 'og:description', content: answer.value.slice(0, 120) || '来自 CoolSkyAI 的站内问答'},
		{name: 'robots', content: expired.value ? 'noindex' : 'index'}
	]
}))
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
</style>
