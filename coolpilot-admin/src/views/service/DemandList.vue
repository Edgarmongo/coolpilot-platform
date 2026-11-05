<template>
	<div>
		<el-form inline>
			<el-form-item :label="t('状态')">
				<el-select v-model="query.status" :placeholder="t('全部')" clearable style="width: 150px" @change="search">
					<el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
				</el-select>
			</el-form-item>
			<el-form-item :label="t('关键词')">
				<el-input v-model="query.keyword" :placeholder="t('需求号 / 标题')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
				<el-button :icon="Refresh" @click="reset">{{ t('重置') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('需求')" min-width="240">
				<template #default="{row}">
					<div class="title">{{ row.title }}</div>
					<div class="muted mono">{{ row.demandNo }}</div>
				</template>
			</el-table-column>
			<el-table-column :label="t('客户')" width="140">
				<template #default="{row}">
					<span>{{ row.nickname || row.username || '—' }}</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('类型')" width="92">
				<template #default="{row}">{{ typeText(row.projectType) }}</template>
			</el-table-column>
			<el-table-column :label="t('预算')" width="150">
				<template #default="{row}">{{ budgetText(row) }}</template>
			</el-table-column>
			<el-table-column :label="t('状态')" width="120">
				<template #default="{row}">
					<el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('提交时间')" width="160">
				<template #default="{row}">{{ formatTime(row.createTime) }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="90" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" size="small" @click="open(row)">{{ t('处理') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination
			class="pager"
			background
			layout="total, prev, pager, next"
			:total="total"
			:page-size="query.pageSize"
			:current-page="query.pageNum"
			@current-change="onPage"/>

		<el-drawer v-model="drawer" :title="detail?.demand?.demandNo || t('需求详情')" size="720px">
			<div v-if="detail" v-loading="detailLoading">
				<div class="head">
					<div>
						<h3 class="name">{{ detail.demand.title }}</h3>
						<div class="muted">
							{{ typeText(detail.demand.projectType) }} ·
							{{ t('提交于') }}{{ formatTime(detail.demand.createTime) }}
							<template v-if="detail.demand.productTitle"> · {{ t('来自「{name}」', {name: detail.demand.productTitle}) }}</template>
						</div>
					</div>
					<el-tag :type="statusTag(detail.demand.status)">{{ statusText(detail.demand.status) }}</el-tag>
				</div>

				<el-descriptions :column="2" size="small" border class="block">
					<el-descriptions-item :label="t('预算')">{{ budgetText(detail.demand) }}</el-descriptions-item>
					<el-descriptions-item :label="t('期望交付')">{{ detail.demand.expectDate || t('未填写') }}</el-descriptions-item>
					<el-descriptions-item :label="t('联系方式')" :span="2">
						<span class="contact">{{ detail.demand.contact || t('未填写') }}</span>
					</el-descriptions-item>
					<el-descriptions-item :label="t('技术栈')" :span="2">{{ detail.demand.techStack || t('未填写') }}</el-descriptions-item>
				</el-descriptions>

				<div class="block">
					<div class="label">{{ t('需求描述') }}</div>
					<div class="desc">{{ detail.demand.description }}</div>
				</div>

				<div v-if="detail.demand.rejectReason" class="block">
					<el-alert type="error" :closable="false" :title="t('未通过原因：') + detail.demand.rejectReason"/>
				</div>

				<!-- 审核 -->
				<div v-if="detail.demand.status === 'PENDING_REVIEW'" class="block actions">
					<el-button type="success" :loading="acting" @click="approve">{{ t('通过，进入待报价') }}</el-button>
					<el-button type="danger" plain :loading="acting" @click="reject">{{ t('拒绝') }}</el-button>
				</div>

				<!-- 状态推进 -->
				<div v-if="nextStatuses.length" class="block actions">
					<span class="label inline">{{ t('推进到') }}</span>
					<el-button v-for="s in nextStatuses" :key="s" size="small" plain :loading="acting"
					           @click="move(s)">{{ statusText(s) }}</el-button>
				</div>

				<!-- 报价 -->
				<el-divider content-position="left">{{ t('报价单') }}</el-divider>
				<el-table :data="detail.quotes" size="small">
					<el-table-column prop="version" :label="t('版本')" width="64"/>
					<el-table-column :label="t('金额')" width="110">
						<template #default="{row}">¥{{ money(row.amount) }}</template>
					</el-table-column>
					<el-table-column prop="scope" :label="t('范围')" show-overflow-tooltip/>
					<el-table-column :label="t('有效期')" width="110">
						<template #default="{row}">{{ row.validUntil || '—' }}</template>
					</el-table-column>
					<el-table-column :label="t('状态')" width="90">
						<template #default="{row}">
							<el-tag size="small" :type="quoteTag(row.status)">{{ quoteText(row.status) }}</el-tag>
						</template>
					</el-table-column>
					<el-table-column :label="t('操作')" width="90">
						<template #default="{row}">
							<el-button v-if="row.status === 'DRAFT'" link type="primary" size="small"
							           :loading="acting" @click="send(row)">{{ t('发送') }}</el-button>
						</template>
					</el-table-column>
				</el-table>

				<div v-if="canQuote" class="quote-form">
					<el-form :model="quoteForm" label-width="80px" size="small">
						<el-form-item :label="t('金额')" required>
							<el-input-number v-model="quoteForm.amount" :min="0" :precision="2" :step="1000"
							                 controls-position="right"/>
						</el-form-item>
						<el-form-item :label="t('范围')">
							<el-input v-model="quoteForm.scope" type="textarea" :rows="3"
							          :placeholder="t('工作范围、交付物、不含项')"/>
						</el-form-item>
						<el-form-item :label="t('有效期')">
							<el-date-picker v-model="quoteForm.validUntil" type="date" value-format="YYYY-MM-DD"
							                :placeholder="t('选择日期')"/>
						</el-form-item>
						<el-form-item>
							<el-button type="primary" :loading="acting" @click="saveQuote">{{ t('创建报价草稿') }}</el-button>
							<span class="hint">{{ t('创建后点「发送」，客户才能看到并确认') }}</span>
						</el-form-item>
					</el-form>
				</div>

				<!-- 项目 -->
				<template v-if="detail.projects?.length">
					<el-divider content-position="left">{{ t('项目') }}</el-divider>
					<div v-for="p in detail.projects" :key="p.id" class="project-row">
						<span class="mono">{{ p.projectNo }}</span>
						<span>{{ p.name }}</span>
						<span>¥{{ money(p.amount) }}</span>
						<el-tag size="small">{{ projectText(p.status) }}</el-tag>
					</div>
				</template>

				<!-- 流水 -->
				<el-divider content-position="left">{{ t('进度记录') }}</el-divider>
				<el-timeline>
					<el-timeline-item v-for="event in detail.timeline" :key="event.id"
					                  :timestamp="formatTime(event.createTime)" size="small">
						<div>{{ actionText(event.action) }}
							<span class="muted">{{ statusText(event.fromStatus) }} → {{ statusText(event.toStatus) }}</span>
						</div>
						<div class="muted small">{{ event.operatorName }}<template v-if="event.remark"> · {{ event.remark }}</template></div>
					</el-timeline-item>
				</el-timeline>
			</div>
		</el-drawer>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref, computed} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Search, Refresh} from '@element-plus/icons-vue'
