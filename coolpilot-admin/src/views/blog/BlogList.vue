<template>
	<div>
		<el-form inline>
			<el-form-item :label="t('标题')">
				<el-input v-model="query.title" :placeholder="t('搜标题')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item :label="t('分类')">
				<el-select v-model="query.categoryId" :placeholder="t('全部')" clearable style="width: 140px" @change="search">
					<el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
				<el-button type="success" :icon="EditPen" @click="$router.push('/blog/write')">{{ t('写文章') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="title" :label="t('标题')" show-overflow-tooltip min-width="220"/>
			<el-table-column :label="t('分类')" width="110">
				<template #default="{row}">{{ row.category ? row.category.name : '-' }}</template>
			</el-table-column>
			<el-table-column :label="t('标签')" width="180">
				<template #default="{row}">
					<el-tag v-for="tag in row.tags" :key="tag.id" size="small" class="tag">{{ tag.name }}</el-tag>
					<span v-if="!row.tags || !row.tags.length" class="muted">-</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('发布')" width="80">
				<template #default="{row}">
					<el-tag size="small" :type="row.published ? 'success' : 'info'">
						{{ row.published ? t('公开') : t('私密') }}
					</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('置顶')" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.top" size="small" @change="value => changeTop(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column :label="t('推荐')" width="80">
				<template #default="{row}">
					<el-switch :model-value="row.recommend" size="small" @change="value => changeRecommend(row, value)"/>
				</template>
			</el-table-column>
			<el-table-column prop="views" :label="t('阅读')" width="80"/>
			<el-table-column :label="t('更新时间')" width="160">
				<template #default="{row}">{{ formatDate(row.updateTime) }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="140" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" @click="$router.push(`/blog/edit/${row.id}`)">{{ t('编辑') }}</el-button>
					<el-button link type="danger" @click="remove(row)">{{ t('删除') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30, 50]" :total="total" background
		               layout="total, sizes, prev, pager, next, jumper"
		               @size-change="load" @current-change="load"/>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {EditPen, Search} from '@element-plus/icons-vue'
import {deleteBlog, getBlogList, getCategoryAndTag, updateRecommend, updateTop} from '@/api/blog'
import {formatDate} from '@/util/date'

const loading = ref(false)
const query = reactive({title: '', categoryId: null, pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const categories = ref([])

async function load() {
	loading.value = true
	try {
		const res = await getBlogList(query)
		// 老接口直接返回 PageHelper 的 PageInfo，数据在 data.blogs 里
		const page = res.data.blogs || {}
		list.value = page.list || []
		total.value = page.total || 0
	} finally {
		loading.value = false
	}
}

function search() {
	query.pageNum = 1
	load()
}

async function changeTop(row, value) {
	await updateTop(row.id, value)
	row.top = value
	ElMessage.success(value ? t('已置顶') : t('已取消置顶'))
}

async function changeRecommend(row, value) {
	await updateRecommend(row.id, value)
	row.recommend = value
	ElMessage.success(value ? t('已推荐') : t('已取消推荐'))
}

async function remove(row) {
	try {
		await ElMessageBox.confirm(`删除《${row.title}》？评论会一并删除。`, t('删除文章'), {type: 'warning'})
	} catch (e) {
		return
	}
	await deleteBlog(row.id)
	ElMessage.success(t('已删除'))
	load()
}

onMounted(async () => {
	const res = await getCategoryAndTag()
	categories.value = res.data.categories || []
	load()
})
</script>

<style scoped>
.tag {
	margin-right: 4px;
	margin-bottom: 2px;
}
</style>
