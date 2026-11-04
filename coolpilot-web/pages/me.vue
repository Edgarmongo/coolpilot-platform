<template>
	<div>
		<div class="head">
			<div>
				<h1 class="page-title">我的</h1>
				<p class="page-desc">订单与已购内容</p>
			</div>
		</div>

		<div class="tabs">
			<button :class="['tab', {active: tab === 'order'}]" @click="tab = 'order'">订单记录</button>
			<button :class="['tab', {active: tab === 'ent'}]" @click="tab = 'ent'">我的权益</button>
		</div>

		<div class="card">
			<template v-if="tab === 'order'">
				<div v-if="!orders.length" class="empty">还没有订单</div>
				<div v-for="order in orders" :key="order.id" class="item">
					<NuxtLink :to="`/order/${order.orderNo}`" class="item-main">
						<strong>{{ order.titleSummary || order.orderNo }}</strong>
						<span class="muted">{{ formatDate(order.createTime) }}</span>
					</NuxtLink>
					<div class="item-side">
						<span class="amount">¥{{ price(order.payAmount) }}</span>
						<span class="status">{{ statusText(order.status) }}</span>
					</div>
				</div>
			</template>

			<template v-else>
				<div v-if="!entitlements.length" class="empty">还没有已购内容</div>
				<div v-for="ent in entitlements" :key="ent.id" class="item">
					<NuxtLink :to="`/knowledge/${ent.productNo}`" class="item-main">
						<strong>{{ ent.productTitle }}</strong>
						<span class="muted">{{ ent.expireTime ? '有效期至 ' + formatDate(ent.expireTime, 'YYYY-MM-DD') : '永久有效' }}</span>
					</NuxtLink>
					<div class="item-side">
						<span class="status">可访问</span>
					</div>
				</div>
			</template>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call, isLogin} = useApi()

const tab = ref<'order' | 'ent'>('order')
const orders = ref<any[]>([])
const entitlements = ref<any[]>([])

if (!isLogin.value) {
	await navigateTo('/login?redirect=/me')
} else {
	const [orderRes, entRes] = await Promise.all([
		call('/orders?pageNum=1&pageSize=50').catch(() => null),
		call('/entitlements').catch(() => null)
	])
	orders.value = (orderRes as any)?.data?.list || []
	entitlements.value = (entRes as any)?.data || []
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function statusText(status: string) {
	return {
		PENDING_PAY: '待支付', PENDING_CONFIRM: '待确认', PAID: '已支付', COMPLETED: '已完成',
		CANCELLED: '已取消', CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款'
	}[status] || status
}
</script>

<style scoped>
.tabs {
	display: flex;
	gap: 8px;
	margin-bottom: 12px;
}

.tab {
	padding: 6px 16px;
	border: 1px solid var(--line);
	border-radius: 8px;
	background: #fff;
	font-size: 13.5px;
	cursor: pointer;
	color: var(--muted);
}

.tab.active {
	border-color: var(--teal);
	color: var(--teal);
	font-weight: 600;
	background: #eef7f5;
}

.item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	padding: 12px 0;
	border-bottom: 1px dashed var(--line);
}

.item:last-child {
	border-bottom: none;
}

.item-main {
	display: flex;
	flex-direction: column;
	gap: 4px;
	flex: 1;
	min-width: 0;
}

.item-main strong {
	font-size: 14px;
	font-weight: 600;
}

.item-side {
	display: flex;
	align-items: center;
	gap: 12px;
}

.amount {
	color: #b45309;
	font-weight: 600;
}

.status {
	padding: 2px 9px;
	border-radius: 999px;
	background: #eef7f5;
	color: var(--teal);
	font-size: 12px;
}
</style>
