<template>
	<div class="projects">
		<h1 class="page-title">{{ t('projectsPage.title') }}</h1>
		<p class="page-desc">{{ t('projectsPage.desc') }}</p>

		<div v-if="!items.length" class="card empty-box">
			<p class="empty">{{ t('projectsPage.empty') }}</p>
		</div>

		<div v-else class="grid">
			<NuxtLink v-for="item in items" :key="item.id" :to="localePath(`/projects/${item.slug}`)" class="card project">
				<div v-if="item.coverUrl" class="cover">
					<img :src="item.coverUrl" :alt="item.name" loading="lazy"/>
				</div>
				<div v-else class="cover cover-empty">
					<span>{{ initial(item.name) }}</span>
				</div>

				<div class="body">
					<div class="title-row">
						<h2 class="name">{{ item.name }}</h2>
						<span class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
					</div>
					<p v-if="item.summary" class="summary">{{ item.summary }}</p>
					<div v-if="techList(item.techStack).length" class="tech">
						<span v-for="tech in techList(item.techStack)" :key="tech" class="tech-tag">{{ tech }}</span>
					</div>
				</div>
			</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

useHead({title: t('projectsPage.title')})

const {data} = await useAsyncData('portfolios', () => call('/portfolios'))

const items = computed(() => (data.value as any)?.data || [])

function techList(tech: string) {
	return (tech || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
}

function initial(name: string) {
	return (name || 'P').trim().slice(0, 1).toUpperCase()
}

function statusText(status: string) {
	const key = `projectsPage.status.${status}`
	return te(key) ? t(key) : status
}

function statusClass(status: string) {
	return {
		'is-active': status === 'ACTIVE',
		'is-wip': status === 'WIP',
		'is-maintaining': status === 'MAINTAINING',
		'is-archived': status === 'ARCHIVED'
	}
}
</script>

<style scoped>
.projects {
	max-width: 1000px;
}

.grid {
	display: grid;
	grid-template-columns: repeat(2, 1fr);
	gap: 18px;
}

.project {
	display: flex;
	flex-direction: column;
	padding: 0;
	overflow: hidden;
	transition: transform .15s, box-shadow .15s, border-color .15s;
}

.project:hover {
	transform: translateY(-2px);
	box-shadow: 0 8px 20px rgba(0, 0, 0, .07);
}

.cover {
	height: 148px;
	background: #f5f8f7;
	overflow: hidden;
}

.cover img {
	display: block;
	width: 100%;
	height: 100%;
	object-fit: cover;
}

.cover-empty {
	display: flex;
	align-items: center;
	justify-content: center;
	background: linear-gradient(135deg, #e7f5ee 0%, #f5f8f7 100%);
}

.cover-empty span {
	font-size: 40px;
	font-weight: 800;
	color: rgba(0, 135, 90, .35);
}

.body {
	padding: 16px 18px 18px;
	display: flex;
	flex-direction: column;
	gap: 8px;
	flex: 1;
}

.title-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
}

.name {
	margin: 0;
	font-size: 16px;
	font-weight: 700;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.status {
	flex-shrink: 0;
	font-size: 11.5px;
	padding: 2px 8px;
	border-radius: 4px;
	background: #f2f7f6;
	color: var(--muted);
}

.status.is-active {
	background: #e7f5ee;
	color: var(--teal);
}

.status.is-wip {
	background: #fff7e6;
	color: #b26a00;
}

.status.is-maintaining {
	background: #eef2ff;
	color: #3b5bdb;
}

.status.is-archived {
	background: #f3f4f6;
	color: #6b7280;
}

.summary {
	margin: 0;
	color: var(--muted);
	font-size: 13px;
	line-height: 1.7;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.tech {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-top: auto;
	padding-top: 4px;
}

.tech-tag {
	font-size: 11.5px;
	padding: 2px 8px;
	border-radius: 4px;
	background: #f5f8f7;
	color: var(--muted);
}

.empty-box {
	padding: 56px 24px;
	text-align: center;
}

@media (max-width: 720px) {
	.grid {
		grid-template-columns: 1fr;
	}

	.cover {
		height: 170px;
	}
}
</style>
