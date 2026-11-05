<template>
	<div>
		<h1 class="page-title">{{ t('topics.title') }}</h1>
		<p class="page-desc">{{ t('topics.desc') }}</p>

		<div v-if="!topics.length" class="card muted">{{ t('topics.empty') }}</div>

		<div v-else class="topic-grid">
			<NuxtLink v-for="item in topics" :key="item.id" :to="localePath(`/topic/${item.slug}`)" class="topic-card">
				<div v-if="item.cover" class="cover" :style="{backgroundImage: `url(${item.cover})`}"></div>
				<div class="body">
					<h2>{{ item.title }}</h2>
					<p class="desc">{{ item.description || t('topics.noDesc') }}</p>
					<span class="meta">{{ t('topics.meta', {n: item.articleCount || 0, date: formatDate(item.updateTime)}) }}</span>
				</div>
			</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const {data} = await useAsyncData('topic-list', () => call('/topics'))
const topics = computed<any[]>(() => (data.value as any)?.data || [])

useHead({
	title: t('topics.title')
})
</script>

<style scoped>
.topic-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
	gap: 14px;
}

.topic-card {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
	border-radius: 12px;
	overflow: hidden;
	transition: transform .15s, box-shadow .15s;
}

.topic-card:hover {
	transform: translateY(-2px);
	box-shadow: 0 12px 28px rgba(15, 118, 110, .1);
}

.cover {
	height: 108px;
	background-size: cover;
	background-position: center;
}

.body {
	padding: 14px 16px 16px;
}

.body h2 {
	margin: 0 0 6px;
	font-size: 16px;
	font-weight: 650;
}

.desc {
	margin: 0 0 10px;
	font-size: 13px;
	line-height: 1.7;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.meta {
	font-size: 12px;
	color: var(--muted);
}
</style>
