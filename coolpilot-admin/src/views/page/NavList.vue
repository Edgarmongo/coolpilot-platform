<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">导航管理</h2>
				<p class="page-desc">
					前台顶部菜单的文字、顺序、显隐都在这里改，保存后前台刷新即可生效。
					站内链接以 <code>/</code> 开头，外链以 <code>http(s)://</code> 开头。
				</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">新增菜单</el-button>
		</div>

		<el-alert v-if="!list.length && !loading" type="info" :closable="false" show-icon
		          title="还没有任何导航项" description="前台会回落到内置的默认菜单，新增一项后即以这里为准。"/>

		<el-table :data="list" v-loading="loading" size="small" row-key="id">
			<el-table-column label="顺序" width="110">
				<template #default="{row, $index}">
					<el-button link :icon="Top" :disabled="$index === 0" title="上移"
					           @click="move(row, 'up')"/>
					<el-button link :icon="Bottom" :disabled="$index === list.length - 1" title="下移"
					           @click="move(row, 'down')"/>
					<span class="sort-no">{{ row.sort }}</span>
				</template>
			</el-table-column>
			<el-table-column prop="label" label="菜单文字" width="150"/>
			<el-table-column label="英文" width="130">
				<template #default="{row}">
					<span v-if="row.labelEn">{{ row.labelEn }}</span>
					<span v-else class="muted">同中文</span>
				</template>
			</el-table-column>
			<el-table-column label="链接" min-width="240">
				<template #default="{row}">
					<code class="path">{{ row.path }}</code>
					<el-tag v-if="isExternal(row.path)" size="small" type="warning" class="ml">外链</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="打开方式" width="110">
				<template #default="{row}">
					{{ row.target === '_blank' ? '新窗口' : '本页' }}
				</template>
			</el-table-column>
			<el-table-column label="显示" width="90">
				<template #default="{row}">
					<el-switch :model-value="row.visible" @change="value => toggle(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column label="操作" width="140" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">编辑</el-button>
					<el-button link type="danger" @click="remove(row)">删除</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-dialog v-model="visible" :title="form.id ? '编辑菜单' : '新增菜单'" width="460px">
			<el-form :model="form" label-width="80px" size="small">
				<el-form-item label="菜单文字" required>
					<el-input v-model="form.label" maxlength="20" show-word-limit placeholder="如：知识付费"/>
				</el-form-item>
				<el-form-item label="英文名称">
					<el-input v-model="form.labelEn" maxlength="20" placeholder="如 Knowledge，留空则英文站沿用中文"/>
				</el-form-item>
				<el-form-item label="链接" required>
					<el-input v-model="form.path" maxlength="255" placeholder="如：/knowledge 或 https://example.com"/>
					<div class="hint">站内页面以 / 开头；外部站点写完整地址，会自动按外链渲染。</div>
				</el-form-item>
				<el-form-item label="打开方式">
					<el-radio-group v-model="form.target">
						<el-radio value="_self">本页打开</el-radio>
						<el-radio value="_blank">新窗口</el-radio>
					</el-radio-group>
				</el-form-item>
				<el-form-item label="排序">
					<el-input-number v-model="form.sort" :min="0" :step="10" controls-position="right"/>
					<div class="hint">数值越小越靠前；不确定就留空，新菜单会排在最后。</div>
				</el-form-item>
				<el-form-item label="显示">
					<el-switch v-model="form.visible"/>
				</el-form-item>
			</el-form>
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
import {Bottom, Plus, Top} from '@element-plus/icons-vue'
import {
	changeNavVisible,
	createNavMenu,
	deleteNavMenu,
	getNavMenus,
	moveNavMenu,
	updateNavMenu
} from '@/api/nav'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const list = ref([])
const form = reactive({
	id: null,
	label: '',
	labelEn: '',
	path: '',
	sort: undefined,
	visible: true,
	target: '_self'
})

function isExternal(path) {
	return /^https?:\/\//i.test(path || '')
}

async function load() {
	loading.value = true
	try {
		const res = await getNavMenus()
		list.value = res.data || []
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	form.id = row ? row.id : null
	form.label = row ? row.label : ''
	form.labelEn = row ? (row.labelEn || '') : ''
	form.path = row ? row.path : ''
	form.sort = row ? row.sort : undefined
	form.visible = row ? !!row.visible : true
	form.target = row ? (row.target || '_self') : '_self'
	visible.value = true
}

async function submit() {
	if (!form.label.trim()) {
		ElMessage.warning('请填写菜单文字')
		return
	}
	if (!form.path.trim()) {
		ElMessage.warning('请填写链接')
		return
	}
	const payload = {
		label: form.label.trim(),
		labelEn: form.labelEn.trim(),
		path: form.path.trim(),
		sort: form.sort === undefined ? null : form.sort,
		visible: form.visible,
		target: form.target
	}
	saving.value = true
	try {
		if (form.id) {
			await updateNavMenu(form.id, payload)
		} else {
			await createNavMenu(payload)
		}
		ElMessage.success('已保存，前台刷新即可看到')
		visible.value = false
		load()
	} finally {
		saving.value = false
	}
}

async function move(row, direction) {
	// 只调接口不刷新整表会与「本地顺序」不一致，统一重拉，菜单量很小
	await moveNavMenu(row.id, direction)
	load()
}

async function toggle(row, value) {
	await changeNavVisible(row.id, value)
	row.visible = value
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(`删除菜单「${row.label}」？删除后前台立即不再显示。`, '删除菜单',
			{type: 'warning', confirmButtonText: '删除'})
	} catch (e) {
		return
	}
	await deleteNavMenu(row.id)
	ElMessage.success('已删除')
	load()
}

onMounted(load)
</script>

<style scoped>
.sort-no {
	margin-left: 6px;
	font-size: 12px;
	color: #a8abb2;
}

.path {
	padding: 2px 6px;
	border-radius: 4px;
	background: #f5f7fa;
	font-size: 12.5px;
}

.ml {
	margin-left: 8px;
}

.hint {
	margin-top: 4px;
	font-size: 12px;
	color: #a8abb2;
	line-height: 1.5;
}
</style>
