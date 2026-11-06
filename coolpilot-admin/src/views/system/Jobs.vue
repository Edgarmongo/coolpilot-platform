<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('定时任务') }}</h2>
				<p class="page-desc">{{ t('由 Quartz 调度，页面上的「执行」与定时触发用的是同一个任务') }}</p>
			</div>
			<el-button :icon="Refresh" @click="loadJobs">{{ t('刷新') }}</el-button>
		</div>

		<el-alert class="mb" type="info" :closable="false" show-icon
		          :title="t('执行是异步的：点完立即返回，实际结果看「日志」。同一个任务如果上一次还没跑完，两次执行可能重叠。')"/>

		<el-table v-loading="loading" :data="jobs" size="small" border>
			<el-table-column :label="t('任务名称')" min-width="210">
				<template #default="{row}">
					<div class="job-name">{{ jobName(row) }}</div>
					<div class="muted small">{{ row.beanName }}.{{ row.methodName }}</div>
				</template>
			</el-table-column>
			<el-table-column prop="cron" :label="t('Cron 表达式')" width="150"/>
			<el-table-column :label="t('状态')" width="96">
				<template #default="{row}">
					<el-switch :model-value="row.status" size="small" @change="value => toggleStatus(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column :label="t('上次执行')" width="170">
				<template #default="{row}">
					<span :class="{muted: !row.lastFireTime}">{{ formatDate(row.lastFireTime) }}</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('下次执行')" width="170">
				<template #default="{row}">
					<span :class="{muted: !row.nextFireTime}">{{ formatDate(row.nextFireTime) }}</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="190" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" :loading="running === row.jobId" @click="run(row)">
						{{ t('执行') }}
					</el-button>
					<el-button link @click="openLogs(row)">{{ t('日志') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]"
		               :total="total" background layout="total, sizes, prev, pager, next"
		               @size-change="loadJobs" @current-change="loadJobs"/>

		<el-drawer v-model="logVisible" :title="logTitle" size="72%">
			<div class="log-head">
				<span class="muted">{{ t('Cron') }} {{ logJob && logJob.cron }}</span>
				<el-button size="small" :icon="Refresh" @click="loadLogs">{{ t('刷新') }}</el-button>
			</div>

			<el-table v-loading="logLoading" :data="logs" size="small" border>
				<el-table-column :label="t('开始时间')" width="170">
					<template #default="{row}">{{ formatDate(row.createTime) }}</template>
				</el-table-column>
				<el-table-column :label="t('耗时')" width="96">
					<template #default="{row}">{{ row.times == null ? '-' : row.times + ' ms' }}</template>
				</el-table-column>
				<el-table-column :label="t('结束时间')" width="170">
					<template #default="{row}">{{ endTime(row) }}</template>
				</el-table-column>
				<el-table-column :label="t('结果')" width="90">
					<template #default="{row}">
						<el-tag :type="row.status ? 'success' : 'danger'" size="small">
							{{ row.status ? t('成功') : t('失败') }}
						</el-tag>
					</template>
				</el-table-column>
				<el-table-column :label="t('异常信息')" min-width="240">
					<template #default="{row}">
						<span class="error-text">{{ row.error || '—' }}</span>
					</template>
				</el-table-column>
			</el-table>

			<el-pagination v-model:current-page="logPage" v-model:page-size="logSize" :page-sizes="[10, 20, 50]"
			               :total="logTotal" background layout="total, sizes, prev, pager, next"
			               @size-change="loadLogs" @current-change="loadLogs"/>
		</el-drawer>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Refresh} from '@element-plus/icons-vue'
import {getJobLogs, getJobs, runJob, updateJobStatus} from '@/api/job'
import {formatDate} from '@/util/date'

const loading = ref(false)
const jobs = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const running = ref(null)

const logVisible = ref(false)
const logJob = ref(null)
const logs = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)

const logTitle = computed(() =>
	logJob.value ? t('执行日志 · {name}', {name: jobName(logJob.value)}) : t('执行日志'))

/**
 * 表里只有 remark 是给人看的；没写备注就退回 Bean.方法，
 * 至少能对上是哪个任务
 */
function jobName(row) {
	if (!row) {
		return ''
	}
	return row.remark || `${row.beanName}.${row.methodName}`
}

/**
 * 结束时间 = 开始时间 + 耗时。
 * 表里只存这两项，没有独立的结束时间列，这里算出来给运维看
 */
function endTime(row) {
	if (!row.createTime || row.times == null) {
		return '-'
	}
	return formatDate(new Date(row.createTime).getTime() + Number(row.times))
}

async function loadJobs() {
	loading.value = true
	try {
		const res = await getJobs({pageNum: pageNum.value, pageSize: pageSize.value})
		const page = res.data || {}
		jobs.value = page.list || []
		total.value = page.total || 0
	} finally {
		loading.value = false
	}
}

async function run(row) {
	try {
		await ElMessageBox.confirm(
			t('立即执行「{name}」？任务在后台异步运行，结果稍后到「日志」里看。', {name: jobName(row)}),
			t('确认执行'), {type: 'warning'}
		)
	} catch (e) {
		return
	}
	running.value = row.jobId
	try {
		await runJob(row.jobId)
		ElMessage.success(t('已提交执行'))
		// 执行是异步的，立刻刷新看不到结果；等两秒再拉一次触发时间
		setTimeout(loadJobs, 2000)
	} finally {
		running.value = null
	}
}

/**
 * 启停：用单向绑定（:model-value），接口成功才改本地状态。
 * 失败时开关会自己弹回原位，不会出现「界面显示已启用、后端其实没动」
 */
async function toggleStatus(row, next) {
	try {
		await updateJobStatus(row.jobId, next)
		row.status = next
		ElMessage.success(next ? t('已启用') : t('已禁用'))
	} catch (e) {
		loadJobs()
	}
}

async function openLogs(row) {
	logJob.value = row
	logPage.value = 1
	logVisible.value = true
	await loadLogs()
}

async function loadLogs() {
	if (!logJob.value) {
		return
	}
	logLoading.value = true
	try {
		const res = await getJobLogs({
			jobId: logJob.value.jobId,
			pageNum: logPage.value,
			pageSize: logSize.value
		})
		const page = res.data || {}
		logs.value = page.list || []
		logTotal.value = page.total || 0
	} finally {
		logLoading.value = false
	}
}

onMounted(loadJobs)
</script>

<style scoped>
.mb {
	margin-bottom: 12px;
}

.job-name {
	font-size: 13.5px;
	color: #303133;
}

.small {
	font-size: 11.5px;
	margin-top: 2px;
}

.log-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10px;
}

.error-text {
	font-size: 12px;
	color: #c45656;
	word-break: break-all;
}
</style>
