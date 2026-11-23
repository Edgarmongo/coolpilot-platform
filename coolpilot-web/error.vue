<template>
	<div class="err">
		<div class="err-inner">
			<p class="err-code">{{ code }}</p>
			<h1 class="err-title">{{ copy.title }}</h1>
			<p class="err-desc">{{ copy.desc }}</p>

			<form class="err-search" @submit.prevent="goSearch">
				<input v-model="keyword" type="search" :placeholder="copy.searchPlaceholder" class="err-input"/>
				<button type="submit" class="err-btn">{{ copy.search }}</button>
			</form>

			<div class="err-links">
				<NuxtLink :to="homePath" class="err-btn err-btn-primary">{{ copy.home }}</NuxtLink>
				<NuxtLink :to="prefix + '/archives'" class="err-link">{{ copy.articles }}</NuxtLink>
				<NuxtLink :to="prefix + '/open-source'" class="err-link">{{ copy.projects }}</NuxtLink>
				<NuxtLink :to="prefix + '/service'" class="err-link">{{ copy.services }}</NuxtLink>
			</div>

			<p class="err-hint">{{ copy.hint }}</p>
		</div>
	</div>
</template>

<script setup lang="ts">
import type { NuxtError } from '#app'

/**
 * 全局错误页。
 *
 * 不用 Nuxt 默认页的原因很实际：默认页标题是「404 - Article not found | Nuxt」，
 * 正文是英文，还带框架品牌名。个人站出现框架水印既不专业，
 * 也白白浪费了一次「访客走错路还能留下来」的机会。
 *
 * 文案直接按语言分支，不去读 i18n 词条：错误页可能在 i18n 准备好之前渲染，
 * 读词条有拿到 key 的风险，这里用字面量最稳。
 */
const props = defineProps<{ error: NuxtError }>()

const {locale} = useI18n()
const isEn = computed(() => locale.value === 'en')
const code = computed(() => props.error?.statusCode || 500)
// /en 前缀单独拿一个变量：直接拿它拼 '/archives' 会变成 '/enarchives'
const prefix = computed(() => (isEn.value ? '/en' : ''))
const homePath = computed(() => (isEn.value ? '/en' : '/'))
const keyword = ref('')

const copy = computed(() => {
	const notFound = code.value === 404
	if (isEn.value) {
		return {
			title: notFound ? 'Page not found' : 'Something went wrong',
			desc: notFound
				? 'The address may have changed, or the article was never published.'
				: 'The server hit an error. Please try again in a moment.',
			search: 'Search',
			searchPlaceholder: 'Search the site…',
			home: 'Back to home',
			articles: 'Articles',
			projects: 'Open source',
			services: 'Services',
			hint: 'If you followed a link from elsewhere, the article may have been renamed.'
		}
	}
	return {
		title: notFound ? '页面不存在' : '服务出了点问题',
		desc: notFound
			? '这个地址可能已经改了，或者这篇文章从未发布过。'
			: '服务端遇到了错误，请稍后再试一次。',
		search: '搜索',
		searchPlaceholder: '搜索站内内容…',
		home: '返回首页',
		articles: '技术文章',
		projects: '开源项目',
		services: '技术服务',
		hint: '如果是别处链过来的，可能是文章改过地址。'
	}
})

function goSearch() {
	const q = keyword.value.trim()
	navigateTo(`${prefix.value}/search${q ? `?q=${encodeURIComponent(q)}` : ''}`)
}

// 错误页只给人和爬虫看个明白，不该被收录
useHead({
	title: computed(() => `${code.value} · ${copy.value.title}`),
	meta: [
		{name: 'robots', content: 'noindex, follow'},
		{name: 'description', content: computed(() => copy.value.desc)}
	],
	htmlAttrs: {lang: computed(() => (isEn.value ? 'en' : 'zh-CN'))}
})
</script>

<style scoped>
.err {
	min-height: 100vh;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40px 24px;
	background: var(--bg, #f7faf9);
}

.err-inner {
	width: 100%;
	max-width: 560px;
	text-align: center;
}

.err-code {
	font-size: 84px;
	font-weight: 800;
	line-height: 1;
	letter-spacing: -3px;
	color: var(--teal, #0f766e);
	opacity: .18;
	margin-bottom: 10px;
}

.err-title {
	font-size: 26px;
	font-weight: 800;
	color: var(--ink, #111827);
	margin-bottom: 12px;
}

.err-desc {
	font-size: 14.5px;
	line-height: 1.75;
	color: var(--muted, #6b7280);
	margin-bottom: 26px;
}

.err-search {
	display: flex;
	gap: 8px;
	margin-bottom: 26px;
}

.err-input {
	flex: 1;
	padding: 11px 14px;
	border: 1px solid var(--line, #e8eded);
	border-radius: 9px;
	font-size: 14px;
	background: #fff;
	outline: none;
}

.err-input:focus {
	border-color: var(--teal, #0f766e);
}

.err-btn {
	padding: 11px 20px;
	border: none;
	border-radius: 9px;
	background: var(--teal, #0f766e);
	color: #fff;
	font-size: 14px;
	cursor: pointer;
	text-decoration: none;
	display: inline-block;
}

.err-btn:hover {
	opacity: .9;
}

.err-links {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	justify-content: center;
	gap: 18px;
	margin-bottom: 22px;
}

.err-btn-primary {
	background: var(--teal, #0f766e);
	color: #fff;
}

.err-link {
	font-size: 13.5px;
	color: var(--muted, #6b7280);
	text-decoration: none;
}

.err-link:hover {
	color: var(--teal, #0f766e);
}

.err-hint {
	font-size: 12.5px;
	color: var(--faint, #9ca3af);
}
</style>
