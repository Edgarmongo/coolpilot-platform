<template>
	<div>
		<el-form inline>
			<el-form-item label="用户ID">
				<el-input v-model="query.userId" placeholder="全部" clearable style="width: 110px"/>
			</el-form-item>
			<el-form-item label="状态">
				<el-select v-model="query.status" placeholder="全部" clearable style="width: 120px" @change="search">
					<el-option label="生效中" value="ACTIVE"/>
					<el-option label="已过期" value="EXPIRED"/>
					<el-option label="已撤销" value="REVOKED"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">搜索</el-button>
				<el-button type="success" :icon="Plus" @click="openGrant">人工发放</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="userId" label="用户ID" width="80"/>
			<el-table-column prop="productTitle" label="商品" show-overflow-tooltip/>
			<el-table-column label="类型" width="90">
				<template #default="{row}">{{ entTypeText(row.entType) }}</template>
			</el-table-column>
			<el-table-column label="来源" width="80">
				<template #default="{row}">{{ row.source === 'GIFT' ? '赠送' : '购买' }}</template>
			</el-table-column>
			<el-table-column prop="orderNo" label="来源订单" width="190"/>
			<el-table-column label="有效期" width="130">
				<template #default="{row}">
					{{ row.expireTime ? '至 ' + formatDate(row.expireTime, 'YYYY-MM-DD') : '永久' }}
				</template>
			</el-table-column>
			<el-table-column label="状态" width="90">
				<template #default="{row}">
					<el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="操作" width="90" fixed="right">
				<template #default="{row}">
					<el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="revoke(row)">撤销</el-button>
					<span v-else class="muted">-</span>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30, 50]" :total="total" background
		               layout="total, sizes, prev, pager, next, jumper"
		               @size-change="load" @current-change="load"/>

		<el-dialog v-model="grantVisible" title="人工发放权益" width="460px">
			<el-form label-width="90px" size="small">
				<el-form-item label="用户ID">
					<el-input v-model="grantForm.userId" placeholder="访客用户ID"/>
				</el-form-item>
				<el-form-item label="商品">
					<el-select v-model="grantForm.productId" filterable placeholder="选择商品" style="width: 100%">
						<el-option v-for="p in products" :key="p.id" :label="p.title" :value="p.id"/>
					</el-select>
				</el-form-item>
				<el-form-item label="权益类型">
					<el-select v-model="grantForm.entType" style="width: 100%">
						<el-option label="阅读（ACCESS）" value="ACCESS"/>
						<el-option label="下载（DOWNLOAD）" value="DOWNLOAD"/>
						<el-option label="学习（LEARN）" value="LEARN"/>
					</el-select>
				</el-form-item>
				<el-form-item label="过期时间">
					<el-date-picker v-model="grantForm.expireTime" type="datetime" placeholder="留空表示永久"
					                value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%"/>
				</el-form-item>
				<el-form-item label="原因">
					<el-input v-model="grantForm.reason" placeholder="例如：活动赠送"/>
				</el-form-item>
			</el-form>
			<template #footer>
				<el-button @click="grantVisible = false">取消</el-button>
				<el-button type="primary" :loading="granting" @click="submitGrant">确认发放</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Plus, Search} from '@element-plus/icons-vue'
import {getEntitlementList, getProductOptions, grantEntitlement, revokeEntitlement} from '@/api/trade'
import {formatDate} from '@/util/date'

const loading = ref(false)
const granting = ref(false)
const query = reactive({userId: '', status: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const products = ref([])
const grantVisible = ref(false)
const grantForm = reactive({userId: '', productId: null, entType: 'ACCESS', expireTime: '', reason: ''})

function entTypeText(type) {
	return {ACCESS: '阅读', DOWNLOAD: '下载', LEARN: '学习'}[type] || type
}

function statusText(status) {
	return {ACTIVE: '生效中', EXPIRED: '已过期', REVOKED: '已撤销'}[status] || status
}

function statusTag(status) {
	return {ACTIVE: 'success', EXPIRED: 'info', REVOKED: 'danger'}[status] || ''
}

async function load() {
	loading.value = true
	try {
		const res = await getEntitlementList(query)
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

async function openGrant() {
	Object.assign(grantForm, {userId: '', productId: null, entType: 'ACCESS', expireTime: '', reason: ''})
	grantVisible.value = true
	if (!products.value.length) {
		const res = await getProductOptions()
		products.value = res.data.list || []
	}
}

async function submitGrant() {
	if (!grantForm.userId) {
		ElMessage.warning('请填写用户ID')
		return
	}
	if (!grantForm.productId) {
		ElMessage.warning('请选择商品')
		return
	}
	granting.value = true
	try {
		await grantEntitlement({
			userId: Number(grantForm.userId),
			productId: grantForm.productId,
			entType: grantForm.entType,
			expireTime: grantForm.expireTime || null,
			reason: grantForm.reason || '后台人工发放'
		})
		ElMessage.success('发放成功')
		grantVisible.value = false
		load()
	} finally {
		granting.value = false
	}
}

async function revoke(row) {
	let value
	try {
		const result = await ElMessageBox.prompt('撤销原因（会写入流水，必填）', '撤销权益', {
			inputPlaceholder: '例如：违规分享',
			inputValidator: input => (input && input.trim() ? true : '原因不能为空')
		})
		value = result.value
	} catch (e) {
		return
	}
	await revokeEntitlement({id: row.id, reason: value.trim()})
	ElMessage.success('已撤销')
	load()
}

onMounted(load)
</script>
