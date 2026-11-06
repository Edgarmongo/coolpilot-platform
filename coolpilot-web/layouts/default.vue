<template>
	<div class="shell">
		<header class="header">
			<!-- 第一行：Logo + 搜索 + 语言 + 登录 -->
			<div class="topbar">
				<div class="container topbar-inner">
					<NuxtLink :to="localePath('/')" class="brand">
						<span v-if="brand.brandMark" class="brand-mark">{{ brand.brandMark }}</span>
						<span class="brand-text">{{ brand.brandName }}</span>
					</NuxtLink>

					<form class="search" :action="localePath('/search')" @submit.prevent="doSearch">
						<input v-model="q" class="search-input" type="search"
						       :placeholder="t('header.searchPlaceholder')"/>
						<button type="submit" class="search-btn" :aria-label="t('header.search')">
							<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
							     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<circle cx="11" cy="11" r="7"/>
								<path d="m21 21-4.3-4.3"/>
							</svg>
						</button>
					</form>

					<div class="topbar-right">
						<div class="langs">
							<NuxtLink v-for="item in langOptions" :key="item.code" :to="switchLocalePath(item.code)"
							          class="lang-item" :class="{active: locale === item.code}">
								{{ item.short }}
							</NuxtLink>
						</div>
						<NuxtLink :to="localePath('/me')" class="topbar-my">{{ t('header.my') }}</NuxtLink>
						<template v-if="isLogin">
							<span class="who">{{ user && (user.nickname || user.username) }}</span>
							<button class="link" @click="logout">{{ t('header.logout') }}</button>
						</template>
						<NuxtLink v-else :to="localePath('/login')" class="topbar-login">
							<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor"
							     stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
								<circle cx="12" cy="8" r="4"/>
								<path d="M4 21c0-4 3.6-6 8-6s8 2 8 6"/>
							</svg>
							{{ t('header.login') }}
						</NuxtLink>
					</div>
				</div>
			</div>

			<!-- 第二行：横向多分类菜单 -->
			<nav class="nav">
				<div class="container nav-inner">
					<template v-for="item in navItems" :key="item.id || item.path">
						<a v-if="isExternal(item.path)" :href="item.path" class="nav-item"
						   :target="item.target || '_self'" rel="noopener noreferrer">{{ item.label }}</a>
						<NuxtLink v-else :to="localePath(item.path)" class="nav-item" active-class="is-active"
						          :target="item.target === '_blank' ? '_blank' : undefined">{{ item.label }}</NuxtLink>
					</template>
				</div>
			</nav>
		</header>

		<main class="main">
			<div class="container">
				<slot/>
			</div>
		</main>

		<footer class="footer">
			<div class="container">
				<nav class="footer-links">
					<NuxtLink :to="localePath('/planet')">{{ t('footer.planet') }}</NuxtLink>
					<NuxtLink :to="localePath('/follow')">{{ t('footer.follow') }}</NuxtLink>
					<NuxtLink :to="localePath('/about')">{{ t('footer.about') }}</NuxtLink>
					<NuxtLink :to="localePath('/friends')">{{ t('footer.friends') }}</NuxtLink>
				</nav>
				<div class="footer-info">
					<span v-if="brand.footerText">{{ brand.footerText }}</span>
					<span v-if="brand.footerText && brand.footerTagline" class="dot">·</span>
					<span v-if="brand.footerTagline">{{ brand.footerTagline }}</span>
					<span v-if="brand.beian && (brand.footerText || brand.footerTagline)" class="dot">·</span>
					<a v-if="brand.beian" class="beian" href="https://beian.miit.gov.cn/" target="_blank"
					   rel="noopener noreferrer">{{ brand.beian }}</a>
				</div>
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

// 顶栏搜索：跳转到搜索页并带上关键词
const q = ref('')
function doSearch() {
	router.push({path: localePath('/search'), query: {q: q.value || undefined}})
}

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
	background: linear-gradient(180deg, var(--nav-bg) 0%, var(--nav-bg-deep) 100%);
	box-shadow: 0 1px 6px rgba(0, 0, 0, .12);
}

/* ---------------- 第一行 ---------------- */
.topbar-inner {
	display: flex;
	align-items: center;
	gap: 18px;
	height: 58px;
}

