<template>
	<div class="portal-me">
		<div class="portal-section-head">
			<div>
				<h2 class="portal-section-title">个人中心</h2>
				<p class="portal-section-desc">我的收藏与站内消息</p>
			</div>
			<div class="portal-me-user" v-if="user">
				<router-link to="/orders" class="portal-me-orders">我的订单</router-link>
				<img :src="user.avatar" alt="">
				<span>{{ user.nickname || user.username }}</span>
			</div>
		</div>

		<div class="portal-me-tabs">
			<button :class="['portal-me-tab', {active: tab === 'favorite'}]" @click="switchTab('favorite')">
				我的收藏
			</button>
			<button :class="['portal-me-tab', {active: tab === 'message'}]" @click="switchTab('message')">
				站内消息
				<span class="portal-badge" v-if="unread > 0">{{ unread > 99 ? '99+' : unread }}</span>
			</button>
		</div>

		<div class="portal-me-panel" v-loading="loading">
			<!--收藏-->
			<template v-if="tab === 'favorite'">
				<div v-if="favorites.length === 0 && !loading" class="portal-empty">还没有收藏，去文章页点个收藏吧</div>
				<ul class="portal-me-list">
					<li v-for="item in favorites" :key="item.id" class="portal-me-item">
						<router-link v-if="item.targetType === 'ARTICLE'" :to="`/blog/${item.targetId}`" class="portal-me-title">
							{{ item.targetTitle }}
						</router-link>
						<span v-else class="portal-me-title">{{ item.targetTitle }}</span>
						<span class="portal-me-meta">
							{{ item.targetType === 'ARTICLE' ? '文章' : '动态' }}
							· 收藏于 {{ item.createTime | dateFormat('YYYY-MM-DD') }}
						</span>
					</li>
				</ul>
			</template>

			<!--消息-->
			<template v-else>
				<div v-if="messages.length === 0 && !loading" class="portal-empty">暂无消息</div>
				<ul class="portal-me-list">
					<li v-for="item in messages" :key="item.id"
					    :class="['portal-me-item', 'portal-me-item--clickable', {unread: !item.isRead}]"
					    @click="markRead(item)">
						<div class="portal-me-item-head">
							<strong>{{ item.title }}</strong>
							<span class="portal-me-meta">{{ item.createTime | dateFormat('YYYY-MM-DD HH:mm') }}</span>
						</div>
						<p class="portal-me-text">{{ item.content }}</p>
					</li>
				</ul>
			</template>

			<div class="portal-me-pager" v-if="totalPage > 1">
				<button :disabled="pageNum <= 1" @click="changePage(-1)">上一页</button>
				<span>{{ pageNum }} / {{ totalPage }}</span>
				<button :disabled="pageNum >= totalPage" @click="changePage(1)">下一页</button>
			</div>
		</div>
	</div>
</template>

<script>
	import {getFavorites, getMessages, getUnreadCount, readMessage} from "@/api/community";

	export default {
		name: "Me",
		data() {
			return {
				tab: 'favorite',
				loading: false,
				favorites: [],
				messages: [],
				unread: 0,
				pageNum: 1,
				totalPage: 1,
				loaded: {favorite: false, message: false}
			}
		},
		computed: {
			user() {
				return this.$store.state.visitor
			}
		},
		created() {
			if (!this.user) {
				this.$router.replace('/login?redirect=/me')
				return
			}
			this.loadUnread()
			this.loadFavorites()
		},
		methods: {
			switchTab(tab) {
				this.tab = tab
				this.pageNum = 1
				if (tab === 'favorite' && !this.loaded.favorite) {
					this.loadFavorites()
				}
				if (tab === 'message' && !this.loaded.message) {
					this.loadMessages()
				}
			},
			loadUnread() {
				getUnreadCount().then(res => {
					if (res.code === 200) {
						this.unread = res.data.count
					}
				}).catch(() => {
				})
			},
			loadFavorites() {
				this.loading = true
				getFavorites(this.pageNum).then(res => {
					if (res.code === 200) {
						this.favorites = res.data.list
						this.totalPage = res.data.totalPage
						this.loaded.favorite = true
					}
				}).finally(() => {
					this.loading = false
				})
			},
			loadMessages() {
				this.loading = true
				getMessages(this.pageNum).then(res => {
					if (res.code === 200) {
						this.messages = res.data.list
						this.totalPage = res.data.totalPage
						this.loaded.message = true
					}
				}).finally(() => {
					this.loading = false
				})
			},
			changePage(delta) {
				this.pageNum += delta
				this.tab === 'favorite' ? this.loadFavorites() : this.loadMessages()
			},
			markRead(item) {
				if (item.isRead) {
					return
				}
				readMessage(item.id).then(() => {
					item.isRead = true
					this.unread = Math.max(this.unread - 1, 0)
				})
			}
		}
	}
