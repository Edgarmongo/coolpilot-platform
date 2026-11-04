<template>
	<div>
		<!--门户 Hero-->
		<PortalHero
			:siteName="portal.siteName"
			:slogan="portal.slogan"
			:title="portal.title"
			:description="portal.description"
			:actions="heroActions"
			:stats="heroStats"
		/>

		<!--按分类浏览-->
		<section class="portal-section" v-if="categoryList.length > 0">
			<div class="portal-section-head">
				<h2>按分类浏览</h2>
				<p>共 {{ categoryList.length }} 个分类，{{ articleCount }} 篇文章。</p>
			</div>
			<div class="portal-card-grid portal-card-grid--4" :style="{'--cols': Math.min(categoryList.length, 4)}">
				<router-link
					v-for="category in categoryList"
					:key="category.id"
					:to="`/category/${category.name}`"
					class="portal-route-card"
				>
					<strong>{{ category.name }}</strong>
					<span>浏览该分类下的全部文章</span>
				</router-link>
			</div>
		</section>

		<!--最新文章-->
		<section class="portal-section">
			<div class="portal-section-head">
				<h2>最新文章</h2>
				<p>按发布时间倒序排列。</p>
			</div>
			<BlogList :getBlogList="getBlogList" :blogList="blogList" :totalPage="totalPage"/>
		</section>
	</div>
</template>

<script>
	import BlogList from "@/components/blog/BlogList";
	import PortalHero from "@/components/index/PortalHero";
	import {getBlogList} from '@/api/home';
	import {getArchives} from '@/api/archives';
	import {SET_IS_BLOG_TO_HOME} from "../../store/mutations-types";
	import {portal as portalSettings} from '@/settings';

	export default {
		name: "Home",
		components: {BlogList, PortalHero},
		data() {
			return {
				blogList: [],
				totalPage: 0,
				getBlogListFinish: false,
				articleCount: 0,
				portal: portalSettings
			}
		},
		computed: {
			categoryList() {
				return this.$store.state.categoryList || []
			},
			//Hero 主按钮组
			heroActions() {
				return [
					{label: '文章归档', path: '/archives'},
					{label: '最新动态', path: '/moments'},
					{label: '友人帐', path: '/friends'},
					{label: '关于我', path: '/about'}
				]
			},
			//Hero 统计卡
			heroStats() {
				const categories = this.categoryList.length
				const tags = (this.$store.state.tagList || []).length
				return [
					{value: this.articleCount, label: '篇文章', path: '/archives'},
					{value: categories, label: '个分类', path: categories > 0 ? `/category/${this.categoryList[0].name}` : '/home'},
					{value: tags, label: '个标签', path: '/archives'}
				]
			}
		},
		beforeRouteEnter(to, from, next) {
			next(vm => {
				if (from.name !== 'blog') {
					//其它页面跳转到首页时，重新请求数据
					//设置一个flag，让首页的分页组件指向正确的页码
					vm.$store.commit(SET_IS_BLOG_TO_HOME, false)
					vm.getBlogList()
				} else {
					//从文章页面跳转到首页时，使用首页缓存
					vm.$store.commit(SET_IS_BLOG_TO_HOME, true)
				}
			})
		},
		created() {
			this.getArticleCount()
		},
		methods: {
			//文章总数用于 Hero 统计，接口异常时静默降级
			getArticleCount() {
				getArchives().then(res => {
					if (res.code === 200) {
						this.articleCount = res.data.count
					}
				}).catch(() => {
					this.articleCount = 0
				})
			},
			getBlogList(pageNum) {
				getBlogList(pageNum).then(res => {
					if (res.code === 200) {
						this.blogList = res.data.list
						this.totalPage = res.data.totalPage
						this.$nextTick(() => {
							Prism.highlightAll()
						})
						this.getBlogListFinish = true
					} else {
						this.msgError(res.msg)
					}
				}).catch(() => {
					this.msgError("请求失败")
				})
			}
		}
	}
</script>

<style scoped>

</style>
