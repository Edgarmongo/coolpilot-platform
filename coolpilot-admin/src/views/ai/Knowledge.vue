<template>
	<div>
		<el-alert type="info" :closable="false" class="tip">
			<template #title>
				索引覆盖：已发布文章（跳过密码文章）、上架商品文案、课时正文。
				课时属于付费内容，检索时按用户权益过滤；公开内容未登录也能搜到。
			</template>
		</el-alert>

		<el-row :gutter="12" class="stats">
			<el-col :span="6">
				<div class="stat-card">
					<div class="num">{{ stats.documents || 0 }}</div>
					<div class="label">文档</div>
				</div>
			</el-col>
			<el-col :span="6">
				<div class="stat-card">
					<div class="num">{{ stats.chunks || 0 }}</div>
					<div class="label">分块</div>
				</div>
			</el-col>
			<el-col :span="6">
				<div class="stat-card">
					<div class="num">{{ stats.loadedEntries || 0 }}</div>
					<div class="label">已装载向量</div>
				</div>
			</el-col>
			<el-col :span="6">
				<div class="stat-card">
					<div class="num small-num">{{ stats.model || '—' }}</div>
					<div class="label">{{ stats.dim || 0 }} 维</div>
				</div>
			</el-col>
		</el-row>

		<el-form inline class="actions">
			<el-form-item>
				<el-button type="primary" :icon="Refresh" :loading="rebuilding" @click="rebuild">
					重建索引
				</el-button>
			</el-form-item>
			<el-form-item>
				<el-button :icon="RefreshRight" :loading="loading" @click="load">刷新统计</el-button>
			</el-form-item>
			<el-form-item v-if="stats.lastIndexedAt">
				<span class="muted">上次索引：{{ formatTime(stats.lastIndexedAt) }}</span>
			</el-form-item>
		</el-form>

		<div class="panel">
			<div class="llm-head">
				<h3>问答模型</h3>
				<el-tag v-if="llm.configured" type="success" size="small">已配置</el-tag>
				<el-tag v-else type="warning" size="small">未配置</el-tag>
			</div>
			<div class="llm-row">
				<span class="muted">模型</span>
				<code>{{ llm.model || '—' }}</code>
				<span class="muted">引用上限</span>
				<code>{{ llm.topK || 6 }}</code>
				<span class="muted">最低相似度</span>
				<code>{{ llm.minScore ?? '—' }}</code>
			</div>
			<p class="muted small" v-if="!llm.configured">
				未配置时问答只返回检索片段、不调用模型。在 .env 里设置 AI_LLM_BASE_URL 与
				AI_LLM_MODEL（本地 Ollama 与云端 DeepSeek、通义都支持）后重启生效。
			</p>
			<div class="llm-actions">
				<el-button :icon="Connection" :loading="testing" @click="testModel">测试连通</el-button>
				<span v-if="testResult" :class="['test-result', testResult.ok ? 'ok' : 'bad']">
					{{ testResult.ok ? `连通正常，${testResult.tookMs}ms，返回：${testResult.reply}` : testResult.message }}
				</span>
			</div>
		</div>


		<div class="panel">
			<div class="llm-head">
				<h3>检索质量</h3>
				<el-tag :type="suggestionType" size="small">{{ suggestionText }}</el-tag>
				<el-tag type="info" size="small">当前严格度：{{ quality.strictness || '—' }}</el-tag>
			</div>
			<div class="llm-row">
				<span class="muted">近 {{ quality.days || 7 }} 天提问</span>
				<code>{{ quality.questions || 0 }}</code>
				<span class="muted">0 引用</span>
				<code>{{ percent(quality.zeroRate) }}</code>
				<span class="muted">平均引用</span>
				<code>{{ quality.avgCitations ?? 0 }}</code>
				<span class="muted">改写率</span>
				<code>{{ percent(quality.rewriteRate) }}</code>
			</div>
			<p class="muted small" v-if="quality.distribution">
				引用分布：0 条 {{ quality.distribution['0'] }} · 1 条 {{ quality.distribution['1'] }} ·
				2 条 {{ quality.distribution['2'] }} · 3 条以上 {{ quality.distribution['3+'] }}
			</p>
			<p class="small" :class="['advice', suggestionType]">{{ quality.reason }}</p>
			<p class="muted small">
				严格度由 .env 的 AI_RERANK_STRICTNESS 决定（strict / balanced / loose），改完重启生效。
				系统只给建议，不自动切换 —— 「宁可答不出来还是宁可答偏」是运营判断。
			</p>
		</div>

		<div class="panel">
			<h3>检索自测</h3>
			<div class="test-row">
				<el-input v-model="query" placeholder="输入关键词，检查召回效果" clearable @keyup.enter="search"/>
				<el-button type="primary" :loading="searching" @click="search">检索</el-button>
			</div>
			<div v-if="searchMeta" class="muted small">
				{{ searchMeta.candidates }} 个候选 · {{ searchMeta.tookMs }}ms
			</div>
			<div v-for="item in results" :key="item.docId" class="hit">
				<div class="hit-head">
					<span class="hit-title">{{ item.title }}</span>
					<el-tag size="small">{{ sourceText(item.sourceType) }}</el-tag>
					<span class="muted small">{{ Math.round(item.score * 100) }}%</span>
				</div>
				<p class="hit-snippet">{{ item.snippet }}</p>
			</div>
			<div v-if="searched && !results.length" class="muted small">没有匹配片段</div>
		</div>
	</div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Connection, Refresh, RefreshRight} from '@element-plus/icons-vue'
