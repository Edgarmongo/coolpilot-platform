<template>
	<div>
		<el-form inline>
			<el-form-item :label="t('状态')">
				<el-select v-model="query.status" :placeholder="t('全部')" clearable style="width: 140px" @change="search">
					<el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
				</el-select>
			</el-form-item>
			<el-form-item :label="t('关键词')">
				<el-input v-model="query.keyword" :placeholder="t('项目号 / 名称')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
				<el-button :icon="Refresh" @click="reset">{{ t('重置') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('项目')" min-width="240">
				<template #default="{row}">
					<div class="title">{{ row.name }}</div>
					<div class="muted mono">{{ row.projectNo }}</div>
				</template>
			</el-table-column>
			<el-table-column :label="t('客户')" width="120">
				<template #default="{row}">{{ row.nickname || '—' }}</template>
			</el-table-column>
			<el-table-column :label="t('金额')" width="110">
				<template #default="{row}">¥{{ money(row.amount) }}</template>
			</el-table-column>
			<el-table-column :label="t('里程碑')" width="110">
				<template #default="{row}">{{ t('{a} / {b} 已验收', {a: row.acceptedCount || 0, b: row.milestoneCount || 0}) }}</template>
			</el-table-column>
			<el-table-column :label="t('状态')" width="100">
				<template #default="{row}">
					<el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('开始')" width="110">
				<template #default="{row}">{{ row.startDate || '—' }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="90" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" size="small" @click="open(row)">{{ t('管理') }}</el-button>
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

		<el-drawer v-model="drawer" :title="detail?.project?.projectNo || t('项目详情')" size="780px">
			<div v-if="detail" v-loading="detailLoading">
				<div class="head">
					<div>
						<h3 class="name">{{ detail.project.name }}</h3>
						<div class="muted">
							¥{{ money(detail.project.amount) }} ·
							{{ detail.project.startDate || t('未定') }} 起
							<template v-if="detail.project.demandNo"> · {{ t(' · 需求 {no}', {no: detail.project.demandNo}) }}</template>
						</div>
					</div>
					<div class="head-right">
						<el-tag :type="statusTag(detail.project.status)">{{ statusText(detail.project.status) }}</el-tag>
						<el-dropdown trigger="click" @command="moveProject">
							<el-button size="small" plain>{{ t('调整状态') }}</el-button>
							<template #dropdown>
								<el-dropdown-menu>
									<el-dropdown-item v-for="s in projectStatuses" :key="s.value" :command="s.value">
										{{ s.label }}
									</el-dropdown-item>
								</el-dropdown-menu>
							</template>
						</el-dropdown>
					</div>
				</div>

				<el-divider content-position="left">{{ t('里程碑') }}</el-divider>
				<el-table :data="detail.milestones" size="small">
					<el-table-column prop="sort" label="#" width="50"/>
					<el-table-column prop="name" :label="t('名称')" min-width="140"/>
					<el-table-column :label="t('金额')" width="100">
						<template #default="{row}">¥{{ money(row.amount) }}</template>
					</el-table-column>
					<el-table-column :label="t('截止')" width="110">
						<template #default="{row}">{{ row.dueDate || '—' }}</template>
					</el-table-column>
					<el-table-column :label="t('状态')" width="96">
						<template #default="{row}">
							<el-tag size="small" :type="milestoneTag(row.status)">{{ milestoneText(row.status) }}</el-tag>
						</template>
					</el-table-column>
					<el-table-column :label="t('操作')" width="150">
						<template #default="{row}">
							<el-button v-if="row.status !== 'ACCEPTED'" link type="primary" size="small"
							           @click="openEditMilestone(row)">{{ t('编辑') }}</el-button>
							<el-button v-if="['PENDING','REJECTED','DELIVERING'].includes(row.status)" link
							           type="success" size="small" @click="openDeliver(row)">{{ t('交付') }}</el-button>
							<el-button v-if="row.status === 'ACCEPTED'" link size="small" disabled>{{ t('已验收') }}</el-button>
						</template>
					</el-table-column>
				</el-table>

				<div class="milestone-form">
					<el-form :model="milestoneForm" label-width="80px" size="small" inline>
						<el-form-item :label="t('名称')" required>
							<el-input v-model="milestoneForm.name" style="width: 180px"/>
						</el-form-item>
						<el-form-item :label="t('金额')">
							<el-input-number v-model="milestoneForm.amount" :min="0" :precision="2"
							                 controls-position="right" style="width: 130px"/>
						</el-form-item>
						<el-form-item :label="t('截止')">
							<el-date-picker v-model="milestoneForm.dueDate" type="date" value-format="YYYY-MM-DD"
							                style="width: 140px"/>
						</el-form-item>
						<el-form-item>
							<el-button type="primary" :loading="acting" @click="saveMilestone">
								{{ milestoneForm.id ? t('保存') : t('新增里程碑') }}
							</el-button>
							<el-button v-if="milestoneForm.id" @click="resetMilestone">{{ t('取消') }}</el-button>
						</el-form-item>
					</el-form>
					<el-input v-model="milestoneForm.remark" size="small" :placeholder="t('备注（可选）')" style="max-width: 520px"/>
				</div>

				<el-divider content-position="left">{{ t('交付记录') }}</el-divider>
				<div v-if="!detail.deliveries?.length" class="muted small">{{ t('还没有交付记录') }}</div>
				<div v-for="item in detail.deliveries" :key="item.id" class="delivery">
					<el-tag size="small">{{ deliveryText(item.deliveryType) }}</el-tag>
					<span class="muted small">{{ formatTime(item.deliveredTime) }}</span>
					<span class="flex-1">{{ item.content || item.fileName }}</span>
					<el-tag size="small" :type="item.status === 'ACCEPTED' ? 'success' : (item.status === 'REJECTED' ? 'danger' : 'warning')">
						{{ item.status === 'ACCEPTED' ? t('已验收') : (item.status === 'REJECTED' ? t('已打回') : t('待验收')) }}
					</el-tag>
				</div>

				<el-divider content-position="left">{{ t('进度记录') }}</el-divider>
				<el-timeline>
					<el-timeline-item v-for="event in detail.events" :key="event.id"
					                  :timestamp="formatTime(event.createTime)" size="small">
						<div>{{ actionText(event.action) }}
							<span v-if="event.toStatus" class="muted">{{ event.fromStatus || '—' }} → {{ event.toStatus }}</span>
						</div>
						<div class="muted small">{{ event.operatorName }}<template v-if="event.remark"> · {{ event.remark }}</template></div>
					</el-timeline-item>
				</el-timeline>
			</div>
		</el-drawer>

		<!-- 交付对话框 -->
		<el-dialog v-model="deliverDialog" :title="t('交付里程碑')" width="560px">
			<el-form :model="deliverForm" label-width="80px" size="small">
				<el-form-item :label="t('交付类型')">
					<el-radio-group v-model="deliverForm.deliveryType">
						<el-radio-button value="LINK">{{ t('链接') }}</el-radio-button>
						<el-radio-button value="TEXT">{{ t('文字说明') }}</el-radio-button>
						<el-radio-button value="FILE">{{ t('文件') }}</el-radio-button>
					</el-radio-group>
				</el-form-item>
				<el-form-item v-if="deliverForm.deliveryType === 'FILE'" :label="t('文件')" required>
					<input ref="fileInput" type="file" hidden @change="uploadDeliveryFile">
					<el-button size="small" :loading="uploading" @click="$refs.fileInput.click()">
						{{ deliverForm.fileId ? t('已选：') + deliverForm.fileName : t('选择并上传') }}
					</el-button>
					<div class="hint">{{ t('交付文件为私有存储，仅项目客户可下载') }}</div>
				</el-form-item>
				<el-form-item :label="t('内容')">
					<el-input v-model="deliverForm.content" type="textarea" :rows="3"
					          :placeholder="deliverForm.deliveryType === 'FILE' ? t('补充说明（可选）') : t('链接地址或交付说明')"/>
				</el-form-item>
				<el-form-item :label="t('备注')">
					<el-input v-model="deliverForm.remark" :placeholder="t('显示在进度记录里（可选）')"/>
				</el-form-item>
			</el-form>
			<template #footer>
				<el-button @click="deliverDialog = false">{{ t('取消') }}</el-button>
				<el-button type="primary" :loading="acting" @click="submitDeliver">{{ t('确认交付') }}</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Search, Refresh} from '@element-plus/icons-vue'
import {getProjects, getProject, addMilestone, updateMilestone, deliverMilestone, moveProject as moveProjectApi} from '@/api/service'
import {uploadFile} from '@/api/product'

const statusOptions = [
	{value: 'NOT_STARTED', label: t('未开始')},
	{value: 'IN_PROGRESS', label: t('实施中')},
	{value: 'PENDING_ACCEPT', label: t('待验收')},
	{value: 'COMPLETED', label: t('已完成')},
	{value: 'ARCHIVED', label: t('已归档')}
]

const projectStatuses = statusOptions

const query = reactive({status: '', keyword: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const drawer = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const acting = ref(false)

const milestoneForm = reactive({id: null, name: '', amount: null, dueDate: '', remark: ''})
const deliverDialog = ref(false)
const deliverForm = reactive({milestoneId: null, deliveryType: 'LINK', fileId: null, fileName: '', content: '', remark: ''})
const uploading = ref(false)

onMounted(load)

async function load() {
	loading.value = true
	try {
		const res = await getProjects(query)
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
	await refreshDetail(row.projectNo)
}

async function refreshDetail(projectNo) {
	detailLoading.value = true
	try {
		const res = await getProject(projectNo)
		detail.value = res.data
	} finally {
		detailLoading.value = false
	}
}

function openEditMilestone(row) {
	milestoneForm.id = row.id
	milestoneForm.name = row.name
	milestoneForm.amount = Number(row.amount)
	milestoneForm.dueDate = row.dueDate || ''
	milestoneForm.remark = row.remark || ''
}

function resetMilestone() {
	milestoneForm.id = null
	milestoneForm.name = ''
	milestoneForm.amount = null
	milestoneForm.dueDate = ''
	milestoneForm.remark = ''
}

async function saveMilestone() {
	if (!milestoneForm.name.trim()) {
		ElMessage.warning(t('请填写里程碑名称'))
		return
	}
	acting.value = true
	try {
		const payload = {
			projectId: detail.value.project.id,
			name: milestoneForm.name,
			amount: milestoneForm.amount,
			dueDate: milestoneForm.dueDate || null,
			remark: milestoneForm.remark
		}
		const res = milestoneForm.id
			? await updateMilestone(milestoneForm.id, payload)
			: await addMilestone(payload)
		ElMessage.success(res.msg)
		resetMilestone()
		await refreshDetail(detail.value.project.projectNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

function openDeliver(row) {
	deliverForm.milestoneId = row.id
	deliverForm.deliveryType = 'LINK'
	deliverForm.fileId = null
	deliverForm.fileName = ''
	deliverForm.content = ''
	deliverForm.remark = ''
	deliverDialog.value = true
}

async function uploadDeliveryFile(event) {
	const file = event.target.files?.[0]
	if (!file) {
		return
	}
	uploading.value = true
	try {
		const body = new FormData()
		body.append('file', file)
		const res = await uploadFile(body)
		deliverForm.fileId = res.data.fileId
		deliverForm.fileName = file.name
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		uploading.value = false
	}
}

async function submitDeliver() {
	const type = deliverForm.deliveryType
	if (type === 'FILE' && !deliverForm.fileId) {
		ElMessage.warning(t('请上传交付文件'))
		return
	}
	if (type !== 'FILE' && !deliverForm.content.trim()) {
		ElMessage.warning(t('请填写交付内容'))
		return
	}
	acting.value = true
	try {
		const res = await deliverMilestone(deliverForm.milestoneId, {
			deliveryType: type,
			fileId: deliverForm.fileId,
			content: deliverForm.content,
			remark: deliverForm.remark
		})
		ElMessage.success(res.msg)
		deliverDialog.value = false
		await refreshDetail(detail.value.project.projectNo)
		load()
	} catch (e) {
		ElMessage.error(errorMsg(e))
	} finally {
		acting.value = false
	}
}

async function moveProject(status) {
	acting.value = true
	try {
		const res = await moveProjectApi(detail.value.project.projectNo, {status})
		ElMessage.success(res.msg)
		await refreshDetail(detail.value.project.projectNo)
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

function statusText(status) {
	return statusOptions.find(item => item.value === status)?.label || status || ''
}

function statusTag(status) {
	if (['COMPLETED', 'IN_PROGRESS'].includes(status)) {
		return 'success'
	}
	if (['ARCHIVED'].includes(status)) {
		return 'info'
	}
	if (['PENDING_ACCEPT'].includes(status)) {
		return 'warning'
	}
	return 'info'
}

function milestoneText(status) {
	return {PENDING: t('待交付'), DELIVERING: t('交付中'), DELIVERED: t('待验收'), ACCEPTED: t('已验收'), REJECTED: t('已打回')}[status] || status
}

function milestoneTag(status) {
	if (status === 'ACCEPTED') {
		return 'success'
	}
	if (status === 'REJECTED') {
		return 'danger'
	}
	if (status === 'DELIVERED') {
		return 'warning'
	}
	return 'info'
}

function deliveryText(type) {
	return {FILE: t('文件'), LINK: t('链接'), TEXT: t('说明')}[type] || type
}

function actionText(action) {
	return {
		CREATE: t('项目创建'), DELIVER: t('提交交付'), COMPLETE: t('项目完成'), MOVE: t('状态调整'),
		ADD_MILESTONE: t('新增里程碑'), UPDATE: t('里程碑调整'), ACCEPT: t('验收通过'), REJECT: t('验收打回')
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

.head-right {
	display: flex;
	align-items: center;
	gap: 8px;
}

.name {
	margin: 0 0 4px;
}

.milestone-form {
	margin-top: 12px;
	border-top: 1px dashed #ebeef5;
	padding-top: 12px;
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.delivery {
	display: flex;
	align-items: center;
	gap: 10px;
	padding: 6px 0;
	font-size: 13px;
}

.flex-1 {
	flex: 1;
	word-break: break-all;
}

.hint {
	color: #909399;
	font-size: 12px;
}
</style>
