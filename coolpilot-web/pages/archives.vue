<template>
	<div class="archives">
		<div class="page-head">
			<h1 class="page-title">{{ t('archives.listTitle') }}</h1>
			<p class="page-desc">{{ t('archives.listDesc') }}</p>
		</div>

		<!-- 筛选：全部 + 专题。专题不是前端过滤，点了就去专题页，地址可分享 -->
		<div class="filters">
			<div class="chips">
				<NuxtLink :to="localePath('/archives')" class="chip on">
					{{ t('archives.all') }}
				</NuxtLink>
				<NuxtLink v-for="item in topics" :key="item.id" :to="localePath(`/topic/${item.slug}`)" class="chip">
					{{ item.title }}
				</NuxtLink>
			</div>

			<div class="view-switch">
				<NuxtLink :to="localePath('/archives')" class="vs-item" :class="{on: view === 'list'}">
					{{ t('archives.viewList') }}
				</NuxtLink>
				<NuxtLink :to="localePath('/archives') + '?view=timeline'" class="vs-item"
				          :class="{on: view === 'timeline'}">
					{{ t('archives.viewTimeline') }}
				</NuxtLink>
			</div>
		</div>

		<!-- 列表视图 -->
		<section v-if="view === 'list'" class="section">
			<div class="with-side">
				<div>
					<div v-if="!posts.length" class="empty">{{ t('archives.listEmpty') }}</div>
					<template v-else>
						<article v-for="post in posts" :key="post.id" class="article-row">
							<NuxtLink :to="localePath(`/blog/${post.slug || post.id}`)" class="article-thumb">
								<AppCover :seed="`${post.id}-${post.title}`" :height="124"/>
							</NuxtLink>
							<div>
								<div class="tags">
									<NuxtLink v-if="post.category" :to="localePath(`/category/${post.category.name}`)"
									          class="tag field">{{ post.category.name }}</NuxtLink>
									<NuxtLink v-for="tag in (post.tags || []).slice(0, 2)" :key="tag.id"
									          :to="localePath(`/tag/${tag.name}`)" class="tag">{{ tag.name }}</NuxtLink>
								</div>
								<h2 class="row-title">
									<NuxtLink :to="localePath(`/blog/${post.slug || post.id}`)">{{ post.title }}</NuxtLink>
								</h2>
								<p v-if="plain(post.description)" class="row-excerpt">{{ plain(post.description) }}</p>
								<div class="meta">
									<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
									<span v-if="post.readTime" class="sep">·</span>
									<span v-if="post.readTime">{{ t('home.minutes', {n: post.readTime}) }}</span>
									<span v-if="post.views" class="sep">·</span>
									<span v-if="post.views">{{ t('post.reads', {n: post.views}) }}</span>
								</div>
							</div>
						</article>

						<div v-if="totalPage > 1" class="pager">
							<button :disabled="pageNum <= 1" @click="go(pageNum - 1)">{{ t('common.prev') }}</button>
							<span class="pg on">{{ pageNum }}</span>
							<span class="pg-page">/ {{ totalPage }}</span>
							<button :disabled="pageNum >= totalPage" @click="go(pageNum + 1)">{{ t('common.next') }}</button>
						</div>
					</template>
				</div>

				<!-- 侧栏：统计 / 专题 / 归档入口 -->
				<aside class="side">
					<div class="side-block">
						<div class="side-title">{{ t('archives.sideStats') }}</div>
						<ul class="side-list">
							<li class="stat-row">
								<span>{{ t('archives.statPosts') }}</span>
								<span class="n">{{ totalCount }}</span>
							</li>
							<li class="stat-row">
								<span>{{ t('archives.statTopics') }}</span>
								<span class="n">{{ topics.length }}</span>
							</li>
						</ul>
					</div>

					<div v-if="topics.length" class="side-block">
						<div class="side-title">{{ t('archives.sideTopics') }}</div>
						<ul class="side-list">
							<li v-for="item in topics" :key="item.id">
								<NuxtLink :to="localePath(`/topic/${item.slug}`)">
									{{ item.title }}<span class="n">{{ item.articleCount || 0 }}</span>
								</NuxtLink>
							</li>
						</ul>
					</div>

					<div class="side-block">
						<div class="side-title">{{ t('archives.sideArchive') }}</div>
						<ul class="side-list">
							<li v-for="group in timeline" :key="group.month">
								<a :href="`#m-${group.month}`" @click.prevent="jumpMonth(group.month)">
									{{ group.month }}<span class="n">{{ group.items.length }}</span>
								</a>
							</li>
						</ul>
					</div>
				</aside>
			</div>
		</section>

		<!-- 时间线视图 -->
		<section v-else class="section">
			<div v-if="!timeline.length" class="empty">{{ t('archives.empty') }}</div>
			<div v-else class="timeline">
				<section v-for="group in timeline" :id="`m-${group.month}`" :key="group.month" class="month">
					<h2 class="month-title">
						{{ group.month }}
						<span class="muted">{{ t('archives.count', {n: group.items.length}) }}</span>
					</h2>
					<ul class="month-list">
						<li v-for="item in group.items" :key="item.id">
							<span class="day">{{ item.day }}</span>
							<NuxtLink :to="localePath(`/blog/${item.id}`)" class="month-link">{{ item.title }}</NuxtLink>
						</li>
					</ul>
				</section>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const view = computed(() => (route.query.view === 'timeline' ? 'timeline' : 'list'))

