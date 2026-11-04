<template>
	<div>
		<section class="hero card">
			<div class="hero-main">
				<span class="tag">CoolSkyAI 技术平台</span>
				<h1>把技术写成可以交付的东西</h1>
				<p>文章、课程、文档与代码资源，一套账号打通阅读、购买与下载。</p>
				<div class="hero-actions">
					<NuxtLink to="/knowledge" class="btn">看看知识付费</NuxtLink>
					<a class="btn btn-ghost" href="#latest">最新文章</a>
				</div>
			</div>
			<div class="hero-side">
				<div class="metric">
					<strong>{{ blogTotal }}</strong>
					<span>篇文章</span>
				</div>
				<div class="metric">
					<strong>{{ productTotal }}</strong>
					<span>个知识商品</span>
				</div>
			</div>
		</section>

		<section id="latest" class="section">
			<div class="section-head">
				<h2>最新文章</h2>
			</div>
			<div class="card">
				<div v-if="!posts.length" class="empty">还没有文章</div>
				<NuxtLink v-for="post in posts" :key="post.id" :to="`/blog/${post.slug || post.id}`" class="post-item">
					<h3 class="post-title">{{ post.title }}</h3>
					<div class="post-meta">
						<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
						<span v-if="post.category">· {{ post.category.name }}</span>
						<span>· {{ post.views || 0 }} 次阅读</span>
					</div>
				</NuxtLink>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()

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
