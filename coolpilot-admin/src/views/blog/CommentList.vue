<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">评论管理</h2>
				<p class="page-desc">审核、隐藏或删除访客评论</p>
			</div>
		</div>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column label="评论内容" min-width="260">
				<template #default="{row}">
					<div class="content">{{ row.content }}</div>
					<div class="muted">{{ row.nickname || '匿名' }} · {{ formatDate(row.createTime) }}</div>
				</template>
			</el-table-column>
			<el-table-column label="所属文章" min-width="160" show-overflow-tooltip>
				<template #default="{row}">{{ row.blog ? row.blog.title : (row.pageName || '-') }}</template>
			</el-table-column>
			<el-table-column label="显示" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.published" size="small" @change="value => changePublished(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column label="操作" width="90" fixed="right">
				<template #default="{row}">
					<el-button link type="danger" @click="remove(row)">删除</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30]" :total="total" background
		               layout="total, sizes, prev, pager, next" @size-change="load" @current-change="load"/>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {deleteComment, getCommentList, updateCommentPublished} from '@/api/comment'
import {formatDate} from '@/util/date'

const loading = ref(false)
const total = ref(0)
const list = ref([])
const query = reactive({pageNum: 1, pageSize: 10})

async function load() {
	loading.value = true
	try {
		// 老接口直接返回 PageHelper 的 PageInfo
		const res = await getCommentList({pageNum: query.pageNum, pageSize: query.pageSize})
		list.value = res.data.list || []
		total.value = res.data.total || 0
	} finally {
		loading.value = false
	}
}

async function changePublished(row, value) {
	await updateCommentPublished(row.id, value)
	row.published = value
	ElMessage.success(value ? '已通过' : '已隐藏')
}

async function remove(row) {
	try {
		await ElMessageBox.confirm('删除这条评论？其下回复会一并删除。', '删除评论', {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteComment(row.id)
	ElMessage.success('已删除')
	load()
}

onMounted(load)
</script>

<style scoped>
.content {
	font-size: 13px;
	line-height: 1.6;
	color: #303133;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}
</style>
