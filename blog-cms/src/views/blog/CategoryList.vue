<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">分类管理</h2>
				<p class="page-desc">文章分类，名称全局唯一</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">新增分类</el-button>
		</div>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="name" label="分类名称"/>
			<el-table-column label="操作" width="160" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">重命名</el-button>
					<el-button link type="danger" @click="remove(row)">删除</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" background
		               layout="total, prev, pager, next" @current-change="load"/>

		<el-dialog v-model="visible" :title="form.id ? '重命名分类' : '新增分类'" width="380px">
			<el-input v-model="form.name" placeholder="分类名称" maxlength="32" @keyup.enter="submit"/>
			<template #footer>
				<el-button @click="visible = false">取消</el-button>
				<el-button type="primary" :loading="saving" @click="submit">保存</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Plus} from '@element-plus/icons-vue'
import {addCategory, deleteCategory, editCategory, getCategoryList} from '@/api/category'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref([])
const form = reactive({id: null, name: ''})

async function load() {
	loading.value = true
	try {
		const res = await getCategoryList({pageNum: pageNum.value, pageSize: pageSize.value})
		list.value = res.data.list || []
		total.value = res.data.total || (res.data.list || []).length
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	form.id = row ? row.id : null
	form.name = row ? row.name : ''
	visible.value = true
}

async function submit() {
	if (!form.name.trim()) {
		ElMessage.warning('请输入分类名称')
		return
	}
	saving.value = true
	try {
		if (form.id) {
			await editCategory({id: form.id, name: form.name.trim()})
		} else {
			await addCategory({name: form.name.trim()})
		}
		ElMessage.success('保存成功')
		visible.value = false
		load()
	} finally {
		saving.value = false
	}
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(`删除分类「${row.name}」？该分类下的文章不会被删除，但会失去分类。`, '删除分类',
			{type: 'warning'})
	} catch (e) {
		return
	}
	await deleteCategory(row.id)
	ElMessage.success('已删除')
	load()
}

onMounted(load)
</script>
