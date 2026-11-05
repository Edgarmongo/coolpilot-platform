<template>
	<div class="shell">
		<header class="header">
			<div class="container header-inner">
				<NuxtLink :to="localePath('/')" class="brand">
					<span v-if="brand.brandMark" class="brand-mark">{{ brand.brandMark }}</span>
					<span class="brand-text">{{ brand.brandName }}</span>
				</NuxtLink>

				<nav class="nav">
					<template v-for="item in navItems" :key="item.id || item.path">
						<a v-if="isExternal(item.path)" :href="item.path" class="nav-item"
						   :target="item.target || '_self'" rel="noopener noreferrer">{{ item.label }}</a>
						<NuxtLink v-else :to="localePath(item.path)" class="nav-item" active-class="is-active"
						          :target="item.target === '_blank' ? '_blank' : undefined">{{ item.label }}</NuxtLink>
					</template>
				</nav>

				<div class="header-right">
					<div class="langs">
						<NuxtLink v-for="item in langOptions" :key="item.code" :to="switchLocalePath(item.code)"
						          class="lang-item" :class="{active: locale === item.code}">
							{{ item.short }}
						</NuxtLink>
					</div>
					<NuxtLink :to="localePath('/me')" class="nav-item">{{ t('header.my') }}</NuxtLink>
					<template v-if="isLogin">
						<span class="who">{{ user && (user.nickname || user.username) }}</span>
						<button class="link" @click="logout">{{ t('header.logout') }}</button>
					</template>
					<NuxtLink v-else :to="localePath('/login')" class="btn btn-sm">{{ t('header.login') }}</NuxtLink>
				</div>
			</div>
		</header>

		<main class="main">
			<div class="container">
				<slot/>
			</div>
		</main>

		<footer class="footer">
			<div class="container">
				<span v-if="brand.footerText">{{ brand.footerText }}</span>
				<span v-if="brand.footerText && brand.footerTagline" class="dot">·</span>
				<span v-if="brand.footerTagline">{{ brand.footerTagline }}</span>
				<span v-if="brand.beian && (brand.footerText || brand.footerTagline)" class="dot">·</span>
				<a v-if="brand.beian" class="beian" href="https://beian.miit.gov.cn/" target="_blank"
				   rel="noopener noreferrer">{{ brand.beian }}</a>
			</div>
		</footer>
	</div>
</template>

<script setup lang="ts">
const router = useRouter()
const {call, isLogin, user, clearSession} = useApi()
// 品牌名与页脚文案来自站点设置，接口失败时由 composable 回落到改造前的默认文案
const brand = useBrand()

const {t, locale} = useI18n()
const localePath = useLocalePath()
const switchLocalePath = useSwitchLocalePath()

const langOptions = [
	{code: 'zh-CN', short: '中'},
	{code: 'en', short: 'EN'}
]

interface NavItem {
	id?: number
	label: string
	path: string
	target?: string
}

/**
 * 兜底菜单：只在「接口拿不到」时使用（后端重启、网络抖动、迁移没跑）。
 * 配置化之后导航仍不能因为一次请求失败就白掉 —— 导航消失比菜单文字旧更伤。
 * 注意这里存的是 i18n key，渲染时再翻译，否则兜底菜单永远是中文。
 */
const FALLBACK_NAV: { key: string, path: string }[] = [
	{key: 'nav.home', path: '/'},
	{key: 'nav.archives', path: '/archives'},
	{key: 'nav.topics', path: '/topic'},
	{key: 'nav.moments', path: '/moments'},
	{key: 'nav.friends', path: '/friends'},
	{key: 'nav.about', path: '/about'},
	{key: 'nav.knowledge', path: '/knowledge'},
	{key: 'nav.service', path: '/service'},
	{key: 'nav.ask', path: '/ask'},
	{key: 'nav.search', path: '/search'}
]

// 导航是站长配置的内容，会随语言不同返回不同文字；locale 变化要重新取
const {data: navMenu} = await useAsyncData('nav-menu', async () => {
	const res: any = await call(`/nav?lang=${locale.value}`).catch(() => null)
	// code !== 200 时返回 null，交给下面的 computed 走兜底；
	// 返回空数组是站长的真实配置（把菜单全隐藏了），此时不兜底
	return res && res.code === 200 && Array.isArray(res.data) ? (res.data as NavItem[]) : null
}, {watch: [locale]})

const navItems = computed<NavItem[]>(() => {
	if (Array.isArray(navMenu.value)) {
		return navMenu.value!
	}
	return FALLBACK_NAV.map(item => ({label: t(item.key), path: item.path}))
})

function isExternal(path: string) {
	return /^https?:\/\//i.test(path || '')
}

async function logout() {
	// 先让服务端吊销 token（递增版本号），失败也要清本地，不能卡住用户
	await call('/me/logout', {method: 'POST'}).catch(() => null)
	clearSession()
	router.push(localePath('/'))
}
</script>

<style scoped>
.shell {
	display: flex;
	flex-direction: column;
	min-height: 100vh;
}

.header {
	position: sticky;
	top: 0;
	z-index: 20;
	background: rgba(255, 255, 255, .92);
	border-bottom: 1px solid var(--line);
	backdrop-filter: blur(8px);
}

.header-inner {
	display: flex;
	align-items: center;
	gap: 28px;
	height: 62px;
}

.brand {
	display: flex;
	align-items: center;
	gap: 10px;
}

.brand-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 30px;
	height: 30px;
	border-radius: 8px;
	background: var(--teal);
	color: #fff;
	font-weight: 700;
	font-size: 13px;
	/* 标记现在可配置，超过两个字时裁切，避免撑破方块 */
	overflow: hidden;
	white-space: nowrap;
}

.brand-text {
	font-size: 16.5px;
	font-weight: 700;
	letter-spacing: .3px;
}

.nav {
	display: flex;
	gap: 4px;
	flex: 1;
}

.nav-item {
	padding: 6px 12px;
	border-radius: 7px;
	font-size: 14px;
	color: var(--muted);
}

.nav-item:hover {
	background: #f2f7f6;
	color: var(--teal);
}

.nav-item.is-active {
	color: var(--teal);
	font-weight: 600;
	background: #eef7f5;
}

.header-right {
	display: flex;
	align-items: center;
	gap: 12px;
}

.langs {
	display: flex;
	align-items: center;
	gap: 2px;
}

.lang-item {
	padding: 3px 7px;
	border-radius: 6px;
	font-size: 12.5px;
	color: var(--muted);
}

.lang-item:hover {
	color: var(--teal);
}

.lang-item.active {
	color: var(--teal);
	font-weight: 600;
	background: #eef7f5;
}

.who {
	font-size: 13px;
	color: var(--muted);
}

.link {
	border: none;
	background: none;
	color: var(--muted);
	font-size: 13px;
	cursor: pointer;
}

.link:hover {
	color: var(--teal);
}

.btn-sm {
	padding: 5px 14px;
	font-size: 13px;
}

.main {
	flex: 1;
	padding: 26px 0 40px;
}

.footer {
	border-top: 1px solid var(--line);
	padding: 18px 0;
	color: var(--muted);
	font-size: 12.5px;
	text-align: center;
}

.dot {
	margin: 0 6px;
}

.beian {
	color: var(--muted);
	text-decoration: none;
}

.beian:hover {
	color: var(--teal);
}
</style>
