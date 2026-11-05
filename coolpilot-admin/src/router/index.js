import {createRouter, createWebHistory} from 'vue-router'
import {i18n} from '@/i18n'
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
		meta: {title: 'menu.login', hidden: true}
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
				meta: {title: 'menu.dashboard', icon: 'Odometer'}
			}
		]
	},
	{
		path: '/blog',
		component: Layout,
		redirect: '/blog/list',
		meta: {title: 'menu.content', icon: 'Document'},
		children: [
			{
				path: 'list',
				name: 'BlogList',
				component: () => import('@/views/blog/BlogList.vue'),
				meta: {title: 'menu.blogList', icon: 'List'}
			},
			{
				path: 'write',
				name: 'WriteBlog',
				component: () => import('@/views/blog/WriteBlog.vue'),
				meta: {title: 'menu.blogWrite', icon: 'EditPen'}
			},
			{
				path: 'edit/:id',
				name: 'EditBlog',
				component: () => import('@/views/blog/WriteBlog.vue'),
				meta: {title: 'menu.blogEdit', hidden: true}
			},
			{
				path: 'category',
				name: 'CategoryList',
				component: () => import('@/views/blog/CategoryList.vue'),
				meta: {title: 'menu.categories', icon: 'Folder'}
			},
			{
				path: 'tag',
				name: 'TagList',
				component: () => import('@/views/blog/TagList.vue'),
				meta: {title: 'menu.tags', icon: 'CollectionTag'}
			},
			{
				path: 'topic',
				name: 'TopicList',
				component: () => import('@/views/topic/TopicList.vue'),
				meta: {title: 'menu.topics', icon: 'Notebook'}
			},
			{
				path: 'moment',
				name: 'MomentList',
				component: () => import('@/views/blog/MomentList.vue'),
				meta: {title: 'menu.moments', icon: 'ChatDotRound'}
			},
			{
				path: 'feedback',
				name: 'FeedbackList',
				component: () => import('@/views/community/FeedbackList.vue'),
				meta: {title: 'menu.feedback', icon: 'ChatLineSquare'}
			},
			{
				path: 'comment',
				name: 'CommentList',
				component: () => import('@/views/blog/CommentList.vue'),
				meta: {title: 'menu.comments', icon: 'ChatLineSquare'}
			}
		]
	},
	{
		path: '/page',
		component: Layout,
		redirect: '/page/site',
		meta: {title: 'menu.pages', icon: 'Files'},
		children: [
			{
				path: 'site',
				name: 'SiteSetting',
				component: () => import('@/views/page/SiteSetting.vue'),
				meta: {title: 'menu.siteSettings', icon: 'Setting'}
			},
			{
				path: 'nav',
				name: 'NavManage',
				component: () => import('@/views/page/NavList.vue'),
				meta: {title: 'menu.navManage', icon: 'Menu'}
			},
			{
				path: 'about',
				name: 'AboutPage',
				component: () => import('@/views/page/About.vue'),
				meta: {title: 'menu.aboutPage', icon: 'InfoFilled'}
			},
			{
				path: 'friend',
				name: 'FriendList',
				component: () => import('@/views/page/FriendList.vue'),
				meta: {title: 'menu.friends', icon: 'Link'}
			}
		]
	},
	{
		path: '/user',
		component: Layout,
		redirect: '/user/list',
		meta: {title: 'menu.users', icon: 'UserFilled'},
		children: [
			{
				path: 'list',
				name: 'UserManage',
				component: () => import('@/views/user/UserList.vue'),
				meta: {title: 'menu.userList', icon: 'User'}
			}
		]
	},
	{
		path: '/system',
		component: Layout,
		redirect: '/system/account',
		// bottom: 排到菜单最后
		meta: {title: 'menu.system', icon: 'Setting', bottom: true},
		children: [
			{
				path: 'account',
				name: 'Account',
				component: () => import('@/views/system/Account.vue'),
				meta: {title: 'menu.account', icon: 'User'}
			},
			{
				path: 'log',
				name: 'Logs',
				component: () => import('@/views/log/Logs.vue'),
				meta: {title: 'menu.logs', icon: 'Document'}
			}
		]
	},
	{
		path: '/product',
		component: Layout,
		redirect: '/product/list',
		meta: {title: 'menu.products', icon: 'Goods'},
		children: [
			{
				path: 'list',
				name: 'ProductManage',
				component: () => import('@/views/product/ProductList.vue'),
				meta: {title: 'menu.productList', icon: 'Goods'}
			}
		]
	},
	{
		path: '/trade',
		component: Layout,
		redirect: '/trade/order',
		meta: {title: 'menu.trade', icon: 'Money'},
		children: [
			{
				path: 'order',
				name: 'OrderManage',
				component: () => import('@/views/trade/Orders.vue'),
				meta: {title: 'menu.orders', icon: 'List'}
			},
			{
				path: 'refund',
				name: 'RefundManage',
				component: () => import('@/views/trade/Refunds.vue'),
				meta: {title: 'menu.refunds', icon: 'RefreshLeft'}
			},
			{
				path: 'entitlement',
				name: 'EntitlementManage',
				component: () => import('@/views/trade/Entitlements.vue'),
				meta: {title: 'menu.entitlements', icon: 'Key'}
			}
		]
	},
	{
		path: '/ai',
		component: Layout,
		redirect: '/ai/knowledge',
		meta: {title: 'menu.ai', icon: 'MagicStick'},
		children: [
			{
				path: 'knowledge',
				name: 'KnowledgeIndex',
				component: () => import('@/views/ai/Knowledge.vue'),
				meta: {title: 'menu.knowledge', icon: 'Cpu'}
			},
			{
				path: 'chat-logs',
				name: 'ChatLogs',
				component: () => import('@/views/ai/ChatLogs.vue'),
				meta: {title: 'menu.chatLogs', icon: 'ChatLineSquare'}
			}
		]
	},
	{
		path: '/service',
		component: Layout,
		redirect: '/service/demand',
		meta: {title: 'menu.services', icon: 'Suitcase'},
		children: [
			{
				path: 'demand',
				name: 'DemandManage',
				component: () => import('@/views/service/DemandList.vue'),
				meta: {title: 'menu.demands', icon: 'ChatLineSquare'}
			},
			{
				path: 'project',
				name: 'ProjectManage',
				component: () => import('@/views/service/ProjectList.vue'),
				meta: {title: 'menu.projects', icon: 'Files'}
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
	// meta.title 现在存的是 i18n key，标题随语言切换
	const adminTitle = i18n.global.t('layout.adminTitle')
	document.title = to.meta.title ? `${i18n.global.t(to.meta.title)} - ${adminTitle}` : adminTitle
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
