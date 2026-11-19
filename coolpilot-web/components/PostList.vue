<template>
	<div class="card list-card">
		<div v-if="!posts.length" class="empty">{{ emptyText || t('common.empty') }}</div>

		<NuxtLink v-for="post in posts" :key="post.id" :to="localePath(`/blog/${post.slug || post.id}`)"
		          class="post-item">
			<div v-if="post.category" class="tags">
				<span class="tag field">{{ post.category.name }}</span>
			</div>
			<h3 class="post-title">{{ post.title }}</h3>
			<p v-if="plain(post.description)" class="desc">{{ plain(post.description) }}</p>
			<div class="post-meta">
				<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
				<span v-if="post.readTime" class="sep">·</span>
				<span v-if="post.readTime">{{ t('home.minutes', {n: post.readTime}) }}</span>
				<span v-if="post.views" class="sep">·</span>
				<span v-if="post.views">{{ t('post.reads', {n: post.views}) }}</span>
			</div>
		</NuxtLink>

		<div class="pager" v-if="totalPage > 1">
			<button :disabled="pageNum <= 1" @click="$emit('change', pageNum - 1)">{{ t('common.prev') }}</button>
			<span class="pg on">{{ pageNum }}</span>
			<span class="pg-page">/ {{ totalPage }}</span>
			<button :disabled="pageNum >= totalPage" @click="$emit('change', pageNum + 1)">{{ t('common.next') }}</button>
		</div>
	</div>
</template>

<script setup lang="ts">
const {t} = useI18n()
const localePath = useLocalePath()
defineProps<{
	posts: any[]
	pageNum: number
	totalPage: number
	emptyText?: string
}>()

defineEmits<{(e: 'change', page: number): void}>()

/** 后台摘要可能是富文本，列表里只显示纯文本 */
function plain(html: string) {
	return (html || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}
</script>

<style scoped>
.desc {
	margin: 0 0 10px;
	color: var(--muted);
	font-size: 13.5px;
	line-height: 1.7;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.tags {
	margin-bottom: 8px;
}

.sep {
	color: var(--line-3);
}

.pg-page {
	color: var(--faint);
	border: 0;
	background: none;
	font-size: 13px;
}
</style>
