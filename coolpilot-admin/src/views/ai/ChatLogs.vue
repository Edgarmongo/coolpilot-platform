<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">问答记录</h2>
				<p class="page-desc">用户问过什么、有没有检索到资料。引用为 0 的回答最值得看 —— 通常意味着缺内容或说法对不上</p>
			</div>
		</div>

		<el-form inline>
			<el-form-item label="标题关键词">
				<el-input v-model="query.keyword" placeholder="会话标题" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">搜索</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column prop="userName" label="用户" width="140" show-overflow-tooltip/>
			<el-table-column prop="title" label="会话" min-width="260" show-overflow-tooltip/>
			<el-table-column label="消息数" width="90">
				<template #default="{row}">{{ row.messageCount || 0 }}</template>
			</el-table-column>
			<el-table-column label="最近更新" width="150">
				<template #default="{row}">{{ formatDate(row.updateTime, 'YYYY-MM-DD HH:mm') }}</template>
			</el-table-column>
			<el-table-column label="操作" width="90" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDetail(row)">查看</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
		               :page-sizes="[10, 20, 30, 50]" :total="total" background
		               layout="total, sizes, prev, pager, next, jumper"
		               @size-change="load" @current-change="load"/>

		<el-drawer v-model="detailVisible" title="会话详情" size="620px">
			<div v-if="detail" class="detail">
				<div class="detail-head">
					<div class="detail-title">{{ detail.title }}</div>
					<div class="muted">
						用户 ID {{ detail.userId }} · {{ detail.messageCount }} 条消息 ·
						{{ formatDate(detail.updateTime) }}
					</div>
				</div>

				<div v-for="msg in detail.messages" :key="msg.id" :class="['msg', msg.role === 'USER' ? 'ask' : 'answer']">
					<div class="msg-meta">
						<span class="who">{{ msg.role === 'USER' ? '问' : '答' }}</span>
						<span class="muted">{{ formatDate(msg.createTime) }}</span>
						<span v-if="msg.role !== 'USER'" class="muted">· {{ msg.model || '未记录模型' }}</span>
						<el-tag v-if="msg.role !== 'USER'" size="small"
						        :type="msg.citationCount > 0 ? 'success' : 'warning'">
							引用 {{ msg.citationCount }}
						</el-tag>
					</div>
					<div class="msg-content">{{ msg.content }}</div>
					<!-- 追问被改写时，把实际检索句摊开：不显示的话，排查召回问题只能靠猜 -->
					<div v-if="msg.retrievalQuery" class="msg-extra">
						检索用：{{ msg.retrievalQuery }}
					</div>
				</div>

				<div v-if="!detail.messages.length" class="muted">这段会话没有消息</div>
			</div>
		</el-drawer>
	</div>
</template>

<script setup>
import {onMounted, reactive, ref} from 'vue'
import {Search} from '@element-plus/icons-vue'
import {getChatLog, getChatLogs} from '@/api/ai'
import {formatDate} from '@/util/date'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({keyword: '', pageNum: 1, pageSize: 10})

const detailVisible = ref(false)
const detail = ref(null)

async function load() {
	loading.value = true
	try {
		const res = await getChatLogs(query)
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

async function openDetail(row) {
	const res = await getChatLog(row.id)
	detail.value = res.data
	detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.detail-head {
	padding-bottom: 10px;
	border-bottom: 1px solid #ebeef5;
	margin-bottom: 14px;
}

.detail-title {
	font-size: 15px;
	font-weight: 600;
	color: #303133;
	margin-bottom: 4px;
}

.msg {
	margin-bottom: 14px;
}

.msg-meta {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 12px;
	margin-bottom: 4px;
}

.who {
	display: inline-block;
	min-width: 20px;
	height: 20px;
	line-height: 20px;
	text-align: center;
	border-radius: 4px;
	font-size: 12px;
	font-weight: 600;
	background: #eef7f5;
	color: #0f766e;
}

.msg.ask .who {
	background: #eff6ff;
	color: #1d4ed8;
}

.msg-content {
	font-size: 13px;
	line-height: 1.8;
	white-space: pre-wrap;
	word-break: break-word;
	color: #303133;
	background: #fafafa;
	border-radius: 6px;
	padding: 8px 10px;
}

.msg-extra {
	margin-top: 6px;
	padding: 4px 8px;
	border-left: 2px solid var(--el-color-warning);
	background: #fffbeb;
	color: #92400e;
	font-size: 12px;
	border-radius: 0 4px 4px 0;
	word-break: break-all;
}
</style>
