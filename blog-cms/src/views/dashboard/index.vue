<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">仪表盘</h2>
				<p class="page-desc">交易概览与待办</p>
			</div>
			<el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
		</div>

		<el-row :gutter="14">
			<el-col :xs="12" :sm="12" :md="6" v-for="card in cards" :key="card.key">
				<div class="stat" :class="card.tone">
					<span class="stat-label">{{ card.label }}</span>
					<strong class="stat-value">{{ card.value }}</strong>
					<span class="stat-hint">{{ card.hint }}</span>
				</div>
			</el-col>
		</el-row>

		<el-row :gutter="14" class="mt">
			<el-col :xs="24" :md="14">
				<div class="page-card">
					<div class="card-head">
						<h3>最近订单</h3>
						<router-link to="/trade/order" class="card-link">全部订单</router-link>
					</div>
					<el-table :data="orders" size="small" empty-text="暂无订单">
						<el-table-column prop="orderNo" label="订单号" width="190"/>
						<el-table-column prop="titleSummary" label="内容" show-overflow-tooltip/>
						<el-table-column label="金额" width="90">
							<template #default="{row}"><span class="amount">¥{{ row.payAmount }}</span></template>
						</el-table-column>
						<el-table-column label="状态" width="100">
							<template #default="{row}">
								<el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
							</template>
						</el-table-column>
					</el-table>
				</div>
			</el-col>

			<el-col :xs="24" :md="10">
				<div class="page-card">
					<div class="card-head">
						<h3>待办</h3>
					</div>
					<router-link to="/trade/order?status=PENDING_CONFIRM" class="todo">
						<div>
							<strong>待确认收款</strong>
							<p>用户已提交转账凭证，核对后确认即发权益</p>
						</div>
						<el-badge :value="waiting" :hidden="!waiting" type="danger">
							<el-icon class="todo-icon"><Wallet/></el-icon>
						</el-badge>
					</router-link>
					<router-link to="/trade/refund" class="todo">
						<div>
							<strong>退款处理</strong>
							<p>同意申请 → 线下打款 → 确认后撤销权益</p>
						</div>
						<el-icon class="todo-icon"><RefreshLeft/></el-icon>
					</router-link>
					<router-link to="/trade/entitlement" class="todo">
						<div>
							<strong>权益管理</strong>
							<p>人工发放、撤销与流水核查</p>
						</div>
						<el-icon class="todo-icon"><Key/></el-icon>
					</router-link>
				</div>
			</el-col>
		</el-row>
	</div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {Key, Refresh, RefreshLeft, Wallet} from '@element-plus/icons-vue'
import {getOrderList, getSalesStats} from '@/api/trade'

const loading = ref(false)
const stats = ref({})
const orders = ref([])

const waiting = computed(() => stats.value.waitingConfirm || 0)

const cards = computed(() => [
	{key: 'paid', label: '累计销售额', value: money(stats.value.paidAmount), hint: '含已退款订单', tone: 'tone-teal'},
	{key: 'net', label: '净收入', value: money(stats.value.netAmount), hint: '销售额 − 退款额', tone: 'tone-green'},
	{key: 'refund', label: '退款金额', value: money(stats.value.refundAmount), hint: '仅统计已完成退款', tone: 'tone-amber'},
	{key: 'wait', label: '待确认收款', value: waiting.value, hint: '确认后自动发放权益', tone: 'tone-blue'}
])

function money(value) {
	return '¥' + Number(value || 0).toFixed(2)
}

function statusText(status) {
	return {
		PENDING_PAY: '待支付', PENDING_CONFIRM: '待确认', PAID: '已支付', COMPLETED: '已完成',
		CANCELLED: '已取消', CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款'
	}[status] || status
}

function statusTag(status) {
	return {
		PENDING_PAY: 'info', PENDING_CONFIRM: 'warning', PAID: 'success',
		COMPLETED: 'success', CANCELLED: 'info', CLOSED: 'info', REFUNDING: 'danger', REFUNDED: 'danger'
	}[status] || ''
}

async function load() {
	loading.value = true
	try {
		const [statRes, orderRes] = await Promise.all([
			getSalesStats(),
			getOrderList({pageNum: 1, pageSize: 5})
		])
		stats.value = statRes.data || {}
		orders.value = (orderRes.data && orderRes.data.list) || []
	} finally {
		loading.value = false
	}
}

onMounted(load)
</script>

<style scoped>
.mt {
	margin-top: 14px;
}

.stat {
	display: flex;
	flex-direction: column;
	gap: 6px;
	padding: 16px 18px;
	margin-bottom: 14px;
	background: #fff;
	border-radius: 10px;
	border-left: 3px solid #0f766e;
}

.tone-teal { border-left-color: #0f766e; }
.tone-green { border-left-color: #16a34a; }
.tone-amber { border-left-color: #d97706; }
.tone-blue { border-left-color: #2563eb; }

.stat-label {
	font-size: 12.5px;
	color: #909399;
}

.stat-value {
	font-size: 22px;
	font-weight: 600;
	color: #303133;
}

.stat-hint {
	font-size: 11.5px;
	color: #b0b3b8;
}

.card-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10px;
}

.card-head h3 {
	margin: 0;
	font-size: 14px;
	font-weight: 600;
}

.card-link {
	font-size: 12.5px;
	color: #0f766e;
}

.todo {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 14px;
	padding: 12px;
	border-radius: 8px;
	background: #fafafa;
	margin-bottom: 10px;
	transition: background .15s;
}

.todo:hover {
	background: #f0f9f7;
}

.todo strong {
	font-size: 13.5px;
	color: #303133;
}

.todo p {
	margin: 3px 0 0;
	font-size: 11.5px;
	color: #909399;
}

.todo-icon {
	font-size: 20px;
	color: #0f766e;
}
</style>
