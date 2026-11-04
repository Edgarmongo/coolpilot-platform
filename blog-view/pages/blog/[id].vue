<template>
	<div class="card">
		<template v-if="blog">
			<h1 class="page-title">{{ blog.title }}</h1>
			<div class="post-meta meta">
				<span>{{ formatDate(blog.createTime, 'YYYY-MM-DD') }}</span>
				<span v-if="blog.category">· {{ blog.category.name }}</span>
				<span>· {{ blog.views || 0 }} 次阅读</span>
			</div>
			<div class="article" v-html="blog.content"></div>
		</template>
		<div v-else class="empty">文章不存在或已被删除</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()

const {data} = await useAsyncData(`blog-${route.params.id}`, () => call(`/blog?id=${route.params.id}`))
const blog = computed(() => (data.value as any)?.data || null)

if (blog.value) {
	useHead({title: `${blog.value.title} - CoolPilot`})
}
</script>

<style scoped>
.meta {
	margin: 0 0 18px;
	padding-bottom: 14px;
	border-bottom: 1px solid var(--line);
}
</style>
