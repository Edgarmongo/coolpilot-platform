<template>
	<div>
		<h1 class="page-title">搜索</h1>
		<p class="page-desc">按标题与摘要查找文章</p>

		<div class="search-box card">
			<input v-model="keyword" placeholder="输入关键词后回车" @keyup.enter="doSearch">
			<button class="btn" @click="doSearch">搜索</button>
		</div>

		<div v-if="searched" class="result">
			<p class="muted">「{{ current }}」找到 {{ posts.length }} 篇</p>
			<PostList :posts="posts" :page-num="1" :total-page="1" empty-text="没有匹配的文章"/>
		</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call} = useApi()

const keyword = ref(String(route.query.q || ''))
const current = ref(String(route.query.q || ''))
const posts = ref<any[]>([])
const searched = ref(false)

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
	const res: any = await call(`/searchBlog?query=${encodeURIComponent(current.value)}`).catch(() => null)
	posts.value = (res && res.data) || []
}

if (current.value) {
	await run()
}

useHead({title: '搜索 - CoolPilot'})
</script>

<style scoped>
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
</style>
