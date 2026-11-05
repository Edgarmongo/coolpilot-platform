<template>
	<div v-loading="loading">
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ isEdit ? t('编辑文章') : t('写文章') }}</h2>
				<p class="page-desc">{{ t('Markdown 写作，右侧实时预览') }}</p>
			</div>
			<div>
				<el-button :icon="Back" @click="$router.push('/blog/list')">{{ t('返回列表') }}</el-button>
				<el-button type="primary" :loading="saving" @click="submit">{{ isEdit ? t('保存修改') : t('保存并发布') }}</el-button>
			</div>
		</div>

		<div class="editor-layout">
			<div class="editor-main">
				<el-input v-model="form.title" :placeholder="t('文章标题')" class="title-input" maxlength="100" show-word-limit/>
				<el-input v-model="form.description" :placeholder="t('摘要：会显示在列表与搜索结果里')" type="textarea"
				          :rows="2" maxlength="200" show-word-limit class="desc-input"/>

				<div class="pane">
					<div class="pane-head">
						<span>{{ t('正文（Markdown）') }}</span>
						<span class="muted uploading" v-if="uploadingImg">{{ t('正在上传图片…') }}</span>
						<span class="muted">{{ t('{n} 字 · 约 {m} 分钟', {n: wordCount, m: readTime}) }}</span>
					</div>
					<textarea ref="editorRef" v-model="form.content" class="markdown" spellcheck="false"
					          :class="{dragging}"
					          :placeholder="t('支持 Markdown，例如：## 小标题、**加粗**、```代码块```；截图后可直接 Ctrl+V 粘贴')"
					          @paste="onPaste"
					          @dragover.prevent="dragging = true"
					          @dragleave="dragging = false"
					          @drop.prevent="onDrop"/>
				</div>
			</div>

			<aside class="editor-side">
				<div class="side-block">
					<h4>{{ t('发布设置') }}</h4>
					<div class="row">
						<span>{{ t('公开') }}</span>
						<el-switch v-model="form.published"/>
					</div>
					<div class="row">
						<span>{{ t('置顶') }}</span>
						<el-switch v-model="form.top"/>
					</div>
					<div class="row">
						<span>{{ t('推荐') }}</span>
						<el-switch v-model="form.recommend"/>
					</div>
					<div class="row">
						<span>{{ t('赞赏') }}</span>
						<el-switch v-model="form.appreciation"/>
					</div>
					<div class="row">
						<span>{{ t('允许评论') }}</span>
						<el-switch v-model="form.commentEnabled"/>
					</div>
					<el-input v-model="form.password" :placeholder="t('访问密码（留空则无）')" class="mt" show-password/>
				</div>

				<div class="side-block">
					<h4>{{ t('链接别名（slug）') }}</h4>
					<el-input v-model="form.slug" :placeholder="t('如 spring-boot-layering，留空则用 id')"
					          maxlength="120" class="mt"/>
					<p class="hint">{{ t('只能使用字母、数字、连字符和下划线；决定 /blog/xxx 这个地址') }}</p>
				</div>

				<div class="side-block">
					<h4>{{ t('分类与标签') }}</h4>
					<el-select v-model="form.cate" filterable allow-create default-first-option
					           :placeholder="t('选择或输入新分类')" class="mt" style="width: 100%">
						<el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
					</el-select>
					<el-select v-model="form.tagList" multiple filterable allow-create default-first-option
					           :placeholder="t('选择或输入新标签')" class="mt" style="width: 100%">
						<el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id"/>
					</el-select>
				</div>

				<div class="side-block">
					<h4>{{ t('关联付费内容') }}</h4>
					<el-select v-model="relatedIds" multiple filterable :placeholder="t('在文章底部推荐的商品')"
					           class="mt" style="width: 100%">
						<el-option v-for="p in productOptions" :key="p.id" :value="p.id"
						           :label="`${p.title}（${priceText(p)}）`"/>
					</el-select>
					<p class="hint">{{ t('只列出已上架商品；用户已购买时前台显示「已拥有」') }}</p>
				</div>

				<div class="side-block">
					<h4>{{ t('首图') }}</h4>
					<el-input v-model="form.firstPicture" :placeholder="t('上传或直接填写图片地址')" class="mt"/>
					<div class="upload-row">
						<input ref="fileInput" type="file" accept="image/*" hidden @change="uploadCover">
						<el-button size="small" :loading="uploading" @click="$refs.fileInput.click()">
							{{ uploading ? t('上传中…') : t('上传图片') }}
						</el-button>
					</div>
					<img v-if="form.firstPicture" :src="form.firstPicture" class="cover" alt="">
				</div>

				<div class="side-block">
					<h4>{{ t('预览') }}</h4>
					<div class="preview article" v-html="previewHtml"></div>
				</div>
			</aside>
		</div>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, nextTick, onMounted, reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {Back} from '@element-plus/icons-vue'
