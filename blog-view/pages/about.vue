<template>
	<div>
		<h1 class="page-title">{{ about.title || '关于' }}</h1>
		<div class="card article" v-html="about.content || '<p class=\'muted\'>暂无内容</p>'"></div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()

const {data} = await useAsyncData('about', () => call('/about'))
const about = computed(() => (data.value as any)?.data || {})

useHead({title: '关于 - CoolPilot'})
</script>

<style scoped>
.article {
	min-height: 220px;
}
</style>
