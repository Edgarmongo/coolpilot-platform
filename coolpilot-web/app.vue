<template>
	<NuxtLayout>
		<NuxtPage/>
	</NuxtLayout>
</template>

<script setup lang="ts">
const brand = useBrand()
const {t, locale} = useI18n()
const route = useRoute()
const config = useRuntimeConfig()

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
		{name: 'description', content: computed(() => t('site.description'))},
		// 分享到微信/社群时的卡片信息，页面级可以覆盖
		{property: 'og:site_name', content: computed(() => brand.value.brandName)},
		{property: 'og:type', content: 'website'},
		{property: 'og:title', content: computed(() => brand.value.brandName)},
		{property: 'og:description', content: computed(() => t('site.description'))},
		{name: 'twitter:card', content: 'summary'}
	],
	link: [
		/**
		 * canonical 指向当前页自己的规范地址。
		 *
		 * 双语站每个语言版本各有自己的地址，自指是对的；这样即使带 ?q= 、
		 * 带尾斜杠之类的变体被抓到，权重也不会分散到多个 URL 上。
		 * 英文站冻结在现状，但这条对中文站同样生效，不算新增投入。
		 */
		{
			rel: 'canonical',
			href: computed(() => {
				const base = String(config.public.siteUrl || '').replace(/\/$/, '')
				const path = route.path === '/' ? '/' : route.path.replace(/\/$/, '')
				return base + path
			})
		}
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
