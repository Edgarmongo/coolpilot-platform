<template>
	<div class="portal-orders" v-loading="loading">
		<div class="portal-section-head">
			<div>
				<h2 class="portal-section-title">我的订单</h2>
				<p class="portal-section-desc">查看支付状态、已购内容与退款进度</p>
			</div>
			<router-link to="/me" class="portal-orders-back">返回个人中心</router-link>
		</div>

		<div class="portal-me-tabs">
			<button :class="['portal-me-tab', {active: tab === 'order'}]" @click="switchTab('order')">订单记录</button>
			<button :class="['portal-me-tab', {active: tab === 'ent'}]" @click="switchTab('ent')">我的权益</button>
		</div>

		<!--订单-->
		<template v-if="tab === 'order'">
			<div v-if="orders.length === 0 && !loading" class="portal-empty">还没有订单，去知识付费看看</div>
			<ul class="portal-order-list">
				<li v-for="order in orders" :key="order.id" class="portal-order-item">
					<div class="portal-order-main">
						<router-link :to="`/order/${order.orderNo}`" class="portal-order-title">
							{{ order.titleSummary || '订单 ' + order.orderNo }}
						</router-link>
						<p class="portal-order-meta">
							<span>{{ order.createTime | dateFormat('YYYY-MM-DD HH:mm') }}</span>
							<span>·</span>
							<span>{{ order.orderNo }}</span>
						</p>
					</div>
					<div class="portal-order-side">
						<strong class="portal-order-amount">¥{{ formatPrice(order.payAmount) }}</strong>
						<span :class="['portal-order-status', 'is-' + String(order.status).toLowerCase()]">
							{{ statusText(order.status) }}
						</span>
						<router-link v-if="order.status === 'PENDING_PAY'" :to="`/order/${order.orderNo}`" class="portal-order-action">
							去支付
						</router-link>
					</div>
				</li>
			</ul>
		</template>

		<!--权益-->
		<template v-else>
			<div v-if="entitlements.length === 0 && !loading" class="portal-empty">还没有已购内容</div>
			<ul class="portal-ent-list">
				<li v-for="ent in entitlements" :key="ent.id" class="portal-ent-item">
					<router-link :to="`/knowledge/${ent.productNo}`" class="portal-ent-title">{{ ent.productTitle }}</router-link>
					<span class="portal-ent-meta">
						{{ typeLabel(ent.productType) }}
						· {{ ent.entType === 'DOWNLOAD' ? '可下载' : '可阅读' }}
						· {{ ent.expireTime ? '有效期至 ' + formatDate(ent.expireTime) : '永久有效' }}
					</span>
					<router-link :to="`/knowledge/${ent.productNo}`" class="portal-order-action">去查看</router-link>
				</li>
			</ul>
		</template>
	</div>
</template>

<script>
	import {getMyEntitlements, getOrders} from "@/api/trade";

	export default {
		name: "MyOrders",
		data() {
			return {
				tab: 'order',
				loading: false,
				orders: [],
				entitlements: [],
				loaded: {order: false, ent: false}
			}
		},
		created() {
			if (!this.$store.state.visitor) {
				this.$router.replace(`/login?redirect=${this.$route.fullPath}`)
				return
			}
			this.loadOrders()
		},
		methods: {
			switchTab(tab) {
				this.tab = tab
				if (tab === 'order' && !this.loaded.order) {
					this.loadOrders()
				}
				if (tab === 'ent' && !this.loaded.ent) {
					this.loadEntitlements()
				}
			},
			loadOrders() {
				this.loading = true
				getOrders({pageNum: 1, pageSize: 50}).then(res => {
					if (res.code === 200) {
						this.orders = res.data.list || []
						this.loaded.order = true
					}
				}).finally(() => {
					this.loading = false
				})
			},
			loadEntitlements() {
				this.loading = true
				getMyEntitlements().then(res => {
					if (res.code === 200) {
						this.entitlements = res.data || []
						this.loaded.ent = true
					}
				}).finally(() => {
					this.loading = false
				})
			},
			statusText(status) {
				return {
					PENDING_PAY: '待支付',
					PENDING_CONFIRM: '待确认收款',
					PAID: '已支付',
					COMPLETED: '已完成',
					CANCELLED: '已取消',
					CLOSED: '已关闭',
					REFUNDING: '退款中',
					REFUNDED: '已退款'
				}[status] || status
			},
			typeLabel(type) {
				return {COURSE: '视频课程', DOC: '付费文档', COLUMN: '知识专栏', CODE: '代码资源', SERVICE: '技术服务'}[type] || type
			},
			formatPrice(value) {
				return Number(value || 0).toFixed(2).replace(/\.00$/, '')
			},
			formatDate(value) {
				if (!value) {
					return ''
				}
				const d = new Date(value)
				return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
			}
		}
	}
</script>

<style scoped>
	.portal-orders {
		background: #fff;
		border: 1px solid rgba(15, 118, 110, .12);
		border-radius: 12px;
		padding: 24px 26px 30px;
		min-height: 320px;
	}

	.portal-orders-back {
		font-size: 12.5px;
		color: #0F766E;
		text-decoration: none;
	}

	.portal-order-list,
	.portal-ent-list {
		list-style: none;
		margin: 8px 0 0;
		padding: 0;
	}

	.portal-order-item,
	.portal-ent-item {
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 14px 4px;
		border-bottom: 1px dashed #eef2f2;
	}

	.portal-order-main {
		flex: 1;
		min-width: 0;
	}

	.portal-order-title,
	.portal-ent-title {
		color: #111827;
		font-size: 14px;
		text-decoration: none;
	}

	.portal-order-title:hover,
	.portal-ent-title:hover {
		color: #0F766E;
	}

	.portal-order-meta {
		margin: 6px 0 0;
		font-size: 11.5px;
		color: #9ca3af;
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}

	.portal-order-side {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-shrink: 0;
	}

	.portal-order-amount {
		color: #B45309;
		font-size: 15px;
	}

	.portal-order-status {
		padding: 2px 9px;
		border-radius: 999px;
		background: #eef7f5;
		color: #0F766E;
		font-size: 11.5px;
	}

	.portal-order-status.is-pending_pay {
		background: #fef3c7;
		color: #B45309;
	}

	.portal-order-status.is-pending_confirm {
		background: #e0f2fe;
		color: #0369a1;
	}

	.portal-order-status.is-refunded,
	.portal-order-status.is-cancelled,
	.portal-order-status.is-closed {
		background: #f3f4f6;
		color: #6b7280;
	}

	.portal-order-action {
		font-size: 12.5px;
		color: #0F766E;
		text-decoration: none;
		border: 1px solid #0F766E;
		border-radius: 6px;
		padding: 3px 10px;
	}

	.portal-order-action:hover {
		background: #0F766E;
		color: #fff;
	}

	.portal-ent-item {
		flex-wrap: wrap;
	}

	.portal-ent-title {
		flex: 1;
		min-width: 180px;
	}

	.portal-ent-meta {
		font-size: 11.5px;
		color: #9ca3af;
	}

	.portal-empty {
		padding: 50px 0;
		text-align: center;
		color: #9ca3af;
		font-size: 13px;
	}
</style>
