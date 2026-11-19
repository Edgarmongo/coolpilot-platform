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
						   :class="{'is-active': isActive(item.path)}"
						   :target="item.target || '_self'" rel="noopener noreferrer">{{ item.label }}</a>
						<NuxtLink v-else :to="localePath(item.path)" class="nav-item"
						          :class="{'is-active': isActive(item.path)}"
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
				<div class="foot-top">
					<div>
						<NuxtLink :to="localePath('/')" class="foot-brand">
							<span v-if="brand.brandMark" class="brand-mark">{{ brand.brandMark }}</span>
							<span>{{ brand.brandName }}</span>
						</NuxtLink>
						<p v-if="brand.footerText || brand.footerTagline" class="foot-desc">
							{{ [brand.footerText, brand.footerTagline].filter(Boolean).join(' · ') }}
						</p>
					</div>
					<nav class="foot-links">
						<NuxtLink :to="localePath('/planet')">{{ t('footer.planet') }}</NuxtLink>
						<NuxtLink :to="localePath('/follow')">{{ t('footer.follow') }}</NuxtLink>
						<NuxtLink :to="localePath('/about')">{{ t('footer.about') }}</NuxtLink>
						<NuxtLink :to="localePath('/friends')">{{ t('footer.friends') }}</NuxtLink>
					</nav>
				</div>
				<div class="foot-bottom">
					<span>© {{ year }} {{ brand.brandName }}</span>
					<a v-if="brand.beian" class="beian" href="https://beian.miit.gov.cn/" target="_blank"
					   rel="noopener noreferrer">{{ brand.beian }}</a>
					<span class="foot-links-sm">
						<NuxtLink :to="localePath('/archives')">{{ t('nav.archives') }}</NuxtLink>
						<NuxtLink :to="localePath('/topic')">{{ t('nav.topics') }}</NuxtLink>
						<NuxtLink :to="localePath('/knowledge')">{{ t('nav.knowledge') }}</NuxtLink>
					</span>
				</div>
			</div>
		</footer>
	</div>
</template>

<script setup lang="ts">
const router = useRouter()
const route = useRoute()
const {call, isLogin, user, clearSession} = useApi()
// 品牌名与页脚文案来自站点设置，接口失败时由 composable 回落到改造前的默认文案
const brand = useBrand()

const {t, locale} = useI18n()
const localePath = useLocalePath()
const switchLocalePath = useSwitchLocalePath()

const year = new Date().getFullYear()

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

/**
 * 选中态要自己算，不能用 NuxtLink 的 active-class：
 * 默认的 router-link-active 是前缀匹配，首页 "/" 会在所有页面都亮起来。
 */
function isActive(path: string) {
	if (isExternal(path)) {
		return false
	}
	const current = route.path.replace(/\/$/, '') || '/'
	const target = (localePath(path) || '/').replace(/\/$/, '') || '/'
	return current === target || (target !== '/' && current.startsWith(target + '/'))
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
	background: #fff;
}

/* ============================================================
   头部：白底双行，滚动时吸顶
   ============================================================ */
.header {
	position: sticky;
	top: 0;
	z-index: 30;
	background: #fff;
	border-bottom: 1px solid var(--line);
}

.topbar-inner {
	display: flex;
	align-items: center;
	gap: 24px;
	height: 64px;
}

.brand {
	display: flex;
	align-items: center;
	gap: 10px;
	flex-shrink: 0;
	font-size: 18px;
	font-weight: 700;
	letter-spacing: -.2px;
	color: var(--ink);
}

.brand-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 32px;
	height: 32px;
	border-radius: 9px;
	background: var(--teal);
	color: #fff;
	font-size: 13px;
	font-weight: 700;
	letter-spacing: .5px;
	overflow: hidden;
	white-space: nowrap;
}

.brand-text {
	white-space: nowrap;
}

/* 搜索：浅青底胶囊，右侧对齐 */
.search {
	display: flex;
	align-items: center;
	flex: 1;
	max-width: 400px;
	height: 38px;
	margin-left: auto;
	border: 1px solid var(--line);
	border-radius: 999px;
	background: var(--teal-050);
	overflow: hidden;
	transition: border-color .15s, background .15s;
}

.search:focus-within {
	border-color: var(--line-2);
	background: #fff;
}

.search-input {
	flex: 1;
	min-width: 0;
	height: 100%;
	padding: 0 16px;
	border: none;
	background: transparent;
	color: var(--ink);
	font-size: 13.5px;
	outline: none;
}

