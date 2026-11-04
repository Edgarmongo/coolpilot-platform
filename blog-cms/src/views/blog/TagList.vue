<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">标签管理</h2>
				<p class="page-desc">标签用于聚合文章，可指定展示颜色</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">新增标签</el-button>
		</div>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="name" label="标签名称"/>
			<el-table-column label="颜色" width="140">
				<template #default="{row}">
					<el-tag v-if="row.color" size="small" :color="colorHex(row.color)" effect="dark">{{ row.name }}</el-tag>
					<span v-else class="muted">默认</span>
				</template>
			</el-table-column>
			<el-table-column label="操作" width="160" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">编辑</el-button>
					<el-button link type="danger" @click="remove(row)">删除</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" background
		               layout="total, prev, pager, next" @current-change="load"/>

		<el-dialog v-model="visible" :title="form.id ? '编辑标签' : '新增标签'" width="380px">
			<el-input v-model="form.name" placeholder="标签名称" maxlength="32" class="mb"/>
			<el-select v-model="form.color" placeholder="颜色（可选）" clearable style="width: 100%">
				<el-option v-for="color in colors" :key="color" :label="color" :value="color"/>
			</el-select>
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
import {addTag, deleteTag, editTag, getTagList} from '@/api/tag'

const colors = ['red', 'orange', 'yellow', 'olive', 'green', 'teal', 'blue', 'violet', 'purple', 'pink', 'brown', 'grey']
const colorMap = {
	red: '#db2828', orange: '#f2711c', yellow: '#fbbd08', olive: '#b5cc18', green: '#21ba45',
	teal: '#00b5ad', blue: '#2185d0', violet: '#6435c9', purple: '#a333c8', pink: '#e03997',
	brown: '#a5673f', grey: '#767676'
}

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref([])
const form = reactive({id: null, name: '', color: ''})

function colorHex(color) {
	return colorMap[color] || color
}

async function load() {
	loading.value = true
	try {
		const res = await getTagList({pageNum: pageNum.value, pageSize: pageSize.value})
		list.value = res.data.list || []
		total.value = res.data.total || (res.data.list || []).length
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	form.id = row ? row.id : null
	form.name = row ? row.name : ''
	form.color = row ? row.color || '' : ''
	visible.value = true
}

async function submit() {
	if (!form.name.trim()) {
		ElMessage.warning('请输入标签名称')
		return
	}
	saving.value = true
	try {
		if (form.id) {
			await editTag({id: form.id, name: form.name.trim(), color: form.color})
		} else {
			await addTag({name: form.name.trim(), color: form.color})
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
		await ElMessageBox.confirm(`删除标签「${row.name}」？`, '删除标签', {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteTag(row.id)
	ElMessage.success('已删除')
	load()
}

onMounted(load)
</script>

<style scoped>
.mb {
	margin-bottom: 10px;
}
</style>
