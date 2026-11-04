<template>
	<div>
		<div class="portal-page-head">
			<h2>文章归档</h2>
			<p>目前共计 {{ count }} 篇日志，按发布时间倒序排列。</p>
		</div>
		<div class="portal-content-block">
			<div class="portal-timeline">
				<div v-for="(value,key,index) in blogMap" :key="index">
					<div class="portal-timeline-year">{{ key }}</div>
					<div class="portal-timeline-item" v-for="blog in value" :key="blog.id" @click="toBlog(blog)">
						{{ blog.day }} · {{ blog.title }}
					</div>
				</div>
				<div class="portal-timeline-year">更早</div>
				<div class="portal-timeline-item">Hello World!</div>
			</div>
		</div>
	</div>
</template>

<script>
	import {getArchives} from "@/api/archives";

	export default {
		name: "Archives",
		data() {
			return {
				blogMap: {},
				count: 0
			}
		},
		created() {
			this.getArchives()
		},
		methods: {
			getArchives() {
				getArchives().then(res => {
					if (res.code === 200) {
						this.blogMap = res.data.blogMap
						this.count = res.data.count
					} else {
						this.msgError(res.msg);
					}
				}).catch(() => {
					this.msgError("请求失败");
				})
			},
			toBlog(blog) {
				this.$store.dispatch('goBlogPage', blog)
			}
		}
	}
</script>

<style scoped>

</style>
