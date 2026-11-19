<template>
	<div>
		<nav class="crumb">
			<NuxtLink :to="localePath('/open-source')">{{ t('oss.title') }}</NuxtLink>
			<span class="crumb-sep">/</span>
			<span>{{ label }}</span>
		</nav>

		<div class="page-head">
			<h1 class="page-title">{{ label }}</h1>
			<p class="page-desc">{{ t('oss.projectCount', {n: total}) }}</p>
		</div>

		<div class="chips filters">
			<NuxtLink :to="localePath('/open-source/projects')" class="chip">{{ t('oss.allProjects') }}</NuxtLink>
			<NuxtLink v-for="item in categories" :key="item.code" :to="localePath(`/open-source/category/${item.code}`)"
			          class="chip" :class="{on: item.code === code}">
				{{ t(`oss.cats.${item.code}`) }}
				<span class="chip-n">{{ item.count }}</span>
			</NuxtLink>
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

const code = computed(() => String(route.params.code || ''))
const label = computed(() => t(`oss.cats.${code.value}`))

const {data} = await useAsyncData(`oss-category-${code.value}`,
	() => call(`/open-source/projects?category=${encodeURIComponent(code.value)}&pageSize=48`),
	{watch: [code]})
const {data: categoryRes} = await useAsyncData('oss-categories-cat', () => call('/open-source/categories'))

const projects = computed<any[]>(() => (data.value as any)?.data?.list || [])
const total = computed(() => (data.value as any)?.data?.total || 0)
const categories = computed<any[]>(() => (categoryRes.value as any)?.data || [])

useHead({title: label.value})
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

.filters {
	padding: 20px 0 0;
}

.chip-n {
	margin-left: 6px;
	font-size: 11px;
	color: var(--faint);
}

.chip.on .chip-n {
	color: rgba(255, 255, 255, .75);
}

.section {
	padding-top: 22px;
}

@media (max-width: 640px) {
	.chips {
		flex-wrap: nowrap;
		overflow-x: auto;
		scrollbar-width: none;
	}

	.chips::-webkit-scrollbar {
		display: none;
	}
}
</style>
