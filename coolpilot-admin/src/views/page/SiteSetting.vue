<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('站点设置') }}</h2>
				<p class="page-desc">{{ t('站点名、资料卡、徽标、收款码等展示信息；图片类字段可直接上传，保存后前台即时生效') }}</p>
			</div>
			<el-button type="primary" :loading="saving" @click="save">{{ t('保存全部') }}</el-button>
		</div>

		<!-- 图片字段共用一个 file input：点哪一项就记下目标，避免 v-for 里挂一堆 ref -->
		<input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="onPicked">

		<el-tabs v-model="tab">
			<el-tab-pane v-for="group in groups" :key="group.type" :label="group.label" :name="String(group.type)">
				<div v-loading="loading" class="settings">
					<div v-for="item in group.items" :key="item.id" class="row">
						<label>{{ item.nameZh || item.nameEn }}</label>

						<!-- 图片类：左边预览，右边地址 + 上传 -->
						<div v-if="isImage(item)" class="img-field">
							<div class="thumb">
								<img v-if="item.value && previewOk[item.id] !== false" :src="item.value" alt=""
								     @error="previewOk[item.id] = false"
								     @load="previewOk[item.id] = true">
								<span v-else class="thumb-empty">{{ item.value ? t('无法预览') : t('未设置') }}</span>
							</div>
							<div class="img-ops">
								<div class="img-line">
									<el-input v-model="item.value" :placeholder="t('上传后自动填入，也可粘贴完整地址')"
									          @input="previewOk[item.id] = true"/>
									<el-button size="small" :loading="uploadingId === item.id" @click="pick(item)">{{ t('上传') }}</el-button>
								</div>
								<div class="hint">{{ t('支持 png / jpg / webp，单张不超过 5 MB。 相对路径（/img/…）后台预览不到属正常，建议用上传拿到的完整地址。') }}</div>
							</div>
						</div>

						<template v-else>
							<el-input v-if="isLong(item)" v-model="item.value" type="textarea" :rows="3"/>
							<el-input v-else v-model="item.value"/>
							<!-- 基础设置里有品牌名、页脚这类前台展示文案，英文单独一份 -->
							<el-input v-if="item.type === 1 && !isImage(item)" v-model="item.valueEn" class="en-input"
							          :placeholder="t('英文值（留空则英文站沿用中文）')"/>
						</template>
					</div>
				</div>
			</el-tab-pane>
		</el-tabs>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getSiteSettings, updateSiteSettings} from '@/api/siteSetting'
import request from '@/util/request'

const loading = ref(false)
const saving = ref(false)
const tab = ref('1')
const all = ref([])

/** 图片类配置：这些字段的值是图片地址，给预览和上传按钮 */
const IMAGE_KEYS = new Set(['wechatQr', 'alipayQr', 'footerImgUrl', 'reward', 'avatar', 'planetCover', 'planetQr'])
const MAX_IMAGE_BYTES = 5 * 1024 * 1024

const fileInput = ref(null)
const uploadingId = ref(null)
const uploadTarget = ref(null)
// 记录哪些字段的预览加载失败，避免相对路径在后台域下一直显示裂图
const previewOk = reactive({})

// 分组标题按 type 映射；后端按库里实际存在的 type 动态返回，
// 所以这里不再写死 1~4 —— 以后加新分组（比如支付设置）不用再改前端。
const TYPE_LABELS = {
	1: t('基础设置'),
	2: t('资料卡'),
	3: t('徽标'),
	4: t('友链说明'),
	5: t('支付设置'),
	6: t('知识星球')
}

const groups = computed(() => {
	const grouped = new Map()
	for (const item of all.value) {
		if (item.type === null || item.type === undefined) {
			continue
		}
		if (!grouped.has(item.type)) {
			grouped.set(item.type, [])
		}
		grouped.get(item.type).push(item)
	}
	return [...grouped.entries()]
		.sort((a, b) => a[0] - b[0])
		.map(([type, items]) => ({
			type,
			label: TYPE_LABELS[type] || t('其他设置（type {type}）', {type}),
			items
		}))
})

function isLong(item) {
	return (item.value || '').length > 60
}

function isImage(item) {
	return IMAGE_KEYS.has(item.nameEn)
}

function pick(item) {
	uploadTarget.value = item
	// 同一个 input 复用：先记目标，再弹选择框
	if (fileInput.value) {
		fileInput.value.click()
	}
}

/**
 * 上传走的还是 /admin/file/upload（isPublic=true 才返回直链）。
 * 上传成功后只改本地值，不自动保存 —— 站长可能一次改多项，
 * 统一由「保存全部」落库，避免半套配置已经生效。
 */
async function onPicked(event) {
	const file = event.target.files && event.target.files[0]
	// 清空才能连续选同一张图再触发 change
	event.target.value = ''
	const item = uploadTarget.value
	if (!file || !item) {
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
	uploadingId.value = item.id
	try {
		const formData = new FormData()
		formData.append('file', file)
		formData.append('bizType', 'SITE')
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
		item.value = res.data.url
		previewOk[item.id] = true
		ElMessage.success(t('已上传，点右上角「保存全部」后生效'))
	} finally {
		uploadingId.value = null
	}
}

async function load() {
	loading.value = true
	try {
		const res = await getSiteSettings()
		// 后端按 type 分组返回，拍平成一个数组再编辑
		all.value = Object.values(res.data || {}).flat()
	} finally {
		loading.value = false
	}
}

async function save() {
	saving.value = true
	try {
		await updateSiteSettings({
			settings: all.value.map(item => ({
				id: item.id,
				nameEn: item.nameEn,
				nameZh: item.nameZh,
				type: item.type,
				value: item.value,
				valueEn: item.valueEn
			})),
			deleteIds: []
		})
		ElMessage.success(t('已保存'))
	} finally {
		saving.value = false
	}
}

onMounted(load)
</script>

<style scoped>
.settings {
	padding: 4px 2px;
}

.row {
	display: grid;
	grid-template-columns: 150px 1fr;
	align-items: start;
	gap: 14px;
	padding: 8px 0;
	border-bottom: 1px dashed #eef2f2;
}

.row label {
	padding-top: 6px;
	font-size: 13px;
	color: #606266;
}

.img-field {
	display: flex;
	align-items: flex-start;
	gap: 12px;
}

.thumb {
	flex: none;
	display: flex;
	align-items: center;
	justify-content: center;
	width: 72px;
	height: 72px;
	border: 1px dashed #dcdfe6;
	border-radius: 6px;
	background: #fafafa;
	overflow: hidden;
}

.thumb img {
	width: 100%;
	height: 100%;
	object-fit: contain;
}

.thumb-empty {
	font-size: 12px;
	color: #a8abb2;
}

.img-ops {
	flex: 1;
	min-width: 0;
}

.img-line {
	display: flex;
	gap: 8px;
}

.en-input {
	margin-top: 6px;
}

.hint {
	margin-top: 4px;
	font-size: 12px;
	color: #a8abb2;
	line-height: 1.5;
}
</style>
