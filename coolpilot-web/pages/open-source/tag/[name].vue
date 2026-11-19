<template>
	<div>
		<nav class="crumb">
			<NuxtLink :to="localePath('/open-source')">{{ t('oss.title') }}</NuxtLink>
			<span class="crumb-sep">/</span>
			<span>#{{ name }}</span>
		</nav>

		<div class="page-head">
			<h1 class="page-title">#{{ name }}</h1>
			<p class="page-desc">{{ t('oss.projectCount', {n: total}) }}</p>
		</div>

		<div v-if="!projects.length" class="empty">{{ t('oss.emptyList') }}</div>
		<div v-else class="grid3 section">
			<OssProjectCard v-for="item in projects" :key="item.id" :project="item"/>
		</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const name = computed(() => decodeURIComponent(String(route.params.name || '')))

const {data} = await useAsyncData(`oss-tag-${name.value}`,
	() => call(`/open-source/projects?tag=${encodeURIComponent(name.value)}&pageSize=48`),
	{watch: [name]})

const projects = computed<any[]>(() => (data.value as any)?.data?.list || [])
const total = computed(() => (data.value as any)?.data?.total || 0)

useHead({title: `#${name.value}`})
</script>

<style scoped>
.crumb {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 12.5px;
	color: var(--muted);
	margin: 22px 0 4px;
}

.crumb a:hover {
	color: var(--teal);
}

.crumb-sep {
	color: var(--faint);
}

.section {
	padding-top: 22px;
}
</style>
