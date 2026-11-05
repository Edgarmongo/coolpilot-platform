<template>
	<div class="shell">
		<header class="header">
			<div class="container header-inner">
				<NuxtLink to="/" class="brand">
					<span v-if="brand.brandMark" class="brand-mark">{{ brand.brandMark }}</span>
					<span class="brand-text">{{ brand.brandName }}</span>
				</NuxtLink>

				<nav class="nav">
					<template v-for="item in navItems" :key="item.id || item.path">
						<a v-if="isExternal(item.path)" :href="item.path" class="nav-item"
						   :target="item.target || '_self'" rel="noopener noreferrer">{{ item.label }}</a>
						<NuxtLink v-else :to="item.path" class="nav-item" active-class="is-active"
						          :target="item.target === '_blank' ? '_blank' : undefined">{{ item.label }}</NuxtLink>
					</template>
				</nav>

				<div class="header-right">
					<NuxtLink to="/me" class="nav-item">我的</NuxtLink>
					<template v-if="isLogin">
						<span class="who">{{ user && (user.nickname || user.username) }}</span>
						<button class="link" @click="logout">退出</button>
					</template>
					<NuxtLink v-else to="/login" class="btn btn-sm">登录</NuxtLink>
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

interface NavItem {
	id?: number
	label: string
	path: string
	target?: string
}

/**
 * 兜底菜单：只在「接口拿不到」时使用（后端重启、网络抖动、迁移没跑）。
 * 配置化之后导航仍不能因为一次请求失败就白掉 —— 导航消失比菜单文字旧更伤。
 */
const FALLBACK_NAV: NavItem[] = [
	{label: '首页', path: '/'},
	{label: '归档', path: '/archives'},
	{label: '专题', path: '/topic'},
	{label: '动态', path: '/moments'},
	{label: '友链', path: '/friends'},
	{label: '关于', path: '/about'},
	{label: '知识付费', path: '/knowledge'},
	{label: '技术服务', path: '/service'},
	{label: '问答', path: '/ask'},
	{label: '搜索', path: '/search'}
]

// SSR 首屏就要有菜单，所以 await；后端对 /nav 做了 Redis 缓存，这次请求很轻
const {data: navMenu} = await useAsyncData('nav-menu', async () => {
	const res: any = await call('/nav').catch(() => null)
	// code !== 200 时返回 null，交给下面的 computed 走兜底；
	// 返回空数组是站长的真实配置（把菜单全隐藏了），此时不兜底
	return res && res.code === 200 && Array.isArray(res.data) ? (res.data as NavItem[]) : null
})

const navItems = computed<NavItem[]>(() => Array.isArray(navMenu.value) ? navMenu.value! : FALLBACK_NAV)

function isExternal(path: string) {
	return /^https?:\/\//i.test(path || '')
}

async function logout() {
	// 先让服务端吊销 token（递增版本号），失败也要清本地，不能卡住用户
	await call('/me/logout', {method: 'POST'}).catch(() => null)
	clearSession()
	router.push('/')
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