import {getDemands, getDemand, reviewDemand, moveDemand, createQuote, sendQuote} from '@/api/service'

const statusOptions = [
	{value: 'PENDING_REVIEW', label: t('待审核')},
	{value: 'PENDING_CONTACT', label: t('待联系')},
	{value: 'EVALUATING', label: t('评估中')},
	{value: 'PENDING_QUOTE', label: t('待报价')},
	{value: 'PENDING_CONFIRM', label: t('待客户确认')},
	{value: 'IN_PROGRESS', label: t('实施中')},
	{value: 'PENDING_ACCEPT', label: t('待验收')},
	{value: 'COMPLETED', label: t('已完成')},
	{value: 'REJECTED', label: t('未通过')},
	{value: 'CANCELLED', label: t('已取消')},
	{value: 'DISPUTED', label: t('有争议')}
]

const flowNext = {
	PENDING_REVIEW: ['PENDING_CONTACT', 'PENDING_QUOTE'],
	PENDING_CONTACT: ['EVALUATING', 'PENDING_QUOTE', 'CANCELLED'],
	EVALUATING: ['PENDING_QUOTE', 'PENDING_CONTACT', 'CANCELLED'],
	PENDING_QUOTE: ['PENDING_CONFIRM', 'CANCELLED'],
	PENDING_CONFIRM: ['PENDING_QUOTE', 'CANCELLED'],
	IN_PROGRESS: ['PENDING_ACCEPT', 'DISPUTED'],
	PENDING_ACCEPT: ['COMPLETED', 'IN_PROGRESS', 'DISPUTED'],
	DISPUTED: ['IN_PROGRESS', 'COMPLETED', 'CANCELLED']
}

