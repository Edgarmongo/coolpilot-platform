<template>
	<div v-loading="loading">
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('用量统计') }}</h2>
				<p class="page-desc">{{ t('模型调用次数与 token 消耗，含改写、重排、向量化这些看不见的开销') }}</p>
			</div>
			<div class="head-actions">
				<el-radio-group v-model="days" size="small" @change="load">
					<el-radio-button :value="7">{{ t('近 7 天') }}</el-radio-button>
					<el-radio-button :value="30">{{ t('近 30 天') }}</el-radio-button>
					<el-radio-button :value="90">{{ t('近 90 天') }}</el-radio-button>
				</el-radio-group>
				<el-button :icon="Refresh" size="small" @click="load">{{ t('刷新') }}</el-button>
			</div>
		</div>

		<div class="cards">
			<div class="card">
				<div class="label">{{ t('今日 tokens') }}</div>
				<div class="value">{{ num(today.totalTokens) }}</div>
				<div class="sub">{{ t('{n} 次调用', {n: num(today.calls)}) }}</div>
			</div>
			<div class="card">
				<div class="label">{{ t('今日调用') }}</div>
				<div class="value">{{ num(today.calls) }}</div>
				<div class="sub">{{ t('输入 {a} / 输出 {b}', {a: num(today.promptTokens), b: num(today.completionTokens)}) }}</div>
			</div>
			<div class="card">
				<div class="label">{{ t('区间 tokens') }}</div>
				<div class="value">{{ num(summary.totalTokens) }}</div>
				<div class="sub">{{ t('近 {n} 天累计', {n: days}) }}</div>
			</div>
			<div class="card">
				<div class="label">{{ t('区间调用') }}</div>
				<div class="value">{{ num(summary.calls) }}</div>
				<div class="sub">{{ t('平均 {ms}ms', {ms: num(summary.avgDurationMs)}) }}</div>
			</div>
		</div>

		<div class="meta-row">
			<el-tag :type="summary.successRate >= 0.95 ? 'success' : 'warning'" size="small">
				{{ t('成功率 {p}%', {p: (summary.successRate * 100).toFixed(1)}) }}
			</el-tag>
			<el-tag v-if="summary.estimatedCalls" type="info" size="small">
				{{ t('其中 {n} 次为估算值（接口未返回 usage）', {n: num(summary.estimatedCalls)}) }}
			</el-tag>
			<span v-if="!summary.calls" class="muted">
				{{ t('这段时间还没有调用记录，去前台问一句就会有了') }}
			</span>
		</div>

		<div class="grid">
			<div class="block">
				<h4>{{ t('按天') }}</h4>
				<el-table :data="byDay" size="small" max-height="360">
					<el-table-column prop="day" :label="t('日期')" width="120"/>
					<el-table-column :label="t('调用')" width="80">
						<template #default="{row}">{{ num(row.calls) }}</template>
					</el-table-column>
					<el-table-column :label="t('输入')" width="100">
						<template #default="{row}">{{ num(row.promptTokens) }}</template>
					</el-table-column>
					<el-table-column :label="t('输出')" width="100">
						<template #default="{row}">{{ num(row.completionTokens) }}</template>
					</el-table-column>
					<el-table-column :label="t('合计')">
						<template #default="{row}">
							<span class="strong">{{ num(row.totalTokens) }}</span>
						</template>
					</el-table-column>
				</el-table>
			</div>

			<div class="side">
				<div class="block">
					<h4>{{ t('按模型') }}</h4>
					<el-table :data="byModel" size="small" max-height="170">
						<el-table-column prop="model" :label="t('模型')" show-overflow-tooltip/>
						<el-table-column :label="t('调用')" width="70">
							<template #default="{row}">{{ num(row.calls) }}</template>
						</el-table-column>
						<el-table-column :label="t('tokens')" width="90">
							<template #default="{row}">{{ num(row.totalTokens) }}</template>
						</el-table-column>
					</el-table>
				</div>

				<div class="block">
					<h4>{{ t('按场景') }}</h4>
					<el-table :data="bySceneView" size="small" max-height="170">
						<el-table-column :label="t('场景')">
							<template #default="{row}">{{ sceneText(row.scene) }}</template>
						</el-table-column>
						<el-table-column :label="t('调用')" width="70">
							<template #default="{row}">{{ num(row.calls) }}</template>
						</el-table-column>
						<el-table-column :label="t('tokens')" width="90">
							<template #default="{row}">{{ num(row.totalTokens) }}</template>
						</el-table-column>
					</el-table>
				</div>
			</div>
		</div>

		<p class="muted foot">
			{{ t('统计只记计量信息（场景、模型、token、耗时），不保存提问与回答正文；正文在「问答记录」里。') }}
		</p>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, onMounted, ref} from 'vue'
import {Refresh} from '@element-plus/icons-vue'
import {getAiUsage} from '@/api/ai'

const loading = ref(false)
const days = ref(7)
const today = ref(emptySummary())
const summary = ref(emptySummary())
const byDay = ref([])
const byModel = ref([])
const byScene = ref([])

// 场景名跟前端 i18n 走同一套中文原文 key，英文站自动切换
const SCENE_LABEL = {
	CHAT: '问答',
	REWRITE: '追问改写',
	RERANK: '重排',
	EMBED: '向量化',
	TEST: '连通性测试'
}

const bySceneView = computed(() => byScene.value)

function emptySummary() {
	return {
		calls: 0,
		promptTokens: 0,
		completionTokens: 0,
		totalTokens: 0,
		successCalls: 0,
		estimatedCalls: 0,
		avgDurationMs: 0,
		successRate: 0
	}
}

function num(value) {
	const n = Number(value || 0)
	return n.toLocaleString('en-US')
}

function sceneText(scene) {
	const label = SCENE_LABEL[scene]
	return label ? t(label) : scene || '-'
}

async function load() {
	loading.value = true
	try {
		const res = await getAiUsage(days.value)
		const data = res.data || {}
		today.value = {...emptySummary(), ...(data.today || {})}
		summary.value = {...emptySummary(), ...(data.summary || {})}
		byDay.value = data.byDay || []
		byModel.value = data.byModel || []
		byScene.value = data.byScene || []
	} finally {
		loading.value = false
	}
}

onMounted(load)
</script>

<style scoped>
.head-actions {
	display: flex;
	align-items: center;
	gap: 10px;
}

.cards {
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	gap: 12px;
	margin-bottom: 12px;
}

.card {
	padding: 14px 16px;
	background: #fff;
	border-radius: 10px;
	box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.card .label {
	font-size: 12.5px;
	color: #909399;
}

.card .value {
	margin: 6px 0 4px;
	font-size: 24px;
	font-weight: 600;
	color: #303133;
}

.card .sub {
	font-size: 12px;
	color: #a8abb2;
}

.meta-row {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 14px;
}

.grid {
	display: grid;
	grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
	gap: 12px;
	align-items: start;
}

.block {
	padding: 12px 14px;
	background: #fff;
	border-radius: 10px;
	box-shadow: 0 1px 4px rgba(0, 0, 0, .06);
}

.block h4 {
	margin: 0 0 10px;
	font-size: 13.5px;
	font-weight: 600;
	color: #303133;
}

.side {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

.strong {
	font-weight: 600;
}

.foot {
	margin-top: 14px;
	font-size: 12.5px;
}

@media (max-width: 1100px) {
	.cards {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}

	.grid {
		grid-template-columns: minmax(0, 1fr);
	}
}
</style>
