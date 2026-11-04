<template>
	<div>
		<el-form inline>
			<el-form-item label="状态">
				<el-select v-model="query.status" placeholder="全部" clearable style="width: 140px" @change="search">
					<el-option label="待审核" value="APPLIED"/>
					<el-option label="已同意待打款" value="APPROVED"/>
					<el-option label="已退款" value="SUCCESS"/>
					<el-option label="已拒绝" value="REJECTED"/>
				</el-select>
			</el-form-item>
			<el-form-item label="关键词">
				<el-input v-model="query.keyword" placeholder="退款单号 / 订单号" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">搜索</el-button>
				<el-button :icon="Refresh" @click="reset">重置</el-button>
			</el-form-item>
		</el-form>

		<el-alert type="info" :closable="false" show-icon class="tip"
		          title="流程：同意申请 → 线下打款 → 确认已退款（确认后自动撤销该订单发放的权益并回滚销量）"/>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="refundNo" label="退款单号" width="190"/>
			<el-table-column prop="bizNo" label="订单号" width="190"/>
			<el-table-column label="金额" width="100">
				<template #default="{row}"><span class="amount">¥{{ row.amount }}</span></template>
			</el-table-column>
			<el-table-column prop="reason" label="原因" show-overflow-tooltip/>
			<el-table-column label="状态" width="110">
				<template #default="{row}">
					<el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="申请时间" width="160">
				<template #default="{row}">{{ formatDate(row.applyTime) }}</template>
			</el-table-column>
			<el-table-column label="操作" width="220" fixed="right">
				<template #default="{row}">
					<template v-if="row.status === 'APPLIED'">
						<el-button link type="success" @click="approve(row)">同意</el-button>
						<el-button link type="danger" @click="reject(row)">拒绝</el-button>
					</template>
					<el-button v-else-if="row.status === 'APPROVED'" link type="primary" @click="finish(row)">
						确认已退款
					</el-button>
					<span v-else class="muted">{{ row.remark || '-' }}</span>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30, 50]" :total="total" background
		               layout="total, sizes, prev, pager, next, jumper"
		               @size-change="load" @current-change="load"/>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Refresh, Search} from '@element-plus/icons-vue'
import {approveRefund, finishRefund, getRefundList, rejectRefund} from '@/api/trade'
import {formatDate} from '@/util/date'

const loading = ref(false)
const query = reactive({status: '', keyword: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)

function statusText(status) {
	return {APPLIED: '待审核', APPROVED: '待打款', REFUNDING: '退款中', SUCCESS: '已退款', REJECTED: '已拒绝'}[status] || status
}

function statusTag(status) {
	return {APPLIED: 'warning', APPROVED: 'primary', REFUNDING: 'primary', SUCCESS: 'success', REJECTED: 'danger'}[status] || ''
}

async function load() {
	loading.value = true
	try {
		const res = await getRefundList(query)
		list.value = res.data.list || []
		total.value = res.data.total || 0
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

async function approve(row) {
	try {
		await ElMessageBox.confirm(`同意退款 ¥${row.amount}？同意后请尽快完成线下打款。`, '同意退款', {type: 'warning'})
	} catch (e) {
		return
	}
	await approveRefund(row.refundNo)
	ElMessage.success('已同意，等待线下打款')
	load()
}

async function reject(row) {
	let value
	try {
		const result = await ElMessageBox.prompt('请填写拒绝原因（会展示给用户）', '拒绝退款', {
			inputPlaceholder: '例如：已超出可退款期限',
			inputValidator: input => (input && input.trim() ? true : '原因不能为空')
		})
		value = result.value
	} catch (e) {
		return
	}
	await rejectRefund(row.refundNo, value.trim())
	ElMessage.success('已拒绝')
	load()
}

async function finish(row) {
	try {
		await ElMessageBox.confirm(`确认已通过原渠道退款 ¥${row.amount}？确认后将撤销该订单的权益。`, '确认退款完成',
			{type: 'warning'})
	} catch (e) {
		return
	}
	await finishRefund(row.refundNo)
	ElMessage.success('退款完成，权益已撤销')
	load()
}

onMounted(load)
</script>

<style scoped>
.tip {
	margin-bottom: 12px;
}
</style>
