<template>
	<aside class="portal-sidebar">
		<!--静态页面级目录-->
		<div class="portal-nav-group">
			<ul class="portal-nav-list">
				<li
					class="portal-nav-link"
					v-for="item in staticNav"
					:key="item.path"
					:class="{'is-active': isActive(item)}"
					@click="navigate(item)"
				>{{ item.label }}</li>
			</ul>
		</div>

		<!--分类目录（展开时懒加载该分类下的文章）-->
		<div class="portal-nav-group" v-for="group in categoryGroups" :key="group.name">
			<div class="portal-nav-group-title" @click="toggleGroup(group.name)">
				<i class="icon" :class="group.collapsed ? 'caret right' : 'caret down'"></i>
				<span>{{ group.name }}</span>
			</div>
			<ul class="portal-nav-list" v-show="!group.collapsed">
				<li
					class="portal-nav-link"
					:class="{'is-active': isCategoryActive(group.name)}"
					@click="navigate({path: `/category/${group.name}`})"
				>全部文章</li>
				<!--该分类下的文章列表（懒加载）-->
				<ul class="portal-nav-sub" v-if="group.children.length > 0">
					<li
						class="portal-nav-link"
						v-for="child in group.children"
						:key="child.id"
						:class="{'is-active': isBlogActive(child.id)}"
						@click="navigate({path: `/blog/${child.id}`})"
					>{{ child.title }}</li>
				</ul>
				<li class="portal-nav-empty" v-else-if="group.loaded">该分类下暂无文章</li>
				<li class="portal-nav-empty" v-else>加载中...</li>
			</ul>
		</div>

		<div class="portal-nav-group" v-if="categoryGroups.length === 0">
			<p class="portal-nav-empty">暂无分类目录</p>
		</div>
	</aside>
</template>

<script>
	import {getBlogListByCategoryName} from "@/api/category";

	export default {
		name: "DocSidebar",
		props: {
			categoryList: {
				type: Array,
				default: () => []
			}
		},
		data() {
			return {
				//已加载文章的分类名集合
				loadedCategories: [],
				//分类 -> 文章列表 的映射
				categoryChildren: {},
				//折叠状态
				collapsedMap: {}
			}
		},
		computed: {
			//静态页面级目录
			staticNav() {
				return [
					{label: '首页', path: '/home', name: 'home'},
					{label: '知识付费', path: '/knowledge', name: 'knowledge'},
					{label: '文章归档', path: '/archives', name: 'archives'},
					{label: '最新动态', path: '/moments', name: 'moments'},
					{label: '友人帐', path: '/friends', name: 'friends'},
					{label: '关于我', path: '/about', name: 'about'}
				]
			},
			//分类目录，携带折叠与子项状态
			categoryGroups() {
				return this.categoryList.map(category => ({
					name: category.name,
					collapsed: !!this.collapsedMap[category.name],
					loaded: this.loadedCategories.indexOf(category.name) !== -1,
					children: this.categoryChildren[category.name] || []
				}))
			}
		},
		watch: {
			//当前路由所属分类自动展开
			'$route.name'() {
				this.autoExpandCurrentCategory()
			},
			'$route.params.name'() {
				this.autoExpandCurrentCategory()
			},
			categoryList() {
				this.autoExpandCurrentCategory()
				//分类数据异步到达后，加载展开中的分类文章
				this.$nextTick(() => {
					this.loadExpandedCategories()
				})
			}
		},
		mounted() {
			this.autoExpandCurrentCategory()
			//初始展开的分类同步加载其文章列表
			this.loadExpandedCategories()
		},
		methods: {
			//加载所有处于展开状态的分类文章
			loadExpandedCategories() {
				this.categoryGroups.forEach(group => {
					if (!group.collapsed) {
						this.loadCategoryChildren(group.name)
					}
				})
			},
			//展开当前路由所属的分类，并懒加载其文章
			autoExpandCurrentCategory() {
				if (this.$route.name === 'category' && this.$route.params.name) {
					const name = this.$route.params.name
					this.$set(this.collapsedMap, name, false)
					this.loadCategoryChildren(name)
				}
				if (this.$route.name === 'blog' && this.$route.params.id) {
					//文章页无法直接得知分类，展开全部以确保目录可见
					this.categoryList.forEach(c => {
						this.$set(this.collapsedMap, c.name, false)
					})
				}
			},
			//切换折叠状态，展开时才加载文章
			toggleGroup(name) {
				const collapsed = !!this.collapsedMap[name]
				this.$set(this.collapsedMap, name, !collapsed)
				if (!collapsed) {
					this.loadCategoryChildren(name)
				}
			},
			//懒加载某分类下的文章
			loadCategoryChildren(name) {
				if (this.loadedCategories.indexOf(name) !== -1) {
					return
				}
				getBlogListByCategoryName(name, 1).then(res => {
					if (res.code === 200 && res.data && res.data.list) {
						this.$set(this.categoryChildren, name, res.data.list)
					} else {
						this.$set(this.categoryChildren, name, [])
					}
					this.loadedCategories.push(name)
				}).catch(() => {
					this.$set(this.categoryChildren, name, [])
					this.loadedCategories.push(name)
				})
			},
			isActive(item) {
				return this.$route.name === item.name
			},
			isCategoryActive(name) {
				return this.$route.name === 'category' && this.$route.params.name === name
			},
			isBlogActive(id) {
				return this.$route.name === 'blog' && String(this.$route.params.id) === String(id)
			},
			navigate(item) {
				if (this.$route.path !== item.path) {
					this.$router.push(item.path)
				}
			}
		}
	}
</script>

<style scoped>

</style>
