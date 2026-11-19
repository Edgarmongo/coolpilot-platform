<template>
	<div v-if="project" class="oss-detail">
		<nav class="crumb">
			<NuxtLink :to="localePath('/')">{{ t('post.crumbHome') }}</NuxtLink>
			<span class="crumb-sep">/</span>
			<NuxtLink :to="localePath('/open-source')">{{ t('oss.title') }}</NuxtLink>
			<span class="crumb-sep">/</span>
			<span>{{ project.name }}</span>
		</nav>

		<header class="head">
			<div class="head-main">
				<h1>{{ project.name }}</h1>
				<p v-if="project.summary" class="summary">{{ project.summary }}</p>

				<div class="badges">
					<span v-if="project.license" class="badge">{{ project.license }}</span>
					<span v-if="project.language" class="badge">{{ project.language }}</span>
					<NuxtLink :to="localePath(`/open-source/category/${project.category}`)" class="badge soft">
						{{ t(`oss.cats.${project.category}`) }}
					</NuxtLink>
					<span v-if="project.verdict" class="verdict" :class="`is-${project.verdict}`">
						{{ t(`oss.verdicts.${project.verdict}`) }}
					</span>
				</div>

				<div class="links">
					<a v-if="detail.repoUrl" :href="detail.repoUrl" target="_blank" rel="noopener noreferrer">
						{{ t('oss.detail.github') }} ↗
					</a>
					<a v-if="detail.officialUrl" :href="detail.officialUrl" target="_blank" rel="noopener noreferrer">
						{{ t('oss.detail.official') }} ↗
					</a>
					<a v-if="detail.docsUrl" :href="detail.docsUrl" target="_blank" rel="noopener noreferrer">
						{{ t('oss.detail.docs') }} ↗
					</a>
				</div>

				<div class="meta">
					<span v-if="project.lastVerifiedAt">
						{{ t('oss.detail.verifiedAt', {date: formatDate(project.lastVerifiedAt, 'YYYY-MM-DD')}) }}
					</span>
					<span v-if="project.stars" class="sep">·</span>
					<span v-if="project.stars">{{ t('oss.detail.starsRef', {n: project.stars}) }}</span>
				</div>
			</div>
		</header>

		<div class="with-side">
			<div class="main-col">
				<!-- 评估卡放在正文之前：读者先要知道结论，再决定是否读细节 -->
				<OssAssessment :json="detail.assessmentJson" :verdict="project.verdict"/>

				<section v-if="detail.contentHtml" class="content-block">
					<div class="prose" v-html="detail.contentHtml"></div>
				</section>

				<section class="articles">
					<div class="sec-head">
						<h2 class="sec-title">
							<span class="bar"></span>{{ t('oss.detail.analysis') }}
							<span v-if="articles.length" class="count">{{ articles.length }}</span>
						</h2>
					</div>

					<div v-if="!articles.length" class="empty-inline">{{ t('oss.detail.analysisEmpty') }}</div>
					<ul v-else class="article-list">
						<li v-for="item in articles" :key="item.id">
							<NuxtLink :to="localePath(`/blog/${item.slug || item.id}`)" class="article-item">
								<span class="rel">{{ t(`oss.relations.${item.relationType}`) }}</span>
								<span class="title">{{ item.title }}</span>
								<span class="when">{{ formatDate(item.createTime, 'YYYY-MM-DD') }}</span>
							</NuxtLink>
						</li>
					</ul>
				</section>

				<p class="disclaimer">{{ t('oss.disclaimer') }}</p>
			</div>

			<aside v-if="related.length" class="side">
				<div class="side-block">
					<div class="side-title">{{ t('oss.detail.related') }}</div>
					<ul class="rel-list">
						<li v-for="item in related" :key="item.id">
							<NuxtLink :to="localePath(`/open-source/projects/${item.slug}`)">
								<strong>{{ item.name }}</strong>
								<span>{{ item.summary }}</span>
							</NuxtLink>
						</li>
					</ul>
				</div>
			</aside>
		</div>
	</div>

	<div v-else class="empty">{{ t('oss.empty') }}</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()
const config = useRuntimeConfig()

const slug = String(route.params.slug)

const {data: res} = await useAsyncData(`oss-detail-${slug}`, () => call(`/open-source/projects/${slug}`))

const payload = computed<any>(() => (res.value as any)?.data || null)
const project = computed(() => payload.value?.project || null)
const detail = computed<any>(() => payload.value || {})
const articles = computed<any[]>(() => payload.value?.articles || [])
const related = computed<any[]>(() => payload.value?.related || [])

// 未发布 / 已归档：接口返回 code 404，这里转成真正的 404 页面，
// 否则搜索引擎会把「不存在的档案」当成 200 收录。
if (!project.value) {
	throw createError({statusCode: 404, statusMessage: 'Project not found'})
}

