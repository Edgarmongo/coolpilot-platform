<template>
	<div class="home">
		<!-- 1. 品牌首屏 -->
		<section class="hero">
			<div class="hero-inner">
				<span class="hero-tag">{{ t('home.tag') }}</span>
				<h1 class="hero-title">{{ t('home.title') }}</h1>
				<p class="hero-desc">{{ t('home.desc') }}</p>

				<div class="fields">
					<span v-for="field in fields" :key="field" class="field">{{ t(field) }}</span>
				</div>

				<div class="hero-actions">
					<NuxtLink :to="localePath('/archives')" class="btn btn-primary">{{ t('home.ctaArticles') }}</NuxtLink>
					<NuxtLink :to="localePath('/service')" class="btn btn-ghost">{{ t('home.ctaService') }}</NuxtLink>
					<NuxtLink :to="localePath('/ask')" class="btn btn-ghost">{{ t('home.ctaAsk') }}</NuxtLink>
				</div>
			</div>
		</section>

		<!-- 2. 四个快捷入口 -->
		<section class="entries">
			<NuxtLink v-for="entry in entries" :key="entry.key" :to="localePath(entry.path)" class="entry-card">
				<div class="entry-head">
					<span class="entry-icon">{{ entry.icon }}</span>
					<strong class="entry-name">{{ t(entry.nameKey) }}</strong>
				</div>
				<p class="entry-desc">{{ t(entry.descKey) }}</p>
				<span class="entry-arrow">→</span>
			</NuxtLink>
		</section>

		<!-- 3. 最新文章 -->
		<section id="latest" class="section">
			<div class="section-head">
				<h2>{{ t('home.latest') }}</h2>
				<NuxtLink :to="localePath('/archives')" class="section-more">{{ t('home.viewAll') }}</NuxtLink>
			</div>
			<div class="card">
				<div v-if="!posts.length" class="empty">{{ t('home.empty') }}</div>
				<NuxtLink v-for="post in posts" :key="post.id" :to="localePath(`/blog/${post.slug || post.id}`)"
				          class="post-item">
					<h3 class="post-title">{{ post.title }}</h3>
					<div class="post-meta">
						<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
						<span v-if="post.category">· {{ post.category.name }}</span>
						<span>· {{ t('home.reads', {n: post.views || 0}) }}</span>
					</div>
				</NuxtLink>
			</div>
		</section>

		<!-- 4. 知识星球 + 技术服务 -->
		<section class="promo" :class="{'is-single': !planet.enabled}">
			<div v-if="planet.enabled" class="promo-card planet-card">
				<div class="promo-head">
					<span class="promo-tag">{{ t('planetPage.tag') }}</span>
					<h2>{{ planet.name || t('planetPage.title') }}</h2>
				</div>
				<p class="promo-desc">{{ planet.shortIntro }}</p>
				<NuxtLink :to="localePath('/planet')" class="btn btn-primary promo-btn">
					{{ t('home.ctaPlanetDetail') }}
				</NuxtLink>
			</div>

			<div class="promo-card service-card">
				<div class="promo-head">
					<span class="promo-tag">{{ t('servicePage.tag') }}</span>
					<h2>{{ t('servicePage.title') }}</h2>
				</div>
				<p class="promo-desc">{{ t('home.serviceCardDesc') }}</p>
				<ul v-if="services.length" class="service-list">
					<li v-for="item in services" :key="item.id">
						<NuxtLink :to="localePath(`/knowledge/${item.productNo}`)">{{ item.title }}</NuxtLink>
					</li>
				</ul>
				<NuxtLink :to="localePath('/service')" class="btn btn-ghost promo-btn">
					{{ t('home.ctaServiceDetail') }}
				</NuxtLink>
			</div>
		</section>

		<!-- 5. 关注我 -->
		<section v-if="socials.length" class="socials">
			<div class="section-head">
				<h2>{{ t('home.followTitle') }}</h2>
				<NuxtLink :to="localePath('/follow')" class="section-more">{{ t('home.followAll') }}</NuxtLink>
			</div>
			<div class="social-row">
				<NuxtLink v-for="item in socials" :key="item.id" :to="localePath('/follow')" class="social-chip"
				          :title="item.accountName">
					<SocialIcon :platform="item.platform"/>
					<span>{{ item.accountName }}</span>
				</NuxtLink>
			</div>
		</section>

		<!-- 6. 数据指标 -->
		<section class="metrics">
			<div class="metric">
				<strong>{{ blogTotal }}</strong>
				<span>{{ t('home.blogUnit') }}</span>
			</div>
			<div class="metric">
				<strong>{{ projectTotal }}</strong>
				<span>{{ t('home.projectUnit') }}</span>
			</div>
			<div class="metric">
				<strong>{{ productTotal }}</strong>
				<span>{{ t('home.productUnit') }}</span>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
