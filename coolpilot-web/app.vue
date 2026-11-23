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
// 用 i18n 自带的能力算「同一页面在另一种语言下的地址」，
// 比自己拼 /en 前缀可靠：带参数、带尾斜杠的路由都能正确映射。
const switchLocalePath = useSwitchLocalePath()

const siteUrl = computed(() => String(config.public.siteUrl || '').replace(/\/$/, ''))
/** 默认分享图：文章没有首图时兜底，避免微信/社群抓到空白卡片 */
const defaultOgImage = computed(() => `${siteUrl.value}/og-default.png`)

/**
 * 规范路径：英文版归到中文原页（理由见下面 canonical 的注释）。
 * 抽成 computed 是因为 canonical 与 og:url 必须一致，各写一遍迟早写岔。
 */
const canonicalPath = computed(() => {
	let path = route.path === '/' ? '/' : route.path.replace(/\/$/, '')
	if (path === '/en') {
		return '/'
	}
	if (path.startsWith('/en/')) {
		return path.slice(3)
	}
	return path
})
const canonicalUrl = computed(() => siteUrl.value + canonicalPath.value)

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
		// 大图卡片的点击率明显高于纯文字卡片；页面级有封面时会覆盖成文章自己的图
		{property: 'og:image', content: defaultOgImage},
		{property: 'og:url', content: canonicalUrl},
		{property: 'og:locale', content: computed(() => (locale.value === 'en' ? 'en_US' : 'zh_CN'))},
		{name: 'twitter:card', content: 'summary_large_image'},
		{name: 'twitter:image', content: defaultOgImage}
	],
	link: [
		/**
		 * canonical：英文版统一指向中文原页。
		 *
		 * 这里不打 hreflang，而是让 /en/** 的 canonical 指向去掉 /en 的中文地址。
		 * 原因是本站只有界面翻译、没有内容翻译：/en/blog/x 和 /blog/x 的正文
		 * 是同一份中文。hreflang 的前提是「同一内容的不同语言版本」，
		 * 而这种「UI 英文 + 正文中文」的页面并不满足，硬标 hreflang="en"
		 * 属于对搜索引擎撒谎。
		 *
		 * canonical 更诚实也更干净：把英文地址的权重并回中文原页，
		 * 避免两套结构完全相同的页面互相稀释。英文站仍可正常访问与分享，
		 * 只是不参与英文搜索竞争 —— 与「英文站冻结、不投入内容」的策略一致。
		 *
		 * 将来真的翻译了正文，把这段换回 hreflang 互指即可（历史上实现过，
		 * 见 git log app.vue 的 v1.2.3 版本）。
		 */
		{
			rel: 'canonical',
			href: canonicalUrl
		},
		// og:url 跟 canonical 保持一致，否则分享抓取会拿到英文地址、
		// 收录信号却指向中文地址，两边打架
		{
			rel: 'alternate',
			hreflang: 'x-default',
			href: computed(() => {
				const p = switchLocalePath('zh-CN' as 'zh-CN')
				return p ? siteUrl.value + p : ''
			})
		}
	],
	/**
	 * 站点级结构化数据。Organization 让搜索引擎知道「酷天AI 是一个实体」，
	 * WebSite + SearchAction 让它知道站内有搜索（可能触发站内搜索框的富结果）。
	 * 文章级的 Article / Breadcrumb 在文章页单独输出。
	 */
	script: [{
		type: 'application/ld+json',
		innerHTML: computed(() => JSON.stringify({
			'@context': 'https://schema.org',
			'@graph': [
				{
					'@type': 'Organization',
					'@id': `${siteUrl.value}/#organization`,
					name: brand.value.brandName,
					alternateName: 'CoolSkyAI',
					url: siteUrl.value,
					logo: {
						'@type': 'ImageObject',
						url: defaultOgImage.value
					}
				},
				{
					'@type': 'WebSite',
					'@id': `${siteUrl.value}/#website`,
					name: brand.value.brandName,
					alternateName: 'CoolSkyAI',
					url: siteUrl.value,
					inLanguage: locale.value === 'en' ? 'en' : 'zh-CN',
					publisher: {'@id': `${siteUrl.value}/#organization`},
					potentialAction: {
						'@type': 'SearchAction',
						target: {
							'@type': 'EntryPoint',
							urlTemplate: `${siteUrl.value}/search?q={search_term_string}`
						},
						'query-input': 'required name=search_term_string'
					}
				}
			]
		}))
	}],
	titleTemplate: (title?: string) => {
		const name = brand.value.brandName
		if (!title) {
			return name
		}
		// 去掉页面可能已经带上的后缀，避免「归档 - 酷天AI - 酷天AI」。
		// 要同时兼容两种情况：品牌名按语言切换（中文酷天AI / 英文CoolSkyAI），
		// 以及历史上写死在页面里的 ' - CoolSkyAI'。所以不能用固定正则。
		let clean = title.trim()
		for (const suffix of [name, 'CoolSkyAI', '酷天AI']) {
			const tail = ` - ${suffix}`
			if (suffix && clean.endsWith(tail)) {
				clean = clean.slice(0, -tail.length).trim()
				break
			}
		}
		return clean === name ? name : `${clean} - ${name}`
	}
})
</script>
