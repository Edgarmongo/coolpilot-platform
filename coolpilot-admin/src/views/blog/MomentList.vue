<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('动态管理') }}</h2>
				<p class="page-desc">{{ t('短内容，发布后在前台「动态」中展示') }}</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">{{ t('发布动态') }}</el-button>
		</div>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="content" :label="t('内容')" show-overflow-tooltip min-width="320"/>
			<el-table-column :label="t('点赞')" prop="likes" width="80"/>
			<el-table-column :label="t('公开')" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.published" size="small" @change="value => changePublished(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column :label="t('发布时间')" width="160">
				<template #default="{row}">{{ formatDate(row.createTime) }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="140" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">{{ t('编辑') }}</el-button>
					<el-button link type="danger" @click="remove(row)">{{ t('删除') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 30]"
		               :total="total" background layout="total, sizes, prev, pager, next"
		               @size-change="load" @current-change="load"/>

		<el-dialog v-model="visible" :title="form.id ? t('编辑动态') : t('发布动态')" width="520px">
			<el-input v-model="form.content" type="textarea" :rows="5" maxlength="500" show-word-limit
			          :class="{dragging: imgDragging}"
			          :placeholder="t('此刻的想法…')"
			          @paste="onImgPaste"
			          @dragover.prevent="onImgDragOver"
			          @dragleave="onImgDragLeave"
			          @drop.prevent="onImgDrop"/>
			<div v-if="imgUploading" class="muted hint">{{ t('正在上传图片…') }}</div>
			<div class="row">
				<span>{{ t('公开') }}</span>
				<el-switch v-model="form.published"/>
			</div>
			<template #footer>
				<el-button @click="visible = false">{{ t('取消') }}</el-button>
				<el-button type="primary" :loading="saving" @click="submit">{{ t('保存') }}</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Plus} from '@element-plus/icons-vue'
import {deleteMoment, getMomentList, saveMoment, updateMoment, updateMomentPublished} from '@/api/moment'
import {formatDate} from '@/util/date'
import {useImagePaste} from '@/composables/useImagePaste'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref([])
const form = reactive({id: null, content: '', published: true})

// 动态正文是 Markdown（后端渲染成 HTML），支持粘贴 / 拖拽贴图
const {
	uploading: imgUploading,
	dragging: imgDragging,
	onPaste: onImgPaste,
	onDrop: onImgDrop,
	onDragOver: onImgDragOver,
	onDragLeave: onImgDragLeave
} = useImagePaste({
	getValue: () => form.content,
	setValue: value => {
		form.content = value
	},
	bizType: 'MOMENT'
})

async function load() {
	loading.value = true
	try {
		const res = await getMomentList({pageNum: pageNum.value, pageSize: pageSize.value})
		list.value = res.data.list || []
		total.value = res.data.total || (res.data.list || []).length
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	form.id = row ? row.id : null
	form.content = row ? row.content : ''
	form.published = row ? row.published : true
	visible.value = true
}

async function submit() {
	// 上传中的图片在正文里只有一个注释占位，此时保存会把它一起存进去
	if (imgUploading.value) {
		ElMessage.warning(t('图片还在上传，请稍候再保存'))
		return
	}
	if (!form.content.trim()) {
		ElMessage.warning(t('请输入内容'))
		return
	}
	saving.value = true
	try {
		const payload = {id: form.id || undefined, content: form.content.trim(), published: form.published}
		if (form.id) {
			await updateMoment(payload)
		} else {
			await saveMoment(payload)
		}
		ElMessage.success(t('保存成功'))
		visible.value = false
		load()
	} finally {
		saving.value = false
	}
}

async function changePublished(row, value) {
	await updateMomentPublished(row.id, value)
	row.published = value
	ElMessage.success(value ? t('已公开') : t('已隐藏'))
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(t('删除这条动态？'), '删除动态', {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteMoment(row.id)
	ElMessage.success(t('已删除'))
	load()
}

onMounted(load)
</script>

<style scoped>
.row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 12px;
	font-size: 13px;
}
</style>