</script>

<style scoped>
	.portal-me {
		background: #fff;
		border: 1px solid rgba(15, 118, 110, .12);
		border-radius: 12px;
		padding: 22px 24px 26px;
	}

	.portal-me-user {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 13px;
		color: #4b5563;
	}

	.portal-me-user img {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		object-fit: cover;
	}

	.portal-me-orders {
		font-size: 12.5px;
		color: #0F766E;
		text-decoration: none;
		border: 1px solid rgba(15, 118, 110, .35);
		border-radius: 6px;
		padding: 3px 10px;
	}

	.portal-me-orders:hover {
		background: #eef7f5;
	}

	.portal-me-tabs {
		display: flex;
		gap: 8px;
		margin: 18px 0 6px;
		border-bottom: 1px solid #eef2f2;
	}

	.portal-me-tab {
		position: relative;
		padding: 9px 14px;
		border: none;
		background: none;
		font-size: 14px;
		color: #6b7280;
		cursor: pointer;
		border-bottom: 2px solid transparent;
	}

	.portal-me-tab.active {
		color: #0F766E;
		border-bottom-color: #0F766E;
		font-weight: 600;
	}

	.portal-badge {
		display: inline-block;
		margin-left: 4px;
		padding: 0 5px;
		border-radius: 9px;
		background: #dc2626;
		color: #fff;
		font-size: 11px;
		line-height: 17px;
	}

	.portal-me-panel {
		min-height: 160px;
		padding-top: 8px;
	}

	.portal-me-list {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.portal-me-item {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 12px;
		padding: 13px 4px;
		border-bottom: 1px dashed #eef2f2;
	}

	.portal-me-item--clickable {
		flex-direction: column;
		align-items: flex-start;
		cursor: pointer;
	}

	.portal-me-item.unread strong::before {
		content: '';
		display: inline-block;
		width: 6px;
		height: 6px;
		margin-right: 6px;
		border-radius: 50%;
		background: #dc2626;
		vertical-align: middle;
	}

	.portal-me-item-head {
		display: flex;
		justify-content: space-between;
		width: 100%;
		gap: 12px;
	}

	.portal-me-title {
		color: #111827;
		font-size: 14px;
		text-decoration: none;
	}

	.portal-me-title:hover {
		color: #0F766E;
	}

	.portal-me-meta {
		color: #9ca3af;
		font-size: 12px;
		white-space: nowrap;
	}

	.portal-me-text {
		margin: 5px 0 0;
		color: #6b7280;
		font-size: 13px;
		line-height: 1.7;
	}

	.portal-empty {
		padding: 48px 0;
		text-align: center;
		color: #9ca3af;
		font-size: 13px;
	}

	.portal-me-pager {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 14px;
		margin-top: 18px;
		font-size: 13px;
		color: #6b7280;
	}

	.portal-me-pager button {
		padding: 5px 12px;
		border: 1px solid #d1d5db;
		border-radius: 6px;
		background: #fff;
		cursor: pointer;
		font-size: 13px;
	}

	.portal-me-pager button:disabled {
		opacity: .45;
		cursor: not-allowed;
	}
</style>
