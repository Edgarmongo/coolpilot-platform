<template>
	<div>
		<h1 class="page-title">{{ about.title || t('about.title') }}</h1>
		<div class="card article" v-html="contentHtml"></div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()

const {locale} = useI18n()
// 关于页标题与正文由站长配置，中英各一份，切语言要重新取
const {data} = await useAsyncData('about', () => call(`/about?lang=${locale.value}`), {watch: [locale]})
const about = computed(() => (data.value as any)?.data || {})

const contentHtml = computed(() =>
	about.value.content || `<p class="muted">${t('about.empty')}</p>`
)

useHead({title: t('about.headTitle')})
</script>

<style scoped>
.article {
	min-height: 220px;
}
</style>
