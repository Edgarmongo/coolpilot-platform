<template>
	<div class="share-page">
		<div class="ribbon">
			<span>来自 <NuxtLink to="/" class="brand">CoolSkyAI</NuxtLink> 的问答分享</span>
			<NuxtLink to="/ask" class="try">我也去问一个 →</NuxtLink>
		</div>

		<div v-if="!answer" class="empty card">
			<p>分享内容不存在或已失效</p>
			<p class="muted">作者可能取消了分享，或这段对话已被删除。</p>
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

const {data} = await useAsyncData(`share-${token.value}`, () =>
	$fetch<any>(`/api/share/answer/${token.value}`).catch(() => null))

const payload = computed<any>(() => {
	const res = data.value
	return res && res.code === 200 ? res.data : null
})
const question = computed(() => payload.value?.question || '')
const answer = computed(() => payload.value?.answer || '')
const citations = computed<any[]>(() => payload.value?.citations || [])
const model = computed(() => payload.value?.model || '')
const createTime = computed(() => payload.value?.createTime || '')

const highlighted = ref<number | null>(null)

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
	meta: answer.value ? [{name: 'description', content: answer.value.slice(0, 120)}] : []
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
