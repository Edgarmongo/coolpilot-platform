<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('用户管理') }}</h2>
				<p class="page-desc">{{ t('前台注册用户；禁用会立即让该账号下线，但不会删除其订单与权益') }}</p>
			</div>
		</div>

		<el-form inline>
			<el-form-item :label="t('关键词')">
				<el-input v-model="query.keyword" :placeholder="t('用户名 / 昵称 / 邮箱')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item :label="t('状态')">
				<el-select v-model="query.status" :placeholder="t('全部')" clearable style="width: 120px" @change="search">
					<el-option :label="t('正常')" :value="0"/>
					<el-option :label="t('已禁用')" :value="1"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('用户')" min-width="180">
				<template #default="{row}">
					<div class="user-cell">
						<el-avatar :size="26" :src="row.avatar"/>
						<div>
							<div class="user-name">{{ row.nickname || row.username }}</div>
							<div class="muted">@{{ row.username }} · ID {{ row.id }}</div>
						</div>
					</div>
				</template>
			</el-table-column>
			<el-table-column prop="email" :label="t('邮箱')" min-width="180" show-overflow-tooltip/>
			<el-table-column :label="t('订单')" width="80">
				<template #default="{row}">{{ row.orderCount || 0 }}</template>
			</el-table-column>
			<el-table-column :label="t('累计支付')" width="110">
				<template #default="{row}">¥{{ money(row.paidAmount) }}</template>
			</el-table-column>
			<el-table-column :label="t('注册时间')" width="150">
				<template #default="{row}">{{ formatDate(row.createTime, 'YYYY-MM-DD HH:mm') }}</template>
			</el-table-column>
			<el-table-column :label="t('最后登录')" width="150">
				<template #default="{row}">{{ row.lastLoginTime ? formatDate(row.lastLoginTime, 'YYYY-MM-DD HH:mm') : t('从未登录') }}</template>
			</el-table-column>
			<el-table-column :label="t('状态')" width="110" fixed="right">
				<template #default="{row}">
					<el-switch :model-value="row.status === 0" size="small"
					           @change="value => toggle(row, value)"/>
					<span :class="['status-text', {off: row.status === 1}]">{{ row.status === 0 ? t('正常') : t('已禁用') }}</span>
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
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Search} from '@element-plus/icons-vue'
import {getUserList, updateUserStatus} from '@/api/user'
import {formatDate} from '@/util/date'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({keyword: '', status: null, pageNum: 1, pageSize: 10})

async function load() {
	loading.value = true
	try {
		const res = await getUserList(query)
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

function money(value) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

/**
 * 启停账号：禁用是「让人立刻用不了」，值得一次确认；
 * 启用不需要确认，点错也能再点回来。
 */
async function toggle(row, enabled) {
	const next = enabled ? 0 : 1
	if (next === 1) {
		try {
			await ElMessageBox.confirm(
				t('禁用「{name}」？该账号会立即下线，且无法再登录；订单与权益保留。', {name: row.nickname || row.username}),
				t('禁用账号'), {type: 'warning'})
		} catch (e) {
			return
		}
	}
	await updateUserStatus(row.id, next)
	row.status = next
	ElMessage.success(next === 1 ? t('已禁用') : t('已启用'))
}

onMounted(load)
</script>

<style scoped>
.user-cell {
	display: flex;
	align-items: center;
	gap: 8px;
}

.user-name {
	font-size: 13px;
	font-weight: 600;
	color: #303133;
}

.status-text {
	margin-left: 6px;
	font-size: 12px;
	color: #606266;
}

.status-text.off {
	color: #f56c6c;
}
</style>
