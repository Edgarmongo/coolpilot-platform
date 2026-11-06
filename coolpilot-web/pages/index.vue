<template>
	<div class="home">
		<!-- 首屏：大标题 + 副标题 + 行动按钮 -->
		<section class="hero">
			<div class="hero-inner">
				<span class="hero-tag">{{ t('home.tag') }}</span>
				<h1 class="hero-title">{{ t('home.title') }}</h1>
				<p class="hero-desc">{{ t('home.desc') }}</p>
				<div class="hero-actions">
					<NuxtLink :to="localePath('/knowledge')" class="btn btn-primary">{{ t('home.ctaKnowledge') }}</NuxtLink>
					<NuxtLink :to="localePath('/service')" class="btn btn-ghost">{{ t('home.ctaService') }}</NuxtLink>
					<NuxtLink :to="localePath('/ask')" class="btn btn-ghost">{{ t('home.ctaAsk') }}</NuxtLink>
				</div>
			</div>
		</section>

		<!-- 四个入口卡片 -->
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

		<!-- 最新文章列表 -->
		<section id="latest" class="section">
			<div class="section-head">
				<h2>{{ t('home.latest') }}</h2>
				<span class="section-more">
					<NuxtLink :to="localePath('/archives')">{{ t('home.viewAll') }}</NuxtLink>
				</span>
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

		<!-- 数据指标 -->
		<section class="metrics">
			<div class="metric">
				<strong>{{ blogTotal }}</strong>
				<span>{{ t('home.blogUnit') }}</span>
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
const {t} = useI18n()
const localePath = useLocalePath()

const {data: blogRes} = await useAsyncData('home-blogs', () => call('/blogs?pageNum=1'))
const {data: productRes} = await useAsyncData('home-products', () => call('/products?pageNum=1&pageSize=1'))

const posts = computed(() => (blogRes.value as any)?.data?.list || [])
const blogTotal = computed(() => (blogRes.value as any)?.data?.total || posts.value.length)
const productTotal = computed(() => (productRes.value as any)?.data?.total || 0)

const entries = [
	{key: 'blog', icon: '✍', path: '/archives', nameKey: 'home.entryBlog', descKey: 'home.entryBlogDesc'},
	{key: 'knowledge', icon: '📚', path: '/knowledge', nameKey: 'home.entryKnowledge', descKey: 'home.entryKnowledgeDesc'},
	{key: 'service', icon: '⚙', path: '/service', nameKey: 'home.entryService', descKey: 'home.entryServiceDesc'},
	{key: 'ask', icon: '💬', path: '/ask', nameKey: 'home.entryAsk', descKey: 'home.entryAskDesc'}
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
	padding: 48px 40px;
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
	max-width: 720px;
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
	margin: 0 0 24px;
	color: var(--muted);
	font-size: 15px;
	line-height: 1.8;
	max-width: 560px;
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

/* ---------------- 入口卡片 ---------------- */
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

.section-more a {
	color: var(--teal);
	font-size: 13px;
	font-weight: 600;
}

.section-more a:hover {
	text-decoration: underline;
}

/* ---------------- 指标 ---------------- */
.metrics {
	display: flex;
	gap: 24px;
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
@media (max-width: 820px) {
	.entries {
		grid-template-columns: repeat(2, 1fr);
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
}
</style>
