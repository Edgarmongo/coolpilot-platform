import Vue from 'vue'
import VueRouter from 'vue-router'
import getPageTitle from '@/util/get-page-title'

Vue.use(VueRouter)

const routes = [
	{
		//前台访客登录/注册（注意：与 blog-cms 后台博主登录是两套身份）
		path: '/login',
		component: () => import('@/views/visitor/Auth'),
		meta: {title: '访客登录'}
	},
	{
		path: '/',
		component: () => import('@/views/Index'),
		redirect: '/home',
		children: [
			{
				path: '/home',
				name: 'home',
				component: () => import('@/views/home/Home'),
				meta: {title: '首页'}
			},
			{
				path: '/archives',
				name: 'archives',
				component: () => import('@/views/archives/Archives'),
				meta: {title: '归档'}
			},
			{
				path: '/blog/:id',
				name: 'blog',
				component: () => import('@/views/blog/Blog'),
				meta: {title: '博客'}
			},
			{
				path: '/tag/:name',
				name: 'tag',
				component: () => import('@/views/tag/Tag'),
				meta: {title: '标签'}
			},
			{
				path: '/category/:name',
				name: 'category',
				component: () => import('@/views/category/Category'),
				meta: {title: '分类'}
			},
			{
				path: '/moments',
				name: 'moments',
				component: () => import('@/views/moments/Moments'),
				meta: {title: '动态'}
			},
			{
				path: '/friends',
				name: 'friends',
				component: () => import('@/views/friends/Friends'),
				meta: {title: '友人帐'}
			},
			{
				path: '/about',
				name: 'about',
				component: () => import('@/views/about/About'),
				meta: {title: '关于我'}
			},
			{
				//个人中心：收藏与站内消息，需登录
				path: '/me',
				name: 'me',
				component: () => import('@/views/me/Me'),
				meta: {title: '个人中心'}
			},
			{
				path: '/knowledge',
				name: 'knowledge',
				component: () => import('@/views/knowledge/List'),
				meta: {title: '知识付费'}
			},
			{
				path: '/knowledge/:no',
				name: 'knowledgeDetail',
				component: () => import('@/views/knowledge/Detail'),
				meta: {title: '商品详情'}
			},
			{
				//收银台：下单后跳转，未支付/待确认/已完成都在这一页
				path: '/order/:orderNo',
				name: 'orderPay',
				component: () => import('@/views/order/Pay'),
				meta: {title: '收银台'}
			},
			{
				path: '/orders',
				name: 'myOrders',
				component: () => import('@/views/order/Mine'),
				meta: {title: '我的订单'}
			}
		]
	}
]

const router = new VueRouter({
	mode: 'history',
	base: process.env.BASE_URL,
	routes
})

//挂载路由守卫
router.beforeEach((to, from, next) => {
	document.title = getPageTitle(to.meta.title)
	next()
})

export default router
