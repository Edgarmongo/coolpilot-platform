<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('开源项目商业观察') }}</h2>
				<p class="page-desc">{{ t('第三方开源项目的商业分析档案：双状态机、评估卡、关联文章') }}</p>
			</div>
			<el-button type="primary" :icon="Plus" @click="openDrawer()">{{ t('新建项目档案') }}</el-button>
		</div>

		<el-form inline class="filters">
			<el-form-item>
				<el-input v-model="filters.keyword" :placeholder="t('项目名或 slug')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-select v-model="filters.publishStatus" :placeholder="t('发布状态')" clearable style="width: 130px">
					<el-option :label="t('草稿')" value="draft"/>
					<el-option :label="t('已发布')" value="published"/>
					<el-option :label="t('已归档')" value="archived"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-select v-model="filters.researchStatus" :placeholder="t('研究状态')" clearable style="width: 130px">
					<el-option :label="t('待研究')" value="todo"/>
					<el-option :label="t('研究中')" value="researching"/>
					<el-option :label="t('已完成')" value="done"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-select v-model="filters.category" :placeholder="t('技术领域')" clearable style="width: 160px">
					<el-option v-for="item in categories" :key="item.code" :label="categoryLabel(item.code)"
					           :value="item.code"/>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button :icon="Search" @click="search">{{ t('查询') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('项目')" min-width="180">
				<template #default="{row}">
					<div class="cell-title">{{ row.name }}</div>
					<code class="slug">/open-source/projects/{{ row.slug }}</code>
				</template>
			</el-table-column>
			<el-table-column :label="t('领域')" width="120">
				<template #default="{row}">{{ categoryLabel(row.category) }}</template>
			</el-table-column>
			<el-table-column :label="t('结论档')" width="110">
				<template #default="{row}">
					<span v-if="row.verdict">{{ verdictLabel(row.verdict) }}</span>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('分析篇数')" width="86" align="center">
				<template #default="{row}">{{ row.articleCount || 0 }}</template>
			</el-table-column>
			<el-table-column :label="t('发布状态')" width="100" align="center">
				<template #default="{row}">
					<el-tag :type="publishTagType(row.publishStatus)" size="small">
						{{ publishLabel(row.publishStatus) }}
					</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('研究状态')" width="100" align="center">
				<template #default="{row}">{{ researchLabel(row.researchStatus) }}</template>
			</el-table-column>
			<el-table-column :label="t('更新时间')" width="120">
				<template #default="{row}">{{ shortDate(row.updateTime) }}</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="190" align="right">
				<template #default="{row}">
					<el-button link type="primary" @click="openDrawer(row)">{{ t('编辑') }}</el-button>
					<el-button v-if="row.publishStatus !== 'published'" link type="success" @click="publish(row)">
						{{ t('发布') }}
					</el-button>
					<el-button v-else link type="warning" @click="archive(row)">{{ t('归档') }}</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total" background
		               layout="total, prev, pager, next" @current-change="load"/>

		<el-drawer v-model="visible" :title="form.id ? t('编辑项目档案') : t('新建项目档案')" size="880px">
			<el-form :model="form" label-width="96px">
				<el-divider content-position="left">{{ t('基本信息') }}</el-divider>

				<el-row :gutter="12">
					<el-col :span="12">
						<el-form-item :label="t('项目名')" required>
							<el-input v-model="form.name" maxlength="150"/>
						</el-form-item>
					</el-col>
					<el-col :span="12">
						<el-form-item label="slug" required>
							<el-input v-model="form.slug" maxlength="180" :placeholder="t('小写字母、数字、短横线')"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-form-item :label="t('一句话简介')">
					<el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit
					          :placeholder="t('列表页展示，写结论不写形容词')"/>
				</el-form-item>

				<el-row :gutter="12">
					<el-col :span="8">
						<el-form-item :label="t('技术领域')">
							<el-select v-model="form.category" style="width: 100%">
								<el-option v-for="item in categories" :key="item.code" :label="categoryLabel(item.code)"
								           :value="item.code"/>
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item label="License">
							<el-input v-model="form.license" placeholder="MIT / Apache-2.0"/>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('语言')">
							<el-input v-model="form.language" placeholder="Python"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-row :gutter="12">
					<el-col :span="8">
						<el-form-item label="GitHub">
							<el-input v-model="form.repoUrl"/>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('官网')">
							<el-input v-model="form.officialUrl"/>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('文档')">
							<el-input v-model="form.docsUrl"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-form-item :label="t('详细介绍')">
					<el-input v-model="form.description" type="textarea" :rows="8"
					          :placeholder="t('Markdown，支持 ## 小标题、代码块与表格')"/>
					<div class="hint">{{ t('与文章正文同一套 Markdown 渲染，标题会自动生成锚点') }}</div>
				</el-form-item>

				<el-divider content-position="left">{{ t('状态与结论') }}</el-divider>

				<el-row :gutter="12">
					<el-col :span="8">
						<el-form-item :label="t('研究状态')">
							<el-select v-model="form.researchStatus" style="width: 100%">
								<el-option :label="t('待研究')" value="todo"/>
								<el-option :label="t('研究中')" value="researching"/>
								<el-option :label="t('已完成')" value="done"/>
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('发布状态')">
							<el-select v-model="form.publishStatus" style="width: 100%">
								<el-option :label="t('草稿')" value="draft"/>
								<el-option :label="t('已发布')" value="published"/>
								<el-option :label="t('已归档')" value="archived"/>
							</el-select>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('结论档')">
							<el-select v-model="form.verdict" clearable style="width: 100%">
								<el-option v-for="(label, code) in verdicts" :key="code" :label="label" :value="code"/>
							</el-select>
						</el-form-item>
					</el-col>
				</el-row>

				<el-row :gutter="12">
					<el-col :span="8">
						<el-form-item :label="t('精选')">
							<el-switch v-model="form.featured"/>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('排序')">
							<el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right"/>
						</el-form-item>
					</el-col>
					<el-col :span="8">
						<el-form-item :label="t('资料核验')">
							<el-date-picker v-model="form.lastVerifiedAt" type="date" value-format="YYYY-MM-DD"
							                :placeholder="t('核验日期')" style="width: 100%"/>
						</el-form-item>
					</el-col>
				</el-row>

				<el-divider content-position="left">{{ t('商业价值评估卡') }}</el-divider>
				<div class="assess-head">
					<span class="hint">{{ t('未评的档位留空，前台显示「待验证」；每格必须给证据等级') }}</span>
				</div>

				<div class="assess-row assess-row-head">
					<span>{{ t('维度') }}</span>
					<span>{{ t('档位') }}</span>
					<span>{{ t('一句话条件') }}</span>
					<span>{{ t('证据') }}</span>
				</div>
				<div v-for="row in assessmentRows" :key="row.key" class="assess-row">
					<span class="assess-label">{{ fieldLabel(row.key) }}</span>
					<el-select v-model="row.level" clearable size="small" :placeholder="t('待验证')">
						<el-option :label="t('高')" value="high"/>
						<el-option :label="t('中')" value="mid"/>
						<el-option :label="t('低')" value="low"/>
					</el-select>
					<el-input v-model="row.note" size="small" maxlength="40" :placeholder="t('写条件，不写形容词')"/>
					<el-select v-model="row.evidence" size="small">
						<el-option v-for="(label, code) in evidences" :key="code" :label="`${code} · ${label}`"
						           :value="code"/>
					</el-select>
				</div>

				<el-divider content-position="left">{{ t('标签与关联文章') }}</el-divider>

				<el-form-item :label="t('标签')">
					<el-select v-model="form.tagIds" multiple filterable style="width: 100%"
					           :placeholder="t('复用文章标签，缺的先去「标签管理」新建')">
						<el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id"/>
					</el-select>
				</el-form-item>

				<el-form-item :label="t('关联文章')">
					<div class="articles">
						<div v-for="(row, index) in form.articles" :key="index" class="article-row">
							<el-select v-model="row.blogId" filterable :placeholder="t('选择已发布文章')" size="small"
							           style="flex: 1">
								<el-option v-for="blog in blogOptions" :key="blog.id" :label="blog.title" :value="blog.id"/>
							</el-select>
							<el-select v-model="row.relationType" size="small" style="width: 120px">
								<el-option v-for="(label, code) in relations" :key="code" :label="label" :value="code"/>
							</el-select>
							<el-button link type="danger" :icon="Delete" @click="form.articles.splice(index, 1)"/>
						</div>
						<el-button link type="primary" :icon="Plus" @click="addArticle">{{ t('添加一篇') }}</el-button>
						<div class="hint">{{ t('只能在项目保存后关联已发布文章；草稿文章不能挂上来') }}</div>
					</div>
				</el-form-item>
			</el-form>

			<template #footer>
				<el-button @click="visible = false">{{ t('取消') }}</el-button>
				<el-button type="primary" :loading="saving" @click="save">{{ form.id ? t('保存修改') : t('创建') }}</el-button>
			</template>
		</el-drawer>
	</div>
</template>

<script setup lang="ts">
import {computed, reactive, ref} from 'vue'
import {useT} from '@/i18n/ui'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Delete, Plus, Search} from '@element-plus/icons-vue'
import {
	archiveOssProject,
	createOssProject,
	getOssProject,
	getOssProjectList,
	publishOssProject,
	updateOssProject
} from '@/api/oss'
import {getTagList} from '@/api/tag'
import {getBlogList} from '@/api/blog'

const t = useT()

const categories = ref<any[]>([])
const tagOptions = ref<any[]>([])
const blogOptions = ref<any[]>([])

const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)

