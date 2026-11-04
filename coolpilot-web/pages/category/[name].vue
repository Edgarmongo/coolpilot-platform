<template>
	<div>
		<h1 class="page-title">分类：{{ name }}</h1>
		<p class="page-desc">共 {{ total }} 篇</p>
		<PostList :posts="posts" :page-num="pageNum" :total-page="totalPage" empty-text="该分类下暂无文章"
		          @change="go"/>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const name = computed(() => decodeURIComponent(String(route.params.name)))
const pageNum = ref(1)

const {data} = await useAsyncData(`category-${name.value}`, () =>
	call(`/category?categoryName=${encodeURIComponent(name.value)}&pageNum=1`))

const posts = computed(() => (data.value as any)?.data?.list || [])
const total = computed(() => (data.value as any)?.data?.total || posts.value.length)
const totalPage = computed(() => (data.value as any)?.data?.totalPage || 1)

async function go(page: number) {
	pageNum.value = page
	const res: any = await call(`/category?categoryName=${encodeURIComponent(name.value)}&pageNum=${page}`)
	data.value = res
	window.scrollTo({top: 0, behavior: 'smooth'})
}

useHead({title: `分类 ${name.value} - CoolSkyAI`})
</script>
