<template>
	<div v-if="topic">
		<NuxtLink to="/topic" class="back">← 全部专题</NuxtLink>

		<div class="head card">
			<h1 class="page-title">{{ topic.title }}</h1>
			<p class="page-desc">{{ topic.description || '按顺序阅读，更容易形成完整认知' }}</p>
			<div class="meta">
				<span>{{ articles.length }} 篇</span>
				<span>· 更新于 {{ formatDate(topic.updateTime) }}</span>
			</div>
		</div>

		<div v-if="!articles.length" class="card empty">这个专题还没有文章</div>

		<ol v-else class="list">
			<li v-for="(post, index) in articles" :key="post.id" class="item">
				<NuxtLink :to="`/blog/${post.slug || post.id}`" class="item-link">
					<span class="idx">{{ index + 1 }}</span>
					<div class="item-body">
						<h2 class="item-title">{{ post.title }}</h2>
						<p v-if="!post.privacy && post.description" class="item-desc">{{ post.description }}</p>
						<div class="item-meta">
							<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
							<span v-if="post.readTime">· {{ post.readTime }} 分钟</span>
							<span v-if="post.views">· {{ post.views }} 次阅读</span>
							<span v-if="post.privacy" class="tag lock">需要密码</span>
						</div>
					</div>
				</NuxtLink>
			</li>
		</ol>
	</div>

	<div v-else class="card empty">
		<p>专题不存在或未发布</p>
		<NuxtLink to="/topic" class="btn">看看其他专题</NuxtLink>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const slug = computed(() => String(route.params.slug))

const {data} = await useAsyncData(`topic-${slug.value}`, () => call(`/topic/${slug.value}`))

const topic = computed<any>(() => {
	const res = data.value as any
	return res && res.code === 200 ? res.data.topic : null
})
const articles = computed<any[]>(() => {
	const res = data.value as any
	return res && res.code === 200 ? (res.data.articles || []) : []
})

useHead(() => ({
	title: topic.value ? `${topic.value.title} - 专题 - CoolSkyAI` : '专题 - CoolSkyAI',
	meta: topic.value && topic.value.description
		? [{name: 'description', content: topic.value.description}]
		: []
}))
</script>

<style scoped>
.back {
	display: inline-block;
	margin-bottom: 12px;
	font-size: 13px;
	color: var(--muted);
}

.back:hover {
	color: var(--teal);
}

.head {
	margin-bottom: 14px;
}

.head .page-desc {
	margin-bottom: 8px;
}

.meta {
	display: flex;
	gap: 6px;
	font-size: 12.5px;
	color: var(--muted);
}

.empty {
	text-align: center;
	color: var(--muted);
}

.empty .btn {
	margin-top: 12px;
}

.list {
	list-style: none;
	margin: 0;
	padding: 0;
	display: flex;
	flex-direction: column;
	gap: 10px;
}

.item {
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
	border-radius: 12px;
	transition: border-color .15s, box-shadow .15s;
}

.item:hover {
	border-color: rgba(15, 118, 110, .3);
	box-shadow: 0 8px 20px rgba(15, 118, 110, .07);
}

.item-link {
	display: flex;
	gap: 14px;
	padding: 14px 18px;
	align-items: flex-start;
}

.idx {
	flex: none;
	width: 24px;
	height: 24px;
	line-height: 24px;
	text-align: center;
	border-radius: 50%;
	background: #eef7f5;
	color: var(--teal);
	font-size: 12.5px;
	font-weight: 700;
	margin-top: 2px;
}

.item-body {
	flex: 1;
	min-width: 0;
}

.item-title {
	margin: 0 0 4px;
	font-size: 16px;
	font-weight: 650;
}

.item:hover .item-title {
	color: var(--teal);
}

.item-desc {
	margin: 0 0 6px;
	font-size: 13px;
	line-height: 1.7;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.item-meta {
	display: flex;
	gap: 6px;
	flex-wrap: wrap;
	align-items: center;
	font-size: 12.5px;
	color: var(--muted);
}

.lock {
	background: #fff4e5;
	color: #b45309;
}
</style>
