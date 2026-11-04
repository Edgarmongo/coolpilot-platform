<template>
	<div>
		<h1 class="page-title">标签：{{ name }}</h1>
		<p class="page-desc">共 {{ total }} 篇</p>
		<PostList :posts="posts" :page-num="pageNum" :total-page="totalPage" empty-text="该标签下暂无文章"
		          @change="go"/>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const name = computed(() => decodeURIComponent(String(route.params.name)))
const pageNum = ref(1)

const {data} = await useAsyncData(`tag-${name.value}`, () =>
	call(`/tag?tagName=${encodeURIComponent(name.value)}&pageNum=1`))

const posts = computed(() => (data.value as any)?.data?.list || [])
const total = computed(() => (data.value as any)?.data?.total || posts.value.length)
const totalPage = computed(() => (data.value as any)?.data?.totalPage || 1)

async function go(page: number) {
	pageNum.value = page
	data.value = await call(`/tag?tagName=${encodeURIComponent(name.value)}&pageNum=${page}`)
	window.scrollTo({top: 0, behavior: 'smooth'})
}

useHead({title: `标签 ${name.value} - CoolSkyAI`})
</script>
