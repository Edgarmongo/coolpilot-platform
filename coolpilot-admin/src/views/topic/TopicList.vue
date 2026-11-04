<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">专题管理</h2>
				<p class="page-desc">把系列文章组织成一条可分享的阅读路径</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDrawer()">新建专题</el-button>
		</div>

		<el-form inline class="filters">
			<el-form-item>
				<el-input v-model="keyword" placeholder="标题或 slug" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-select v-model="published" placeholder="全部状态" clearable style="width: 130px">
					<el-option label="已发布" :value="true"/>
					<el-option label="未发布" :value="false"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button :icon="Search" @click="search">查询</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="title" label="专题名称" min-width="180">
				<template #default="{row}">
					<div class="cell-title">{{ row.title }}</div>
					<div class="muted small">{{ row.description || '—' }}</div>
				</template>
			</el-table-column>
			<el-table-column label="slug" width="200">
				<template #default="{row}">
					<code class="slug">/topic/{{ row.slug }}</code>
				</template>
			</el-table-column>
			<el-table-column label="文章数" width="90" align="center">
				<template #default="{row}">{{ row.articleCount || 0 }}</template>
			</el-table-column>
			<el-table-column label="状态" width="90" align="center">
				<template #default="{row}">
					<el-switch :model-value="row.isPublished" @change="value => toggleStatus(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column prop="sort" label="排序" width="80" align="center"/>
			<el-table-column label="更新时间" width="150">
				<template #default="{row}">{{ formatTime(row.updateTime) }}</template>
			</el-table-column>
			<el-table-column label="操作" width="150" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDrawer(row)">编辑</el-button>
					<el-button link type="danger" @click="remove(row)">删除</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" background
		               layout="total, prev, pager, next" @current-change="load"/>

		<el-drawer v-model="visible" :title="form.id ? '编辑专题' : '新建专题'" size="620px">
			<el-form :model="form" label-width="90px" size="default">
				<el-form-item label="专题名称" required>
					<el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：从零搭建个人博客"/>
				</el-form-item>
				<el-form-item label="slug">
					<el-input v-model="form.slug" maxlength="120" placeholder="留空自动生成，建议填英文，如 build-blog"/>
					<div class="hint">专题链接为 <code>/topic/{slug}</code>，发布后改动会让旧链接失效</div>
				</el-form-item>
				<el-form-item label="简介">
					<el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit
					          placeholder="一句话说明这个专题讲什么"/>
				</el-form-item>
				<el-form-item label="封面">
					<el-input v-model="form.cover" placeholder="图片地址，可留空"/>
				</el-form-item>
				<el-form-item label="排序">
					<el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right"/>
					<span class="hint inline">越大越靠前</span>
				</el-form-item>
				<el-form-item label="发布">
					<el-switch v-model="form.published"/>
					<span class="hint inline">未发布的专题前台访问返回「不存在」</span>
				</el-form-item>

				<el-form-item label="关联文章">
					<el-select v-model="form.articleIds" multiple filterable remote reserve-keyword
					           :remote-method="searchBlogs" :loading="blogLoading" style="width: 100%"
					           placeholder="搜索并选择文章">
						<el-option v-for="item in blogOptions" :key="item.id" :value="item.id"
						           :label="item.title"/>
					</el-select>
					<div class="hint">选择顺序即阅读顺序；未发布的文章不会出现在前台专题页</div>
				</el-form-item>

				<el-form-item v-if="form.articleIds.length" label="阅读顺序">
					<div class="order-list">
						<div v-for="(id, idx) in form.articleIds" :key="id" class="order-item">
							<span class="idx">{{ idx + 1 }}</span>
							<span class="order-title">{{ blogTitle(id) }}</span>
							<span class="order-actions">
								<el-button link :disabled="idx === 0" @click="move(idx, -1)">上移</el-button>
								<el-button link :disabled="idx === form.articleIds.length - 1"
								           @click="move(idx, 1)">下移</el-button>
								<el-button link type="danger" @click="form.articleIds.splice(idx, 1)">移除</el-button>
							</span>
						</div>
					</div>
				</el-form-item>
			</el-form>

			<template #footer>
				<el-button @click="visible = false">取消</el-button>
				<el-button type="primary" :loading="saving" @click="submit">保存</el-button>
			</template>
		</el-drawer>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Plus, Search} from '@element-plus/icons-vue'
import {createTopic, deleteTopic, getTopic, getTopicList, updateTopic, updateTopicStatus} from '@/api/topic'
import {getBlogList} from '@/api/blog'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const blogLoading = ref(false)

