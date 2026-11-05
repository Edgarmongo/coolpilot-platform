<template>
	<div>
		<el-form inline>
			<el-form-item :label="t('状态')">
				<el-select v-model="query.status" :placeholder="t('全部')" clearable style="width: 130px"
				           @change="search">
					<el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
				</el-select>
			</el-form-item>
			<el-form-item :label="t('关键词')">
				<el-input v-model="query.keyword" :placeholder="t('订单号 / 商品名')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
				<el-button :icon="Refresh" @click="reset">{{ t('重置') }}</el-button>
			</el-form-item>
			<el-form-item>
				<el-button type="warning" plain :icon="Clock" :loading="closing" @click="closeExpired">{{ t('关闭超时订单') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column type="expand">
				<template #default="{row}">
					<div class="expand">
						<div class="expand-col">
							<h4>{{ t('商品') }}</h4>
							<p v-for="item in row.items" :key="item.id">
								{{ item.titleSnapshot }} × {{ item.quantity }}
								<span class="muted">¥{{ item.priceSnapshot }}</span>
							</p>
						</div>
						<div class="expand-col">
							<h4>{{ t('支付单') }}</h4>
							<p v-if="!row.paymentList.length" class="muted">{{ t('尚未创建支付单') }}</p>
							<p v-for="pay in row.paymentList" :key="pay.paymentNo">
								{{ pay.paymentNo }} · {{ channelText(pay.channel) }} · {{ payStatusText(pay.status) }}
								<span v-if="pay.payerName" class="muted">{{ t('付款人') }} {{ pay.payerName }}</span>
							</p>
						</div>
					</div>
				</template>
			</el-table-column>
			<el-table-column prop="orderNo" :label="t('订单号')" width="200"/>
			<el-table-column prop="titleSummary" :label="t('内容')" show-overflow-tooltip/>
			<el-table-column :label="t('金额')" width="100">
				<template #default="{row}"><span class="amount">¥{{ row.payAmount }}</span></template>
			</el-table-column>
			<el-table-column :label="t('状态')" width="110">
				<template #default="{row}">
					<el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('下单时间')" width="160">
				<template #default="{row}">{{ formatDate(row.createTime) }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="190" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDetail(row.orderNo)">{{ t('详情') }}</el-button>
					<el-button v-if="row.status === 'PENDING_CONFIRM'" link type="success" @click="confirmRow(row)">{{ t('确认收款') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30, 50]" :total="total" background
		               layout="total, sizes, prev, pager, next, jumper"
		               @size-change="load" @current-change="load"/>

		<el-dialog v-model="detailVisible" :title="t('订单详情')" width="720px">
			<div v-if="detail">
				<div class="detail-grid">
					<div><span>{{ t('订单号') }}</span>{{ detail.order.orderNo }}</div>
					<div><span>{{ t('状态') }}</span>{{ statusText(detail.order.status) }}</div>
					<div><span>{{ t('金额') }}</span>¥{{ detail.order.payAmount }}</div>
					<div><span>{{ t('支付时间') }}</span>{{ formatDate(detail.order.paidTime) }}</div>
				</div>

				<h4 class="detail-title">{{ t('支付记录') }}</h4>
				<el-table :data="detail.payments" size="small" :empty-text="t('无')">
					<el-table-column prop="paymentNo" :label="t('支付单号')" width="190"/>
					<el-table-column :label="t('渠道')" width="110">
						<template #default="{row}">{{ channelText(row.channel) }}</template>
					</el-table-column>
					<el-table-column :label="t('状态')" width="100">
						<template #default="{row}">{{ payStatusText(row.status) }}</template>
					</el-table-column>
					<el-table-column prop="payerName" :label="t('付款人')" width="100"/>
					<el-table-column :label="t('转账时间')" width="150">
						<template #default="{row}">{{ formatDate(row.transferTime, 'YYYY-MM-DD HH:mm') }}</template>
					</el-table-column>
					<el-table-column :label="t('凭证')" width="80">
						<template #default="{row}">
							<el-button v-if="row.proofFileId" link type="primary" @click="viewProof(row.proofFileId)">{{ t('查看') }}</el-button>
							<span v-else class="muted">{{ t('无') }}</span>
						</template>
					</el-table-column>
				</el-table>

				<h4 class="detail-title">{{ t('权益') }}</h4>
				<el-table :data="detail.entitlements" size="small" :empty-text="t('无')">
					<el-table-column prop="productTitle" :label="t('商品')"/>
					<el-table-column prop="entType" :label="t('类型')" width="100"/>
					<el-table-column prop="status" :label="t('状态')" width="100"/>
				</el-table>
			</div>
		</el-dialog>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {useRoute} from 'vue-router'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Clock, Refresh, Search} from '@element-plus/icons-vue'
import {closeExpiredOrders, confirmPayment, getFileToken, getOrderDetail, getOrderList} from '@/api/trade'
import {formatDate} from '@/util/date'

const route = useRoute()

const statusOptions = [
	{label: t('待支付'), value: 'PENDING_PAY'},
	{label: t('待确认收款'), value: 'PENDING_CONFIRM'},
	{label: t('已完成'), value: 'COMPLETED'},
	{label: t('退款中'), value: 'REFUNDING'},
	{label: t('已退款'), value: 'REFUNDED'},
	{label: t('已关闭'), value: 'CLOSED'},
	{label: t('已取消'), value: 'CANCELLED'}
]

const loading = ref(false)
const closing = ref(false)
const query = reactive({
	status: route.query.status || '',
	keyword: '',
	pageNum: 1,
	pageSize: 10
})
const list = ref([])
const total = ref(0)
const detailVisible = ref(false)
const detail = ref(null)

function statusText(status) {
	return {
		PENDING_PAY: t('待支付'), PENDING_CONFIRM: t('待确认收款'), PAID: t('已支付'), COMPLETED: t('已完成'),
		CANCELLED: t('已取消'), CLOSED: t('已关闭'), REFUNDING: t('退款中'), REFUNDED: t('已退款')
	}[status] || status
}

function statusTag(status) {
	return {
		PENDING_PAY: 'info', PENDING_CONFIRM: 'warning', PAID: 'success',
		COMPLETED: 'success', CANCELLED: 'info', CLOSED: 'info', REFUNDING: 'danger', REFUNDED: 'danger'
	}[status] || ''
}

function payStatusText(status) {
	return {INIT: t('待付款'), WAIT_CONFIRM: t('待确认'), SUCCESS: t('已成功'), FAIL: t('失败'), CLOSED: t('已关闭')}[status] || status
}

function channelText(channel) {
	return {MANUAL_WECHAT: t('微信转账'), MANUAL_ALIPAY: t('支付宝转账'), MANUAL_PAYPAL: 'PayPal'}[channel] || channel
}

async function load() {
	loading.value = true
	try {
		const res = await getOrderList(query)
		const rows = res.data.list || []
		total.value = res.data.total || 0
		// 列表需要展示支付单摘要，逐条取详情（订单量小，实现简单可靠）
		const details = await Promise.all(rows.map(row => getOrderDetail(row.orderNo).then(r => r.data).catch(() => null)))
		rows.forEach((row, index) => {
			row.paymentList = details[index] ? details[index].payments || [] : []
		})
		list.value = rows
	} finally {
		loading.value = false
	}
}

function search() {
	query.pageNum = 1
	load()
}

function reset() {
	query.status = ''
	query.keyword = ''
	query.pageNum = 1
	load()
}

async function closeExpired() {
	closing.value = true
	try {
		const res = await closeExpiredOrders()
		ElMessage.success(t('已关闭 {n} 笔超时订单', {n: res.data.count}))
		load()
	} finally {
		closing.value = false
	}
}

async function openDetail(orderNo) {
	const res = await getOrderDetail(orderNo)
	detail.value = res.data
	detailVisible.value = true
}

async function confirmRow(row) {
	const res = await getOrderDetail(row.orderNo)
	const payments = res.data.payments || []
	const pay = payments.find(item => item.status === 'WAIT_CONFIRM') || payments.find(item => item.status === 'INIT')
	if (!pay) {
		ElMessage.warning(t('该订单没有待确认的支付单'))
		return
	}
	const who = pay.payerName ? `付款人：${pay.payerName}` : t('用户尚未填写付款人')
	try {
		await ElMessageBox.confirm(`确认已收到 ¥${pay.amount}？\n${who}\n支付单：${pay.paymentNo}`, t('确认收款'), {
			type: 'warning',
			confirmButtonText: t('确认收款'),
			cancelButtonText: t('取消')
		})
	} catch (e) {
		return
	}
	const result = await confirmPayment(pay.paymentNo)
	ElMessage.success((result.data && result.data.message) || t('已确认'))
	load()
}

async function viewProof(fileId) {
	const res = await getFileToken(fileId)
	window.open(`/api/file/download?token=${encodeURIComponent(res.data.token)}`)
}

onMounted(load)
</script>

<style scoped>
.expand {
	display: flex;
	gap: 40px;
	padding: 4px 20px 10px;
	flex-wrap: wrap;
}

.expand-col {
	flex: 1;
	min-width: 240px;
}

.expand-col h4 {
	margin: 0 0 6px;
	font-size: 13px;
	color: #606266;
}

.expand-col p {
	margin: 3px 0;
	font-size: 13px;
	color: #303133;
}

.detail-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 8px 20px;
	padding: 12px 14px;
	background: #fafafa;
	border-radius: 6px;
	font-size: 13px;
	color: #303133;
}

.detail-grid span {
	display: inline-block;
	width: 70px;
	color: #909399;
}

.detail-title {
	margin: 18px 0 8px;
	font-size: 13px;
	color: #606266;
}
</style>