// 首页不单独设 title：app.vue 的 titleTemplate 在没有 title 时直接用品牌名，
// 写成 t('site.brandTitle') 反而会造一个不存在的键
const {t} = useI18n()
const localePath = useLocalePath()
const {locale} = useI18n()

const {data: blogRes} = await useAsyncData('home-blogs', () => call('/blogs?pageNum=1'))
const {data: productRes} = await useAsyncData('home-products', () => call('/products?pageNum=1&pageSize=1'))
const {data: projectRes} = await useAsyncData('home-portfolios', () => call('/portfolios'))
const {data: planetRes} = await useAsyncData('home-planet', () => call(`/planet?lang=${locale.value}`), {watch: [locale]})
const {data: socialRes} = await useAsyncData('home-socials', () => call('/social'))
const {data: serviceRes} = await useAsyncData('home-services', () => call('/products?type=SERVICE&pageNum=1&pageSize=3'))

const posts = computed(() => (blogRes.value as any)?.data?.list || [])
const blogTotal = computed(() => (blogRes.value as any)?.data?.total || posts.value.length)
const productTotal = computed(() => (productRes.value as any)?.data?.total || 0)
const projectTotal = computed(() => ((projectRes.value as any)?.data || []).length)
const services = computed(() => (serviceRes.value as any)?.data?.list || [])
const socials = computed(() => ((socialRes.value as any)?.data || []).slice(0, 6))

const planet = computed(() => {
	const d = (planetRes.value as any)?.data || {}
	const intro = d.intro || ''
	return {
		enabled: !!d.enabled,
		name: d.name || '',
		// 首页只放一行，取第一段并截断，完整介绍在 /planet
		shortIntro: intro.split('\n')[0].slice(0, 80) || t('home.planetFallbackDesc')
	}
})

const fields = computed(() => [
	t('home.fieldJava'), t('home.fieldAi'), t('home.fieldAiEng'), t('home.fieldEnterprise'), t('home.fieldPractice')
])

const entries = [
	{key: 'articles', icon: '✍', path: '/archives', nameKey: 'home.entryBlog', descKey: 'home.entryBlogDesc'},
	{key: 'topics', icon: '🧭', path: '/topic', nameKey: 'home.entryTopic', descKey: 'home.entryTopicDesc'},
	{key: 'projects', icon: '🧱', path: '/projects', nameKey: 'home.entryProject', descKey: 'home.entryProjectDesc'},
	{key: 'products', icon: '📚', path: '/knowledge', nameKey: 'home.entryKnowledge', descKey: 'home.entryKnowledgeDesc'}
]
</script>

<style scoped>
.home {
	max-width: 100%;
}

/* ---------------- 首屏 ---------------- */
.hero {
	background: linear-gradient(135deg, #ffffff 0%, #eef8f3 100%);
	border: 1px solid rgba(0, 135, 90, .12);
	border-radius: 16px;
	padding: 44px 40px;
	margin-bottom: 24px;
	position: relative;
	overflow: hidden;
}

.hero::after {
	content: '';
	position: absolute;
	right: -60px;
	top: -60px;
	width: 280px;
	height: 280px;
	border-radius: 50%;
	background: radial-gradient(circle, rgba(0, 135, 90, .08) 0%, transparent 70%);
	pointer-events: none;
}

.hero-inner {
	max-width: 740px;
	position: relative;
	z-index: 1;
}

.hero-tag {
	display: inline-block;
	padding: 3px 12px;
	border-radius: 20px;
	background: #e7f5ee;
	color: var(--teal);
	font-size: 12.5px;
	font-weight: 600;
	margin-bottom: 16px;
}

.hero-title {
	margin: 0 0 14px;
	font-size: 36px;
	line-height: 1.3;
	letter-spacing: -.6px;
	font-weight: 800;
}

.hero-desc {
	margin: 0 0 18px;
	color: var(--muted);
	font-size: 15px;
	line-height: 1.8;
	max-width: 600px;
}

.fields {
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	margin-bottom: 22px;
}

.field {
	font-size: 12.5px;
	padding: 3px 11px;
	border-radius: 20px;
	background: rgba(255, 255, 255, .8);
	border: 1px solid rgba(0, 135, 90, .18);
	color: var(--teal);
}

.hero-actions {
	display: flex;
	gap: 12px;
	flex-wrap: wrap;
}

.btn {
	display: inline-block;
	padding: 10px 22px;
	border-radius: 10px;
	font-size: 14px;
	font-weight: 600;
	cursor: pointer;
	border: 1px solid transparent;
}

.btn-primary {
	background: var(--teal);
	color: #fff;
}

.btn-primary:hover {
	background: var(--teal-dark);
}

.btn-ghost {
	background: #fff;
	color: var(--teal);
	border-color: var(--teal);
}

.btn-ghost:hover {
	background: #e7f5ee;
}

/* ---------------- 快捷入口 ---------------- */
.entries {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 16px;
	margin-bottom: 24px;
}

.entry-card {
	display: flex;
	flex-direction: column;
	gap: 10px;
	background: #fff;
	border: 1px solid rgba(0, 135, 90, .14);
	border-radius: 14px;
	padding: 20px;
	position: relative;
	transition: transform .15s, box-shadow .15s, border-color .15s;
}

.entry-card:hover {
	transform: translateY(-2px);
	border-color: rgba(0, 135, 90, .36);
	box-shadow: 0 6px 18px rgba(0, 0, 0, .06);
}

.entry-head {
	display: flex;
	align-items: center;
	gap: 10px;
}

.entry-icon {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 38px;
	height: 38px;
	border-radius: 10px;
	background: #e7f5ee;
	font-size: 18px;
}

.entry-name {
	font-size: 16px;
	font-weight: 700;
}

.entry-desc {
	margin: 0;
	color: var(--muted);
	font-size: 13px;
	line-height: 1.6;
}

.entry-arrow {
	position: absolute;
	right: 16px;
	top: 16px;
	color: var(--teal);
	font-size: 16px;
	opacity: .5;
	transition: transform .15s, opacity .15s;
}

.entry-card:hover .entry-arrow {
	transform: translateX(3px);
	opacity: 1;
}

/* ---------------- 分区 ---------------- */
.section {
	margin-bottom: 24px;
}

.section-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	margin-bottom: 12px;
}

