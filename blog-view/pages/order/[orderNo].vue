<template>
	<div class="card" v-if="order">
		<div class="head">
			<div>
				<h1 class="page-title">收银台</h1>
				<p class="page-desc">订单 {{ order.orderNo }}</p>
			</div>
			<span class="status">{{ statusText(order.status) }}</span>
		</div>

		<div class="summary">
			<div v-for="item in items" :key="item.id" class="row">
				<span>{{ item.titleSnapshot }}</span>
				<span>¥{{ price(item.amount) }}</span>
			</div>
			<div class="row total">
				<span>应付金额</span>
				<strong>¥{{ price(order.payAmount) }}</strong>
			</div>
		</div>

		<!--待支付-->
		<template v-if="order.status === 'PENDING_PAY'">
			<div v-if="!payInfo" class="block">
				<h3>选择收款方式</h3>
				<div class="channels">
					<button v-for="(name, code) in channels" :key="code"
					        :class="['channel', {active: channel === code}]" @click="channel = code">
						{{ name }}
					</button>
				</div>
				<button class="btn" :disabled="busy" @click="startPay">
					{{ busy ? '生成中…' : '生成收款信息' }}
				</button>
			</div>

			<div v-else class="block">
				<h3>请完成转账</h3>
				<ul class="info">
					<li><span>收款方式</span><b>{{ payInfo.channelName }}</b></li>
					<li><span>应付金额</span><b class="amount">¥{{ price(payInfo.amount) }}</b></li>
					<li><span>转账备注</span><b>{{ payInfo.transferRemark }}</b></li>
					<li v-if="payInfo.payeeName"><span>收款人</span><b>{{ payInfo.payeeName }}</b></li>
				</ul>
				<p class="tip">{{ payInfo.tip }}</p>

				<div class="proof">
					<h4>已转账？填写付款信息</h4>
					<div class="form">
						<input v-model="payerName" placeholder="付款人姓名">
						<input v-model="transferTime" type="datetime-local">
					</div>
					<button class="btn" :disabled="busy" @click="submitProof">提交付款凭证</button>
				</div>
			</div>
		</template>

		<div v-else-if="order.status === 'PENDING_CONFIRM'" class="block">
			<h3>已收到付款信息</h3>
			<p class="tip">我会在 24 小时内核对到账，确认后自动开通，无需重复提交。</p>
		</div>

		<div v-else-if="order.status === 'COMPLETED'" class="block">
			<h3>开通成功</h3>
			<p class="tip">权益已发放，可返回商品页查看内容。</p>
			<NuxtLink :to="`/knowledge/${productNo}`" class="btn">去查看商品</NuxtLink>
		</div>

		<div v-else class="block">
			<h3>{{ statusText(order.status) }}</h3>
			<p class="tip">该订单当前不可支付，如有疑问请联系站长。</p>
		</div>
	</div>
	<div v-else class="empty card">订单不存在</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()

const busy = ref(false)
const channel = ref('MANUAL_WECHAT')
const channels = ref<Record<string, string>>({})
const payInfo = ref<any>(null)
const payerName = ref('')
const transferTime = ref('')
const order = ref<any>(null)
const items = ref<any[]>([])

if (!isLogin.value) {
	await navigateTo(`/login?redirect=${route.fullPath}`)
} else {
	await load()
	const channelRes: any = await call('/payment/channels').catch(() => null)
	channels.value = (channelRes && channelRes.data) || {}
}

const productNo = computed(() => (items.value.length ? items.value[0].productNo : ''))

async function load() {
	const res: any = await call(`/order/${route.params.orderNo}`)
	if (res.code === 200) {
		order.value = res.data.order
		items.value = res.data.items || []
	}
}

async function startPay() {
	busy.value = true
	try {
		const res: any = await call('/payment', {method: 'POST', body: {orderNo: order.value.orderNo, channel: channel.value}})
		if (res.code === 200) {
			payInfo.value = res.data
		} else {
			alert(res.msg || '生成收款信息失败')
		}
	} finally {
		busy.value = false
	}
}

async function submitProof() {
	if (!payerName.value.trim()) {
		alert('请填写付款人姓名')
		return
	}
	busy.value = true
	try {
		const time = transferTime.value ? transferTime.value.replace('T', ' ') + ':00' : ''
		const res: any = await call(`/payment/${payInfo.value.paymentNo}/manual-confirm`, {
			method: 'POST',
			body: {payerName: payerName.value.trim(), transferTime: time}
		})
		if (res.code === 200) {
			await load()
		} else {
			alert(res.msg || '提交失败')
		}
	} finally {
		busy.value = false
	}
}

function statusText(status: string) {
	return {
		PENDING_PAY: '待支付', PENDING_CONFIRM: '待确认收款', PAID: '已支付', COMPLETED: '已完成',
		CANCELLED: '已取消', CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款'
	}[status] || status
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}
</script>

<style scoped>
.head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
}

.status {
	padding: 3px 12px;
	border-radius: 999px;
	background: #eef7f5;
	color: var(--teal);
	font-size: 12.5px;
}

.summary {
	margin: 14px 0 6px;
	padding: 14px 16px;
	background: #fbfdfd;
	border: 1px solid var(--line);
	border-radius: 10px;
}

.row {
	display: flex;
	justify-content: space-between;
	font-size: 14px;
	padding: 4px 0;
}

.total {
	margin-top: 8px;
	padding-top: 10px;
	border-top: 1px dashed var(--line);
	color: var(--muted);
}

.total strong {
	color: #b45309;
	font-size: 20px;
}

.block {
	margin-top: 18px;
}

.block h3 {
	margin: 0 0 10px;
	font-size: 15px;
}

.channels {
	display: flex;
	gap: 10px;
	flex-wrap: wrap;
	margin-bottom: 14px;
}

.channel {
	padding: 8px 16px;
	border: 1px solid var(--line);
	border-radius: 8px;
	background: #fff;
	cursor: pointer;
	font-size: 13.5px;
	color: var(--ink);
}

.channel.active {
	border-color: var(--teal);
	background: #eef7f5;
	color: var(--teal);
	font-weight: 600;
}

.info {
	list-style: none;
	margin: 0 0 12px;
	padding: 0;
}

.info li {
	display: flex;
	justify-content: space-between;
	padding: 7px 0;
	border-bottom: 1px dashed var(--line);
	font-size: 13.5px;
	color: var(--muted);
}

.info b {
	color: var(--ink);
}

.amount {
	color: #b45309 !important;
	font-size: 16px;
}

.tip {
	margin: 10px 0 0;
	padding: 10px 12px;
	background: #fffbeb;
	border-radius: 8px;
	color: #92400e;
	font-size: 12.5px;
}

.proof {
	margin-top: 22px;
	padding-top: 16px;
	border-top: 1px solid var(--line);
}

.proof h4 {
	margin: 0 0 10px;
	font-size: 14px;
}

.form {
	display: flex;
	gap: 10px;
	flex-wrap: wrap;
	margin-bottom: 12px;
}

.form input {
	padding: 8px 10px;
	border: 1px solid var(--line);
	border-radius: 7px;
	font-size: 13px;
	outline-color: var(--teal);
}
</style>