.search-input::placeholder {
	color: var(--faint);
}

.search-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 44px;
	height: 100%;
	border: none;
	background: transparent;
	color: var(--teal);
	cursor: pointer;
}

.search-btn:hover {
	color: var(--teal-dark);
}

.topbar-right {
	display: flex;
	align-items: center;
	gap: 16px;
	margin-left: auto;
	flex-shrink: 0;
	font-size: 13.5px;
	color: var(--ink-2);
}

.langs {
	display: flex;
	align-items: center;
	gap: 8px;
	color: var(--muted);
}

.lang-item {
	font-size: 13px;
	color: var(--muted);
}

.lang-item:hover {
	color: var(--teal);
}

.lang-item.active {
	color: var(--teal);
	font-weight: 600;
}

.topbar-my {
	color: var(--ink-2);
}

.topbar-my:hover {
	color: var(--teal);
}

.topbar-login {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	color: var(--teal);
	font-weight: 600;
}

.topbar-login:hover {
	color: var(--teal-dark);
}

.who {
	font-size: 13px;
	color: var(--muted);
	max-width: 120px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.link {
	border: none;
	background: none;
	padding: 0;
	color: var(--muted);
	font-size: 13px;
	cursor: pointer;
}

.link:hover {
	color: var(--teal);
}

/* ============================================================
   第二行：菜单（选中态用下划线，不用色块）
   ============================================================ */
.nav-inner {
	display: flex;
	align-items: center;
	gap: 30px;
	height: 52px;
	overflow-x: auto;
	scrollbar-width: none;
}

.nav-inner::-webkit-scrollbar {
	display: none;
}

.nav-item {
	position: relative;
	padding: 15px 0;
	font-size: 14.5px;
	color: var(--ink-2);
	white-space: nowrap;
	transition: color .15s;
}

.nav-item:hover {
	color: var(--teal);
}

.nav-item.is-active {
	color: var(--teal);
	font-weight: 600;
}

.nav-item.is-active::after {
	content: "";
	position: absolute;
	left: 0;
	right: 0;
	bottom: -1px;
	height: 2px;
	border-radius: 2px;
	background: var(--teal);
}

/* ============================================================
   主体
   ============================================================ */
.main {
	flex: 1;
	/* 22px 顶距留给没有自带标题区的页面（关于、服务、动态…），
	   有 .page-head 的页面在里面再补一段，合计约 40px。
	   首页首屏用负 margin 抵消这段距离，让浅色背景贴住头部。 */
	padding: 22px 0 56px;
	/* 页面里的通栏区块用 100vw 拉宽度，这里统一收掉横向溢出。
	   放在 main 上而不是 body 上：body 一旦成为裁剪容器，头部吸顶会失效。 */
	overflow-x: clip;
}

/* ============================================================
   页脚
   ============================================================ */
.footer {
	border-top: 1px solid var(--line);
	padding: 36px 0 40px;
	margin-top: 8px;
}

.foot-top {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 32px;
	flex-wrap: wrap;
}

.foot-brand {
	display: flex;
	align-items: center;
	gap: 10px;
	font-size: 15px;
	font-weight: 700;
	color: var(--ink);
}

.foot-desc {
	margin-top: 10px;
	font-size: 13px;
	color: var(--muted);
}

.foot-links {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 22px;
	font-size: 13.5px;
	color: var(--ink-2);
}

.foot-links a:hover {
	color: var(--teal);
}

.foot-bottom {
	display: flex;
	align-items: center;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 12px;
	margin-top: 26px;
	padding-top: 18px;
	border-top: 1px solid var(--line);
	font-size: 12.5px;
	color: var(--muted);
}

.beian {
	color: var(--muted);
}

.beian:hover {
	color: var(--teal);
}

.foot-links-sm {
	display: flex;
	gap: 16px;
}

.foot-links-sm a {
	color: var(--muted);
}

.foot-links-sm a:hover {
	color: var(--teal);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 860px) {
	.topbar-inner {
		flex-wrap: wrap;
		height: auto;
		padding: 12px 0 10px;
		gap: 10px;
	}

	.search {
		order: 3;
		flex-basis: 100%;
		max-width: none;
		margin-left: 0;
	}

	.topbar-right {
		margin-left: auto;
	}

	.nav-inner {
		gap: 22px;
		height: 48px;
	}
}

@media (max-width: 520px) {
	.langs, .topbar-my {
		display: none;
	}

	.foot-bottom {
		justify-content: flex-start;
	}
}
</style>