.brand {
	display: flex;
	align-items: center;
	gap: 10px;
	flex-shrink: 0;
}

.brand-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 32px;
	height: 32px;
	border-radius: 8px;
	background: rgba(255, 255, 255, .14);
	color: #fff;
	font-weight: 700;
	font-size: 13px;
	overflow: hidden;
	white-space: nowrap;
}

.brand-text {
	font-size: 17px;
	font-weight: 700;
	letter-spacing: .3px;
	color: #fff;
}

.search {
	display: flex;
	align-items: center;
	flex: 1;
	max-width: 340px;
	height: 34px;
	border-radius: 8px;
	background: rgba(0, 0, 0, .18);
	overflow: hidden;
}

.search-input {
	flex: 1;
	min-width: 0;
	height: 100%;
	padding: 0 12px;
	border: none;
	background: transparent;
	color: #fff;
	font-size: 13.5px;
	outline: none;
}

.search-input::placeholder {
	color: rgba(255, 255, 255, .6);
}

.search-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 34px;
	height: 100%;
	border: none;
	background: transparent;
	color: rgba(255, 255, 255, .8);
	cursor: pointer;
}

.search-btn:hover {
	color: #fff;
}

.topbar-right {
	display: flex;
	align-items: center;
	gap: 12px;
	margin-left: auto;
	flex-shrink: 0;
}

.langs {
	display: flex;
	align-items: center;
	gap: 4px;
}

.lang-item {
	padding: 3px 7px;
	border-radius: 6px;
	font-size: 12.5px;
	color: rgba(255, 255, 255, .75);
}

.lang-item:hover {
	color: #fff;
}

.lang-item.active {
	color: #fff;
	font-weight: 600;
	background: rgba(255, 255, 255, .16);
}

.topbar-my {
	font-size: 13.5px;
	color: rgba(255, 255, 255, .85);
}

.topbar-my:hover {
	color: #fff;
}

.topbar-login {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 6px 14px;
	border-radius: 8px;
	background: #fff;
	color: var(--teal);
	font-size: 13.5px;
	font-weight: 600;
}

.topbar-login:hover {
	background: #eafff5;
}

.who {
	font-size: 13px;
	color: rgba(255, 255, 255, .85);
}

.link {
	border: none;
	background: none;
	color: rgba(255, 255, 255, .8);
	font-size: 13px;
	cursor: pointer;
}

.link:hover {
	color: #fff;
}

/* ---------------- 第二行：菜单 ---------------- */
.nav {
	background: rgba(0, 0, 0, .08);
	border-top: 1px solid rgba(255, 255, 255, .08);
}

.nav-inner {
	display: flex;
	align-items: center;
	gap: 2px;
	height: 44px;
	overflow-x: auto;
	scrollbar-width: none;
}

.nav-inner::-webkit-scrollbar {
	display: none;
}

.nav-item {
	padding: 6px 13px;
	border-radius: 7px;
	font-size: 13.5px;
	color: rgba(255, 255, 255, .85);
	white-space: nowrap;
}

.nav-item:hover {
	background: rgba(255, 255, 255, .14);
	color: #fff;
}

.nav-item.is-active {
	color: #fff;
	font-weight: 600;
	background: rgba(255, 255, 255, .18);
}

.main {
	flex: 1;
	padding: 26px 0 40px;
}

.footer {
	border-top: 1px solid var(--line);
	padding: 22px 0 20px;
	color: var(--muted);
	font-size: 12.5px;
	text-align: center;
}

.footer-links {
	display: flex;
	justify-content: center;
	flex-wrap: wrap;
	gap: 22px;
	margin-bottom: 12px;
}

.footer-links a {
	color: var(--muted);
	font-size: 13px;
}

.footer-links a:hover {
	color: var(--teal);
}

.footer-info {
	display: flex;
	justify-content: center;
	align-items: center;
	flex-wrap: wrap;
	gap: 4px;
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

/* 移动端：顶栏换行，搜索框占满，菜单横向可滚动 */
@media (max-width: 720px) {
	.topbar-inner {
		flex-wrap: wrap;
		height: auto;
		padding: 10px 0;
		gap: 10px;
	}

	.search {
		order: 3;
		flex-basis: 100%;
		max-width: none;
	}

	.nav-inner {
		height: 42px;
	}
}
</style>
