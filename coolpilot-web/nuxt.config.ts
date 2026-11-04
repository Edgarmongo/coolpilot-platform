export default defineNuxtConfig({
	compatibilityDate: '2025-01-01',
	devtools: {enabled: false},
	typescript: {strict: false},
	css: ['~/assets/css/main.css'],
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
				{name: 'viewport', content: 'width=device-width, initial-scale=1'},
				{name: 'description', content: 'CoolSkyAI 技术平台 —— 个人技术知识与服务'}
			]
		}
	}
})
