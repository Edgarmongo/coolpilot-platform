<template>
	<div>
		<div class="page-head">
			<h1 class="page-title">{{ t('topics.title') }}</h1>
			<p class="page-desc">{{ t('topics.desc') }}</p>
		</div>

		<div v-if="!topics.length" class="empty">{{ t('topics.empty') }}</div>

		<div v-else class="grid3 section">
			<NuxtLink v-for="item in topics" :key="item.id" :to="localePath(`/topic/${item.slug}`)"
			          class="card cover-card">
				<img v-if="item.cover" class="cover-img" :src="item.cover" :alt="item.title" loading="lazy">
				<AppCover v-else :seed="`topic-${item.id}-${item.title}`" :height="132" :variant="2"/>
				<div class="card-pad">
					<h2 class="topic-title">{{ item.title }}</h2>
					<p class="topic-desc">{{ item.description || t('topics.noDesc') }}</p>
					<div class="topic-meta">
						<span>{{ t('home.articleCount', {n: item.articleCount || 0}) }}</span>
						<span v-if="item.updateTime">{{ formatDate(item.updateTime, 'YYYY-MM-DD') }}</span>
					</div>
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
.section {
	padding-top: 26px;
}

.cover-card {
	padding: 0;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	transition: transform .18s ease, border-color .18s ease;
}

.cover-card:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.cover-img {
	width: 100%;
	height: 132px;
	object-fit: cover;
	display: block;
}

.topic-title {
	font-size: 16.5px;
	font-weight: 700;
	line-height: 1.45;
	margin-bottom: 8px;
}

.cover-card:hover .topic-title {
	color: var(--teal);
}

.topic-desc {
	flex: 1;
	font-size: 13px;
	line-height: 1.65;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.topic-meta {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	margin-top: 14px;
	padding-top: 11px;
	border-top: 1px solid var(--line);
	font-size: 12px;
	color: var(--muted);
}
</style>
