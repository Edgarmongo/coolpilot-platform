<template>
	<div class="project-detail">
		<NuxtLink :to="localePath('/projects')" class="back">{{ t('projectDetail.back') }}</NuxtLink>

		<div v-if="!item" class="card empty-box">
			<p class="empty">{{ t('projectDetail.notFound') }}</p>
			<NuxtLink :to="localePath('/projects')" class="btn">{{ t('projectDetail.backToList') }}</NuxtLink>
		</div>

		<template v-else>
			<header class="head">
				<div class="head-main">
					<h1 class="title">{{ item.name }}</h1>
					<p v-if="item.summary" class="summary">{{ item.summary }}</p>

					<div class="meta">
						<span class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</span>
						<span v-if="item.createTime" class="date">
							{{ t('projectDetail.updated', {date: formatDate(item.createTime, 'YYYY-MM')}) }}
						</span>
					</div>

					<div v-if="techList.length" class="tech">
						<span v-for="tech in techList" :key="tech" class="tech-tag">{{ tech }}</span>
					</div>

					<div class="links">
						<a v-if="item.repoUrl" :href="item.repoUrl" target="_blank" rel="noopener noreferrer"
						   class="btn">{{ t('projectDetail.repo') }}</a>
						<a v-if="item.demoUrl" :href="item.demoUrl" target="_blank" rel="noopener noreferrer"
						   class="btn btn-ghost">{{ t('projectDetail.demo') }}</a>
					</div>
				</div>

				<div v-if="item.coverUrl" class="head-cover">
					<img :src="item.coverUrl" :alt="item.name"/>
				</div>
			</header>

			<article v-if="contentHtml" class="article card" v-html="contentHtml"></article>
			<div v-else class="card empty-box">
				<p class="empty">{{ t('projectDetail.noContent') }}</p>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const slug = String(route.params.slug || '')

const {data} = await useAsyncData(`portfolio-${slug}`, async () => {
	const res: any = await call(`/portfolio/${slug}`).catch(() => null)
	return res && res.code === 200 ? res.data : null
})

const item = computed(() => (data.value as any)?.portfolio || null)
const contentHtml = computed(() => (data.value as any)?.contentHtml || '')

const techList = computed(() =>
	(item.value?.techStack || '').split(/[,，]/).map((s: string) => s.trim()).filter(Boolean)
)

// 注意：t 是 const，useHead 必须放在它之后，否则 setup 阶段 TDZ 报错
useHead({
	title: item.value ? `${item.value.name} - ${t('projectsPage.title')}` : t('projectsPage.title'),
	meta: item.value?.summary ? [{name: 'description', content: item.value.summary}] : []
})

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
.project-detail {
	max-width: 880px;
}

.back {
	display: inline-block;
	margin-bottom: 14px;
	color: var(--muted);
	font-size: 13px;
}

.back:hover {
	color: var(--teal);
}

.head {
	display: flex;
	gap: 28px;
	align-items: flex-start;
	background: linear-gradient(135deg, #ffffff 0%, #f4faf7 100%);
	border: 1px solid rgba(0, 135, 90, .12);
	border-radius: 16px;
	padding: 30px 30px 28px;
	margin-bottom: 18px;
}

.head-main {
	flex: 1;
	min-width: 0;
}

.title {
	margin: 0 0 10px;
	font-size: 27px;
	line-height: 1.35;
	letter-spacing: -.3px;
}

.summary {
	margin: 0 0 14px;
	color: var(--muted);
	font-size: 14px;
	line-height: 1.8;
}

.meta {
	display: flex;
	align-items: center;
	gap: 12px;
	flex-wrap: wrap;
	margin-bottom: 14px;
}

.status {
	font-size: 11.5px;
	padding: 2px 9px;
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

.date {
	font-size: 12.5px;
	color: var(--muted);
}

.tech {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-bottom: 18px;
}

.tech-tag {
	font-size: 12px;
	padding: 3px 9px;
	border-radius: 4px;
	background: #e7f5ee;
	color: var(--teal);
}

.links {
	display: flex;
	gap: 10px;
	flex-wrap: wrap;
}

.head-cover {
	flex-shrink: 0;
	width: 240px;
	border-radius: 12px;
	overflow: hidden;
	border: 1px solid rgba(0, 135, 90, .12);
}

.head-cover img {
	display: block;
	width: 100%;
	height: auto;
}

.article {
	padding: 28px 32px;
}

.article :deep(h2) {
	margin: 26px 0 12px;
	font-size: 19px;
}

.article :deep(h2:first-child) {
	margin-top: 0;
}

.article :deep(p) {
	margin: 12px 0;
}

.article :deep(ul),
.article :deep(ol) {
	margin: 12px 0;
	padding-left: 22px;
}

.article :deep(li) {
	margin: 6px 0;
}

.article :deep(img) {
	max-width: 100%;
	border-radius: 8px;
}

.article :deep(code) {
	background: #f3f6f5;
	padding: 1px 5px;
	border-radius: 4px;
	font-size: 13px;
}

.article :deep(pre) {
	background: #0f172a;
	color: #e2e8f0;
	padding: 14px 16px;
	border-radius: 8px;
	overflow-x: auto;
}

.empty-box {
	padding: 56px 24px;
	text-align: center;
}

.empty-box .btn {
	margin-top: 16px;
}

@media (max-width: 720px) {
	.head {
		flex-direction: column;
		padding: 22px 20px;
	}

	.head-cover {
		width: 100%;
	}

	.title {
		font-size: 22px;
	}

	.article {
		padding: 20px 18px;
	}
}
</style>