const list = ref([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keyword = ref('')
const published = ref(null)

const blogOptions = ref([])
const form = reactive({
	id: null,
	title: '',
	slug: '',
	description: '',
	cover: '',
	sort: 0,
	published: true,
	articleIds: []
})

onMounted(() => {
	load()
	searchBlogs('')
})

async function load() {
	loading.value = true
	try {
		const res = await getTopicList({
			keyword: keyword.value || undefined,
			published: published.value === null ? undefined : published.value,
			pageNum: pageNum.value,
			pageSize: pageSize.value
		})
		list.value = res.data.list || []
		total.value = res.data.total || 0
	} finally {
		loading.value = false
	}
}

function search() {
	pageNum.value = 1
	load()
}

/**
 * 远程搜索文章：后台列表接口把 PageInfo 包在 data.blogs 里
 */
async function searchBlogs(query) {
	blogLoading.value = true
	try {
		const res = await getBlogList({title: query || '', pageNum: 1, pageSize: 20})
		const blogs = (res.data && res.data.blogs && res.data.blogs.list) || []
		mergeOptions(blogs.map(item => ({id: item.id, title: item.title})))
	} finally {
		blogLoading.value = false
	}
}

function mergeOptions(items) {
	const map = new Map(blogOptions.value.map(item => [item.id, item]))
	items.forEach(item => map.set(item.id, item))
	blogOptions.value = Array.from(map.values())
}

function blogTitle(id) {
	const found = blogOptions.value.find(item => item.id === id)
	return found ? found.title : `#${id}`
}

async function openDrawer(row) {
	Object.assign(form, {
		id: null, title: '', slug: '', description: '', cover: '',
		sort: 0, published: true, articleIds: []
	})
	if (row) {
		const res = await getTopic(row.id)
		const topic = res.data.topic || {}
		Object.assign(form, {
			id: topic.id,
			title: topic.title || '',
			slug: topic.slug || '',
			description: topic.description || '',
			cover: topic.cover || '',
			sort: topic.sort || 0,
			published: !!topic.isPublished,
			articleIds: res.data.articleIds || []
		})
		// 已绑定的文章要能显示标题，否则下拉里只有 id
		mergeOptions((res.data.articles || []).map(item => ({id: item.id, title: item.title})))
	}
	visible.value = true
}

function move(index, delta) {
	const target = index + delta
	if (target < 0 || target >= form.articleIds.length) {
		return
	}
	const ids = form.articleIds
	const [item] = ids.splice(index, 1)
	ids.splice(target, 0, item)
}

async function submit() {
	if (!form.title.trim()) {
		ElMessage.warning('请填写专题名称')
		return
	}
	saving.value = true
	try {
		const payload = {
			title: form.title.trim(),
			slug: form.slug.trim(),
			description: form.description,
			cover: form.cover,
			sort: form.sort || 0,
			published: form.published,
			articleIds: form.articleIds
		}
		if (form.id) {
			await updateTopic(form.id, payload)
		} else {
			await createTopic(payload)
		}
		ElMessage.success('保存成功')
		visible.value = false
		load()
	} catch (e) {
		ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
	} finally {
		saving.value = false
	}
}

async function toggleStatus(row, value) {
	try {
		await updateTopicStatus(row.id, value)
		row.isPublished = value
		ElMessage.success(value ? '已发布' : '已下线')
	} catch (e) {
		ElMessage.error(e?.response?.data?.msg || '操作失败')
		load()
	}
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(`删除专题「${row.title}」？文章本身不会被删除，只是不再出现在这个专题里。`,
			'删除专题', {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteTopic(row.id)
	ElMessage.success('已删除')
	load()
}

function formatTime(value) {
	return value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '—'
}
</script>

<style scoped>
.filters {
	margin-bottom: 4px;
}

.cell-title {
	font-weight: 600;
}

.muted {
	color: #909399;
}

.small {
	font-size: 12px;
}

.slug {
	font-size: 12px;
	color: #0f766e;
	background: #f2f6f5;
	padding: 1px 6px;
	border-radius: 4px;
}

.hint {
	color: #909399;
	font-size: 12px;
	line-height: 1.6;
	margin-top: 2px;
}

.hint.inline {
	margin-top: 0;
	margin-left: 8px;
}

.order-list {
	width: 100%;
	border: 1px solid #ebeef5;
	border-radius: 6px;
	padding: 4px 8px;
	max-height: 260px;
	overflow-y: auto;
}

.order-item {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 4px 0;
	border-bottom: 1px dashed #ebeef5;
}

.order-item:last-child {
	border-bottom: none;
}

.idx {
	width: 20px;
	height: 20px;
	line-height: 20px;
	text-align: center;
	border-radius: 50%;
	background: #0f766e;
	color: #fff;
	font-size: 12px;
	flex: none;
}

.order-title {
	flex: 1;
	font-size: 13px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.order-actions {
	flex: none;
}
</style>
