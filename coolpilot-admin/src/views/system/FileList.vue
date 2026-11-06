<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('附件管理') }}</h2>
				<p class="page-desc">
					{{ t('所有上传过的文件都在这里。删除前会检查是否还被文章、商品、项目或其他配置引用；被引用时会提示具体位置并拒绝删除。') }}
				</p>
			</div>
			<el-button :icon="Refresh" @click="reload">{{ t('刷新') }}</el-button>
		</div>

		<div class="stats">
			<div class="stat">
				<strong>{{ stats.count || 0 }}</strong>
				<span>{{ t('个文件') }}</span>
			</div>
			<div class="stat">
				<strong>{{ formatSize(stats.bytes || 0) }}</strong>
				<span>{{ t('总占用') }}</span>
			</div>
			<div class="stat stats-biz">
				<el-tag v-for="item in stats.byBizType || []" :key="item.bizType" size="small" effect="plain"
				        class="biz-tag">
					{{ bizLabel(item.bizType) }} · {{ item.cnt }}
				</el-tag>
			</div>
		</div>

		<div class="filters">
			<el-select v-model="bizType" clearable :placeholder="t('全部类型')" size="small" style="width: 160px"
			           @change="reload">
				<el-option v-for="item in bizOptions" :key="item.value" :label="item.label" :value="item.value"/>
			</el-select>
			<el-input v-model="keyword" size="small" clearable :placeholder="t('按文件名搜索')" style="width: 220px"
			          @keyup.enter="reload" @clear="reload"/>
			<el-button size="small" @click="reload">{{ t('查询') }}</el-button>
		</div>

		<el-table :data="list" v-loading="loading" size="small" row-key="id">
			<el-table-column :label="t('预览')" width="88">
				<template #default="{row}">
					<img v-if="isImage(row) && row.isPublic" :src="fileUrl(row)" class="thumb" alt=""
					     @click="preview(row)"/>
					<span v-else class="ext">{{ extOf(row.originalName) }}</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('文件名')" min-width="220">
				<template #default="{row}">
					<div class="name">{{ row.originalName || '—' }}</div>
					<div class="path">{{ row.objectKey }}</div>
				</template>
			</el-table-column>

			<el-table-column :label="t('类型')" width="110">
				<template #default="{row}">
					<el-tag size="small" effect="plain">{{ bizLabel(row.bizType) }}</el-tag>
				</template>
			</el-table-column>

			<el-table-column :label="t('大小')" width="90">
				<template #default="{row}">{{ formatSize(row.fileSize) }}</template>
			</el-table-column>

			<el-table-column :label="t('可见性')" width="90">
				<template #default="{row}">
					<span :class="row.isPublic ? 'pub' : 'priv'">
						{{ row.isPublic ? t('公开') : t('私有') }}
					</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('上传时间')" width="150">
				<template #default="{row}">{{ formatDate(row.createTime, 'YYYY-MM-DD HH:mm') }}</template>
			</el-table-column>

			<el-table-column :label="t('操作')" width="150" align="right">
				<template #default="{row}">
					<el-button v-if="row.isPublic" link type="primary" @click="copyUrl(row)">{{ t('复制地址') }}</el-button>
					<el-button link type="danger" @click="remove(row)">{{ t('删除') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[20, 50, 100]"
		               :total="total" background layout="total, sizes, prev, pager, next"
		               @size-change="load" @current-change="load"/>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Refresh} from '@element-plus/icons-vue'
import {deleteFile, getFileReferences, getFiles, getFileStats} from '@/api/file'
import {formatDate} from '@/util/date'

const BIZ = [
	{value: 'ARTICLE', label: '文章'},
	{value: 'PRODUCT', label: '商品'},
	{value: 'PORTFOLIO', label: '项目'},
	{value: 'SOCIAL', label: '社交账号'},
	{value: 'SITE', label: '站点设置'},
	{value: 'AVATAR', label: '头像'},
	{value: 'PROOF', label: '付款凭证'},
	{value: 'MOMENT', label: '动态'},
	{value: 'OTHER', label: '其他'}
]

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const bizType = ref('')
const keyword = ref('')
const stats = ref({})

const bizOptions = computed(() => {
	const fromStats = (stats.value.byBizType || []).map(i => i.bizType)
	const known = new Set(BIZ.map(b => b.value))
	const extra = fromStats.filter(x => x && !known.has(x)).map(x => ({value: x, label: x}))
	return [...BIZ, ...extra]
})

function bizLabel(value) {
	const hit = BIZ.find(b => b.value === value)
	return hit ? hit.label : (value || '其他')
}

function isImage(row) {
	return /^image\//.test(row.mimeType || '')
}

/**
 * 后端只存 objectKey，公开文件走 /image/** 直链。
 * 后台在 8079、图片在 8091，必须带 /api 前缀走代理，
 * 否则相对路径会打到 admin 自己的静态目录，显示成裂图。
 */
function fileUrl(row) {
	if (!row.isPublic) {
		return ''
	}
	return `/api/image/${row.objectKey}`
}

function preview(row) {
	if (!row.isPublic) {
		ElMessage.info(t('私有文件需要登录后才能查看，付款凭证请在对应订单里查看'))
		return
	}
	window.open(fileUrl(row), '_blank')
}

function extOf(name) {
	const dot = (name || '').lastIndexOf('.')
	return dot > -1 ? name.slice(dot + 1).toUpperCase() : 'FILE'
}

function formatSize(bytes) {
	const n = Number(bytes || 0)
	if (n < 1024) {
		return n + ' B'
	}
	if (n < 1024 * 1024) {
		return (n / 1024).toFixed(1) + ' KB'
	}
	return (n / 1024 / 1024).toFixed(1) + ' MB'
}

async function load() {
	loading.value = true
	try {
		const res = await getFiles({
			pageNum: pageNum.value,
			pageSize: pageSize.value,
			bizType: bizType.value || undefined,
			keyword: keyword.value || undefined
		})
		const page = res.data || {}
		list.value = page.list || []
		total.value = page.total || 0
	} finally {
		loading.value = false
	}
}

async function loadStats() {
	const res = await getFileStats()
	stats.value = res.data || {}
}

async function reload() {
	pageNum.value = 1
	await Promise.all([load(), loadStats()])
}

async function copyUrl(row) {
	const url = fileUrl(row)
	try {
		await navigator.clipboard.writeText(url)
		ElMessage.success(t('地址已复制'))
	} catch (e) {
		ElMessage.warning(t('复制失败，请手动复制：{url}', {url}))
	}
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(
			t('删除「{name}」？磁盘文件会一并移除，无法恢复。', {name: row.originalName || row.objectKey}),
			t('确认删除'), {type: 'warning'}
		)
	} catch (e) {
		return
	}
	try {
		await deleteFile(row.id)
		ElMessage.success(t('已删除'))
		await reload()
	} catch (e) {
		// 被引用时后端会返回具体位置，这里再拉一次引用列表，便于直接跳去看
		const refs = await getFileReferences(row.id).then(r => r.data?.references || []).catch(() => [])
		if (refs.length) {
			ElMessageBox.alert(refs.join('；'), t('这个附件还在被使用，无法删除'), {type: 'warning'})
		}
	}
}

