<template>
	<div class="card" v-if="order">
		<div class="head">
			<div>
				<h1 class="page-title">{{ t('order.title') }}</h1>
				<p class="page-desc">{{ t('order.orderNo', {no: order.orderNo}) }}</p>
			</div>
			<span class="status">{{ statusText(order.status) }}</span>
		</div>

		<div class="summary">
			<div v-for="item in items" :key="item.id" class="row">
				<span>{{ item.titleSnapshot }}</span>
				<span>¥{{ price(item.amount) }}</span>
			</div>
			<div class="row total">
				<span>{{ t('order.amountDue') }}</span>
				<strong>¥{{ price(order.payAmount) }}</strong>
			</div>
		</div>

		<!--待支付-->
		<template v-if="order.status === 'PENDING_PAY'">
			<div v-if="!payInfo" class="block">
				<h3>{{ t('order.chooseChannel') }}</h3>
				<div class="channels">
					<button v-for="(name, code) in channels" :key="code"
					        :class="['channel', {active: channel === code}]" @click="channel = code">
						{{ channelLabel(code, name) }}
					</button>
				</div>
				<button class="btn" :disabled="busy" @click="startPay">
					{{ busy ? t('order.generating') : t('order.generate') }}
				</button>
			</div>

			<div v-else class="block">
				<h3>{{ t('order.completeTransfer') }}</h3>
				<ul class="info">
					<li><span>{{ t('order.channel') }}</span><b>{{ payInfo.channelName }}</b></li>
					<li><span>应付金额</span><b class="amount">¥{{ price(payInfo.amount) }}</b></li>
					<li><span>{{ t('order.remark') }}</span><b>{{ payInfo.transferRemark }}</b></li>
					<li v-if="payInfo.payeeName"><span>{{ t('order.payee') }}</span><b>{{ payInfo.payeeName }}</b></li>
				</ul>
				<p class="tip">{{ payTip }}</p>

				<!-- 收款码：后端一直有返回 qrCodeUrl，之前这里没渲染，买家只能自己找收款方 -->
				<template v-if="payInfo.qrCodeUrl">
					<div v-if="!qrFailed" class="qr-box">
						<img :src="payInfo.qrCodeUrl" :alt="t('order.channel')" @error="qrFailed = true">
						<p class="qr-tip">{{ t('order.qrTip') }}</p>
					</div>
					<p v-else class="tip warn">{{ t('order.qrFailed') }}</p>
				</template>
				<a v-else-if="payInfo.payUrl" class="pay-link btn" :href="payInfo.payUrl"
				   target="_blank" rel="noopener noreferrer">{{ t('order.paypal') }}</a>

				<div class="proof">
					<h4>{{ t('order.proofTitle') }}</h4>
					<div class="form">
						<input v-model="payerName" :placeholder="t('order.payerName')">
						<input v-model="transferTime" type="datetime-local">
					</div>
					<button class="btn" :disabled="busy" @click="submitProof">{{ t('order.submitProof') }}</button>
				</div>
			</div>
		</template>

		<div v-else-if="order.status === 'PENDING_CONFIRM'" class="block">
			<h3>{{ t('order.waitConfirm') }}</h3>
			<p class="tip">{{ t('order.waitConfirmTip') }}</p>
		</div>

		<div v-else-if="order.status === 'COMPLETED'" class="block">
			<h3>{{ t('order.completed') }}</h3>
			<p class="tip">{{ t('order.completedTip') }}</p>
			<NuxtLink :to="localePath(`/knowledge/${productNo}`)" class="btn">{{ t('order.viewProduct') }}</NuxtLink>
		</div>

		<div v-else class="block">
			<h3>{{ statusText(order.status) }}</h3>
			<p class="tip">{{ t('order.notPayable') }}</p>
		</div>
	</div>
	<div v-else class="empty card">{{ t('order.notFound') }}</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const busy = ref(false)
const channel = ref('MANUAL_WECHAT')
const channels = ref<Record<string, string>>({})
const payInfo = ref<any>(null)
// 图片可能因为没配置好而 404，加载失败就别一直显示裂图
const qrFailed = ref(false)
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
			qrFailed.value = false
			payInfo.value = res.data
		} else {
			alert(res.msg || t('order.failedGenerate'))
		}
	} finally {
		busy.value = false
	}
}

async function submitProof() {
	if (!payerName.value.trim()) {
		alert(t('order.needPayer'))
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
			alert(res.msg || t('order.failedSubmit'))
		}
	} finally {
		busy.value = false
	}
}

function statusText(status: string) {
	const key = `order.status.${status}`
	return te(key) ? t(key) : status
}

/** 渠道显示名与提示都由前端按代码取词条，后端返回的中文只在未知渠道时兜底 */
function channelLabel(code: string, fallback: string) {
	const key = `order.channelName.${code}`
	return te(key) ? t(key) : fallback
}

const payTip = computed(() => {
	const code = payInfo.value?.channel
	const key = `order.tip.${code}`
	return code && te(key) ? t(key) : (payInfo.value?.tip || '')
})

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

.tip.warn {
	background: #fef2f2;
	color: #b91c1c;
}

.qr-box {
	margin: 16px 0 0;
	text-align: center;
}

.qr-box img {
	width: 180px;
	height: 180px;
	padding: 6px;
	object-fit: contain;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: 10px;
}

.qr-tip {
	margin: 6px 0 0;
	font-size: 12.5px;
	color: var(--muted);
}

.pay-link {
	display: inline-block;
	margin: 14px 0 0;
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
