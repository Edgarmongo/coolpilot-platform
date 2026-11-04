<template>
	<div v-loading="loading">
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ isEdit ? '编辑文章' : '写文章' }}</h2>
				<p class="page-desc">Markdown 写作，右侧实时预览</p>
			</div>
			<div>
				<el-button :icon="Back" @click="$router.push('/blog/list')">返回列表</el-button>
				<el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? '保存修改' : '保存并发布' }}</el-button>
			</div>
		</div>

		<div class="editor-layout">
			<div class="editor-main">
				<el-input v-model="form.title" placeholder="文章标题" class="title-input" maxlength="100" show-word-limit/>
				<el-input v-model="form.description" placeholder="摘要：会显示在列表与搜索结果里" type="textarea"
				          :rows="2" maxlength="200" show-word-limit class="desc-input"/>

				<div class="pane">
					<div class="pane-head">
						<span>正文（Markdown）</span>
						<span class="muted">{{ wordCount }} 字 · 约 {{ readTime }} 分钟</span>
					</div>
					<textarea v-model="form.content" class="markdown" spellcheck="false"
					          placeholder="支持 Markdown，例如：## 小标题、**加粗**、```代码块```"/>
				</div>
			</div>

			<aside class="editor-side">
				<div class="side-block">
					<h4>发布设置</h4>
					<div class="row">
						<span>公开</span>
						<el-switch v-model="form.published"/>
					</div>
					<div class="row">
						<span>置顶</span>
						<el-switch v-model="form.top"/>
					</div>
					<div class="row">
						<span>推荐</span>
						<el-switch v-model="form.recommend"/>
					</div>
					<div class="row">
						<span>赞赏</span>
						<el-switch v-model="form.appreciation"/>
					</div>
					<div class="row">
						<span>允许评论</span>
						<el-switch v-model="form.commentEnabled"/>
					</div>
					<el-input v-model="form.password" placeholder="访问密码（留空则无）" class="mt" show-password/>
				</div>

				<div class="side-block">
					<h4>分类与标签</h4>
					<el-select v-model="form.cate" filterable allow-create default-first-option
					           placeholder="选择或输入新分类" class="mt" style="width: 100%">
						<el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
					</el-select>
					<el-select v-model="form.tagList" multiple filterable allow-create default-first-option
					           placeholder="选择或输入新标签" class="mt" style="width: 100%">
						<el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id"/>
					</el-select>
				</div>

				<div class="side-block">
					<h4>首图</h4>
					<el-input v-model="form.firstPicture" placeholder="/img/cover.jpg 或完整图片地址" class="mt"/>
					<img v-if="form.firstPicture" :src="form.firstPicture" class="cover" alt="">
				</div>

				<div class="side-block">
					<h4>预览</h4>
					<div class="preview article" v-html="previewHtml"></div>
				</div>
			</aside>
		</div>
	</div>
</template>

<script setup>
import {computed, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {Back} from '@element-plus/icons-vue'
import {marked} from 'marked'
import {getBlog, getCategoryAndTag, saveBlog, updateBlog} from '@/api/blog'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const categories = ref([])
const tags = ref([])
const isEdit = computed(() => !!route.params.id)

const form = reactive({
	id: null,
	title: '',
	description: '',
	content: '',
	firstPicture: '/img/cover.jpg',
	published: true,
	top: false,
	recommend: false,
	appreciation: true,
	commentEnabled: true,
	password: '',
	cate: null,
	tagList: []
})

const wordCount = computed(() => (form.content || '').replace(/\s/g, '').length)
const readTime = computed(() => Math.max(1, Math.ceil(wordCount.value / 350)))
const previewHtml = computed(() => (form.content ? marked.parse(form.content) : '<p class="muted">暂无内容</p>'))

async function submit() {
	if (!form.title.trim()) {
		ElMessage.warning('请填写标题')
		return
	}
	if (!form.content.trim()) {
		ElMessage.warning('请填写正文')
		return
	}
	if (form.cate === null || form.cate === '') {
		ElMessage.warning('请选择分类')
		return
	}
	if (!form.description.trim()) {
		ElMessage.warning('请填写摘要')
		return
	}
	saving.value = true
	try {
		const payload = {
			id: form.id || undefined,
			title: form.title.trim(),
			description: form.description.trim(),
			content: form.content,
			firstPicture: form.firstPicture || '/img/cover.jpg',
			published: form.published,
			top: form.top,
			recommend: form.recommend,
			appreciation: form.appreciation,
			commentEnabled: form.commentEnabled,
			password: form.password || '',
			words: wordCount.value,
			readTime: readTime.value,
			cate: form.cate,
			tagList: form.tagList
		}
		if (isEdit.value) {
			await updateBlog(payload)
		} else {
			await saveBlog(payload)
		}
		ElMessage.success('保存成功')
		router.push('/blog/list')
	} finally {
		saving.value = false
	}
}

onMounted(async () => {
	loading.value = true
	try {
		const meta = await getCategoryAndTag()
		categories.value = meta.data.categories || []
		tags.value = meta.data.tags || []
		if (isEdit.value) {
			const res = await getBlog(route.params.id)
			const blog = res.data
			Object.assign(form, {
				id: blog.id,
				title: blog.title,
				description: blog.description,
				content: blog.content,
				firstPicture: blog.firstPicture,
				published: blog.published,
				top: blog.top,
				recommend: blog.recommend,
				appreciation: blog.appreciation,
				commentEnabled: blog.commentEnabled,
				password: blog.password || '',
				cate: blog.category ? blog.category.id : null,
				tagList: (blog.tags || []).map(tag => tag.id)
			})
		}
	} finally {
		loading.value = false
	}
})
</script>

<style scoped>
.editor-layout {
	display: grid;
	grid-template-columns: minmax(0, 1fr) 320px;
	gap: 14px;
	align-items: start;
}

.editor-main {
	display: flex;
	flex-direction: column;
	gap: 10px;
}

.title-input :deep(.el-input__inner) {
	font-size: 18px;
	font-weight: 600;
	height: 46px;
}

.pane {
	background: #fff;
	border: 1px solid #e4e7ed;
	border-radius: 8px;
	overflow: hidden;
	display: flex;
	flex-direction: column;
}

.pane-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 8px 12px;
	background: #fafafa;
	border-bottom: 1px solid #e4e7ed;
	font-size: 12.5px;
	color: #606266;
}

.markdown {
	width: 100%;
	min-height: 58vh;
	padding: 14px 16px;
	border: none;
	outline: none;
	resize: vertical;
	font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	font-size: 13.5px;
	line-height: 1.75;
	color: #303133;
}

.editor-side {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

.side-block {
	background: #fff;
	border: 1px solid #e4e7ed;
	border-radius: 8px;
	padding: 12px 14px;
}

.side-block h4 {
	margin: 0 0 8px;
	font-size: 13px;
	color: #606266;
	font-weight: 600;
}

.row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 4px 0;
	font-size: 13px;
	color: #303133;
}

.mt {
	margin-top: 8px;
}

.cover {
	width: 100%;
	margin-top: 8px;
	border-radius: 6px;
	object-fit: cover;
	max-height: 130px;
}

.preview {
	max-height: 260px;
	overflow: auto;
	font-size: 13px;
}

.preview :deep(img) {
	max-width: 100%;
}
</style>
