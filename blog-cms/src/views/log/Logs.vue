<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">日志中心</h2>
				<p class="page-desc">访问、登录、操作与异常记录</p>
			</div>
			<el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
		</div>

		<el-tabs v-model="tab" @tab-change="onTabChange">
			<el-tab-pane v-for="item in configs" :key="item.key" :label="item.label" :name="item.key">
				<el-table :data="rows" v-loading="loading" size="small">
					<el-table-column v-for="col in item.columns" :key="col.prop" :label="col.label"
					                 :prop="col.prop" :width="col.width" :min-width="col.minWidth"
					                 :show-overflow-tooltip="col.tooltip !== false"/>
					<el-table-column label="时间" width="160">
						<template #default="{row}">{{ formatDate(row.createTime) }}</template>
					</el-table-column>
					<el-table-column label="操作" width="80" fixed="right">
						<template #default="{row}">
							<el-button link type="danger" @click="remove(row)">删除</el-button>
						</template>
					</el-table-column>
				</el-table>

				<el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
				               :page-sizes="[10, 20, 50]" :total="total" background
				               layout="total, sizes, prev, pager, next"
				               @size-change="load" @current-change="load"/>
			</el-tab-pane>
		</el-tabs>
	</div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Refresh} from '@element-plus/icons-vue'
import {deleteLog, getExceptionLogs, getLoginLogs, getOperationLogs, getVisitLogs} from '@/api/log'
import {formatDate} from '@/util/date'

const configs = [
	{
		key: 'visit', label: '访问日志', api: getVisitLogs, remove: 'visitLog',
		columns: [
			{prop: 'ip', label: 'IP', width: 130},
			{prop: 'ipSource', label: '来源', width: 150},
			{prop: 'os', label: '系统', width: 120},
			{prop: 'browser', label: '浏览器', minWidth: 160}
		]
	},
	{
		key: 'login', label: '登录日志', api: getLoginLogs, remove: 'loginLog',
		columns: [
			{prop: 'username', label: '账号', width: 120},
			{prop: 'ip', label: 'IP', width: 130},
			{prop: 'ipSource', label: '来源', width: 150},
			{prop: 'browser', label: '浏览器', minWidth: 140}
		]
	},
	{
		key: 'operation', label: '操作日志', api: getOperationLogs, remove: 'operationLog',
		columns: [
			{prop: 'username', label: '操作者', width: 110},
			{prop: 'method', label: '方式', width: 80},
			{prop: 'uri', label: '接口', minWidth: 200},
			{prop: 'description', label: '描述', minWidth: 160},
			{prop: 'ip', label: 'IP', width: 130},
			{prop: 'times', label: '耗时(ms)', width: 100}
		]
	},
	{
		key: 'exception', label: '异常日志', api: getExceptionLogs, remove: 'exceptionLog',
		columns: [
			{prop: 'uri', label: '接口', minWidth: 200},
			{prop: 'method', label: '方式', width: 80},
			{prop: 'description', label: '描述', minWidth: 220}
		]
	}
]

const tab = ref('visit')
const current = computed(() => configs.find(item => item.key === tab.value) || configs[0])
const loading = ref(false)
const rows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

async function load() {
	loading.value = true
	try {
		const res = await current.value.api({pageNum: pageNum.value, pageSize: pageSize.value})
		rows.value = res.data.list || []
		total.value = res.data.total || 0
	} finally {
		loading.value = false
	}
}

function onTabChange() {
	pageNum.value = 1
	load()
}

async function remove(row) {
	try {
		await ElMessageBox.confirm('删除这条日志？', '删除日志', {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteLog(current.value.remove, row.id)
	ElMessage.success('已删除')
	load()
}

onMounted(load)
watch(() => current.value.key, () => {
	rows.value = []
})
</script>