const query = reactive({status: '', keyword: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const acting = ref(false)

const quoteForm = reactive({amount: null, scope: '', validUntil: ''})

const canQuote = computed(() => detail.value?.demand?.status === 'PENDING_QUOTE')
const nextStatuses = computed(() => {
	const status = detail.value?.demand?.status
	if (status === 'PENDING_REVIEW') {
		return []
	}
	return flowNext[status] || []
})

onMounted(load)

async function load() {
	loading.value = true
	try {
		const res = await getDemands(query)
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

function onPage(page) {
	query.pageNum = page
	load()
}

async function open(row) {
	drawer.value = true
	await refreshDetail(row.demandNo)
}

async function refreshDetail(demandNo) {
	detailLoading.value = true
	try {
		const res = await getDemand(demandNo)
		detail.value = res.data
	} finally {
		detailLoading.value = false
	}
}

async function approve() {
	acting.value = true
	try {
		const res = await reviewDemand(detail.value.demand.demandNo, {approve: true})
		ElMessage.success(res.msg)
		await refreshDetail(detail.value.demand.demandNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

async function reject() {
	let reason
	try {
		const result = await ElMessageBox.prompt(t('请说明拒绝原因（客户可见）'), '拒绝需求', {
			inputPlaceholder: t('例如：需求范围不清晰，建议先补充文档'),
			inputValidator: value => (value && value.trim().length >= 2) || t('至少 2 个字')
		})
		reason = result.value
	} catch (e) {
		return
	}
	acting.value = true
	try {
		const res = await reviewDemand(detail.value.demand.demandNo, {approve: false, reason})
		ElMessage.success(res.msg)
		await refreshDetail(detail.value.demand.demandNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

async function move(status) {
	acting.value = true
	try {
		const res = await moveDemand(detail.value.demand.demandNo, {status})
		ElMessage.success(res.msg)
		await refreshDetail(detail.value.demand.demandNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

async function saveQuote() {
	if (!quoteForm.amount || quoteForm.amount <= 0) {
		ElMessage.warning(t('请填写报价金额'))
		return
	}
	acting.value = true
	try {
		const res = await createQuote({
			demandId: detail.value.demand.id,
			amount: quoteForm.amount,
			scope: quoteForm.scope,
			validUntil: quoteForm.validUntil || null
		})
		ElMessage.success(res.msg)
		quoteForm.amount = null
		quoteForm.scope = ''
		quoteForm.validUntil = ''
		await refreshDetail(detail.value.demand.demandNo)
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

async function send(row) {
	acting.value = true
	try {
		const res = await sendQuote(row.id)
		ElMessage.success(res.msg)
		await refreshDetail(detail.value.demand.demandNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

function errorMsg(e) {
	return e?.response?.data?.msg || e?.message || t('操作失败')
}

function money(value) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function budgetText(row) {
	if (row.budgetNegotiable) {
		return t('面议')
	}
	if (row.budgetMin && row.budgetMax) {
		return `¥${money(row.budgetMin)} - ${money(row.budgetMax)}`
	}
	if (row.budgetMax) {
		return `≤ ¥${money(row.budgetMax)}`
	}
	if (row.budgetMin) {
		return `≥ ¥${money(row.budgetMin)}`
	}
	return t('面议')
}

function typeText(type) {
	return {BACKEND: t('后端开发'), AI: t('AI 应用'), DEPLOY: t('部署运维'), CONSULT: t('技术咨询'), OTHER: t('其他')}[type] || type
}

function statusText(status) {
	return statusOptions.find(item => item.value === status)?.label || status || ''
}

function statusTag(status) {
	if (['COMPLETED', 'IN_PROGRESS'].includes(status)) {
		return 'success'
	}
	if (['REJECTED', 'CANCELLED', 'DISPUTED'].includes(status)) {
		return 'danger'
	}
	if (['PENDING_REVIEW', 'PENDING_QUOTE'].includes(status)) {
		return 'warning'
	}
	return 'info'
}

function quoteText(status) {
	return {DRAFT: t('草稿'), SENT: t('待确认'), ACCEPTED: t('已接受'), REJECTED: t('已拒绝'), EXPIRED: t('已失效')}[status] || status
}

function quoteTag(status) {
	return {DRAFT: 'info', SENT: 'warning', ACCEPTED: 'success', REJECTED: 'danger', EXPIRED: 'info'}[status] || 'info'
}

function projectText(status) {
	return {NOT_STARTED: t('未开始'), IN_PROGRESS: t('实施中'), PENDING_ACCEPT: t('待验收'), COMPLETED: t('已完成'), ARCHIVED: t('已归档')}[status] || status
}

function actionText(action) {
	return {
		SUBMIT: t('提交需求'), APPROVE: t('审核通过'), REJECT: t('拒绝'), MOVE: t('状态调整'),
		QUOTE: t('创建报价'), UPDATE: t('修改报价'), SEND: t('发送报价'), SEND_QUOTE: t('报价已发出'),
		ACCEPT: t('接受报价'), ACCEPT_QUOTE: t('确认报价'), REJECT_QUOTE: t('拒绝报价'), CANCEL: t('取消需求')
	}[action] || action
}

function formatTime(value) {
	if (!value) {
		return '—'
	}
	return new Date(value).toLocaleString('zh-CN', {hour12: false})
}
</script>

<style scoped>
.title {
	font-weight: 600;
}

.muted {
	color: #909399;
}

.mono {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	font-size: 12px;
}

.small {
	font-size: 12px;
}

.pager {
	margin-top: 12px;
	justify-content: flex-end;
}

.head {
	display: flex;
	justify-content: space-between;
	gap: 12px;
	align-items: flex-start;
}

.name {
	margin: 0 0 4px;
}

.block {
	margin-bottom: 14px;
}

.label {
	font-size: 13px;
	color: #606266;
	margin-bottom: 6px;
}

.label.inline {
	margin: 0 8px 0 0;
}

.desc {
	white-space: pre-wrap;
	line-height: 1.8;
	background: #fafafa;
	border-radius: 6px;
	padding: 10px 12px;
	font-size: 13px;
}

.contact {
	font-weight: 600;
	color: #b45309;
}

.actions {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}

.quote-form {
	margin-top: 12px;
	border-top: 1px dashed #ebeef5;
	padding-top: 12px;
}

.hint {
	color: #909399;
	font-size: 12px;
	margin-left: 8px;
}

.project-row {
	display: flex;
	align-items: center;
	gap: 12px;
	font-size: 13px;
	padding: 6px 0;
}
</style>
