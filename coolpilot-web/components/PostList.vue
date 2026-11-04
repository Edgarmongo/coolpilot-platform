<template>
	<div class="card">
		<div v-if="!posts.length" class="empty">{{ emptyText }}</div>
		<NuxtLink v-for="post in posts" :key="post.id" :to="`/blog/${post.slug || post.id}`" class="post-item">
			<h3 class="post-title">{{ post.title }}</h3>
			<p v-if="post.description" class="desc" v-html="post.description"></p>
			<div class="post-meta">
				<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
				<span v-if="post.category">· {{ post.category.name }}</span>
				<span>· {{ post.views || 0 }} 次阅读</span>
			</div>
		</NuxtLink>

		<div class="pager" v-if="totalPage > 1">
			<button :disabled="pageNum <= 1" @click="$emit('change', pageNum - 1)">上一页</button>
			<span>{{ pageNum }} / {{ totalPage }}</span>
			<button :disabled="pageNum >= totalPage" @click="$emit('change', pageNum + 1)">下一页</button>
		</div>
	</div>
</template>

<script setup lang="ts">
defineProps<{
	posts: any[]
	pageNum: number
	totalPage: number
	emptyText?: string
}>()

defineEmits<{(e: 'change', page: number): void}>()
</script>

<style scoped>
.desc {
	margin: 0 0 8px;
	color: var(--muted);
	font-size: 13px;
	line-height: 1.7;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.pager {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 14px;
	margin-top: 18px;
	font-size: 13px;
	color: var(--muted);
}

.pager button {
	padding: 5px 14px;
	border: 1px solid var(--line);
	border-radius: 7px;
	background: #fff;
	cursor: pointer;
	font-size: 13px;
}

.pager button:disabled {
	opacity: .45;
	cursor: not-allowed;
}
</style>
