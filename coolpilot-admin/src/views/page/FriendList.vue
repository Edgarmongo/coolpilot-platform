<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('友链管理') }}</h2>
				<p class="page-desc">{{ t('前台 /friends 展示的友链，可单独控制是否公开') }}</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">{{ t('新增友链') }}</el-button>
		</div>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('站点')" min-width="180">
				<template #default="{row}">
					<strong>{{ row.nickname }}</strong>
					<p class="muted url">{{ row.website }}</p>
				</template>
			</el-table-column>
			<el-table-column prop="description" :label="t('一句话')" show-overflow-tooltip min-width="180"/>
			<el-table-column :label="t('公开')" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.published" size="small" @change="value => changePublished(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="130" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDialog(row)">{{ t('编辑') }}</el-button>
					<el-button link type="danger" @click="remove(row)">{{ t('删除') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]"
		               :total="total" background layout="total, sizes, prev, pager, next"
		               @size-change="load" @current-change="load"/>

		<el-dialog v-model="visible" :title="form.id ? t('编辑友链') : t('新增友链')" width="460px">
			<el-form label-width="80px" size="small">
				<el-form-item :label="t('昵称')">
					<el-input v-model="form.nickname" maxlength="30"/>
				</el-form-item>
				<el-form-item :label="t('网址')">
					<el-input v-model="form.website" placeholder="https://example.com"/>
				</el-form-item>
				<el-form-item :label="t('头像')">
					<el-input v-model="form.avatar" :placeholder="t('头像图片地址（可选）')"/>
				</el-form-item>
				<el-form-item :label="t('一句话')">
					<el-input v-model="form.description" maxlength="60"/>
				</el-form-item>
			</el-form>
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
import {addFriend, deleteFriend, editFriend, getFriendList, updateFriendPublished} from '@/api/friend'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const list = ref([])
const form = reactive({id: null, nickname: '', website: '', avatar: '', description: '', published: true})

async function load() {
	loading.value = true
	try {
		const res = await getFriendList({pageNum: pageNum.value, pageSize: pageSize.value})
		list.value = res.data.list || []
		total.value = res.data.total || 0
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	Object.assign(form, {
		id: row ? row.id : null,
		nickname: row ? row.nickname : '',
		website: row ? row.website : '',
		avatar: row ? row.avatar : '',
		description: row ? row.description : '',
		published: row ? row.published : true
	})
	visible.value = true
}

async function submit() {
	if (!form.nickname.trim() || !form.website.trim()) {
		ElMessage.warning(t('昵称和网址必填'))
		return
	}
	saving.value = true
	try {
		const payload = {id: form.id || undefined, ...form}
		if (form.id) {
			await editFriend(payload)
		} else {
			await addFriend(payload)
		}
		ElMessage.success(t('保存成功'))
		visible.value = false
		load()
	} finally {
		saving.value = false
	}
}

async function changePublished(row, value) {
	await updateFriendPublished(row.id, value)
	row.published = value
	ElMessage.success(value ? t('已公开') : t('已隐藏'))
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(`删除友链「${row.nickname}」？`, t('删除友链'), {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteFriend(row.id)
	ElMessage.success(t('已删除'))
	load()
}

onMounted(load)
</script>

<style scoped>
.url {
	margin: 2px 0 0;
	word-break: break-all;
}
</style>