if (project.value) {
	const siteUrl = String(config.public.siteUrl || '').replace(/\/$/, '')
	const desc = (project.value.summary || '').slice(0, 150)
	useHead({
		title: project.value.name,
		meta: [
			{name: 'description', content: desc},
			{property: 'og:type', content: 'article'},
			{property: 'og:title', content: `${project.value.name} · ${t('oss.title')}`},
			{property: 'og:description', content: desc}
		],
		link: [{rel: 'canonical', href: `${siteUrl}/open-source/projects/${project.value.slug}`}],
		script: [{
			type: 'application/ld+json',
			innerHTML: JSON.stringify({
				'@context': 'https://schema.org',
				'@type': 'BreadcrumbList',
				itemListElement: [
					{'@type': 'ListItem', position: 1, name: t('post.crumbHome'), item: `${siteUrl}/`},
					{'@type': 'ListItem', position: 2, name: t('oss.title'), item: `${siteUrl}/open-source`},
					{'@type': 'ListItem', position: 3, name: project.value.name,
						item: `${siteUrl}/open-source/projects/${project.value.slug}`}
				]
			})
		}]
	})
}
</script>

<style scoped>
.crumb {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	font-size: 12.5px;
	color: var(--muted);
	margin: 22px 0 16px;
}

.crumb a:hover {
	color: var(--teal);
}

.crumb-sep {
	color: var(--faint);
}

.head h1 {
	font-size: 34px;
	font-weight: 800;
	letter-spacing: -.7px;
	line-height: 1.35;
	margin-bottom: 10px;
}

.summary {
	font-size: 15px;
	line-height: 1.75;
	color: var(--ink-2);
	max-width: 760px;
	margin-bottom: 14px;
}

.badges {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	margin-bottom: 14px;
}

.verdict {
	padding: 4px 10px;
	border-radius: 6px;
	background: var(--teal);
	color: #fff;
	font-size: 12px;
	font-weight: 600;
}

.verdict.is-hold {
	background: #6b7280;
}

.verdict.is-watch {
	background: #2c5d8f;
}

.links {
	display: flex;
	flex-wrap: wrap;
	gap: 18px;
	font-size: 13.5px;
	font-weight: 600;
	color: var(--teal);
	margin-bottom: 12px;
}

.links a:hover {
	color: var(--teal-dark);
}

.meta {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	font-size: 12.5px;
	color: var(--muted);
	padding-bottom: 20px;
	border-bottom: 1px solid var(--line);
}

.sep {
	color: var(--line-3);
}

/* ---------------- 主体 ---------------- */
.with-side {
	display: grid;
	grid-template-columns: minmax(0, 1fr) 280px;
	gap: 44px;
	align-items: start;
	padding-top: 26px;
}

.main-col {
	min-width: 0;
}

.content-block {
	margin-top: 30px;
}

.prose {
	font-size: 15.5px;
}

.articles {
	margin-top: 38px;
}

.count {
	margin-left: 8px;
	font-size: 13px;
	font-weight: 500;
	color: var(--faint);
}

.article-list {
	list-style: none;
	margin: 0;
	padding: 0;
	border-top: 1px solid var(--line);
}

.article-item {
	display: grid;
	grid-template-columns: 76px minmax(0, 1fr) auto;
	align-items: baseline;
	gap: 14px;
	padding: 14px 2px;
	border-bottom: 1px solid var(--line);
}

.article-item:hover .title {
	color: var(--teal);
}

.rel {
	font-size: 11.5px;
	font-weight: 600;
	color: var(--teal-700);
	background: var(--teal-100);
	border-radius: 5px;
	padding: 3px 7px;
	text-align: center;
}

.title {
	font-size: 15px;
	font-weight: 600;
	line-height: 1.5;
}

.when {
	font-size: 12px;
	color: var(--faint);
	font-variant-numeric: tabular-nums;
}

.empty-inline {
	padding: 20px 0;
	font-size: 13.5px;
	color: var(--muted);
}

.disclaimer {
	margin-top: 28px;
	padding-top: 16px;
	border-top: 1px dashed var(--line);
	font-size: 12px;
	color: var(--faint);
}

/* ---------------- 侧栏 ---------------- */
.rel-list {
	list-style: none;
	margin: 0;
	padding: 0;
	display: flex;
	flex-direction: column;
	gap: 14px;
}

.rel-list a {
	display: flex;
	flex-direction: column;
	gap: 4px;
}

.rel-list strong {
	font-size: 13.5px;
	font-weight: 700;
}

.rel-list a:hover strong {
	color: var(--teal);
}

.rel-list span {
	font-size: 12px;
	line-height: 1.6;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

@media (max-width: 1100px) {
	.with-side {
		grid-template-columns: 1fr;
		gap: 30px;
	}
}

@media (max-width: 640px) {
	.head h1 {
		font-size: 26px;
	}

	.article-item {
		grid-template-columns: 72px minmax(0, 1fr);
		row-gap: 4px;
	}

	.when {
		grid-column: 2;
	}
}
</style>
