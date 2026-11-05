<template>
	<NuxtLayout>
		<NuxtPage/>
	</NuxtLayout>
</template>

<script setup lang="ts">
const brand = useBrand()
const {t, locale} = useI18n()

/**
 * 页面标题统一在这里拼品牌后缀。
 *
 * 页面里只写「归档」「搜索」这样的短标题，语言切换时自动跟着变；
 * 若某个页面仍按老写法带了「 - CoolSkyAI」后缀，先剥掉再拼，避免出现两个品牌名。
 */
useHead({
	// 语言切换时 <html lang> 跟着变，读屏软件与搜索引擎都靠它判断页面语言
	htmlAttrs: {lang: computed(() => locale.value)},
	meta: [
		{name: 'description', content: computed(() => t('site.description'))}
	],
	titleTemplate: (title?: string) => {
		const name = brand.value.brandName
		if (!title) {
			return name
		}
		const clean = title.replace(/ - CoolSkyAI$/, '')
		return clean === name ? name : `${clean} - ${name}`
	}
})
</script>
