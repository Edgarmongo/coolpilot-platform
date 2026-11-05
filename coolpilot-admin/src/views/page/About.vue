<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('关于我') }}</h2>
				<p class="page-desc">{{ t('前台 /about 的内容，支持 HTML') }}</p>
			</div>
			<el-button type="primary" :loading="saving" @click="save">{{ t('保存') }}</el-button>
		</div>

		<div class="page-card" v-loading="loading">
			<el-form label-width="90px" size="small">
				<el-form-item :label="t('标题')">
					<el-input v-model="form.title" maxlength="50"/>
				</el-form-item>
				<el-form-item :label="t('网易云ID')">
					<el-input v-model="form.musicId" :placeholder="t('歌单/歌曲 id，可留空')"/>
				</el-form-item>
				<el-form-item :label="t('允许评论')">
					<el-switch v-model="form.commentEnabled"/>
				</el-form-item>
				<el-form-item :label="t('正文(HTML)')">
					<el-input v-model="form.content" type="textarea" :rows="12"
					          :class="{dragging: zhDragging}"
					          :placeholder="t('支持 HTML / Markdown，例如 &lt;p&gt;简介&lt;/p&gt;；截图后可直接 Ctrl+V 粘贴')"
					          @paste="onZhPaste"
					          @dragover.prevent="onZhDragOver"
					          @dragleave="onZhDragLeave"
					          @drop.prevent="onZhDrop"/>
				</el-form-item>
				<el-divider content-position="left">{{ t('英文版（留空则英文站显示上面的中文）') }}</el-divider>
				<el-form-item :label="t('英文标题')">
					<el-input v-model="form.titleEn" maxlength="50" placeholder="About CoolSkyAI"/>
				</el-form-item>
				<el-form-item :label="t('英文正文')">
					<el-input v-model="form.contentEn" type="textarea" :rows="12"
					          :class="{dragging: enDragging}"
					          :placeholder="t('Markdown / HTML，留空则回落中文正文；截图后可直接 Ctrl+V 粘贴')"
					          @paste="onEnPaste"
					          @dragover.prevent="onEnDragOver"
					          @dragleave="onEnDragLeave"
					          @drop.prevent="onEnDrop"/>
				</el-form-item>
			</el-form>
		</div>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getAbout, updateAbout} from '@/api/about'
import {useImagePaste} from '@/composables/useImagePaste'

const loading = ref(false)
const saving = ref(false)
const form = reactive({title: '', titleEn: '', musicId: '', content: '', contentEn: '', commentEnabled: true})

// 中英正文各自一份配置，所以各接一个实例，互不影响
const {
	uploading: zhUploading,
	dragging: zhDragging,
	onPaste: onZhPaste,
	onDrop: onZhDrop,
	onDragOver: onZhDragOver,
	onDragLeave: onZhDragLeave
} = useImagePaste({
	getValue: () => form.content,
	setValue: value => {
		form.content = value
	},
	bizType: 'ABOUT'
})

const {
	uploading: enUploading,
	dragging: enDragging,
	onPaste: onEnPaste,
	onDrop: onEnDrop,
	onDragOver: onEnDragOver,
	onDragLeave: onEnDragLeave
} = useImagePaste({
	getValue: () => form.contentEn,
	setValue: value => {
		form.contentEn = value
	},
	bizType: 'ABOUT'
})

async function load() {
	loading.value = true
	try {
		const res = await getAbout()
		const data = res.data || {}
		form.title = data.title || ''
		form.titleEn = data.titleEn || ''
		form.musicId = data.musicId || ''
		form.content = data.content || ''
		form.contentEn = data.contentEn || ''
		form.commentEnabled = String(data.commentEnabled) === 'true' || data.commentEnabled === true
	} finally {
		loading.value = false
	}
}

async function save() {
	// 上传中的图片只是正文里的一行注释，此刻保存会把占位存进正文
	if (zhUploading.value || enUploading.value) {
		ElMessage.warning(t('图片还在上传，请稍候再保存'))
		return
	}
	saving.value = true
	try {
		// 后端按 nameEn -> value 逐条更新，值必须是字符串
		await updateAbout({
			title: form.title,
			titleEn: form.titleEn,
			musicId: form.musicId,
			content: form.content,
			contentEn: form.contentEn,
			commentEnabled: String(form.commentEnabled)
		})
		ElMessage.success(t('已保存'))
	} finally {
		saving.value = false
	}
}

onMounted(load)
</script>
