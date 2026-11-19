<template>
	<div>
		<div class="page-head">
			<h1 class="page-title">{{ t('oss.list.title') }}</h1>
			<p class="page-desc">{{ t('oss.list.desc') }}</p>
		</div>

		<div class="toolbar">
			<div class="chips">
				<NuxtLink :to="localePath('/open-source/projects')" class="chip" :class="{on: !category}">
					{{ t('oss.allProjects') }}
				</NuxtLink>
				<NuxtLink v-for="item in categories" :key="item.code"
				          :to="localePath(`/open-source/category/${item.code}`)" class="chip">
					{{ t(`oss.cats.${item.code}`) }}
					<span class="chip-n">{{ item.count }}</span>
				</NuxtLink>
			</div>

			<div class="sort">
				<NuxtLink :to="localePath('/open-source/projects')" class="sort-item" :class="{on: sort !== 'density'}">
					{{ t('oss.list.sortDefault') }}
				</NuxtLink>
				<NuxtLink :to="localePath('/open-source/projects') + '?sort=density'" class="sort-item"
				          :class="{on: sort === 'density'}">
					{{ t('oss.list.sortDensity') }}
				</NuxtLink>
			</div>
		</div>

		<div v-if="!projects.length" class="empty">{{ t('oss.emptyList') }}</div>

		<template v-else>
			<div class="grid3 section">
				<OssProjectCard v-for="item in projects" :key="item.id" :project="item"/>
			</div>

			<div v-if="totalPage > 1" class="pager">
				<button :disabled="pageNum <= 1" @click="go(pageNum - 1)">{{ t('common.prev') }}</button>
				<span class="pg on">{{ pageNum }}</span>
				<span class="pg-page">/ {{ totalPage }}</span>
				<button :disabled="pageNum >= totalPage" @click="go(pageNum + 1)">{{ t('common.next') }}</button>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const sort = computed(() => String(route.query.sort || 'default'))
const pageNum = ref(Number(route.query.page) || 1)
// 列表页不带领域筛选：领域有自己的可收录地址 /open-source/category/{code}
const category = computed(() => String(route.query.category || ''))

const {data} = await useAsyncData('oss-project-list', () => call(
	`/open-source/projects?pageNum=${pageNum.value}&pageSize=12&sort=${sort.value}`
), {watch: [pageNum, sort]})

const {data: categoryRes} = await useAsyncData('oss-categories-list', () => call('/open-source/categories'))
const categories = computed<any[]>(() => (categoryRes.value as any)?.data || [])
const projects = computed<any[]>(() => (data.value as any)?.data?.list || [])
const totalPage = computed(() => (data.value as any)?.data?.totalPage || 1)

function go(page: number) {
	if (page < 1 || page > totalPage.value) {
		return
	}
	pageNum.value = page
	router.replace({query: {...route.query, page: page > 1 ? String(page) : undefined}})
	window.scrollTo({top: 0, behavior: 'smooth'})
}

useHead({title: t('oss.list.title')})
</script>

<style scoped>
.toolbar {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 20px;
	flex-wrap: wrap;
	padding: 22px 0 4px;
}

.chip-n {
	margin-left: 6px;
	font-size: 11px;
	color: var(--faint);
}

.chip.on .chip-n {
	color: rgba(255, 255, 255, .75);
}

.sort {
	display: inline-flex;
	padding: 3px;
	border: 1px solid var(--line);
	border-radius: var(--radius-sm);
	background: var(--teal-050);
}

.sort-item {
	padding: 5px 14px;
	border-radius: 6px;
	font-size: 12.5px;
	color: var(--muted);
	white-space: nowrap;
}

.sort-item:hover {
	color: var(--teal);
}

.sort-item.on {
	background: #fff;
	color: var(--teal);
	font-weight: 600;
	box-shadow: 0 1px 2px rgba(16, 24, 40, .06);
}

.section {
	padding-top: 20px;
}

.pg-page {
	color: var(--faint);
	border: 0;
	background: none;
	font-size: 13px;
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
