export default defineNuxtConfig({
	compatibilityDate: '2025-01-01',
	devtools: {enabled: false},
	typescript: {strict: false},
	css: ['~/assets/css/main.css'],
	modules: ['@nuxtjs/i18n'],
	/*
	 * 中文在 /，英文在 /en/（prefix_except_default）。
	 * 不做浏览器语言自动跳转：同一篇文章的中英地址必须稳定可分享，
	 * 自动 302 会让搜索引擎与用户看到不同 URL，也容易在分享链接上产生歧义。
	 */
	i18n: {
		restructureDir: 'i18n',
		langDir: 'locales',
		defaultLocale: 'zh-CN',
		strategy: 'prefix_except_default',
		detectBrowserLanguage: false,
		locales: [
			{code: 'zh-CN', language: 'zh-CN', name: '简体中文', file: 'zh-CN.json'},
			{code: 'en', language: 'en-US', name: 'English', file: 'en.json'}
		]
	},
	runtimeConfig: {
		// 仅服务端可见：Nitro 代理用它拼后端地址
		apiBase: process.env.API_BASE || 'http://localhost:8091',
		public: {
			// 站点对外地址，用于 sitemap / robots 里的绝对链接
			siteUrl: process.env.SITE_URL || 'http://localhost:8081'
		}
	},
	app: {
		head: {
			title: 'CoolSkyAI 技术平台',
			htmlAttrs: {lang: 'zh-CN'},
			meta: [
				{name: 'viewport', content: 'width=device-width, initial-scale=1'}
			]
		}
	}
})