.section-head h2 {
	margin: 0;
	font-size: 18px;
	font-weight: 700;
}

.section-more {
	color: var(--teal);
	font-size: 13px;
	font-weight: 600;
}

.section-more:hover {
	text-decoration: underline;
}

/* ---------------- 星球 / 服务 ---------------- */
.promo {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 16px;
	margin-bottom: 24px;
}

/* 星球没启用时只剩服务卡片，让它占满一行，别在右边留一块空白 */
.promo.is-single {
	grid-template-columns: 1fr;
}

.promo-card {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid rgba(0, 135, 90, .14);
	border-radius: 14px;
	padding: 22px 24px;
}

.planet-card {
	background: linear-gradient(135deg, #f2fbf7 0%, #ffffff 70%);
}

.promo-head {
	margin-bottom: 8px;
}

.promo-tag {
	display: inline-block;
	font-size: 11.5px;
	padding: 2px 9px;
	border-radius: 4px;
	background: #e7f5ee;
	color: var(--teal);
	margin-bottom: 8px;
}

.promo-head h2 {
	margin: 0;
	font-size: 17px;
	font-weight: 700;
}

.promo-desc {
	margin: 0 0 14px;
	color: var(--muted);
	font-size: 13px;
	line-height: 1.7;
	flex: 1;
}

.promo-btn {
	align-self: flex-start;
	padding: 8px 18px;
	font-size: 13px;
}

.service-list {
	margin: 0 0 14px;
	padding: 0;
	list-style: none;
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.service-list li a {
	font-size: 13px;
	color: var(--teal);
}

.service-list li a:hover {
	text-decoration: underline;
}

/* ---------------- 关注我 ---------------- */
.socials {
	margin-bottom: 24px;
}

.social-row {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.social-chip {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 7px 14px 7px 8px;
	border-radius: 10px;
	background: #fff;
	border: 1px solid rgba(0, 135, 90, .14);
	font-size: 13px;
	color: var(--ink);
	transition: border-color .15s, transform .15s;
}

.social-chip:hover {
	border-color: rgba(0, 135, 90, .4);
	transform: translateY(-1px);
}

/* ---------------- 指标 ---------------- */
.metrics {
	display: flex;
	gap: 40px;
	justify-content: center;
	padding: 8px 0;
}

.metric {
	text-align: center;
}

.metric strong {
	display: block;
	font-size: 26px;
	color: var(--teal);
	font-weight: 800;
}

.metric span {
	font-size: 12.5px;
	color: var(--muted);
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 860px) {
	.entries {
		grid-template-columns: repeat(2, 1fr);
	}

	.promo {
		grid-template-columns: 1fr;
	}

	.hero-title {
		font-size: 28px;
	}

	.hero {
		padding: 32px 24px;
	}
}

@media (max-width: 480px) {
	.entries {
		grid-template-columns: 1fr;
	}

	.metrics {
		gap: 24px;
	}
}
</style>
