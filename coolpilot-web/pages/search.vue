<template>
	<div>
		<h1 class="page-title">{{ t('search.title') }}</h1>
		<p class="page-desc">{{ t('search.desc') }}</p>

		<div class="search-box card">
			<input v-model="keyword" :placeholder="t('search.placeholder')" @keyup.enter="doSearch">
			<button class="btn" @click="doSearch">{{ t('search.title') }}</button>
		</div>

		<!-- 智能检索：知识库片段召回，带来源与相关度 -->
		<NuxtLink v-if="current" :to="`/ask?q=${encodeURIComponent(current)}`" class="ask-tip">
			{{ t('search.askTip', {q: current}) }}
		</NuxtLink>

		<div v-if="searched" class="result ai">
			<div class="ai-head">
				<h2>{{ t('search.aiTitle') }}</h2>
				<span class="muted small" v-if="aiMeta">
					{{ t('search.aiMeta', {n: aiMeta.candidates, ms: aiMeta.tookMs, model: aiMeta.model}) }}
				</span>
			</div>
			<p v-if="aiLoading" class="muted">{{ t('search.aiLoading') }}</p>
			<p v-else-if="!aiResults.length" class="muted">{{ t('search.aiEmpty') }}</p>
			<div v-for="item in aiResults" :key="item.docId" class="ai-item">
				<NuxtLink :to="item.url" class="ai-title">{{ item.title }}</NuxtLink>
				<span class="tag">{{ sourceText(item.sourceType) }}</span>
				<span class="score">{{ Math.round(item.score * 100) }}%</span>
				<p class="ai-snippet">{{ item.snippet }}</p>
			</div>
		</div>

		<div v-if="searched" class="result">
			<p class="muted">{{ t('search.resultCount', {q: current, n: posts.length}) }}</p>
			<PostList :posts="pageItems" :page-num="page" :total-page="totalPage" :empty-text="t('search.emptyResult')"
			          @change="go"/>
		</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call} = useApi()
const {t, te} = useI18n()

const keyword = ref(String(route.query.q || ''))
const current = ref(String(route.query.q || ''))
const posts = ref<any[]>([])
const aiResults = ref<any[]>([])
const aiMeta = ref<any>(null)
const aiLoading = ref(false)
const searched = ref(false)
const page = ref(1)
const PAGE_SIZE = 10
// 搜索接口一次性返回全部结果，分页在前端切片
const totalPage = computed(() => Math.max(1, Math.ceil(posts.value.length / PAGE_SIZE)))
const pageItems = computed(() => posts.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function doSearch() {
	if (!keyword.value.trim()) {
		return
	}
	await router.replace({query: {q: keyword.value.trim()}})
	await run()
}

async function run() {
	if (!current.value) {
		return
	}
	searched.value = true
	aiLoading.value = true
	const [res, aiRes] = await Promise.all([
		call(`/searchBlog?query=${encodeURIComponent(current.value)}`).catch(() => null),
		call(`/ai/search?q=${encodeURIComponent(current.value)}&limit=6`).catch(() => null)
	])
	aiLoading.value = false
	posts.value = (res && res.data) || []
	aiResults.value = (aiRes as any)?.data?.results || []
	aiMeta.value = (aiRes as any)?.data || null
	page.value = 1
}

if (current.value) {
	await run()
}

function go(target: number) {
	page.value = target
	window.scrollTo({top: 0, behavior: 'smooth'})
}

function sourceText(type: string) {
	const key = `search.sources.${type.toLowerCase()}`
	return te(key) ? t(key) : type
}

useHead({title: t('search.title')})
</script>

<style scoped>
.small {
	font-size: 12px;
}

.ai {
	margin-bottom: 20px;
}

.ai-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 10px;
	flex-wrap: wrap;
}

.ai-head h2 {
	margin: 0 0 6px;
	font-size: 15px;
}

.ai-item {
	border: 1px solid var(--line);
	border-radius: 10px;
	padding: 10px 12px;
	margin-bottom: 8px;
}

.ai-title {
	font-weight: 600;
	font-size: 14px;
	color: var(--teal);
}

.score {
	float: right;
	font-size: 12px;
	color: var(--muted);
}

.ai-snippet {
	margin: 6px 0 0;
	font-size: 13px;
	color: var(--muted);
	line-height: 1.7;
}

.search-box {
	display: flex;
	gap: 10px;
	align-items: center;
}

.search-box input {
	flex: 1;
	padding: 10px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 14px;
	outline-color: var(--teal);
}

.result {
	margin-top: 16px;
}

.ask-tip {
	display: inline-block;
	margin: -8px 0 14px;
	font-size: 13px;
	color: var(--teal);
}

.ask-tip:hover {
	text-decoration: underline;
}
</style>
