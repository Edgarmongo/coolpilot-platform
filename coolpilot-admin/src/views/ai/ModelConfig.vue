<template>
	<div v-loading="loading">
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('模型管理') }}</h2>
				<p class="page-desc">{{ t('对话、向量与重排模型的参数，保存后立即生效') }}</p>
			</div>
			<div>
				<el-button :icon="Refresh" @click="load(true)">{{ t('重新读取') }}</el-button>
				<el-button type="primary" :loading="saving" @click="save">{{ t('保存') }}</el-button>
			</div>
		</div>

		<el-alert v-if="!ready" class="mb" type="warning" :closable="false" show-icon
		          :title="t('还没有配置对话模型，问答会退化为只返回检索片段')"/>

		<el-tabs v-model="tab">
			<el-tab-pane :label="t('对话模型')" name="llm">
				<div class="pane">
					<div v-for="field in llmFields" :key="field.key" class="row">
						<label>
							{{ t(field.label) }}
							<el-tag v-if="isOverridden(field.key)" size="small" type="success">{{ t('已修改') }}</el-tag>
						</label>
						<el-input v-if="field.type === 'textarea'" v-model="values[field.key]" type="textarea"
						          :rows="2" :placeholder="field.placeholder"/>
						<el-input v-else v-model="values[field.key]" :placeholder="field.placeholder"
						          :show-password="field.secret"/>
						<div v-if="field.hint" class="hint">{{ t(field.hint) }}</div>
					</div>

					<div class="actions">
						<el-button :loading="testing" @click="test">{{ t('测试连接') }}</el-button>
						<span class="muted">{{ t('用已保存的配置发一句话，确认通不通') }}</span>
					</div>
					<el-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :closable="false"
					          show-icon :title="testText"/>
				</div>
			</el-tab-pane>

			<el-tab-pane :label="t('向量模型')" name="embedding">
				<div class="pane">
					<el-alert class="mb" type="info" :closable="false" show-icon
					          :title="t('换方案（离线字面 / 远程向量）需要重启服务；只改地址、模型、维度保存即生效')"/>
					<div v-for="field in embeddingFields" :key="field.key" class="row">
						<label>
							{{ t(field.label) }}
							<el-tag v-if="isOverridden(field.key)" size="small" type="success">{{ t('已修改') }}</el-tag>
						</label>
						<el-select v-if="field.type === 'select'" v-model="values[field.key]" class="w-full">
							<el-option v-for="option in field.options" :key="option.value"
							           :label="t(option.label)" :value="option.value"/>
						</el-select>
						<el-input v-else v-model="values[field.key]" :placeholder="field.placeholder"
						          :show-password="field.secret"/>
						<div v-if="field.hint" class="hint">{{ t(field.hint) }}</div>
					</div>
				</div>
			</el-tab-pane>

			<el-tab-pane :label="t('重排模型')" name="rerank">
				<div class="pane">
					<el-alert class="mb" type="info" :closable="false" show-icon
					          :title="t('换方案需要重启服务。重排是可选的：不配也能用，只是候选排序靠打分')"/>
					<div v-for="field in rerankFields" :key="field.key" class="row">
						<label>
							{{ t(field.label) }}
							<el-tag v-if="isOverridden(field.key)" size="small" type="success">{{ t('已修改') }}</el-tag>
						</label>
						<el-select v-if="field.type === 'select'" v-model="values[field.key]" class="w-full">
							<el-option v-for="option in field.options" :key="option.value"
							           :label="t(option.label)" :value="option.value"/>
						</el-select>
						<el-input v-else v-model="values[field.key]" :placeholder="field.placeholder"
						          :show-password="field.secret"/>
						<div v-if="field.hint" class="hint">{{ t(field.hint) }}</div>
					</div>
				</div>
			</el-tab-pane>
		</el-tabs>

		<p class="muted foot">
			{{ t('清空某一项再保存，会回到配置文件（.env）里的默认值；密钥留空表示不修改。') }}
		</p>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Refresh} from '@element-plus/icons-vue'
import {getAiConfig, reloadAiConfig, saveAiConfig, testLlm} from '@/api/ai'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const tab = ref('llm')
const ready = ref(false)
const testResult = ref(null)
const overridden = ref([])

// 键名与后端 AiRuntimeConfig 保持一致；values 用扁平结构，避免两边各写一套嵌套转换
const values = reactive({})

