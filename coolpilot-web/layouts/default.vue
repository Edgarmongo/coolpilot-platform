<template>
	<div class="shell">
		<header class="header">
			<div class="container header-inner">
				<NuxtLink to="/" class="brand">
					<span class="brand-mark">CP</span>
					<span class="brand-text">CoolSkyAI</span>
				</NuxtLink>

				<nav class="nav">
					<NuxtLink to="/" class="nav-item" active-class="is-active">首页</NuxtLink>
					<NuxtLink to="/archives" class="nav-item" active-class="is-active">归档</NuxtLink>
					<NuxtLink to="/moments" class="nav-item" active-class="is-active">动态</NuxtLink>
					<NuxtLink to="/friends" class="nav-item" active-class="is-active">友链</NuxtLink>
					<NuxtLink to="/about" class="nav-item" active-class="is-active">关于</NuxtLink>
					<NuxtLink to="/knowledge" class="nav-item" active-class="is-active">知识付费</NuxtLink>
					<NuxtLink to="/service" class="nav-item" active-class="is-active">技术服务</NuxtLink>
					<NuxtLink to="/search" class="nav-item" active-class="is-active">搜索</NuxtLink>
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
				<span>CoolSkyAI 技术平台</span>
				<span class="dot">·</span>
				<span>技术知识与服务</span>
			</div>
		</footer>
	</div>
</template>

<script setup lang="ts">
const router = useRouter()
const {isLogin, user, clearSession} = useApi()

function logout() {
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
</style>
