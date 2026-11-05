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
		path: '/blog',
		component: Layout,
		redirect: '/blog/list',
		meta: {title: '内容管理', icon: 'Document'},
		children: [
			{
				path: 'list',
				name: 'BlogList',
				component: () => import('@/views/blog/BlogList.vue'),
				meta: {title: '文章列表', icon: 'List'}
			},
			{
				path: 'write',
				name: 'WriteBlog',
				component: () => import('@/views/blog/WriteBlog.vue'),
				meta: {title: '写文章', icon: 'EditPen'}
			},
			{
				path: 'edit/:id',
				name: 'EditBlog',
				component: () => import('@/views/blog/WriteBlog.vue'),
				meta: {title: '编辑文章', hidden: true}
			},
			{
				path: 'category',
				name: 'CategoryList',
				component: () => import('@/views/blog/CategoryList.vue'),
				meta: {title: '分类管理', icon: 'Folder'}
			},
			{
				path: 'tag',
				name: 'TagList',
				component: () => import('@/views/blog/TagList.vue'),
				meta: {title: '标签管理', icon: 'CollectionTag'}
			},
			{
				path: 'topic',
				name: 'TopicList',
				component: () => import('@/views/topic/TopicList.vue'),
				meta: {title: '专题管理', icon: 'Notebook'}
			},
			{
				path: 'moment',
				name: 'MomentList',
				component: () => import('@/views/blog/MomentList.vue'),
				meta: {title: '动态管理', icon: 'ChatDotRound'}
			},
			{
				path: 'feedback',
				name: 'FeedbackList',
				component: () => import('@/views/community/FeedbackList.vue'),
				meta: {title: '用户反馈', icon: 'ChatLineSquare'}
			},
			{
				path: 'comment',
				name: 'CommentList',
				component: () => import('@/views/blog/CommentList.vue'),
				meta: {title: '评论管理', icon: 'ChatLineSquare'}
			}
		]
	},
	{
		path: '/page',
		component: Layout,
		redirect: '/page/site',
		meta: {title: '页面管理', icon: 'Files'},
		children: [
			{
				path: 'site',
				name: 'SiteSetting',
				component: () => import('@/views/page/SiteSetting.vue'),
				meta: {title: '站点设置', icon: 'Setting'}
			},
			{
				path: 'about',
				name: 'AboutPage',
				component: () => import('@/views/page/About.vue'),
				meta: {title: '关于我', icon: 'InfoFilled'}
			},
			{
				path: 'friend',
				name: 'FriendList',
				component: () => import('@/views/page/FriendList.vue'),
				meta: {title: '友链管理', icon: 'Link'}
			}
		]
	},
	{
		path: '/system',
		component: Layout,
		redirect: '/system/account',
		// bottom: 排到菜单最后
		meta: {title: '系统管理', icon: 'Setting', bottom: true},
		children: [
			{
				path: 'account',
				name: 'Account',
				component: () => import('@/views/system/Account.vue'),
				meta: {title: '账号设置', icon: 'User'}
			},
			{
				path: 'log',
				name: 'Logs',
				component: () => import('@/views/log/Logs.vue'),
				meta: {title: '日志中心', icon: 'Document'}
			}
		]
	},
	{
		path: '/product',
		component: Layout,
		redirect: '/product/list',
		meta: {title: '商品管理', icon: 'Goods'},
		children: [
			{
				path: 'list',
				name: 'ProductManage',
				component: () => import('@/views/product/ProductList.vue'),
				meta: {title: '商品列表', icon: 'Goods'}
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
		path: '/ai',
		component: Layout,
		redirect: '/ai/knowledge',
		meta: {title: '智能检索', icon: 'MagicStick'},
		children: [
			{
				path: 'knowledge',
				name: 'KnowledgeIndex',
				component: () => import('@/views/ai/Knowledge.vue'),
				meta: {title: '知识库索引', icon: 'Cpu'}
			}
		]
	},
	{
		path: '/service',
		component: Layout,
		redirect: '/service/demand',
		meta: {title: '技术服务', icon: 'Suitcase'},
		children: [
			{
				path: 'demand',
				name: 'DemandManage',
				component: () => import('@/views/service/DemandList.vue'),
				meta: {title: '需求管理', icon: 'ChatLineSquare'}
			},
			{
				path: 'project',
				name: 'ProjectManage',
				component: () => import('@/views/service/ProjectList.vue'),
				meta: {title: '项目管理', icon: 'Files'}
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
	document.title = to.meta.title ? `${to.meta.title} - CoolSkyAI` : 'CoolSkyAI 管理后台'
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
