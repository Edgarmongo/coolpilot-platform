import {createRouter, createWebHistory} from 'vue-router'
import Layout from '@/layout/index.vue'

/**
 * 路由即菜单：meta.title 用于侧栏与面包屑，meta.hidden 表示不在菜单展示。
 * 迁移进度：已迁 仪表盘 / 交易管理；其余模块按 docs/FRONTEND-MIGRATION.md 分批补齐。
 */
export const routes = [
	{
		path: '/login',
		name: 'Login',
		component: () => import('@/views/login/index.vue'),
		meta: {title: '登录', hidden: true}
	},
	{
		path: '/',
		component: Layout,
		redirect: '/dashboard',
		children: [
			{
				path: 'dashboard',
				name: 'Dashboard',
				component: () => import('@/views/dashboard/index.vue'),
				meta: {title: '仪表盘', icon: 'Odometer'}
			}
		]
	},
	{
		path: '/trade',
		component: Layout,
		redirect: '/trade/order',
		meta: {title: '交易管理', icon: 'Money'},
		children: [
			{
				path: 'order',
				name: 'OrderManage',
				component: () => import('@/views/trade/Orders.vue'),
				meta: {title: '订单管理', icon: 'List'}
			},
			{
				path: 'refund',
				name: 'RefundManage',
				component: () => import('@/views/trade/Refunds.vue'),
				meta: {title: '退款处理', icon: 'RefreshLeft'}
			},
			{
				path: 'entitlement',
				name: 'EntitlementManage',
				component: () => import('@/views/trade/Entitlements.vue'),
				meta: {title: '权益管理', icon: 'Key'}
			}
		]
	},
	{
		path: '/:pathMatch(.*)*',
		redirect: '/dashboard'
	}
]

const router = createRouter({
	history: createWebHistory('/cms/'),
	routes
})

router.beforeEach((to, from, next) => {
	document.title = to.meta.title ? `${to.meta.title} - CoolPilot` : 'CoolPilot 管理后台'
	const token = localStorage.getItem('token')
	if (!token && to.path !== '/login') {
		next({path: '/login', query: {redirect: to.fullPath}})
		return
	}
	if (token && to.path === '/login') {
		next('/dashboard')
		return
	}
	next()
})

export default router
