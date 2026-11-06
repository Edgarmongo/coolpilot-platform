<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('项目展示') }}</h2>
				<p class="page-desc">
					{{ t('对应前台 /projects 列表与 /projects/{slug} 详情页。正文用 Markdown 写，支持截图后直接 Ctrl+V 粘贴。') }}
				</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">{{ t('新增项目') }}</el-button>
		</div>

		<el-alert v-if="!list.length && !loading" type="info" :closable="false" show-icon
		          :title="t('还没有项目')"
		          :description="t('新增后会在前台「项目」页展示；关闭「显示」则只保留在后台。')"/>

		<el-table :data="list" v-loading="loading" size="small" row-key="id">
			<el-table-column :label="t('顺序')" width="110">
				<template #default="{row, $index}">
					<el-button link :icon="Top" :disabled="$index === 0" :title="t('上移')"
					           @click="move(row, 'up')"/>
					<el-button link :icon="Bottom" :disabled="$index === list.length - 1" :title="t('下移')"
					           @click="move(row, 'down')"/>
					<span class="sort-no">{{ row.sort }}</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('封面')" width="76">
				<template #default="{row}">
					<img v-if="row.coverUrl" :src="row.coverUrl" class="cover-thumb" alt=""/>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('项目')" min-width="220">
				<template #default="{row}">
					<div class="name">{{ row.name }}</div>
					<div class="slug">/projects/{{ row.slug }}</div>
				</template>
			</el-table-column>

			<el-table-column :label="t('技术栈')" min-width="180">
				<template #default="{row}">
					<span v-if="row.techStack" class="tech">{{ row.techStack }}</span>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('状态')" width="96">
				<template #default="{row}">
					<el-tag size="small" :type="statusType(row.status)" effect="plain">
						{{ statusLabel(row.status) }}
					</el-tag>
				</template>
			</el-table-column>

			<el-table-column :label="t('显示')" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.enabled" @change="value => toggle(row, value)"/>
				</template>
			</el-table-column>

			<el-table-column :label="t('操作')" width="130" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">{{ t('编辑') }}</el-button>
					<el-button link type="danger" @click="remove(row)">{{ t('删除') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-dialog v-model="visible" :title="form.id ? t('编辑项目') : t('新增项目')" width="720px" top="6vh">
			<el-form :model="form" label-width="92px" size="small">
				<el-form-item :label="t('项目名称')" required>
					<el-input v-model="form.name" maxlength="100" show-word-limit :placeholder="t('如：CoolPilot Platform')"/>
				</el-form-item>

				<el-form-item :label="t('地址标识')" required>
					<el-input v-model="form.slug" maxlength="64"
					          :placeholder="t('小写字母、数字、连字符，如 coolpilot-platform')">
						<template #prepend>/projects/</template>
					</el-input>
				</el-form-item>

				<el-form-item :label="t('一句话简介')">
					<el-input v-model="form.summary" type="textarea" :rows="2" maxlength="255" show-word-limit
					          :placeholder="t('列表页展示，建议 30 字以内')"/>
				</el-form-item>

				<el-form-item :label="t('技术栈')">
					<el-input v-model="form.techStack" :placeholder="t('逗号分隔，如 Java, Spring Boot, Vue, MySQL')"/>
				</el-form-item>

				<el-row :gutter="12">
					<el-col :span="12">
						<el-form-item :label="t('代码仓库')">
							<el-input v-model="form.repoUrl" placeholder="https://github.com/…"/>
						</el-form-item>
					</el-col>
					<el-col :span="12">
						<el-form-item :label="t('演示地址')">
							<el-input v-model="form.demoUrl" placeholder="https://…"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-row :gutter="12">
					<el-col :span="12">
						<el-form-item :label="t('状态')">
							<el-select v-model="form.status" style="width: 100%">
								<el-option v-for="s in STATUSES" :key="s.value" :label="s.label" :value="s.value"/>
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="12">
						<el-form-item :label="t('显示')">
							<el-switch v-model="form.enabled"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-form-item :label="t('封面')">
					<div class="cover-field">
						<div v-if="form.coverUrl" class="cover-preview">
							<img :src="form.coverUrl" alt="cover"/>
						</div>
						<div class="cover-actions">
							<el-button size="small" :loading="uploading" @click="pickFile">
								{{ form.coverUrl ? t('更换图片') : t('上传图片') }}
							</el-button>
							<el-button v-if="form.coverUrl" size="small" text type="danger" @click="form.coverUrl = ''">
								{{ t('移除') }}
							</el-button>
							<input ref="fileInput" type="file" accept="image/*" class="hidden-input" @change="onPicked"/>
						</div>
						<p class="hint">{{ t('列表卡片顶部展示，建议 16:9，5 MB 以内') }}</p>
					</div>
				</el-form-item>

				<el-form-item :label="t('详情正文')">
					<div class="content-field" :class="{dragging: imgDragging}">
						<el-input v-model="form.content" type="textarea" :rows="12"
						          :placeholder="t('支持 Markdown：## 背景 / ## 架构 / ## 核心功能 / ## 技术实现。截图后可直接 Ctrl+V 粘贴')"
						          @paste="onImgPaste" @drop="onImgDrop" @dragover="onImgDragOver"
						          @dragleave="onImgDragLeave"/>
						<p class="hint">
							{{ imgUploading ? t('图片上传中…') : t('正文按 Markdown 渲染，## 会变成小标题') }}
						</p>
					</div>
				</el-form-item>
			</el-form>

			<template #footer>
				<el-button @click="visible = false">{{ t('取消') }}</el-button>
				<el-button type="primary" :loading="saving" :disabled="imgUploading" @click="submit">
					{{ t('保存') }}
				</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Bottom, Plus, Top} from '@element-plus/icons-vue'
import {
	changePortfolioVisible,
	createPortfolio,
	deletePortfolio,
	getPortfolio,
	getPortfolios,
	movePortfolio,
	updatePortfolio
} from '@/api/portfolio'
import {useImagePaste} from '@/composables/useImagePaste'
import request from '@/util/request'

const STATUSES = [
	{value: 'ACTIVE', label: '运行中'},
	{value: 'WIP', label: '开发中'},
	{value: 'MAINTAINING', label: '维护中'},
	{value: 'ARCHIVED', label: '已归档'}
]

const MAX_IMAGE_BYTES = 5 * 1024 * 1024

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const list = ref([])
const visible = ref(false)
const fileInput = ref(null)

const form = reactive({
	id: null,
	slug: '',
	name: '',
	summary: '',
	coverUrl: '',
	techStack: '',
	repoUrl: '',
	demoUrl: '',
	status: 'ACTIVE',
	content: '',
	enabled: true
})

// 正文是 Markdown（后端渲染成 HTML），支持粘贴 / 拖拽贴图
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
	bizType: 'PORTFOLIO'
})