onMounted(async () => {
	await Promise.all([load(), loadStats()])
})
</script>

<style scoped>
.stats {
	display: flex;
	align-items: center;
	gap: 28px;
	padding: 14px 18px;
	background: #f7faf9;
	border: 1px solid #e8eded;
	border-radius: 8px;
	margin-bottom: 14px;
	flex-wrap: wrap;
}

.stat strong {
	display: block;
	font-size: 20px;
	color: #00604a;
}

.stat span {
	font-size: 12px;
	color: #6b7280;
}

.stats-biz {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-left: auto;
}

.biz-tag {
	margin: 0;
}

.filters {
	display: flex;
	gap: 10px;
	margin-bottom: 12px;
	flex-wrap: wrap;
}

.thumb {
	width: 44px;
	height: 44px;
	object-fit: cover;
	border-radius: 5px;
	border: 1px solid #e8eded;
	cursor: pointer;
}

.ext {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 44px;
	height: 44px;
	border-radius: 5px;
	background: #f2f5f4;
	color: #6b7280;
	font-size: 11px;
	font-weight: 600;
}

.name {
	font-size: 13px;
	color: #303133;
	word-break: break-all;
}

.path {
	font-size: 11.5px;
	color: #9aa0a6;
	margin-top: 2px;
	font-family: ui-monospace, monospace;
}

.pub {
	color: #0f766e;
	font-size: 12.5px;
}

.priv {
	color: #9aa0a6;
	font-size: 12.5px;
}
</style>