const llmFields = [
	{
		key: 'llm.baseUrl',
		label: '接口地址',
		placeholder: 'https://api.deepseek.com/v1',
		hint: '要带版本段；本地 Ollama 用 http://host.docker.internal:11434/v1'
	},
	{
		key: 'llm.apiKey',
		label: 'API Key',
		secret: true,
		hint: '本地模型通常留空；保存后只显示掩码，留空表示不修改'
	},
	{
		key: 'llm.model',
		label: '模型名',
		placeholder: 'deepseek-flash',
		hint: '以服务端 /models 返回为准，写成带后缀的名字会被拒'
	},
	{key: 'llm.timeoutSeconds', label: '超时（秒）', placeholder: '60'},
	{key: 'llm.maxTokens', label: '最大输出 tokens', placeholder: '800'},
	{key: 'llm.temperature', label: '温度', placeholder: '0.3'},
	{
		key: 'llm.extraBody',
		label: '厂商专有参数（JSON）',
		type: 'textarea',
		placeholder: '{"thinking":{"type":"disabled"}}',
		hint: 'DeepSeek V4 默认开思考模式，只要正文就填上面这串'
	}
]

const embeddingFields = [
	{
		key: 'embedding.provider',
		label: '方案',
		type: 'select',
		options: [
			{value: 'hashing', label: '离线字面召回（无需服务）'},
			{value: 'openai', label: '远程向量模型（bge-m3 等）'}
		]
	},
	{key: 'embedding.baseUrl', label: '接口地址', placeholder: 'http://host.docker.internal:11434/v1'},
	{key: 'embedding.apiKey', label: 'API Key', secret: true, hint: '本地模型通常留空'},
	{key: 'embedding.model', label: '模型名', placeholder: 'bge-m3'},
	{key: 'embedding.dim', label: '向量维度', placeholder: '1024', hint: '必须与模型一致：bge-m3=1024'},
	{key: 'embedding.batchSize', label: '每批条数', placeholder: '16', hint: '重建索引时每批发多少条'}
]

const rerankFields = [
	{
		key: 'rerank.provider',
		label: '方案',
		type: 'select',
		options: [
			{value: 'none', label: '不重排'},
			{value: 'llm', label: '用对话模型排序'},
			{value: 'http', label: '远程 bge-reranker 服务'}
		]
	},
	{key: 'rerank.baseUrl', label: '接口地址', placeholder: 'http://host.docker.internal:8080'},
	{key: 'rerank.apiKey', label: 'API Key', secret: true},
	{key: 'rerank.model', label: '模型名', placeholder: 'bge-reranker-v2-m3'},
	{key: 'rerank.candidates', label: '候选条数', placeholder: '20'},
	{
		key: 'rerank.strictness',
		label: '严格度',
		type: 'select',
		options: [
			{value: 'strict', label: '严格（只留能直接回答的）'},
			{value: 'balanced', label: '均衡'},
			{value: 'loose', label: '宽松（宁多勿漏）'}
		]
	}
]

const testText = computed(() => {
	const result = testResult.value
	if (!result) {
		return ''
	}
	return result.ok
		? t('连通正常，{ms}ms，返回：{reply}', {ms: result.tookMs, reply: result.reply})
		: result.message
})

function isOverridden(key) {
	return overridden.value.includes(key)
}

async function load(force = false) {
	loading.value = true
	try {
		// 「重新读取」先让后端重载一遍：直接改库时进程里还是旧快照，
		// 只发 GET 只会把旧值再读一次，看不出变化
		const res = force ? await reloadAiConfig() : await getAiConfig()
		const data = res.data || {}
		Object.keys(values).forEach(key => delete values[key])
		Object.assign(values, data.values || {})
		overridden.value = data.overridden || []
		ready.value = !!data.llmReady
		testResult.value = null
	} finally {
		loading.value = false
	}
}

async function save() {
	saving.value = true
	try {
		// 整组提交：后端只处理出现的键，空值表示「回落默认」
		const res = await saveAiConfig({...values})
		overridden.value = (res.data && res.data.overridden) || []
		ready.value = !!(res.data && res.data.llmReady)
		ElMessage.success(t('已保存，立即生效'))
	} finally {
		saving.value = false
	}
}

async function test() {
	testing.value = true
	testResult.value = null
	try {
		const res = await testLlm()
		testResult.value = res.data || {ok: false, message: t('测试失败')}
	} finally {
		testing.value = false
	}
}

onMounted(() => load())
</script>

<style scoped>
.pane {
	padding: 4px 2px 8px;
}

.row {
	margin-bottom: 14px;
	max-width: 720px;
}

.row label {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-bottom: 6px;
	font-size: 13px;
	color: #606266;
}

.hint {
	margin-top: 4px;
	font-size: 12px;
	color: #909399;
	line-height: 1.6;
}

.actions {
	display: flex;
	align-items: center;
	gap: 10px;
	margin: 4px 0 12px;
}

.w-full {
	width: 100%;
}

.mb {
	margin-bottom: 14px;
}

.foot {
	margin-top: 16px;
	font-size: 12.5px;
}
</style>
