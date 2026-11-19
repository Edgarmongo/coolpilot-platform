<template>
	<div>
		<nav class="crumb">
			<NuxtLink :to="localePath('/open-source')">{{ t('oss.title') }}</NuxtLink>
			<span class="crumb-sep">/</span>
			<span>{{ t('oss.search.title') }}</span>
		</nav>

		<div class="page-head">
			<h1 class="page-title">{{ t('oss.search.title') }}</h1>
			<p class="page-desc">{{ t('oss.search.desc') }}</p>
		</div>

		<form class="search-bar" @submit.prevent="submit">
			<input v-model="q" type="search" :placeholder="t('oss.search.placeholder')">
			<button type="submit" class="btn btn-sm">{{ t('oss.searchBtn') }}</button>
		</form>

		<template v-if="searched">
			<div v-if="!projects.length && !articles.length" class="empty">{{ t('oss.search.empty') }}</div>

			<section v-if="projects.length" class="block">
				<div class="sec-head">
					<h2 class="sec-title"><span class="bar"></span>{{ t('oss.search.projects') }}
						<span class="count">{{ projects.length }}</span>
					</h2>
				</div>
				<div class="grid3">
					<OssProjectCard v-for="item in projects" :key="item.id" :project="item"/>
				</div>
			</section>

			<section v-if="articles.length" class="block">
				<div class="sec-head">
					<h2 class="sec-title"><span class="bar"></span>{{ t('oss.search.articles') }}
						<span class="count">{{ articles.length }}</span>
					</h2>
				</div>
				<ul class="article-list">
					<li v-for="item in articles" :key="item.id">
						<NuxtLink :to="localePath(`/blog/${item.slug || item.id}`)" class="article-item">
							<span class="rel">{{ t(`oss.relations.${item.relationType}`) }}</span>
							<span class="title">{{ item.title }}</span>
							<span class="project">{{ item.project && item.project.name }}</span>
						</NuxtLink>
					</li>
				</ul>
			</section>
		</template>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const q = ref(String(route.query.q || ''))
const current = ref(String(route.query.q || ''))
const projects = ref<any[]>([])
const articles = ref<any[]>([])
const searched = ref(false)

async function run() {
	if (!current.value) {
		return
	}
	searched.value = true
	const res: any = await call(`/open-source/search?q=${encodeURIComponent(current.value)}`).catch(() => null)
	projects.value = (res && res.data && res.data.projects) || []
	articles.value = (res && res.data && res.data.articles) || []
}

if (current.value) {
	await run()
}

async function submit() {
	const keyword = q.value.trim()
	if (!keyword) {
		return
	}
	await router.replace({query: {q: keyword}})
	current.value = keyword
	await run()
}

useHead({title: t('oss.search.title')})
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

.search-bar {
	display: flex;
	gap: 10px;
	max-width: 560px;
	margin: 22px 0 8px;
}

.search-bar input {
	flex: 1;
	min-width: 0;
	height: 38px;
	padding: 0 14px;
	border: 1px solid var(--line);
	border-radius: var(--radius-sm);
	font-size: 14px;
	outline: none;
}

.search-bar input:focus {
	border-color: var(--teal);
}

.block {
	padding-top: 30px;
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

.project {
	font-size: 12px;
	color: var(--faint);
}

@media (max-width: 640px) {
	.article-item {
		grid-template-columns: 72px minmax(0, 1fr);
		row-gap: 4px;
	}

	.project {
		grid-column: 2;
	}
}
</style>
