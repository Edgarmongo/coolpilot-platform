<template>
	<div>
		<el-form inline>
			<el-form-item label="状态">
				<el-select v-model="query.status" placeholder="全部" clearable style="width: 140px" @change="search">
					<el-option label="待处理" value="PENDING"/>
					<el-option label="已回复" value="RESOLVED"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">刷新</el-button>
			</el-form-item>
			<el-form-item>
				<span class="muted">用户在「我的 → 意见反馈」提交，这里处理。回复会显示在对方页面。</span>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column label="反馈内容" min-width="300">
				<template #default="{row}">
					<div class="content">{{ row.content }}</div>
					<div v-if="imagesOf(row).length" class="imgs">
						<el-image v-for="(src, i) in imagesOf(row)" :key="i" :src="src" :preview-src-list="imagesOf(row)"
						          :initial-index="i" fit="cover" class="img"/>
					</div>
				</template>
			</el-table-column>
			<el-table-column label="联系方式" width="170">
				<template #default="{row}">
					<span v-if="row.contact">{{ row.contact }}</span>
					<span v-else class="muted">未留</span>
				</template>
			</el-table-column>
			<el-table-column label="用户" width="90">
				<template #default="{row}">
					<span v-if="row.userId" class="mono">#{{ row.userId }}</span>
					<span v-else class="muted">匿名</span>
				</template>
			</el-table-column>
			<el-table-column label="状态" width="96">
				<template #default="{row}">
					<el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">
						{{ row.status === 'RESOLVED' ? '已回复' : '待处理' }}
					</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="提交时间" width="150">
				<template #default="{row}">{{ fmt(row.createTime) }}</template>
			</el-table-column>
			<el-table-column label="操作" width="96" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" size="small" @click="openReply(row)">
						{{ row.status === 'RESOLVED' ? '查看/改' : '回复' }}
					</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination
			class="pager"
			background
			layout="total, prev, pager, next"
			:total="total"
			:page-size="query.pageSize"
			:current-page="query.pageNum"
			@current-change="onPage"/>

		<el-dialog v-model="dialog" title="处理反馈" width="620px">
			<div v-if="current" class="detail">
				<el-descriptions :column="1" border size="small">
					<el-descriptions-item label="反馈内容">
						<div class="pre">{{ current.content }}</div>
					</el-descriptions-item>
					<el-descriptions-item label="联系方式">{{ current.contact || '未留' }}</el-descriptions-item>
					<el-descriptions-item label="提交时间">{{ fmt(current.createTime) }}</el-descriptions-item>
					<el-descriptions-item v-if="current.reply" label="上次回复">
						<div class="pre">{{ current.reply }}</div>
						<div class="muted">{{ fmt(current.replyTime) }}</div>
					</el-descriptions-item>
				</el-descriptions>

				<div v-if="imagesOf(current).length" class="imgs dialog-imgs">
					<el-image v-for="(src, i) in imagesOf(current)" :key="i" :src="src"
					          :preview-src-list="imagesOf(current)" :initial-index="i" fit="cover" class="img"/>
				</div>

				<el-form label-width="72px" size="small" class="reply-form">
					<el-form-item label="回复">
						<el-input v-model="replyText" type="textarea" :rows="5" maxlength="500" show-word-limit
						          placeholder="回复内容会展示给提交者"/>
					</el-form-item>
					<el-form-item label="状态">
						<el-radio-group v-model="replyStatus">
							<el-radio value="RESOLVED">标记为已解决</el-radio>
							<el-radio value="PENDING">仅留言，保持待处理</el-radio>
						</el-radio-group>
					</el-form-item>
				</el-form>
			</div>
			<template #footer>
				<el-button @click="dialog = false">取消</el-button>
				<el-button type="primary" :loading="saving" @click="submitReply">提交</el-button>
			</template>
		</el-dialog>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Search} from '@element-plus/icons-vue'
import {getFeedbackList, replyFeedback} from '@/api/feedback'

const query = reactive({status: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)
const current = ref(null)
const replyText = ref('')
const replyStatus = ref('RESOLVED')

/**
 * images 存的是逗号分隔的地址串（提交时由前端拼接），这里容忍空值与空格
 */
function imagesOf(row) {
	if (!row || !row.images) return []
	return String(row.images).split(',').map(s => s.trim()).filter(Boolean)
}

function fmt(v) {
	if (!v) return '—'
	const d = new Date(v)
	const p = n => String(n).padStart(2, '0')
	return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

async function load() {
	loading.value = true
	try {
		const res = await getFeedbackList({
			status: query.status || undefined,
			pageNum: query.pageNum,
			pageSize: query.pageSize
		})
		list.value = res.data.list || []
		total.value = res.data.total || 0
	} finally {
		loading.value = false
	}
}

function search() {
	query.pageNum = 1
	load()
}

function onPage(p) {
	query.pageNum = p
	load()
}

function openReply(row) {
	current.value = row
	replyText.value = row.reply || ''
	replyStatus.value = 'RESOLVED'
	dialog.value = true
}

async function submitReply() {
	if (!replyText.value.trim()) {
		ElMessage.warning('请填写回复内容')
		return
	}
	saving.value = true
	try {
		await replyFeedback(current.value.id, replyText.value.trim(), replyStatus.value)
		ElMessage.success('已提交')
		dialog.value = false
		load()
	} finally {
		saving.value = false
	}
}

onMounted(load)
</script>

<style scoped>
.content {
	white-space: pre-wrap;
	line-height: 1.6;
	display: -webkit-box;
	-webkit-line-clamp: 3;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.pre {
	white-space: pre-wrap;
	line-height: 1.6;
}

.muted {
	color: #909399;
	font-size: 12px;
}

.mono {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
	color: #606266;
}

.imgs {
	display: flex;
	gap: 6px;
	margin-top: 6px;
	flex-wrap: wrap;
}

.img {
	width: 56px;
	height: 56px;
	border-radius: 4px;
	background: #f2f4f7;
}

.dialog-imgs {
	margin: 12px 0;
}

.img :deep(img) {
	object-fit: cover;
}

.pager {
	margin-top: 14px;
	justify-content: flex-end;
}

.reply-form {
	margin-top: 14px;
}
</style>
