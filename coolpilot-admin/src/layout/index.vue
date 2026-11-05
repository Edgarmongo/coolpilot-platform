<template>
	<el-container class="layout">
		<el-aside :width="collapsed ? '64px' : '216px'" class="layout-aside">
			<div class="layout-logo">
				<span class="layout-logo-mark">CP</span>
				<span v-show="!collapsed" class="layout-logo-text">CoolSkyAI</span>
			</div>
			<el-menu :default-active="activeMenu" :collapse="collapsed" router
			         background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#4ec9b0">
				<template v-for="item in menuList" :key="item.path">
					<el-sub-menu v-if="item.children" :index="item.path">
						<template #title>
							<el-icon><component :is="item.icon"/></el-icon>
							<span>{{ t(item.title) }}</span>
						</template>
						<el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
							<el-icon><component :is="child.icon"/></el-icon>
							<span>{{ t(child.title) }}</span>
						</el-menu-item>
					</el-sub-menu>
					<el-menu-item v-else :index="item.path">
						<el-icon><component :is="item.icon"/></el-icon>
						<span>{{ item.title }}</span>
					</el-menu-item>
				</template>
			</el-menu>
		</el-aside>

		<el-container>
			<el-header class="layout-header">
				<div class="layout-header-left">
					<el-icon class="layout-collapse" @click="collapsed = !collapsed">
						<Expand v-if="collapsed"/>
						<Fold v-else/>
					</el-icon>
					<el-breadcrumb separator="/">
						<el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ t(item) }}</el-breadcrumb-item>
					</el-breadcrumb>
				</div>
				<div class="layout-header-right">
					<div class="layout-langs">
						<button v-for="item in LOCALES" :key="item.code" class="layout-lang"
						        :class="{active: locale === item.code}" @click="switchLang(item.code)">
							{{ item.short }}
						</button>
					</div>
					<el-dropdown @command="onCommand">
						<span class="layout-user">
							<el-avatar :size="26" :src="user && user.avatar">{{ (username || 'A').slice(0, 1) }}</el-avatar>
							<span class="layout-user-name">{{ username || t('layout.notLoggedIn') }}</span>
							<el-icon><ArrowDown/></el-icon>
						</span>
						<template #dropdown>
							<el-dropdown-menu>
								<el-dropdown-item command="view" :disabled="!user">{{ t('layout.viewSite') }}</el-dropdown-item>
								<el-dropdown-item command="logout" divided>{{ t('layout.logout') }}</el-dropdown-item>
							</el-dropdown-menu>
						</template>
					</el-dropdown>
				</div>
			</el-header>

			<el-main class="layout-main">
				<router-view v-slot="{Component}">
					<component :is="Component"/>
				</router-view>
			</el-main>
		</el-container>
	</el-container>
</template>

<script setup>

import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {LOCALES, setLocale} from '@/i18n'
import {ArrowDown, Expand, Fold} from '@element-plus/icons-vue'
import {routes} from '@/router'
import {useUserStore} from '@/store/user'
import {logout as logoutApi} from '@/api/account'

const route = useRoute()
const router = useRouter()
const {t, locale} = useI18n()

function switchLang(code) {
	setLocale(code)
}
const userStore = useUserStore()

const collapsed = ref(false)
const user = computed(() => userStore.user)
const username = computed(() => userStore.username)

/**
 * 由路由表生成菜单：顶层没有子项时直接渲染，有子项时折叠成二级菜单。
 * 新增模块只需要在 router 里加路由，菜单会自动出现。
 */
const allMenus = computed(() => {
	const result = []
	for (const item of routes) {
		if (item.meta && item.meta.hidden) {
			continue
		}
		if (item.path.includes(':') || item.path === '/login') {
			continue
		}
		const children = (item.children || []).filter(child => child.meta && child.meta.title && !child.meta.hidden)
		if (item.path === '/') {
			children.forEach(child => {
				result.push({
					path: '/' + child.path,
					title: child.meta.title,
					icon: child.meta.icon || 'Menu',
					bottom: !!(child.meta && child.meta.bottom)
				})
			})
			continue
		}
		if (children.length) {
			result.push({
				path: item.path,
				title: (item.meta && item.meta.title) || item.path,
				icon: (item.meta && item.meta.icon) || 'Menu',
				bottom: !!(item.meta && item.meta.bottom),
				children: children.map(child => ({
					path: item.path + '/' + child.path,
					title: child.meta.title,
					icon: child.meta.icon || 'Menu'
				}))
			})
		}
	}
	return result
})

// meta.bottom 的模块（设置类）只参与排序：始终排在最后，不做固定定位
const menuList = computed(() => [
	...allMenus.value.filter(item => !item.bottom),
	...allMenus.value.filter(item => item.bottom)
])

const activeMenu = computed(() => route.path)

const breadcrumbs = computed(() => route.matched
	.filter(item => item.meta && item.meta.title)
	.map(item => item.meta.title))

function onCommand(command) {
	if (command === 'logout') {
		// 让后端吊销 token（递增版本号），失败也照样清本地
		logoutApi().catch(() => null).finally(() => {
			userStore.logout()
			router.push('/login')
		})
		return
	}
	if (command === 'view') {
		window.open('http://localhost:8081', '_blank')
	}
}
</script>

<style scoped>
.layout {
	height: 100%;
}

.layout-aside {
	background: #1f2d3d;
	transition: width .2s;
	overflow: hidden;
}

.layout-logo {
	display: flex;
	align-items: center;
	gap: 10px;
	height: 56px;
	padding: 0 16px;
	color: #fff;
	border-bottom: 1px solid rgba(255, 255, 255, .06);
}

.layout-logo-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 28px;
	height: 28px;
	border-radius: 7px;
	background: #4ec9b0;
	color: #12332e;
	font-weight: 700;
	font-size: 13px;
	flex-shrink: 0;
}

.layout-logo-text {
	font-size: 15px;
	font-weight: 600;
	letter-spacing: .5px;
}

.layout-aside :deep(.el-menu) {
	border-right: none;
}


.layout-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	height: 56px;
	background: #fff;
	border-bottom: 1px solid #e4e7ed;
}

.layout-header-left {
	display: flex;
	align-items: center;
	gap: 14px;
}

.layout-collapse {
	font-size: 18px;
	cursor: pointer;
	color: #606266;
}

.layout-header-right {
	display: flex;
	align-items: center;
	gap: 12px;
}

.layout-langs {
	display: flex;
	gap: 2px;
}

.layout-lang {
	padding: 3px 7px;
	border: none;
	background: none;
	border-radius: 6px;
	font-size: 12.5px;
	color: #909399;
	cursor: pointer;
}

.layout-lang:hover {
	color: #4ec9b0;
}

.layout-lang.active {
	color: #2f9e87;
	font-weight: 600;
	background: #eaf7f4;
}

.layout-user {
	display: flex;
	align-items: center;
	gap: 8px;
	cursor: pointer;
	color: #303133;
	outline: none;
}

.layout-user-name {
	font-size: 13px;
}

.layout-main {
	padding: 16px;
	background: #f5f7fa;
	overflow-y: auto;
}
</style>
