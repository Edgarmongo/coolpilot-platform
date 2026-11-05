<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">关于我</h2>
				<p class="page-desc">前台 /about 的内容，支持 HTML</p>
			</div>
			<el-button type="primary" :loading="saving" @click="save">保存</el-button>
		</div>

		<div class="page-card" v-loading="loading">
			<el-form label-width="90px" size="small">
				<el-form-item label="标题">
					<el-input v-model="form.title" maxlength="50"/>
				</el-form-item>
				<el-form-item label="网易云ID">
					<el-input v-model="form.musicId" placeholder="歌单/歌曲 id，可留空"/>
				</el-form-item>
				<el-form-item label="允许评论">
					<el-switch v-model="form.commentEnabled"/>
				</el-form-item>
				<el-form-item label="正文(HTML)">
					<el-input v-model="form.content" type="textarea" :rows="12"
					          placeholder="支持 HTML，例如 &lt;p&gt;简介&lt;/p&gt;"/>
				</el-form-item>
				<el-divider content-position="left">英文版（留空则英文站显示上面的中文）</el-divider>
				<el-form-item label="英文标题">
					<el-input v-model="form.titleEn" maxlength="50" placeholder="About CoolSkyAI"/>
				</el-form-item>
				<el-form-item label="英文正文">
					<el-input v-model="form.contentEn" type="textarea" :rows="12"
					          placeholder="Markdown / HTML，留空则回落中文正文"/>
				</el-form-item>
			</el-form>
		</div>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getAbout, updateAbout} from '@/api/about'

const loading = ref(false)
const saving = ref(false)
const form = reactive({title: '', titleEn: '', musicId: '', content: '', contentEn: '', commentEnabled: true})

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
		ElMessage.success('已保存')
	} finally {
		saving.value = false
	}
}

onMounted(load)
</script>
