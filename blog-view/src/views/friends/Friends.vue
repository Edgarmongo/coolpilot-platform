<template>
	<div>
		<div class="portal-page-head">
			<h2>小伙伴们</h2>
			<p>友链共 {{ friendList.length }} 位。</p>
		</div>
		<div class="portal-content-block">
			<div class="portal-friend-grid">
				<a :href="item.website" target="_blank" rel="external nofollow noopener" class="portal-friend-card"
				   v-for="(item,index) in friendList" :key="index" @click="addViews(item.nickname)">
					<img class="portal-friend-avatar" :src="item.avatar" onerror="this.src = '/img/error.png'" alt="">
					<div class="portal-friend-name">{{ item.nickname }}</div>
					<div class="portal-friend-desc">{{ item.description }}</div>
				</a>
			</div>
		</div>
		<!--页面描述-->
		<div class="portal-content-block">
			<div class="typo content" v-lazy-container="{selector: 'img'}" v-viewer v-html="info.content"></div>
		</div>
		<!--评论-->
		<div class="portal-content-block">
			<CommentList :page="2" :blogId="null" v-if="info.commentEnabled"/>
			<h3 class="ui header" v-else>评论已关闭</h3>
		</div>
	</div>
</template>

<script>
	import {getData, addViewsByNickname} from "@/api/friend";
	import CommentList from "@/components/comment/CommentList";

	export default {
		name: "Friends",
		components: {CommentList},
		data() {
			return {
				friendList: [],
				info: {
					content: '',
					commentEnabled: false
				}
			}
		},
		created() {
			this.getData()
		},
		methods: {
			getData() {
				getData().then(res => {
					if (res.code === 200) {
						this.friendList = res.data.friendList
						this.info = res.data.friendInfo
					} else {
						this.msgError(res.msg)
					}
				}).catch(() => {
					this.msgError("请求失败")
				})
			},
			addViews(nickname) {
				addViewsByNickname(nickname)
			}
		}
	}
</script>

<style scoped>

</style>