const filters = reactive({
	keyword: '',
	publishStatus: '',
	researchStatus: '',
	category: ''
})

const visible = ref(false)
const saving = ref(false)

const FIELD_KEYS = ['techMaturity', 'marketDemand', 'commercialSpace', 'deployCost', 'competition', 'devValue', 'insightFit']
const EVIDENCES = ['F', 'S', 'T', 'M', 'A', '?']

const verdicts = computed(() => ({
	watch: t('值得持续关注'),
	deploy: t('值得部署使用'),
	extend: t('值得二次开发'),
	commercial: t('值得商业化探索'),
	hold: t('暂不建议投入')
}))

const evidences = computed(() => ({
	F: t('官方事实'),
	S: t('源码'),
	T: t('实测'),
	M: t('市场'),
	A: t('推断'),
	'?': t('待验证')
}))

const relations = computed(() => ({
	business: t('商业分析'),
	research: t('技术研究'),
	practice: t('实践'),
	compare: t('对比')
}))

const assessmentRows = ref<any[]>(FIELD_KEYS.map(key => ({key, level: '', note: '', evidence: '?'})))

const form = reactive<any>({
	id: null,
	slug: '',
	name: '',
	summary: '',
	description: '',
	repoUrl: '',
	officialUrl: '',
	docsUrl: '',
	license: '',
	language: '',
	category: 'ai-project',
	coverUrl: '',
	stars: null,
	starsAt: null,
	verdict: '',
	researchStatus: 'todo',
	publishStatus: 'draft',
	featured: false,
	lastVerifiedAt: '',
	sort: 0,
	tagIds: [],
	articles: []
})

