<template>
	<div class="oss-home">
		<!-- 首屏 -->
		<section class="hero">
			<div class="hero-grid">
				<div>
					<p class="eyebrow">{{ t('oss.eyebrow') }}</p>
					<h1>{{ t('oss.title') }}</h1>
					<p class="lead">{{ t('oss.lead') }}</p>
					<p class="desc">{{ t('oss.desc') }}</p>

					<form class="hero-search" @submit.prevent="goSearch">
						<input v-model="q" type="search" :placeholder="t('oss.searchPlaceholder')">
						<button type="submit">{{ t('oss.searchBtn') }}</button>
					</form>

					<div class="hero-note">
						<span class="dot"></span>{{ t('oss.note') }}
					</div>
				</div>

				<div class="hero-art" aria-hidden="true">
					<svg width="440" height="330" viewBox="0 0 440 330" fill="none" xmlns="http://www.w3.org/2000/svg">
						<defs>
							<linearGradient id="ossGlow" x1="0" y1="0" x2="1" y2="1">
								<stop offset="0" stop-color="#00875A" stop-opacity=".10"/>
								<stop offset="1" stop-color="#00875A" stop-opacity=".02"/>
							</linearGradient>
							<linearGradient id="ossTerm" x1="0" y1="0" x2="0" y2="1">
								<stop offset="0" stop-color="#115E59"/>
								<stop offset="1" stop-color="#0B3F3A"/>
							</linearGradient>
						</defs>

						<circle cx="220" cy="160" r="126" fill="url(#ossGlow)"/>
						<circle cx="220" cy="160" r="126" stroke="#00875A" stroke-opacity=".14" stroke-dasharray="3 6"/>

						<rect x="96" y="72" width="210" height="170" rx="12" fill="url(#ossTerm)"/>
						<path d="M96 84c0-6.6 5.4-12 12-12h186c6.6 0 12 5.4 12 12v16H96V84z" fill="#0A3733"/>
						<circle cx="112" cy="86" r="4" fill="#F87171" fill-opacity=".9"/>
						<circle cx="126" cy="86" r="4" fill="#FBBF24" fill-opacity=".9"/>
						<circle cx="140" cy="86" r="4" fill="#34D399" fill-opacity=".9"/>

						<rect x="112" y="118" width="46" height="7" rx="3.5" fill="#5EEAD4" fill-opacity=".55"/>
						<rect x="166" y="118" width="78" height="7" rx="3.5" fill="#E2E8F0" fill-opacity=".30"/>
						<rect x="126" y="136" width="104" height="7" rx="3.5" fill="#E2E8F0" fill-opacity=".18"/>
						<rect x="126" y="154" width="54" height="7" rx="3.5" fill="#5EEAD4" fill-opacity=".45"/>
						<rect x="188" y="154" width="66" height="7" rx="3.5" fill="#E2E8F0" fill-opacity=".18"/>
						<rect x="112" y="172" width="90" height="7" rx="3.5" fill="#E2E8F0" fill-opacity=".18"/>
						<rect x="210" y="172" width="42" height="7" rx="3.5" fill="#34D399" fill-opacity=".55"/>
						<rect x="132" y="196" width="16" height="7" rx="3.5" fill="#FBBF24" fill-opacity=".7"/>
						<rect x="156" y="196" width="72" height="7" rx="3.5" fill="#E2E8F0" fill-opacity=".22"/>
						<rect x="132" y="216" width="158" height="2" rx="1" fill="#E2E8F0" fill-opacity=".10"/>

						<rect x="318" y="48" width="96" height="60" rx="10" fill="#fff" stroke="#00875A" stroke-opacity=".2"/>
						<rect x="330" y="60" width="34" height="6" rx="3" fill="#00875A" fill-opacity=".38"/>
						<rect x="330" y="74" width="20" height="20" rx="4" fill="#00875A" fill-opacity=".10"/>
						<rect x="356" y="82" width="20" height="12" rx="4" fill="#00875A" fill-opacity=".18"/>
						<rect x="382" y="74" width="18" height="20" rx="4" fill="#00875A" fill-opacity=".30"/>
						<path d="M306 78h6" stroke="#00875A" stroke-opacity=".3" stroke-dasharray="3 4"/>

						<rect x="20" y="198" width="98" height="58" rx="10" fill="#fff" stroke="#00875A" stroke-opacity=".2"/>
						<circle cx="42" cy="219" r="8" fill="#00875A" fill-opacity=".16"/>
						<path d="M38.4 219l2.6 2.6 4.6-4.8" stroke="#00875A" stroke-width="1.8" stroke-linecap="round"
						      stroke-linejoin="round"/>
						<rect x="58" y="215" width="42" height="6" rx="3" fill="#00875A" fill-opacity=".30"/>
						<rect x="34" y="235" width="66" height="6" rx="3" fill="#00875A" fill-opacity=".14"/>

						<circle cx="72" cy="112" r="4" fill="#00875A" fill-opacity=".30"/>
						<circle cx="382" cy="268" r="5" fill="#00875A" fill-opacity=".22"/>
						<circle cx="346" cy="300" r="3" fill="#00875A" fill-opacity=".30"/>
					</svg>
				</div>
			</div>
		</section>

		<!-- 领域筛选 -->
		<section class="filters">
			<div class="chips">
				<NuxtLink :to="localePath('/open-source/projects')" class="chip on">{{ t('oss.allProjects') }}</NuxtLink>
				<NuxtLink v-for="item in categories" :key="item.code" :to="localePath(`/open-source/category/${item.code}`)"
				          class="chip">
					{{ t(`oss.cats.${item.code}`) }}
					<span class="chip-n">{{ item.count }}</span>
				</NuxtLink>
			</div>
		</section>

		<!-- 精选研究 -->
		<section v-if="featured.project" class="section">
			<div class="feature">
				<div class="feature-body">
					<div class="feature-badges">
						<span class="badge">
							<svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
								<path d="M12 2l2.6 6.3 6.8.6-5.2 4.5 1.6 6.7L12 16.8 6.2 20.1l1.6-6.7L2.6 8.9l6.8-.6L12 2z"/>
							</svg>
							{{ t('oss.featured') }}
						</span>
						<span class="badge soft">{{ featured.project.name }}</span>
					</div>

					<h2>{{ featuredTitle }}</h2>
					<p class="feature-desc">{{ featuredDesc }}</p>

					<div class="tags">
						<span class="tag field">{{ t(`oss.cats.${featured.project.category}`) }}</span>
						<span v-for="tag in (featured.project.tags || []).slice(0, 3)" :key="tag" class="tag">{{ tag }}</span>
					</div>

					<div class="feature-meta">
						<span v-if="featured.article">{{ featured.article.relationType ? t(`oss.relations.${featured.article.relationType}`) : '' }}</span>
						<span v-if="featured.project.lastVerifiedAt">
							{{ t('oss.detail.verifiedAt', {date: formatDate(featured.project.lastVerifiedAt, 'YYYY-MM-DD')}) }}
						</span>
						<span v-if="featured.project.articleCount">
							{{ t('oss.articleCount', {n: featured.project.articleCount}) }}
						</span>
					</div>

					<div class="feature-actions">
						<NuxtLink :to="featuredLink" class="btn btn-sm">
							{{ featured.article ? t('oss.readAnalysis') : t('oss.viewProfile') }}
							<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
							     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<path d="M5 12h14M13 6l6 6-6 6"/>
							</svg>
						</NuxtLink>
						<!-- 只有存在分析文章时才给第二个按钮，否则两个按钮指向同一页、文案也一样 -->
						<NuxtLink v-if="featured.article" :to="localePath(`/open-source/projects/${featured.project.slug}`)"
						          class="btn btn-ghost btn-sm">
							{{ t('oss.viewProfile') }}
						</NuxtLink>
					</div>
				</div>

				<div class="feature-art" aria-hidden="true">
					<svg width="424" height="290" viewBox="0 0 380 260" fill="none" xmlns="http://www.w3.org/2000/svg">
						<rect x="28" y="30" width="150" height="196" rx="10" fill="#fff" stroke="#00875A" stroke-opacity=".18"/>
						<rect x="28" y="30" width="150" height="24" rx="10" fill="#00875A" fill-opacity=".08"/>
						<circle cx="42" cy="42" r="3" fill="#00875A" fill-opacity=".35"/>
						<circle cx="53" cy="42" r="3" fill="#00875A" fill-opacity=".2"/>
						<rect x="42" y="68" width="80" height="7" rx="3.5" fill="#00875A" fill-opacity=".28"/>
						<rect x="42" y="84" width="118" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>
						<rect x="42" y="98" width="104" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>
						<rect x="42" y="112" width="120" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>
						<rect x="42" y="134" width="60" height="46" rx="6" fill="#00875A" fill-opacity=".08"/>
						<rect x="110" y="134" width="60" height="46" rx="6" fill="#00875A" fill-opacity=".08"/>
						<rect x="42" y="192" width="92" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>

						<path d="M188 128h32" stroke="#00875A" stroke-width="1.6" stroke-dasharray="4 4"/>
						<path d="M214 122l7 6-7 6" stroke="#00875A" stroke-width="1.6" fill="none" stroke-linecap="round"
						      stroke-linejoin="round"/>

						<rect x="230" y="46" width="122" height="164" rx="10" fill="#fff" stroke="#00875A" stroke-opacity=".18"/>
						<rect x="244" y="62" width="52" height="6" rx="3" fill="#00875A" fill-opacity=".3"/>
						<rect x="244" y="80" width="94" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="244" y="94" width="72" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="244" y="112" width="94" height="24" rx="5" fill="#00875A" fill-opacity=".07"/>
						<rect x="252" y="121" width="40" height="6" rx="3" fill="#00875A" fill-opacity=".35"/>
						<rect x="300" y="121" width="30" height="6" rx="3" fill="#00875A" fill-opacity=".18"/>
						<rect x="244" y="144" width="94" height="24" rx="5" fill="#00875A" fill-opacity=".07"/>
						<rect x="252" y="153" width="34" height="6" rx="3" fill="#00875A" fill-opacity=".35"/>
						<rect x="296" y="153" width="34" height="6" rx="3" fill="#00875A" fill-opacity=".18"/>
						<rect x="244" y="176" width="66" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="244" y="188" width="48" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
					</svg>
				</div>
			</div>
		</section>

		<!-- 最新项目研究 -->
		<section class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('oss.latest') }}</h2>
					<p class="sec-sub">{{ t('oss.latestSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/open-source/projects')" class="more">
					{{ t('oss.viewAll') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<!-- 有文章就展示文章流；只有项目时退回项目卡（与视觉稿的「待研究」状态一致） -->
			<div v-if="articles.length" class="grid3">
				<NuxtLink v-for="item in articles" :key="item.id" :to="localePath(`/blog/${item.slug || item.id}`)"
				          class="card cover-card">
					<AppCover :seed="`${item.id}-${item.title}`" :height="132" :variant="1"/>
					<div class="card-pad">
						<div class="tags">
							<span class="tag field">{{ t(`oss.relations.${item.relationType}`) }}</span>
							<NuxtLink :to="localePath(`/open-source/projects/${item.project && item.project.slug}`)" class="tag">
								{{ item.project && item.project.name }}
							</NuxtLink>
						</div>
						<h3 class="card-title">{{ item.title }}</h3>
						<p class="card-desc">{{ item.description }}</p>
						<div class="card-foot">
							<span>{{ formatDate(item.createTime, 'YYYY-MM-DD') }}</span>
							<span v-if="item.readTime">{{ t('home.minutes', {n: item.readTime}) }}</span>
						</div>
					</div>
				</NuxtLink>
			</div>

			<div v-else-if="projects.length" class="project-grid">
				<OssProjectCard v-for="item in projects" :key="item.id" :project="item"/>
			</div>

			<div v-else class="empty">{{ t('oss.empty') }}</div>
		</section>

		<!-- 研究方法 -->
		<section class="section method">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('oss.method') }}</h2>
				</div>
			</div>
			<div class="grid4">
				<div v-for="item in methods" :key="item.title" class="m-item">
					<div class="m-icon" v-html="item.icon"/>
					<h4>{{ item.title }}</h4>
					<p>{{ item.desc }}</p>
				</div>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const {data: categoryRes} = await useAsyncData('oss-categories', () => call('/open-source/categories'))
const {data: featuredRes} = await useAsyncData('oss-featured', () => call('/open-source/featured'))
const {data: articleRes} = await useAsyncData('oss-articles', () => call('/open-source/articles?pageSize=6'))
const {data: projectRes} = await useAsyncData('oss-projects', () => call('/open-source/projects?pageSize=3'))

const categories = computed<any[]>(() => (categoryRes.value as any)?.data || [])
const featured = computed(() => {
	const d = (featuredRes.value as any)?.data || {}
	return {project: d.project || null, article: d.article || null}
})
const articles = computed<any[]>(() => (articleRes.value as any)?.data?.list || [])
const projects = computed<any[]>(() => (projectRes.value as any)?.data?.list || [])

const featuredTitle = computed(() => {
	if (!featured.value.project) {
		return ''
	}
	if (featured.value.article) {
		return featured.value.article.title
	}
	return featured.value.project.name
})

const featuredDesc = computed(() => {
	if (!featured.value.project) {
		return ''
	}
	if (featured.value.article) {
		return featured.value.article.description || featured.value.project.summary
	}
	return featured.value.project.summary
})

const featuredLink = computed(() => {
	const article = featured.value.article
	if (article) {
		return localePath(`/blog/${article.slug || article.id}`)
	}
	return localePath(`/open-source/projects/${featured.value.project.slug}`)
})

const q = ref('')
function goSearch() {
	if (!q.value.trim()) {
		return
	}
	navigateTo(localePath('/open-source/search') + `?q=${encodeURIComponent(q.value.trim())}`)
}

/** 图标直接内联，不引第三方图标库：四个图标不值得多一个依赖 */
const methods = computed(() => [
	{
		title: t('oss.methodTech'),
		desc: t('oss.methodTechDesc'),
		icon: '<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 12l9 5 9-5"/><path d="M3 16.5l9 5 9-5"/></svg>'
	},
	{
		title: t('oss.methodUser'),
		desc: t('oss.methodUserDesc'),
		icon: '<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-5 6-5s6 1.7 6 5"/><path d="M17 5.5a3 3 0 010 5.6"/><path d="M18 15c2 .8 3 2.4 3 5"/></svg>'
	},
	{
		title: t('oss.methodBiz'),
		desc: t('oss.methodBizDesc'),
		icon: '<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="7" width="18" height="13" rx="2.5"/><path d="M8.5 7V5.5A1.5 1.5 0 0110 4h4a1.5 1.5 0 011.5 1.5V7"/><path d="M3 12h18"/></svg>'
	},
	{
		title: t('oss.methodRisk'),
		desc: t('oss.methodRiskDesc'),
		icon: '<svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7.5 3.4v5.3c0 4.4-3 7.8-7.5 9.3-4.5-1.5-7.5-4.9-7.5-9.3V6.4L12 3z"/><path d="M9 12l2 2 4-4.2"/></svg>'
	}
])

useHead({title: t('oss.title')})
</script>

<style scoped>
.oss-home {
	overflow-x: clip;
}

/* ---------------- 首屏 ---------------- */
.hero {
	background: linear-gradient(180deg, var(--teal-050) 0%, #fff 82%);
	margin: -22px calc(50% - 50vw) 0;
	padding: 52px 0 40px;
}

.hero-grid {
	display: grid;
	grid-template-columns: minmax(0, 1fr) 440px;
	gap: 40px;
	align-items: center;
	max-width: var(--wrap);
	margin: 0 auto;
	padding: 0 24px;
}

.hero h1 {
	font-size: 40px;
	font-weight: 800;
	line-height: 1.2;
	letter-spacing: -.8px;
	margin-bottom: 14px;
}

.lead {
	font-size: 18px;
	font-weight: 500;
	color: var(--ink-2);
	margin-bottom: 12px;
}

.desc {
	font-size: 14.5px;
	line-height: 1.8;
	color: var(--muted);
	max-width: 560px;
	margin-bottom: 24px;
}

.hero-search {
	display: flex;
	align-items: center;
	height: 48px;
	max-width: 520px;
	border: 1px solid var(--line-2);
	border-radius: 10px;
	background: #fff;
	overflow: hidden;
}

.hero-search input {
	flex: 1;
	min-width: 0;
	border: 0;
	outline: 0;
	padding: 0 18px;
	font-size: 14px;
	color: var(--ink);
	background: transparent;
}

.hero-search input::placeholder {
	color: var(--faint);
}

.hero-search button {
	height: 36px;
	margin-right: 6px;
	padding: 0 22px;
	border: 0;
	border-radius: 7px;
	background: var(--teal);
	color: #fff;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
}

.hero-search button:hover {
	background: var(--teal-dark);
}

.hero-note {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-top: 16px;
	font-size: 12.5px;
	color: var(--muted);
}

.hero-art {
	display: flex;
	justify-content: center;
}

/* ---------------- 筛选 ---------------- */
.filters {
	padding: 26px 0 6px;
}

.chip-n {
	margin-left: 6px;
	font-size: 11px;
	color: var(--faint);
}

/* ---------------- 精选 ---------------- */
.section {
	padding: 34px 0 0;
}

.feature {
	display: grid;
	grid-template-columns: 1.1fr .9fr;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
	background: linear-gradient(120deg, #f4fbf9, #eaf6f3);
	transition: transform .18s, border-color .18s;
}

.feature:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.feature-body {
	padding: 30px 34px;
}

.feature-badges {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	margin-bottom: 14px;
}

.feature-body h2 {
	font-size: 25px;
	font-weight: 700;
	line-height: 1.35;
	margin-bottom: 12px;
}

.feature-desc {
	font-size: 14.5px;
	line-height: 1.7;
	color: var(--ink-2);
	max-width: 520px;
}

.feature-body .tags {
	margin-top: 14px;
}

.feature-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 14px;
	margin-top: 14px;
	font-size: 12.5px;
	color: var(--muted);
}

.feature-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
	margin-top: 18px;
}

.feature-art {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 16px;
}

/* 项目卡按数量自适应列宽：只有 1 个项目时不要空出三分之二 */
.project-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
	gap: 20px;
}

/* ---------------- 卡片 ---------------- */
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

.card-title {
	font-size: 16px;
	font-weight: 700;
	line-height: 1.45;
	margin: 10px 0 8px;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.cover-card:hover .card-title {
	color: var(--teal);
}

.card-desc {
	flex: 1;
	font-size: 13px;
	line-height: 1.65;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.card-foot {
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

/* ---------------- 研究方法 ---------------- */
.m-item {
	padding: 20px;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	background: #fff;
}

.m-icon {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 38px;
	height: 38px;
	border-radius: 9px;
	background: var(--teal-050);
	border: 1px solid var(--line);
	color: var(--teal);
	margin-bottom: 12px;
}

.m-item h4 {
	font-size: 15px;
	font-weight: 700;
	margin-bottom: 8px;
}

.m-item p {
	font-size: 13px;
	line-height: 1.7;
	color: var(--muted);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1100px) {
	.hero-grid {
		grid-template-columns: 1fr;
		gap: 24px;
	}

	.hero-art, .feature-art {
		display: none;
	}

	.feature {
		grid-template-columns: 1fr;
	}
}

@media (max-width: 640px) {
	.hero {
		padding: 34px 0 28px;
	}

	.hero-grid {
		padding: 0 18px;
	}

	.hero h1 {
		font-size: 28px;
	}

	.lead {
		font-size: 16px;
	}

	.chips {
		flex-wrap: nowrap;
		overflow-x: auto;
		padding-bottom: 4px;
		scrollbar-width: none;
	}

	.chips::-webkit-scrollbar {
		display: none;
	}

	.feature-body {
		padding: 22px 20px;
	}

	.feature-body h2 {
		font-size: 20px;
	}
}
</style>
