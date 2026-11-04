<template>
	<!--文章目录-->
	<div class="portal-toc" v-show="hasToc">
		<div class="portal-toc-head">
			<i class="list ul icon"></i>
			<span>本文目录</span>
		</div>
		<div class="portal-toc-body">
			<div class="js-toc"></div>
		</div>
	</div>
</template>

<script>
	import {mapState} from 'vuex'

	export default {
		name: "Tocbot",
		data() {
			return {
				hasToc: false
			}
		},
		computed: {
			...mapState(['isBlogRenderComplete'])
		},
		mounted() {
			// 组件创建时文章可能已渲染完成，需直接尝试初始化
			if (window.document.querySelector('.js-toc-content')) {
				this.initTocbot()
			}
		},
		watch: {
			//文章渲染完成时，生成目录
			isBlogRenderComplete() {
				if (this.isBlogRenderComplete) {
					this.initTocbot()
				}
			}
		},
		methods: {
			initTocbot() {
				//销毁旧实例，避免同一页面重复初始化
				if (window.tocbot) {
					window.tocbot.destroy()
				}
				window.tocbot.init({
					tocSelector: '.js-toc',
					contentSelector: '.js-toc-content',
					headingSelector: 'h1,h2,h3,h4',
					scrollSmooth: true,
					scrollSmoothDuration: 420,
					//到顶部导航条的距离
					scrollSmoothOffset: -110,
					headingsOffset: -84
				})
				//目录为空时隐藏整个区块
				this.$nextTick(() => {
					const toc = document.querySelector('.portal-toc .js-toc')
					this.hasToc = !!(toc && toc.querySelector('a'))
				})
			}
		}
	}
</script>

<style scoped>
	.portal-toc {
		margin: 0 0 22px;
		padding: 14px 18px;
		background: var(--portal-surface-alt);
		border: 1px solid var(--portal-border);
		border-radius: var(--portal-radius);
	}

	.portal-toc-head {
		display: flex;
		align-items: center;
		gap: 7px;
		margin-bottom: 8px;
		font-size: 13.5px;
		font-weight: 700;
		color: var(--portal-primary-strong);
	}

	.portal-toc-body :deep(.toc-list) {
		list-style: none;
		margin: 0;
		padding-left: 0;
	}

	.portal-toc-body :deep(.toc-list .toc-list) {
		padding-left: 16px;
	}

	.portal-toc-body :deep(.toc-link) {
		display: block;
		padding: 3px 0;
		font-size: 13px;
		line-height: 1.7;
		color: var(--portal-muted);
		text-decoration: none;
		transition: color .16s ease;
	}

	.portal-toc-body :deep(.toc-link:hover) {
		color: var(--portal-primary);
	}

	.portal-toc-body :deep(.is-active-link) {
		color: var(--portal-primary) !important;
		font-weight: 600;
	}

	/*tocbot 生成的进度条线隐藏，改用颜色高亮*/
	.portal-toc-body :deep(.toc-link::before) {
		display: none;
	}
</style>