function categoryLabel(code: string) {
	const hit = categories.value.find(item => item.code === code)
	return hit ? hit.labelZh : code
}

function verdictLabel(code: string) {
	return verdicts.value[code] || code
}

function publishLabel(status: string) {
	return {draft: t('草稿'), published: t('已发布'), archived: t('已归档')}[status] || status
}

function publishTagType(status: string) {
	return {draft: 'info', published: 'success', archived: 'warning'}[status] || 'info'
}

function researchLabel(status: string) {
	return {todo: t('待研究'), researching: t('研究中'), done: t('已完成')}[status] || status
}

function fieldLabel(key: string) {
	return {
		techMaturity: t('技术成熟度'),
		marketDemand: t('市场需求'),
		commercialSpace: t('商业化空间'),
		deployCost: t('部署成本'),
		competition: t('竞争程度'),
		devValue: t('二次开发价值'),
		insightFit: t('Insight 适配')
	}[key] || key
}

function shortDate(value: string) {
	return value ? String(value).slice(0, 10) : '—'
}

async function load() {
	loading.value = true
	try {
		const res: any = await getOssProjectList({
			pageNum: pageNum.value,
			pageSize,
			keyword: filters.keyword || undefined,
			publishStatus: filters.publishStatus || undefined,
			researchStatus: filters.researchStatus || undefined,
			category: filters.category || undefined
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

async function loadOptions() {
	const [catRes, tagRes, blogRes]: any[] = await Promise.all([
		// 领域是公开接口，和后台不在同一个 baseURL 下，直接用原生 fetch
		fetch('/api/open-source/categories').then(res => res.json()),
		getTagList({pageNum: 1, pageSize: 200}),
		getBlogList({pageNum: 1, pageSize: 200})
	])
	categories.value = catRes.data || []
	tagOptions.value = tagRes.data.list || []
	blogOptions.value = blogRes.data.list || []
}

function resetForm() {
	Object.assign(form, {
		id: null, slug: '', name: '', summary: '', description: '', repoUrl: '', officialUrl: '', docsUrl: '',
		license: '', language: '', category: 'ai-project', coverUrl: '', stars: null, starsAt: null,
		verdict: '', researchStatus: 'todo', publishStatus: 'draft', featured: false,
		lastVerifiedAt: '', sort: 0, tagIds: [], articles: []
	})
	assessmentRows.value = FIELD_KEYS.map(key => ({key, level: '', note: '', evidence: '?'}))
}

async function openDrawer(row?: any) {
	resetForm()
	if (row) {
		const res: any = await getOssProject(row.id)
		const project = res.data.project || {}
		Object.assign(form, {
			...project,
			lastVerifiedAt: project.lastVerifiedAt ? String(project.lastVerifiedAt).slice(0, 10) : '',
			tagIds: res.data.tagIds || [],
			articles: (res.data.articles || []).map((item: any) => ({
				blogId: item.id,
				relationType: item.relationType || 'research'
			}))
		})
		// 评估卡原文解析失败时退回全空行，不阻断编辑
		let parsed: any = {}
		try {
			parsed = project.assessmentJson ? JSON.parse(project.assessmentJson) : {}
		} catch {
			parsed = {}
		}
		assessmentRows.value = FIELD_KEYS.map(key => ({
			key,
			level: (parsed[key] && parsed[key].level) || '',
			note: (parsed[key] && parsed[key].note) || '',
			evidence: (parsed[key] && parsed[key].evidence) || '?'
		}))
	}
	visible.value = true
}

function addArticle() {
	form.articles.push({blogId: null, relationType: 'research'})
}

function buildAssessment() {
	const data: any = {}
	assessmentRows.value.forEach(row => {
		if (!row.level && !row.note) {
			return
		}
		data[row.key] = {level: row.level || null, note: row.note || '', evidence: row.evidence || '?'}
	})
	return Object.keys(data).length ? JSON.stringify(data) : null
}

function toIso(date: string) {
	return date ? `${date}T00:00:00.000Z` : null
}

async function save() {
	if (!form.name || !form.slug) {
		ElMessage.warning(t('项目名与 slug 必填'))
		return
	}
	saving.value = true
	try {
		const payload = {
			...form,
			lastVerifiedAt: toIso(form.lastVerifiedAt),
			assessmentJson: buildAssessment(),
			articles: form.articles
				.filter((row: any) => row.blogId)
				.map((row: any, index: number) => ({blogId: row.blogId, relationType: row.relationType, sort: index}))
		}
		if (form.id) {
			await updateOssProject(payload)
			ElMessage.success(t('已保存'))
		} else {
			await createOssProject(payload)
			ElMessage.success(t('已创建'))
		}
		visible.value = false
		load()
	} finally {
		saving.value = false
	}
}

async function publish(row: any) {
	await publishOssProject(row.id)
	ElMessage.success(t('已发布'))
	load()
}

async function archive(row: any) {
	await ElMessageBox.confirm(t('归档后前台档案 404，但关联文章仍可访问。确认归档？'), t('提示'), {type: 'warning'})
	await archiveOssProject(row.id)
	ElMessage.success(t('已归档'))
	load()
}

loadOptions()
load()
</script>

<style scoped>
.filters {
	margin-bottom: 8px;
}

.cell-title {
	font-weight: 600;
}

.slug {
	font-size: 11.5px;
	color: var(--el-text-color-secondary);
}

.muted {
	color: var(--el-text-color-secondary);
}

.hint {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	line-height: 1.6;
}

.assess-head {
	margin-bottom: 8px;
}

.assess-row {
	display: grid;
	grid-template-columns: 110px 110px minmax(0, 1fr) 150px;
	gap: 10px;
	align-items: center;
	padding: 6px 0;
	border-bottom: 1px dashed var(--el-border-color-lighter);
}

.assess-row-head {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	border-bottom: 1px solid var(--el-border-color);
}

.assess-label {
	font-size: 13px;
}

.articles {
	width: 100%;
	display: flex;
	flex-direction: column;
	gap: 8px;
}

.article-row {
	display: flex;
	gap: 8px;
	align-items: center;
}
</style>
