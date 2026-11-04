<template>
	<header class="portal-header">
		<!--第一层：品牌栏-->
		<div class="portal-brandbar">
			<div class="portal-brandbar-inner">
				<router-link to="/home" class="portal-logo">
					<span class="portal-logo-icon">☕</span>
					<span class="portal-logo-text">{{ blogName }}</span>
				</router-link>

				<!--搜索-->
				<div class="portal-search" :class="{'has-value': queryString}" ref="searchBox">
					<i class="search icon portal-search-icon"></i>
					<input
						class="portal-search-input"
						type="text"
						v-model="queryString"
						placeholder="搜索文章标题或内容..."
						@focus="panelOpen = true"
						@input="debounceQuery"
					/>
					<i class="close icon portal-search-clear" @click="clearQuery"></i>
					<!--搜索建议-->
					<div class="portal-search-panel" :class="{'is-open': panelOpen && queryResult.length > 0}" ref="searchPanel">
						<div class="portal-search-item" v-for="(item,index) in queryResult" :key="index" @click="handleSelect(item)">
							<div class="portal-search-item-title">{{ item.title }}</div>
							<div class="portal-search-item-desc">{{ item.content }}</div>
						</div>
					</div>
				</div>

				<!--访客身份区：已登录显示昵称，未登录显示登录入口-->
				<div class="portal-user" v-if="visitor">
					<router-link to="/me" class="portal-user-link">
						<img class="portal-user-avatar" :src="visitor.avatar" alt="">
						<span class="portal-user-name">{{ visitor.nickname || visitor.username }}</span>
					</router-link>
					<button class="portal-logout" @click="logout">退出</button>
				</div>
				<router-link v-else to="/login" class="portal-login">
					<i class="user icon"></i>
					<span>登录</span>
				</router-link>

				<!--移动端菜单按钮-->
				<button class="portal-menu-toggle" @click="subnavOpen = !subnavOpen">
					<i class="bars icon"></i>
				</button>
			</div>
		</div>

		<!--第二层：分类导航条-->
		<nav class="portal-subnav">
			<div class="portal-subnav-inner" :class="{'is-open': subnavOpen}">
				<router-link to="/home" class="portal-subnav-item" :class="{'is-active': $route.name === 'home'}">首页</router-link>
				<router-link
					to="/archives"
					class="portal-subnav-item"
					:class="{'is-active': $route.name === 'archives'}"
				>归档</router-link>
				<router-link
					v-for="(category,index) in categoryList"
					:key="index"
					:to="`/category/${category.name}`"
					class="portal-subnav-item"
					:class="{'is-active': $route.name === 'category' && $route.params.name === category.name}"
				>{{ category.name }}</router-link>
				<router-link
					to="/moments"
					class="portal-subnav-item"
					:class="{'is-active': $route.name === 'moments'}"
				>动态</router-link>
				<router-link
					to="/friends"
					class="portal-subnav-item"
					:class="{'is-active': $route.name === 'friends'}"
				>友人帐</router-link>
				<router-link
					to="/about"
					class="portal-subnav-item"
					:class="{'is-active': $route.name === 'about'}"
				>关于我</router-link>
			</div>
		</nav>
	</header>
</template>

<script>
	import {getSearchBlogList} from "@/api/blog";
	import {mapState} from 'vuex'
	import {CLEAR_VISITOR} from "@/store/mutations-types";

	export default {
		name: "Nav",
		props: {
			blogName: {
				type: String,
				required: true
			},
			categoryList: {
				type: Array,
				required: true
			}
		},
		data() {
			return {
				subnavOpen: false,
				queryString: '',
				queryResult: [],
				panelOpen: false,
				timer: null
			}
		},
		computed: {
			...mapState(['visitor'])
		},
		watch: {
			//路由改变时收起移动端菜单与搜索面板
			'$route.path'() {
				this.subnavOpen = false
				this.panelOpen = false
			}
		},
		mounted() {
			//点击外部区域关闭搜索建议
			document.addEventListener('click', (e) => {
				if (this.$refs.searchBox && !this.$refs.searchBox.contains(e.target)) {
					this.panelOpen = false
				}
			})
		},
		methods: {
			//退出访客登录态
			logout() {
				this.$store.commit(CLEAR_VISITOR)
				this.msgSuccess('已退出登录')
				//文章页退出后刷新，确保依赖登录态的展示同步
				if (this.$route.path !== '/home') {
					this.$router.push('/home')
				}
			},
			debounceQuery() {
				this.timer && clearTimeout(this.timer)
				this.panelOpen = true
				this.timer = setTimeout(() => this.querySearchAsync(), 350)
			},
			querySearchAsync() {
				const queryString = this.queryString
				if (queryString == null
					|| queryString.trim() === ''
					|| queryString.indexOf('%') !== -1
					|| queryString.indexOf('_') !== -1
					|| queryString.indexOf('[') !== -1
					|| queryString.indexOf('#') !== -1
					|| queryString.indexOf('*') !== -1
					|| queryString.trim().length > 20) {
					this.queryResult = []
					return
				}
				getSearchBlogList(queryString).then(res => {
					if (res.code === 200) {
						this.queryResult = res.data
						if (this.queryResult.length === 0) {
							this.queryResult = []
						}
					}
				}).catch(() => {
					this.queryResult = []
				})
			},
			handleSelect(item) {
				if (item.id) {
					this.panelOpen = false
					this.queryString = ''
					this.queryResult = []
					this.$router.push(`/blog/${item.id}`)
				}
			},
			clearQuery() {
				this.queryString = ''
				this.queryResult = []
				this.panelOpen = false
			}
		}
	}
</script>

<style scoped>
	/*移动端展开的分类导航*/
	@media screen and (max-width: 900px) {
		.portal-subnav-inner {
			flex-wrap: wrap;
			height: auto;
			max-height: 0;
			overflow: hidden;
			transition: max-height .28s ease;
		}

		.portal-subnav-inner.is-open {
			max-height: 60vh;
			overflow-y: auto;
			padding-top: 6px;
			padding-bottom: 6px;
		}

		.portal-subnav-item {
			height: 34px;
		}
	}
</style>
