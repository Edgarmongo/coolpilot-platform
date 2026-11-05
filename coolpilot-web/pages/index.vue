<template>
	<div>
		<section class="hero card">
			<div class="hero-main">
				<span class="tag">{{ t('home.tag') }}</span>
				<h1>{{ t('home.title') }}</h1>
				<p>{{ t('home.desc') }}</p>
				<div class="hero-actions">
					<NuxtLink :to="localePath('/knowledge')" class="btn">{{ t('home.ctaKnowledge') }}</NuxtLink>
					<a class="btn btn-ghost" href="#latest">{{ t('home.ctaLatest') }}</a>
				</div>
			</div>
			<div class="hero-side">
				<div class="metric">
					<strong>{{ blogTotal }}</strong>
					<span>{{ t('home.blogUnit') }}</span>
				</div>
				<div class="metric">
					<strong>{{ productTotal }}</strong>
					<span>{{ t('home.productUnit') }}</span>
				</div>
			</div>
		</section>

		<section id="latest" class="section">
			<div class="section-head">
				<h2>{{ t('home.latest') }}</h2>
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
</script>

<style scoped>
.hero {
	display: flex;
	gap: 36px;
	align-items: center;
	justify-content: space-between;
	flex-wrap: wrap;
	padding: 32px 30px;
	background: linear-gradient(120deg, #ffffff 0%, #f2faf8 100%);
}

.hero-main {
	flex: 1;
	min-width: 260px;
}

.hero h1 {
	margin: 12px 0 10px;
	font-size: 28px;
	line-height: 1.35;
	letter-spacing: -.4px;
}

.hero p {
	margin: 0 0 18px;
	color: var(--muted);
	font-size: 14px;
}

.hero-actions {
	display: flex;
	gap: 10px;
}

.hero-side {
	display: flex;
	gap: 28px;
}

.metric {
	text-align: center;
}

.metric strong {
	display: block;
	font-size: 24px;
	color: var(--teal);
}

.metric span {
	font-size: 12.5px;
	color: var(--muted);
}

.section {
	margin-top: 22px;
}

.section-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	margin-bottom: 10px;
}

.section-head h2 {
	margin: 0;
	font-size: 17px;
}
</style>
