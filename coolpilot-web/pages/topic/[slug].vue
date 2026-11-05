<template>
	<div v-if="topic">
		<NuxtLink :to="localePath('/topic')" class="back">{{ t('topicDetail.back') }}</NuxtLink>

		<div class="head card">
			<h1 class="page-title">{{ topic.title }}</h1>
			<p class="page-desc">{{ topic.description || t('topicDetail.desc') }}</p>
			<div class="meta">
				<span>{{ t('topicDetail.count', {n: articles.length}) }}</span>
				<span>{{ t('topicDetail.updated', {date: formatDate(topic.updateTime)}) }}</span>
			</div>
		</div>

		<div v-if="!articles.length" class="card empty">{{ t('topicDetail.empty') }}</div>

		<ol v-else class="list">
			<li v-for="(post, index) in articles" :key="post.id" class="item">
				<NuxtLink :to="localePath(`/blog/${post.slug || post.id}`)" class="item-link">
					<span class="idx">{{ index + 1 }}</span>
					<div class="item-body">
						<h2 class="item-title">{{ post.title }}</h2>
						<p v-if="!post.privacy && post.description" class="item-desc">{{ post.description }}</p>
						<div class="item-meta">
							<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
							<span v-if="post.readTime">· {{ t('topicDetail.minutes', {n: post.readTime}) }}</span>
							<span v-if="post.views">· {{ t('topicDetail.reads', {n: post.views}) }}</span>
							<span v-if="post.privacy" class="tag lock">{{ t('topicDetail.locked') }}</span>
						</div>
					</div>
				</NuxtLink>
			</li>
		</ol>
	</div>

	<div v-else class="card empty">
		<p>{{ t('topicDetail.notFound') }}</p>
		<NuxtLink :to="localePath('/topic')" class="btn">{{ t('topicDetail.browseOthers') }}</NuxtLink>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()
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
	title: topic.value ? `${topic.value.title} - ${t('topicDetail.head')}` : t('topicDetail.head'),
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