function statusLabel(value) {
	const hit = STATUSES.find(s => s.value === value)
	return hit ? hit.label : value
}

function statusType(value) {
	return {
		ACTIVE: 'success',
		WIP: 'warning',
		MAINTAINING: 'primary',
		ARCHIVED: 'info'
	}[value] || 'info'
}

async function load() {
	loading.value = true
	try {
		const res = await getPortfolios()
		list.value = res.data || []
	} finally {
		loading.value = false
	}
}

async function openDialog(row) {
	if (row) {
		// 列表不带 content，编辑时单独取全文
		const res = await getPortfolio(row.id)
		const data = res.data || {}
		Object.assign(form, {
			id: data.id,
			slug: data.slug,
			name: data.name,
			summary: data.summary || '',
			coverUrl: data.coverUrl || '',
			techStack: data.techStack || '',
			repoUrl: data.repoUrl || '',
			demoUrl: data.demoUrl || '',
			status: data.status || 'ACTIVE',
			content: data.content || '',
			enabled: !!data.enabled
		})
	} else {
		Object.assign(form, {
			id: null,
			slug: '',
			name: '',
			summary: '',
			coverUrl: '',
			techStack: '',
			repoUrl: '',
			demoUrl: '',
			status: 'ACTIVE',
			content: '',
			enabled: true
		})
	}
	visible.value = true
}

