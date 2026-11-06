<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('社交媒体') }}</h2>
				<p class="page-desc">
					{{ t('前台「关注我」页与首页图标行的数据来源。只有打开「显示」的账号才会出现在前台；没有运营的平台建议先关掉。') }}
				</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDialog()">{{ t('新增账号') }}</el-button>
		</div>

		<el-alert v-if="!list.length && !loading" type="info" :closable="false" show-icon
		          :title="t('还没有配置社交账号')"
		          :description="t('新增一个后，前台 /follow 页与首页「关注我」一行就会出现它。')"/>

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

			<el-table-column :label="t('平台')" width="130">
				<template #default="{row}">
					<el-tag size="small" effect="plain">{{ platformLabel(row.platform) }}</el-tag>
				</template>
			</el-table-column>

			<el-table-column prop="accountName" :label="t('账号名')" width="150"/>

			<el-table-column :label="t('简介')" min-width="180">
				<template #default="{row}">
					<span v-if="row.intro" class="intro">{{ row.intro }}</span>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('二维码')" width="80">
				<template #default="{row}">
					<img v-if="row.qrUrl" :src="row.qrUrl" class="qr-thumb" :alt="row.accountName"/>
					<span v-else class="muted">{{ t('无') }}</span>
				</template>
			</el-table-column>

			<el-table-column :label="t('主页链接')" min-width="200">
				<template #default="{row}">
					<code v-if="row.homeUrl" class="path">{{ row.homeUrl }}</code>
					<span v-else class="muted">{{ t('仅二维码') }}</span>
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

		<el-dialog v-model="visible" :title="form.id ? t('编辑账号') : t('新增账号')" width="520px">
			<el-form :model="form" label-width="86px" size="small">
				<el-form-item :label="t('平台')" required>
					<el-select v-model="form.platform" style="width: 100%">
						<el-option v-for="p in PLATFORMS" :key="p.value" :label="p.label" :value="p.value"/>
					</el-select>
				</el-form-item>

				<el-form-item :label="t('账号名')" required>
					<el-input v-model="form.accountName" maxlength="64" show-word-limit
					          :placeholder="t('显示在平台名旁边的名字，如 GitHub 用户名')"/>
				</el-form-item>

				<el-form-item :label="t('简介')">
					<el-input v-model="form.intro" type="textarea" :rows="2" maxlength="255" show-word-limit
					          :placeholder="t('一句话说明这个号发什么')"/>
				</el-form-item>

				<el-form-item :label="t('主页链接')">
					<el-input v-model="form.homeUrl" :placeholder="t('https://… 或 mailto:…，只有二维码就留空')"/>
				</el-form-item>

				<el-form-item :label="t('二维码')">
					<div class="qr-field">
						<div v-if="form.qrUrl" class="qr-preview">
							<img :src="form.qrUrl" alt="qr"/>
						</div>
						<div class="qr-actions">
							<el-button size="small" :loading="uploading" @click="pickFile">
								{{ form.qrUrl ? t('更换图片') : t('上传图片') }}
							</el-button>
							<el-button v-if="form.qrUrl" size="small" text type="danger" @click="form.qrUrl = ''">
								{{ t('移除') }}
							</el-button>
							<input ref="fileInput" type="file" accept="image/*" class="hidden-input"
							       @change="onPicked"/>
						</div>
						<p class="hint">{{ t('建议正方形二维码，5 MB 以内') }}</p>
					</div>
				</el-form-item>

				<el-form-item :label="t('显示')">
					<el-switch v-model="form.enabled"/>
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
import {Bottom, Plus, Top} from '@element-plus/icons-vue'
import {
	changeSocialVisible,
	createSocialAccount,
	deleteSocialAccount,
	getSocialAccounts,
	moveSocialAccount,
	updateSocialAccount
} from '@/api/social'
import request from '@/util/request'

const PLATFORMS = [
	{value: 'GITHUB', label: 'GitHub'},
	{value: 'WECHAT', label: '微信'},
	{value: 'WECHAT_MP', label: '公众号'},
	{value: 'DOUYIN', label: '抖音'},
	{value: 'XIAOHONGSHU', label: '小红书'},
	{value: 'ZHIHU', label: '知乎'},
	{value: 'BILIBILI', label: 'bilibili'},
	{value: 'TELEGRAM', label: 'Telegram'},
	{value: 'QQ', label: 'QQ'},
	{value: 'NETEASE', label: '网易云音乐'},
	{value: 'EMAIL', label: '邮箱'},
	{value: 'CUSTOM', label: '其他'}
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
	platform: 'GITHUB',
	accountName: '',
	intro: '',
	homeUrl: '',
	qrUrl: '',
	icon: '',
	enabled: true
})

function platformLabel(value) {
	const hit = PLATFORMS.find(p => p.value === value)
	return hit ? hit.label : value
}

async function load() {
	loading.value = true
	try {
		const res = await getSocialAccounts()
		list.value = res.data || []
	} finally {
		loading.value = false
	}
}

function openDialog(row) {
	if (row) {
		Object.assign(form, {
			id: row.id,
			platform: row.platform,
			accountName: row.accountName,
			intro: row.intro || '',
			homeUrl: row.homeUrl || '',
			qrUrl: row.qrUrl || '',
			icon: row.icon || '',
			enabled: !!row.enabled
		})
	} else {
		Object.assign(form, {
			id: null,
			platform: 'GITHUB',
			accountName: '',
			intro: '',
			homeUrl: '',
			qrUrl: '',
			icon: '',
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
		formData.append('bizType', 'SOCIAL')
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
		form.qrUrl = res.data.url
	} finally {
		uploading.value = false
	}
}

async function submit() {
	if (!form.accountName.trim()) {
		ElMessage.warning(t('请填写账号名'))
		return
	}
	saving.value = true
	try {
		const payload = {
			platform: form.platform,
			accountName: form.accountName.trim(),
			intro: form.intro,
			homeUrl: form.homeUrl,
			qrUrl: form.qrUrl,
			icon: form.icon,
			enabled: form.enabled
		}
		if (form.id) {
			await updateSocialAccount(form.id, payload)
		} else {
			await createSocialAccount(payload)
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
		await changeSocialVisible(row.id, value)
		row.enabled = value
		ElMessage.success(value ? t('已显示') : t('已隐藏'))
	} catch (e) {
		await load()
	}
}

async function move(row, direction) {
	await moveSocialAccount(row.id, direction)
	await load()
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(t('删除「{name}」？前台会立即消失。', {name: row.accountName}), t('确认删除'),
			{type: 'warning'})
	} catch (e) {
		return
	}
	await deleteSocialAccount(row.id)
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

.intro {
	font-size: 12.5px;
	color: #4b5563;
}

.path {
	font-size: 12px;
	color: #4b5563;
	word-break: break-all;
}

.muted {
	color: #9aa0a6;
	font-size: 12.5px;
}

.qr-thumb {
	width: 34px;
	height: 34px;
	object-fit: contain;
	border: 1px solid #e8eded;
	border-radius: 4px;
	background: #fff;
}

.qr-field {
	display: flex;
	flex-direction: column;
	gap: 8px;
	width: 100%;
}

.qr-preview img {
	width: 84px;
	height: 84px;
	object-fit: contain;
	border: 1px solid #e8eded;
	border-radius: 6px;
	background: #fff;
}

.qr-actions {
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