import {getAiStats, getLlmStatus, getRetrievalQuality, reindexKnowledge, searchAi, testLlm} from '@/api/ai'

const stats = ref({})
const llm = ref({})
const quality = ref({})
const testResult = ref(null)
const testing = ref(false)
const loading = ref(false)
const rebuilding = ref(false)

const query = ref('')
const results = ref([])
const searchMeta = ref(null)
const searching = ref(false)
const searched = ref(false)

const SUGGESTIONS = {
	keep: {text: '保持当前档位', type: 'success'},
	strict: {text: '建议收紧到 strict', type: 'warning'},
	loose: {text: '建议放宽到 loose', type: 'warning'},
	insufficient: {text: '样本不足', type: 'info'}
}

const suggestionType = computed(() => (SUGGESTIONS[quality.value.suggestion] || SUGGESTIONS.keep).type)
const suggestionText = computed(() => (SUGGESTIONS[quality.value.suggestion] || SUGGESTIONS.keep).text)

function percent(value) {
	return `${Math.round((Number(value) || 0) * 100)}%`
}

onMounted(load)

async function load() {
	loading.value = true
	try {
		const [res, llmRes, qualityRes] = await Promise.all([
			getAiStats(),
			getLlmStatus().catch(() => null),
			getRetrievalQuality(7).catch(() => null)
		])
		stats.value = res.data || {}
		llm.value = (llmRes && llmRes.data) || {}
		quality.value = (qualityRes && qualityRes.data) || {}
	} finally {
		loading.value = false
	}
}

async function rebuild() {
	try {
		await ElMessageBox.confirm('会清空并重新生成全部索引，期间检索结果可能短暂为空。继续？', '重建索引', {
			type: 'warning'
		})
	} catch {
		return
	}
	rebuilding.value = true
	try {
		const res = await reindexKnowledge()
		ElMessage.success(`索引完成：${res.data.documents} 篇 / ${res.data.chunks} 块`)
		await load()
	} finally {
		rebuilding.value = false
	}
}

async function testModel() {
	testing.value = true
	testResult.value = null
	try {
		const res = await testLlm()
		testResult.value = res.data || {ok: false, message: '无返回'}
	} finally {
		testing.value = false
	}
}

async function search() {
	if (!query.value.trim()) {
		return
	}
	searching.value = true
	try {
		const res = await searchAi({q: query.value.trim(), limit: 8})
		results.value = (res && res.data && res.data.results) || []
		searchMeta.value = (res && res.data) || null
		searched.value = true
	} finally {
		searching.value = false
	}
}

function sourceText(type) {
	return {BLOG: '文章', PRODUCT: '商品', LESSON: '课时'}[type] || type
}

function formatTime(value) {
	return value ? new Date(value).toLocaleString('zh-CN', {hour12: false}) : '—'
}
</script>

<style scoped>
.tip {
	margin-bottom: 12px;
}

.stats {
	margin-bottom: 4px;
}

.stat-card {
	border: 1px solid #ebeef5;
	border-radius: 8px;
	padding: 14px;
	text-align: center;
}

.num {
	font-size: 24px;
	font-weight: 600;
	color: #0f766e;
}

.small-num {
	font-size: 14px;
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.label {
	color: #909399;
	font-size: 12px;
	margin-top: 4px;
}

.actions {
	margin-top: 12px;
}

.muted {
	color: #909399;
}

.small {
	font-size: 12px;
}

.panel {
	border: 1px solid #ebeef5;
	border-radius: 8px;
	padding: 14px;
}

.panel h3 {
	margin: 0 0 10px;
	font-size: 14px;
}

.test-row {
	display: flex;
	gap: 8px;
	margin-bottom: 10px;
}

.hit {
	border-top: 1px dashed #ebeef5;
	padding: 10px 0;
}

.hit-head {
	display: flex;
	align-items: center;
	gap: 8px;
}

.hit-title {
	font-weight: 600;
	font-size: 13px;
}

.hit-snippet {
	margin: 6px 0 0;
	font-size: 12px;
	color: #606266;
	line-height: 1.7;
}

.llm-head {
	display: flex;
	align-items: center;
	gap: 8px;
}

.llm-row {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
	font-size: 12px;
	margin-top: 6px;
}

.llm-row code {
	background: #f4f7f6;
	border-radius: 4px;
	padding: 1px 6px;
	color: #0f766e;
}

.llm-actions {
	margin-top: 10px;
	display: flex;
	align-items: center;
	gap: 10px;
	flex-wrap: wrap;
}

.test-result {
	font-size: 12px;
}

.test-result.ok {
	color: #15803d;
}

.test-result.bad {
	color: #b42318;
}

.advice {
	margin: 6px 0 0;
	line-height: 1.7;
}

.advice.success {
	color: #047857;
}

.advice.warning {
	color: #b45309;
}

.advice.info {
	color: #6b7280;
}
</style>