const pageNum = ref(Number(route.query.page) || 1)

const {data: listRes} = await useAsyncData('archives-list', () => call(`/blogs?pageNum=${pageNum.value}`), {
	watch: [pageNum]
})
const {data: archiveRes} = await useAsyncData('archives-map', () => call('/archives'))
const {data: topicRes} = await useAsyncData('archives-topics', () => call('/topics'))

const posts = computed(() => (listRes.value as any)?.data?.list || [])
const totalPage = computed(() => (listRes.value as any)?.data?.totalPage || 1)

const blogMap = computed<Record<string, any[]>>(() => (archiveRes.value as any)?.data?.blogMap || {})
const totalCount = computed(() => (archiveRes.value as any)?.data?.count || 0)
const topics = computed<any[]>(() => (topicRes.value as any)?.data || [])

/** 月份倒序；后端给的是 LinkedHashMap，但顺序不该由接口实现决定 */
const timeline = computed(() =>
	Object.keys(blogMap.value)
		.sort((a, b) => (a < b ? 1 : -1))
		.map(month => ({month, items: blogMap.value[month] || []}))
)

function plain(html: string) {
	return (html || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}

function go(page: number) {
	if (page < 1 || page > totalPage.value) {
		return
	}
	pageNum.value = page
	router.replace({query: {...route.query, page: page > 1 ? String(page) : undefined}})
	window.scrollTo({top: 0, behavior: 'smooth'})
}

/** 列表视图里点侧栏的月份：切到时间线并滚到那个月，而不是原地跳一个不存在的锚点 */
function jumpMonth(month: string) {
	router.push({query: {...route.query, view: 'timeline'}}).then(() => {
		requestAnimationFrame(() => {
			document.getElementById(`m-${month}`)?.scrollIntoView({behavior: 'smooth', block: 'start'})
		})
	})
}

useHead({title: t('archives.listTitle')})
</script>

<style scoped>
.filters {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20px;
	flex-wrap: wrap;
	padding: 24px 0 4px;
}

.view-switch {
	display: inline-flex;
	padding: 3px;
	border: 1px solid var(--line);
	border-radius: var(--radius-sm);
	background: var(--teal-050);
}

.vs-item {
	padding: 5px 14px;
	border-radius: 6px;
	font-size: 12.5px;
	color: var(--muted);
}

.vs-item:hover {
	color: var(--teal);
}

.vs-item.on {
	background: #fff;
	color: var(--teal);
	font-weight: 600;
	box-shadow: 0 1px 2px rgba(16, 24, 40, .06);
}

.section {
	padding-top: 14px;
}

.pg-page {
	color: var(--faint);
	border: 0;
	background: none;
	font-size: 13px;
}

/* 统计块是纯文本行，.side-list 的 flex 只加在 a 上，这里单独补 */
.stat-row {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 10px;
	font-size: 13.5px;
	color: var(--ink-2);
}

/* ============================================================
   时间线
   ============================================================ */
.timeline {
	max-width: 860px;
}

.month {
	scroll-margin-top: 130px;
	margin-bottom: 30px;
}

.month-title {
	display: flex;
	align-items: baseline;
	gap: 10px;
	font-size: 15px;
	font-weight: 700;
	color: var(--teal);
	margin-bottom: 10px;
	padding-bottom: 8px;
	border-bottom: 1px solid var(--line);
}

.month-title .muted {
	font-weight: 400;
}

.month-list {
	list-style: none;
	margin: 0;
	padding: 0 0 0 2px;
	border-left: 2px solid var(--teal-100);
}

.month-list li {
	position: relative;
	display: flex;
	align-items: baseline;
	gap: 14px;
	padding: 7px 0 7px 18px;
}

.month-list li::before {
	content: '';
	position: absolute;
	left: -5px;
	top: 15px;
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: var(--teal-100);
	border: 1px solid var(--line-2);
}

.month-list li:hover::before {
	background: var(--teal);
}

.day {
	width: 44px;
	flex-shrink: 0;
	color: var(--muted);
	font-size: 12.5px;
	font-variant-numeric: tabular-nums;
}

.month-link {
	font-size: 14.5px;
	color: var(--ink);
	line-height: 1.5;
}

.month-link:hover {
	color: var(--teal);
}

@media (max-width: 640px) {
	.filters {
		align-items: flex-start;
		flex-direction: column;
		gap: 12px;
	}
}
</style>