function pickFile() {
	fileInput.value && fileInput.value.click()
}

async function onPicked(event) {
	const file = event.target.files && event.target.files[0]
	event.target.value = ''
	if (!file) {
		return
	}
	if (!/^image\//.test(file.type)) {
		ElMessage.warning(t('只支持图片文件'))
		return
	}
	if (file.size > MAX_IMAGE_BYTES) {
		ElMessage.warning(t('图片超过 5 MB，请先压缩'))
		return
	}
	uploading.value = true
	try {
		const formData = new FormData()
		formData.append('file', file)
		formData.append('bizType', 'PORTFOLIO')
		formData.append('isPublic', 'true')
		const res = await request({
			url: 'file/upload',
			method: 'POST',
			data: formData,
			headers: {'Content-Type': 'multipart/form-data'}
		})
		if (!res.data || !res.data.url) {
			ElMessage.error(t('上传成功但未返回图片地址，请检查文件是否被标记为公开'))
			return
		}
		form.coverUrl = res.data.url
	} finally {
		uploading.value = false
	}
}

async function submit() {
	if (!form.name.trim()) {
		ElMessage.warning(t('请填写项目名称'))
		return
	}
	if (!form.slug.trim()) {
		ElMessage.warning(t('请填写地址标识'))
		return
	}
	saving.value = true
	try {
		const payload = {
			slug: form.slug.trim().toLowerCase(),
			name: form.name.trim(),
			summary: form.summary,
			coverUrl: form.coverUrl,
			techStack: form.techStack,
			repoUrl: form.repoUrl,
			demoUrl: form.demoUrl,
			status: form.status,
			content: form.content,
			enabled: form.enabled
		}
		if (form.id) {
			await updatePortfolio(form.id, payload)
		} else {
			await createPortfolio(payload)
		}
		ElMessage.success(t('已保存'))
		visible.value = false
		await load()
	} finally {
		saving.value = false
	}
}

async function toggle(row, value) {
	try {
		await changePortfolioVisible(row.id, value)
		row.enabled = value
		ElMessage.success(value ? t('已显示') : t('已隐藏'))
	} catch (e) {
		await load()
	}
}

async function move(row, direction) {
	await movePortfolio(row.id, direction)
	await load()
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(t('删除项目「{name}」？前台会立即消失。', {name: row.name}), t('确认删除'),
			{type: 'warning'})
	} catch (e) {
		return
	}
	await deletePortfolio(row.id)
	ElMessage.success(t('已删除'))
	await load()
}

onMounted(load)
</script>

<style scoped>
.sort-no {
	margin-left: 4px;
	font-size: 12px;
	color: #9aa0a6;
}

.name {
	font-size: 13px;
	color: #303133;
}

.slug {
	font-size: 11.5px;
	color: #9aa0a6;
	margin-top: 2px;
	font-family: ui-monospace, monospace;
}

.tech {
	font-size: 12px;
	color: #4b5563;
}

.muted {
	color: #9aa0a6;
	font-size: 12.5px;
}

.cover-thumb {
	width: 52px;
	height: 34px;
	object-fit: cover;
	border-radius: 4px;
	border: 1px solid #e8eded;
}

.cover-field,
.content-field {
	display: flex;
	flex-direction: column;
	gap: 8px;
	width: 100%;
}

.content-field.dragging :deep(textarea) {
	border-color: #00875a;
	background: #f4faf7;
}

.cover-preview img {
	width: 160px;
	height: 90px;
	object-fit: cover;
	border-radius: 6px;
	border: 1px solid #e8eded;
}

.cover-actions {
	display: flex;
	align-items: center;
	gap: 8px;
}

.hidden-input {
	display: none;
}

.hint {
	margin: 0;
	font-size: 12px;
	color: #9aa0a6;
}
</style>