import {marked} from 'marked'
import request from '@/util/request'
import {getBlog, getCategoryAndTag, saveBlog, updateBlog} from '@/api/blog'
import {bindBlogProducts, getBlogProducts, getProductList} from '@/api/product'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
// 正文里的图片上传（粘贴 / 拖拽），与封面按钮的 loading 分开，互不干扰
const uploadingImg = ref(false)
const editorRef = ref(null)
const dragging = ref(false)
// 单张上限：截图通常几百 KB，10 MB 足够，也避免误传手机原图撑爆正文
const MAX_IMAGE_MB = 10
const categories = ref([])
const tags = ref([])
const productOptions = ref([])
const relatedIds = ref([])
const isEdit = computed(() => !!route.params.id)

const form = reactive({
	id: null,
	title: '',
	slug: '',
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
const previewHtml = computed(() => (form.content ? marked.parse(form.content) : t('<p class="muted">暂无内容</p>')))

function priceText(product) {
	if (product.priceMode === 'CUSTOM') {
		return t('面议')
	}
	return '¥' + Number(product.price || 0).toFixed(2).replace(/\.00$/, '')
}

/**
 * 在光标处插入文本。
 * textarea 由 v-model 受控，直接改 form.content 会把光标顶到末尾，
 * 所以插入后手动把光标移到内容之后，连续粘贴多张图才不会次序错乱。
 */
function insertAtCursor(text) {
	const el = editorRef.value
	const start = el ? el.selectionStart : form.content.length
	const end = el ? el.selectionEnd : start
	form.content = form.content.slice(0, start) + text + form.content.slice(end)
	nextTick(() => {
		if (!el) {
			return
		}
		el.focus()
		const pos = start + text.length
		el.setSelectionRange(pos, pos)
	})
}

/**
 * 把占位标记换成真实内容。
 *
 * 改 form.content 会让 Vue 重设 textarea 的值，浏览器随后把光标甩到末尾。
 * 如果用户上传期间正在别处打字，就会发现光标突然跳走，所以按替换前后的
 * 长度差把光标平移回去；用户已经切走输入框时则不去抢焦点。
 */
function replaceMarker(marker, replacement) {
	const el = editorRef.value
	const active = document.activeElement === el
	const pos = el ? el.selectionStart : 0
	const index = form.content.indexOf(marker)
	if (index < 0) {
		return
	}
	form.content = form.content.replace(marker, replacement)
	if (!el || !active) {
		return
	}
	const next = pos > index ? pos - marker.length + replacement.length : pos
	nextTick(() => el.setSelectionRange(next, next))
}

/**
 * 上传单张图片，并把占位标记换成 Markdown 图片语法。
 *
 * 先插一个 HTML 注释当占位（marked 不显示它，不像裂图那样难看），
 * 成功就换成 ![](url)，失败就删掉。用户在上传期间继续打字，
 * 图片也不会插到错误的位置。
 */
async function uploadOneImage(file) {
	const marker = `<!--img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}-->`
	insertAtCursor(`\n${marker}\n`)
	const formData = new FormData()
	// 截图拿到的文件名通常就是 image.png，后端按时间戳重命名，这里只借用扩展名
	formData.append('file', file, file.name || 'image.png')
	formData.append('bizType', 'ARTICLE')
	formData.append('isPublic', 'true')
	try {
		const res = await request({
			url: 'file/upload',
			method: 'POST',
			data: formData,
			headers: {'Content-Type': 'multipart/form-data'}
		})
		replaceMarker(marker, `![](${res.data.url})`)
		return true
	} catch (e) {
		// 请求拦截器已经弹过错误，这里只把占位清掉
		replaceMarker(marker, '')
		return false
	}
}

async function uploadImageFiles(files) {
	const images = Array.from(files || []).filter(file => file.type && file.type.startsWith('image/'))
	if (!images.length) {
		return
	}
	if (uploadingImg.value) {
		ElMessage.warning(t('还有图片正在上传，请稍候'))
		return
	}
	uploadingImg.value = true
	let ok = 0
	try {
		for (const file of images) {
			if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
				ElMessage.warning(t('图片超过 {n} MB，请先压缩', {n: MAX_IMAGE_MB}))
				continue
			}
			if (await uploadOneImage(file)) {
				ok++
			}
		}
	} finally {
		uploadingImg.value = false
	}
	if (ok) {
		ElMessage.success(t('已插入 {n} 张图片', {n: ok}))
	}
}

function onPaste(event) {
	const items = (event.clipboardData && event.clipboardData.items) || []
	const files = []
	for (const item of items) {
		if (item.kind === 'file' && item.type.startsWith('image/')) {
			const file = item.getAsFile()
			if (file) {
				files.push(file)
			}
		}
	}
	// 没有图片就走浏览器默认粘贴，纯文本、代码片段照常
	if (!files.length) {
		return
	}
	event.preventDefault()
	uploadImageFiles(files)
}

function onDrop(event) {
	dragging.value = false
	const files = (event.dataTransfer && event.dataTransfer.files) || []
	uploadImageFiles(files)
}

async function uploadCover(event) {	const file = event.target.files && event.target.files[0]
	if (!file) {
		return
	}
	uploading.value = true
	try {
		const formData = new FormData()
		formData.append('file', file)
		formData.append('bizType', 'ARTICLE')
		formData.append('isPublic', 'true')
		const res = await request({
			url: 'file/upload',
			method: 'POST',
			data: formData,
			headers: {'Content-Type': 'multipart/form-data'}
		})
		form.firstPicture = res.data.url
		ElMessage.success(t('已插入首图地址'))
	} finally {
		uploading.value = false
		event.target.value = ''
	}
}

async function submit() {
	// 上传中的图片在正文里只有一个注释占位，此时保存会把它一起存进去
	if (uploadingImg.value) {
		ElMessage.warning(t('图片还在上传，请稍候再保存'))
		return
	}
	if (!form.title.trim()) {
		ElMessage.warning(t('请填写标题'))
		return
	}
	if (!form.content.trim()) {
		ElMessage.warning(t('请填写正文'))
		return
	}
	if (form.cate === null || form.cate === '') {
		ElMessage.warning(t('请选择分类'))
		return
	}
	if (!form.description.trim()) {
		ElMessage.warning(t('请填写摘要'))
		return
	}
	saving.value = true
	try {
		const payload = {
			id: form.id || undefined,
			title: form.title.trim(),
			slug: form.slug.trim() || null,
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
		let blogId = form.id
		if (isEdit.value) {
			await updateBlog(payload)
		} else {
			const created = await saveBlog(payload)
			blogId = created && created.data ? created.data.id : null
		}
		// 文章已保存成功；绑定失败单独提示，不要让用户以为整篇没存上
		if (blogId) {
			try {
				await bindBlogProducts(blogId, relatedIds.value)
			} catch (e) {
				ElMessage.warning(t('文章已保存，但关联商品未保存，请重新编辑后再试'))
			}
		}
		ElMessage.success(t('保存成功'))
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
		const products = await getProductList({status: 'ON_SALE', pageNum: 1, pageSize: 100})
		productOptions.value = (products.data && products.data.list) || []
		if (isEdit.value) {
			const res = await getBlog(route.params.id)
			const blog = res.data
			Object.assign(form, {
				id: blog.id,
				title: blog.title,
				slug: blog.slug || '',
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
			const bound = await getBlogProducts(blog.id).catch(() => null)
			relatedIds.value = (bound && bound.data && bound.data.productIds) || []
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

/* 拖拽悬停时给个明确反馈，否则用户不确定能不能松手 */
.markdown.dragging {
	background: #eaf7f4;
	box-shadow: inset 0 0 0 2px #4ec9b0;
}

.uploading {
	color: #2f9e87;
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

.upload-row {
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
