<template>
	<div>
		<article class="portal-post-card" v-for="item in blogList" :key="item.id">
			<!--标题-->
			<h2 class="portal-post-title">
				<a href="javascript:;" @click.prevent="toBlog(item)">{{ item.title }}</a>
			</h2>

			<!--文章简要信息-->
			<div class="portal-post-meta">
				<span><i class="small calendar icon"></i>{{ item.createTime | dateFormat('YYYY-MM-DD') }}</span>
				<span><i class="small eye icon"></i>{{ item.views }} 阅读</span>
				<span><i class="small pencil alternate icon"></i>约 {{ item.words }} 字</span>
				<span><i class="small clock icon"></i>约 {{ item.readTime }} 分钟</span>
				<router-link :to="`/category/${item.category.name}`" class="portal-post-category" v-if="item.category">
					<i class="small folder open icon"></i>{{ item.category.name }}
				</router-link>
			</div>

			<!--文章Markdown描述-->
			<div class="portal-post-excerpt typo line-numbers match-braces rainbow-braces" v-lazy-container="{selector: 'img'}" v-viewer v-html="item.description"></div>

			<!--阅读全文按钮-->
			<a href="javascript:;" @click.prevent="toBlog(item)" class="portal-read-btn">阅读全文 →</a>

			<!--标签-->
			<div class="portal-post-tags">
				<router-link :to="`/tag/${tag.name}`" class="portal-tag" v-for="(tag,index) in item.tags" :key="index">{{ tag.name }}</router-link>
			</div>
		</article>
	</div>
</template>

<script>
	export default {
		name: "BlogItem",
		props: {
			blogList: {
				type: Array,
				required: true
			}
		},
		methods: {
			toBlog(blog) {
				this.$store.dispatch('goBlogPage', blog)
			}
		}
	}
</script>

<style scoped>
	.portal-post-category {
		color: var(--portal-primary);
	}

	.portal-post-category:hover {
		text-decoration: underline;
	}

	.portal-read-btn {
		display: inline-flex;
		align-items: center;
		min-height: 34px;
		margin-top: 14px;
		padding: 0 13px;
		font-size: 12.5px;
		font-weight: 700;
		color: var(--portal-primary-strong);
		background: var(--portal-primary-soft);
		border: 1px solid var(--portal-primary-line);
		border-radius: 7px;
		text-decoration: none;
		transition: all var(--portal-ease);
	}

	.portal-read-btn:hover {
		color: #fff;
		background: var(--portal-primary);
		border-color: var(--portal-primary);
	}

	/*描述区内的 markdown 样式收敛，避免撑破卡片*/
	.portal-post-excerpt :deep(p) {
		margin: 0 0 8px;
	}

	.portal-post-excerpt :deep(ul),
	.portal-post-excerpt :deep(ol) {
		margin: 0 0 8px;
		padding-left: 22px;
	}

	.portal-post-excerpt :deep(pre) {
		max-width: 100%;
		overflow-x: auto;
	}
</style>
