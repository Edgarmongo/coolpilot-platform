<template>
	<div>
		<el-form inline>
			<el-form-item :label="t('类型')">
				<el-select v-model="query.type" :placeholder="t('全部')" clearable style="width: 130px" @change="search">
					<el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value"/>
				</el-select>
			</el-form-item>
			<el-form-item :label="t('状态')">
				<el-select v-model="query.status" :placeholder="t('全部')" clearable style="width: 130px" @change="search">
					<el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
				</el-select>
			</el-form-item>
			<el-form-item :label="t('关键词')">
				<el-input v-model="query.keyword" :placeholder="t('标题 / 编号')" clearable style="width: 200px"
				          @keyup.enter="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" :icon="Search" @click="search">{{ t('搜索') }}</el-button>
				<el-button :icon="Refresh" @click="reset">{{ t('重置') }}</el-button>
			</el-form-item>
			<el-form-item>
				<el-button type="success" :icon="Plus" @click="openCreate">{{ t('新建商品') }}</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="list" v-loading="loading" size="small">
			<el-table-column :label="t('封面')" width="76">
				<template #default="{row}">
					<img v-if="row.cover" :src="row.cover" class="thumb" alt=""/>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('商品')" min-width="240">
				<template #default="{row}">
					<div class="p-title">
						<el-tag v-if="row.isTop" size="small" type="danger" effect="plain">{{ t('置顶') }}</el-tag>
						{{ row.title }}
					</div>
					<div class="muted mono">{{ row.productNo }}</div>
				</template>
			</el-table-column>
			<el-table-column :label="t('类型')" width="96">
				<template #default="{row}">{{ typeText(row.productType) }}</template>
			</el-table-column>
			<el-table-column :label="t('价格')" width="120">
				<template #default="{row}">
					<span class="amount">{{ priceText(row) }}</span>
				</template>
			</el-table-column>
			<el-table-column :label="t('成交')" width="104">
				<template #default="{row}">
					<span v-if="row.productType === 'SERVICE'">{{ dealModeText(row) }}</span>
					<span v-else class="muted">—</span>
				</template>
			</el-table-column>
			<el-table-column prop="salesCount" :label="t('销量')" width="76"/>
			<el-table-column :label="t('状态')" width="96">
				<template #default="{row}">
					<el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column :label="t('操作')" width="190" fixed="right">
				<template #default="{row}">
					<el-button link type="primary" size="small" @click="openEdit(row.id)">{{ t('编辑') }}</el-button>
					<el-button v-if="row.status !== 'ON_SALE'" link type="success" size="small"
					           @click="setStatus(row, 'ON_SALE')">{{ t('上架') }}</el-button>
					<el-button v-else link type="warning" size="small" @click="setStatus(row, 'OFF_SALE')">{{ t('下架') }}</el-button>
					<el-button link type="info" size="small" @click="setStatus(row, 'ARCHIVED')">{{ t('归档') }}</el-button>
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

		<!-- ---------------- 编辑抽屉 ---------------- -->
		<el-drawer v-model="drawer" :title="form.id ? t('编辑商品') : t('新建商品')" size="680px" @closed="resetDrawer">
			<el-tabs v-model="tab">
				<el-tab-pane :label="t('基础信息')" name="base">
					<el-form :model="form" label-width="92px" size="small">
						<el-form-item :label="t('类型')" required>
							<el-select v-model="form.productType" :disabled="!!form.id" style="width: 100%">
								<el-option v-for="item in typeOptions" :key="item.value" :label="item.label"
								           :value="item.value"/>
							</el-select>
							<div class="hint">{{ t('创建后不可修改；课时仅「课程」类型可用') }}</div>
						</el-form-item>
						<el-form-item :label="t('分类')">
							<el-select v-model="form.categoryId" :placeholder="t('不选则不归类')" clearable style="width: 100%">
								<el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id"/>
							</el-select>
						</el-form-item>
						<el-form-item :label="t('标题')" required>
							<el-input v-model="form.title" maxlength="120" show-word-limit/>
						</el-form-item>
						<el-form-item :label="t('副标题')">
							<el-input v-model="form.subtitle" maxlength="160"/>
						</el-form-item>
						<el-form-item :label="t('封面')">
							<el-input v-model="form.cover" :placeholder="t('图片地址，或点右侧上传')"/>
							<input ref="coverInput" type="file" accept="image/*" hidden @change="uploadCover">
							<el-button size="small" :loading="uploading" @click="$refs.coverInput.click()">{{ t('上传') }}</el-button>
						</el-form-item>
						<el-form-item :label="t('定价方式')">
							<el-radio-group v-model="form.priceMode">
								<el-radio-button v-for="m in priceModeOptions" :key="m.value" :value="m.value">
									{{ m.label }}
								</el-radio-button>
							</el-radio-group>
						</el-form-item>
						<!-- 只有服务类才有「直接买 / 先报价」的选择；实物型商品固定直接下单 -->
						<el-form-item v-if="form.productType === 'SERVICE'" :label="t('成交模式')">
							<el-select v-model="form.dealMode" clearable class="deal-select"
							           :placeholder="t('留空则按定价方式推导')">
								<el-option :label="t('直接下单')" value="DIRECT"/>
								<el-option :label="t('需求 → 报价')" value="QUOTE"/>
								<el-option :label="t('两者都允许')" value="BOTH"/>
							</el-select>
							<span class="hint inline">{{ t('原则上只配一种；双入口会让订单来源变复杂') }}</span>
						</el-form-item>
						<el-form-item :label="t('价格')">
							<el-input-number v-model="form.price" :min="0" :precision="2" :step="10"
							                 controls-position="right"/>
							<span class="hint inline">{{ t('原价') }}</span>
							<el-input-number v-model="form.originalPrice" :min="0" :precision="2" :step="10"
							                 controls-position="right"/>
						</el-form-item>
						<el-form-item :label="t('排序')">
							<el-input-number v-model="form.sort" :min="0" :max="9999" controls-position="right"/>
							<el-checkbox v-model="form.isTop" class="inline-check">{{ t('置顶') }}</el-checkbox>
						</el-form-item>
						<el-form-item :label="t('摘要')">
							<el-input v-model="form.summary" type="textarea" :rows="2" maxlength="255"
							          :placeholder="t('列表页展示的一句话简介')"/>
						</el-form-item>
						<el-form-item v-if="form.productType === 'COURSE'" :label="t('课程简介')">
							<el-input v-model="form.courseDescription" type="textarea" :rows="2"
							          :placeholder="t('显示在课程目录上方')"/>
						</el-form-item>
						<el-form-item :label="t('详情')">
							<el-input v-model="form.detail" type="textarea" :rows="8"
							          :class="{dragging: detailDragging}"
							          :placeholder="t('支持 Markdown，前台渲染为 HTML；截图后可直接 Ctrl+V 粘贴')"
							          @paste="onDetailPaste"
							          @dragover.prevent="onDetailDragOver"
							          @dragleave="onDetailDragLeave"
							          @drop.prevent="onDetailDrop"/>
						</el-form-item>
						<el-form-item>
							<el-button type="primary" :loading="saving" @click="save">
								{{ form.id ? t('保存') : t('创建') }}
							</el-button>
							<span v-if="!form.id" class="hint inline">{{ t('创建后处于「草稿」，确认无误再上架') }}</span>
						</el-form-item>
					</el-form>
				</el-tab-pane>

				<!-- 课时 -->
				<el-tab-pane v-if="form.id && form.productType === 'COURSE'" :label="t('课时')" name="lesson">
					<div v-if="!detail.course" class="empty">{{ t('该商品还没有课程记录') }}</div>
					<template v-else>
						<el-table :data="detail.lessons || []" size="small">
							<el-table-column prop="sort" label="#" width="56"/>
							<el-table-column prop="title" :label="t('标题')" show-overflow-tooltip/>
							<el-table-column :label="t('时长')" width="80">
								<template #default="{row}">{{ row.duration || 0 }} {{ t('分') }}</template>
							</el-table-column>
							<el-table-column :label="t('试看')" width="70">
								<template #default="{row}">
									<el-tag v-if="row.isFreePreview" size="small" type="success">{{ t('可试看') }}</el-tag>
									<span v-else class="muted">—</span>
								</template>
							</el-table-column>
							<el-table-column :label="t('操作')" width="120">
								<template #default="{row}">
									<el-button link type="primary" size="small" @click="editLesson(row)">{{ t('编辑') }}</el-button>
									<el-button link type="danger" size="small" @click="removeLesson(row)">{{ t('删除') }}</el-button>
								</template>
							</el-table-column>
						</el-table>

						<el-divider content-position="left">{{ lessonForm.id ? t('编辑课时') : t('新增课时') }}</el-divider>
						<el-form :model="lessonForm" label-width="82px" size="small">
							<el-form-item :label="t('标题')" required>
								<el-input v-model="lessonForm.title"/>
							</el-form-item>
							<el-form-item :label="t('排序')">
								<el-input-number v-model="lessonForm.sort" :min="0" controls-position="right"/>
								<el-input-number v-model="lessonForm.duration" :min="0" controls-position="right"
								                 class="inline-num"/>
								<span class="hint inline">{{ t('分钟') }}</span>
								<el-checkbox v-model="lessonForm.isFreePreview" class="inline-check">{{ t('可试看') }}</el-checkbox>
							</el-form-item>
							<el-form-item :label="t('正文')">
								<el-input v-model="lessonForm.content" type="textarea" :rows="4"
								          :placeholder="t('课时正文，支持 Markdown')"/>
							</el-form-item>
							<el-form-item>
								<el-button type="primary" size="small" :loading="lessonSaving" @click="submitLesson">{{ t('保存课时') }}</el-button>
								<el-button v-if="lessonForm.id" size="small" @click="resetLessonForm">{{ t('取消编辑') }}</el-button>
							</el-form-item>
						</el-form>
					</template>
				</el-tab-pane>

				<!-- 资源 -->
				<el-tab-pane v-if="form.id" :label="t('配套资源')" name="resource">
					<el-table :data="detail.resources || []" size="small">
						<el-table-column prop="displayName" :label="t('文件名')" show-overflow-tooltip/>
						<el-table-column prop="version" :label="t('版本')" width="80"/>
						<el-table-column prop="resourceType" :label="t('类型')" width="80"/>
						<el-table-column :label="t('操作')" width="90">
							<template #default="{row}">
								<el-tag v-if="row.isLatest" size="small" type="success">{{ t('最新') }}</el-tag>
								<span v-else class="muted">{{ t('历史') }}</span>
							</template>
						</el-table-column>
					</el-table>

					<el-divider content-position="left">{{ t('绑定新资源') }}</el-divider>
					<el-form :model="resourceForm" label-width="82px" size="small">
						<el-form-item :label="t('文件')">
							<input ref="resInput" type="file" hidden @change="uploadResource">
							<el-button size="small" :loading="resUploading" @click="$refs.resInput.click()">
								{{ resourceForm.fileId ? t('已选：') + resourceForm.displayName : t('选择并上传') }}
							</el-button>
							<span class="hint inline">{{ t('上传后自动填入文件 ID') }}</span>
							<div class="hint">{{ t('支持 pdf / zip / md / txt / Office / 音视频，单文件 100MB 以内。 源码请打包成 zip —— 出于安全考虑，java/py 这类源码后缀不在白名单里。') }}</div>
						</el-form-item>
						<el-form-item :label="t('显示名')">
							<el-input v-model="resourceForm.displayName" :placeholder="t('留空则用原文件名')"/>
						</el-form-item>
						<el-form-item :label="t('版本')">
							<el-input v-model="resourceForm.version" placeholder="v1"/>
							<el-checkbox v-model="resourceForm.isLatest" class="inline-check">{{ t('标记为最新') }}</el-checkbox>
						</el-form-item>
						<el-form-item>
							<el-button type="primary" size="small" :loading="resSaving" @click="submitResource">{{ t('绑定') }}</el-button>
						</el-form-item>
					</el-form>
					<p class="hint">{{ t('绑定后，持有该商品权益的用户才能在「我的权益」里下载；后台与本人可随时预览。') }}</p>
				</el-tab-pane>

				<!-- 服务信息（仅技术服务类商品） -->
				<el-tab-pane v-if="form.id && form.productType === 'SERVICE'" :label="t('服务信息')" name="service">
					<el-form :model="serviceForm" label-width="92px" size="small">
						<el-form-item :label="t('交付周期')">
							<el-input-number v-model="serviceForm.deliveryDays" :min="0" :max="365"
							                 controls-position="right"/>
							<span class="hint inline">{{ t('天，填 0 表示面议') }}</span>
						</el-form-item>
						<el-form-item :label="t('服务范围')">
							<el-input v-model="serviceForm.scope" type="textarea" :rows="4"
							          :placeholder="t('包含什么、不包含什么，越具体越少扯皮')"/>
						</el-form-item>
						<el-form-item :label="t('服务流程')">
							<el-input v-model="serviceForm.process" type="textarea" :rows="4"
							          :placeholder="t('评估 → 报价 → 确认 → 实施 → 交付 → 验收')"/>
						</el-form-item>
						<el-form-item :label="t('常见问题')">
							<el-input v-model="serviceForm.faq" type="textarea" :rows="4"
							          :placeholder="t('付款方式、素材由谁提供、售后范围等')"/>
						</el-form-item>
						<el-form-item>
							<el-button type="primary" :loading="serviceSaving" @click="saveServiceInfo">{{ t('保存服务信息') }}</el-button>
							<span class="hint inline">{{ t('前台服务详情页会展示这些内容') }}</span>
						</el-form-item>
					</el-form>
				</el-tab-pane>
			</el-tabs>
		</el-drawer>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {nextTick, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Plus, Refresh, Search} from '@element-plus/icons-vue'
import {
	bindResource,
	changeProductStatus,
	createProduct,
	deleteLesson,
	getProductCategories,
	getProductDetail,
	getProductList,
	saveLesson,
	updateProduct,
	uploadFile
} from '@/api/product'
import {getServiceItem, saveServiceItem} from '@/api/service'
import {useImagePaste} from '@/composables/useImagePaste'

const typeOptions = [
	{value: 'DOC', label: t('文档')},
	{value: 'COURSE', label: t('课程')},
	{value: 'COLUMN', label: t('专栏')},
	{value: 'CODE', label: t('代码资源')},
	{value: 'SERVICE', label: t('技术服务')}
]
const statusOptions = [
	{value: 'DRAFT', label: t('草稿')},
	{value: 'ON_SALE', label: t('已上架')},
	{value: 'OFF_SALE', label: t('已下架')},
	{value: 'ARCHIVED', label: t('已归档')}
]
const priceModeOptions = [
	{value: 'FIXED', label: t('一口价')},
	{value: 'STARTING', label: t('起步价')},
	{value: 'CUSTOM', label: t('面议')}
]

const typeText = v => (typeOptions.find(i => i.value === v) || {}).label || v || '—'
const statusText = v => (statusOptions.find(i => i.value === v) || {}).label || v || '—'
const statusTag = v => ({DRAFT: 'info', ON_SALE: 'success', OFF_SALE: 'warning', ARCHIVED: 'info'}[v] || 'info')

/**
 * 列表里显示成交模式：没配就按规则推导，跟前台一致，避免「列表空着但前台有按钮」
 */
function dealModeText(row) {
	const mode = row.dealMode || (row.priceMode === 'FIXED' ? 'DIRECT' : 'QUOTE')
	return {DIRECT: t('直接下单'), QUOTE: t('需求报价'), BOTH: t('两者都允许')}[mode] || mode
}

function priceText(row) {
	if (row.priceMode === 'CUSTOM') return t('面议')
	const prefix = row.priceMode === 'STARTING' ? '¥' + (row.price || 0) + t(' 起') : '¥' + (row.price || 0)
	return prefix
}

// ---------------- 列表 ----------------
const query = reactive({type: '', status: '', keyword: '', pageNum: 1, pageSize: 10})
const list = ref([])
const total = ref(0)
const loading = ref(false)
const categories = ref([])

async function load() {
	loading.value = true
	try {
		const res = await getProductList({
			type: query.type || undefined,
			status: query.status || undefined,
			keyword: query.keyword || undefined,
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

function reset() {
	query.type = ''
	query.status = ''
	query.keyword = ''
	search()
}

function onPage(p) {
	query.pageNum = p
	load()
}

async function loadCategories() {
	const res = await getProductCategories()
	categories.value = res.data || []
}

async function setStatus(row, status) {
	const label = statusText(status)
	try {
		await ElMessageBox.confirm(`确认将「${row.title}」${label}？`, t('提示'), {type: 'warning'})
	} catch {
		return
	}
	await changeProductStatus(row.id, status)
	ElMessage.success(t('操作成功'))
	load()
}

// ---------------- 抽屉 ----------------
const drawer = ref(false)
const tab = ref('base')
const saving = ref(false)
const uploading = ref(false)
const detail = ref({course: null, lessons: [], resources: []})
const serviceForm = reactive({deliveryDays: 0, scope: '', process: '', faq: ''})
const serviceSaving = ref(false)

const emptyForm = () => ({
	id: null,
	productType: 'DOC',
	categoryId: null,
	title: '',
	subtitle: '',
	cover: '',
	summary: '',
	detail: '',
	price: 0,
	originalPrice: null,
	priceMode: 'FIXED',
	dealMode: null,
	sort: 0,
	isTop: false,
	courseDescription: ''
})
const form = reactive(emptyForm())

// 商品详情是 Markdown，前台 v-html 渲染，支持粘贴 / 拖拽贴图
const {
	uploading: detailUploading,
	dragging: detailDragging,
	onPaste: onDetailPaste,
	onDrop: onDetailDrop,
	onDragOver: onDetailDragOver,
	onDragLeave: onDetailDragLeave
} = useImagePaste({
	getValue: () => form.detail,
	setValue: value => {
		form.detail = value
	},
	bizType: 'PRODUCT'
})

async function openCreate() {
	Object.assign(form, emptyForm())
	detail.value = {course: null, lessons: [], resources: []}
	tab.value = 'base'
	drawer.value = true
}

async function openEdit(id) {
	Object.assign(form, emptyForm())
	await loadDetail(id)
	tab.value = 'base'
	drawer.value = true
}

async function loadDetail(id) {
	const res = await getProductDetail(id)
	const p = res.data.product || {}
	Object.assign(form, {
		id: p.id,
		productType: p.productType,
		categoryId: p.categoryId,
		title: p.title,
		subtitle: p.subtitle,
		cover: p.cover,
		summary: p.summary,
		detail: p.detail,
		price: p.price ? Number(p.price) : 0,
		originalPrice: p.originalPrice ? Number(p.originalPrice) : null,
		priceMode: p.priceMode || 'FIXED',
		dealMode: p.dealMode || null,
		sort: p.sort || 0,
		isTop: !!p.isTop,
		courseDescription: (res.data.course && res.data.course.description) || ''
	})
	detail.value = {
		course: res.data.course || null,
		lessons: res.data.lessons || [],
		resources: res.data.resources || []
	}
	Object.assign(serviceForm, {deliveryDays: 0, scope: '', process: '', faq: ''})
	if (p.productType === 'SERVICE') {
		const serviceRes = await getServiceItem(p.id).catch(() => null)
		if (serviceRes && serviceRes.data) {
			Object.assign(serviceForm, {
				deliveryDays: serviceRes.data.deliveryDays || 0,
				scope: serviceRes.data.scope || '',
				process: serviceRes.data.process || '',
				faq: serviceRes.data.faq || ''
			})
		}
	}
}

async function saveServiceInfo() {
	serviceSaving.value = true
	try {
		const res = await saveServiceItem(form.id, {
			deliveryDays: serviceForm.deliveryDays || 0,
			scope: serviceForm.scope,
			process: serviceForm.process,
			faq: serviceForm.faq
		})
		ElMessage.success(res.msg || t('已保存'))
	} catch (e) {
		ElMessage.error(e?.response?.data?.msg || e?.message || t('保存失败'))
	} finally {
		serviceSaving.value = false
	}
}

function resetDrawer() {
	tab.value = 'base'
	Object.assign(serviceForm, {deliveryDays: 0, scope: '', process: '', faq: ''})
	resetLessonForm()
	Object.assign(resourceForm, {fileId: null, displayName: '', version: 'v1', isLatest: true})
}

async function save() {
	// 上传中的图片只是正文里的一行注释，此刻保存会把占位存进正文
	if (detailUploading.value) {
		ElMessage.warning(t('图片还在上传，请稍候再保存'))
		return
	}
	if (!form.title || !form.title.trim()) {
		ElMessage.warning(t('请填写标题'))
		return
	}
	saving.value = true
	try {
		const payload = {
			productType: form.productType,
			categoryId: form.categoryId || null,
			title: form.title.trim(),
			subtitle: form.subtitle,
			cover: form.cover,
			summary: form.summary,
			detail: form.detail,
			price: form.price,
			originalPrice: form.originalPrice,
			priceMode: form.priceMode,
			dealMode: form.dealMode || null,
			sort: form.sort,
			isTop: form.isTop,
			courseDescription: form.courseDescription
		}
		if (form.id) {
			await updateProduct(form.id, payload)
			ElMessage.success(t('已保存'))
			await loadDetail(form.id)
		} else {
			const res = await createProduct(payload)
			const newId = res.data.id
			ElMessage.success(t('创建成功，当前为草稿'))
			await loadDetail(newId)
			// 创建后留在抽屉里，可以直接去加课时/资源
			if (form.productType === 'COURSE') tab.value = 'lesson'
		}
		load()
	} finally {
		saving.value = false
	}
}

async function uploadCover(event) {
	const file = event.target.files && event.target.files[0]
	if (!file) return
	uploading.value = true
	try {
		const fd = new FormData()
		fd.append('file', file)
		fd.append('bizType', 'PRODUCT')
		fd.append('isPublic', 'true')
		const res = await uploadFile(fd)
		form.cover = res.data.url
		ElMessage.success(t('封面已上传'))
	} finally {
		uploading.value = false
		event.target.value = ''
	}
}

// ---------------- 课时 ----------------
const lessonSaving = ref(false)
const emptyLesson = () => ({id: null, title: '', sort: 0, duration: 0, content: '', isFreePreview: false})
const lessonForm = reactive(emptyLesson())

function editLesson(row) {
	Object.assign(lessonForm, {
		id: row.id,
		title: row.title,
		sort: row.sort || 0,
		duration: row.duration || 0,
		content: row.content || '',
		isFreePreview: !!row.isFreePreview
	})
}

function resetLessonForm() {
	Object.assign(lessonForm, emptyLesson())
}

async function submitLesson() {
	if (!lessonForm.title.trim()) {
		ElMessage.warning(t('请填写课时标题'))
		return
	}
	if (!detail.value.course) {
		ElMessage.warning(t('课程信息缺失，请先保存商品'))
		return
	}
	lessonSaving.value = true
	try {
		await saveLesson({
			id: lessonForm.id || undefined,
			courseId: detail.value.course.id,
			title: lessonForm.title.trim(),
			sort: lessonForm.sort,
			duration: lessonForm.duration,
			content: lessonForm.content,
			isFreePreview: lessonForm.isFreePreview,
			contentType: 'DOC'
		})
		ElMessage.success(t('已保存'))
		resetLessonForm()
		await loadDetail(form.id)
	} finally {
		lessonSaving.value = false
	}
}

async function removeLesson(row) {
	try {
		await ElMessageBox.confirm(`删除课时「${row.title}」？`, t('提示'), {type: 'warning'})
	} catch {
		return
	}
	await deleteLesson(row.id)
	ElMessage.success(t('已删除'))
	await loadDetail(form.id)
}

// ---------------- 资源 ----------------
const resSaving = ref(false)
const resUploading = ref(false)
const resourceForm = reactive({fileId: null, displayName: '', version: 'v1', isLatest: true})

async function uploadResource(event) {
	const file = event.target.files && event.target.files[0]
	if (!file) return
	resUploading.value = true
	try {
		const fd = new FormData()
		fd.append('file', file)
		fd.append('bizType', 'PRODUCT_RESOURCE')
		fd.append('isPublic', 'false')
		const res = await uploadFile(fd)
		resourceForm.fileId = res.data.fileId
		resourceForm.displayName = resourceForm.displayName || file.name
		ElMessage.success(t('已上传，点「绑定」才会关联到商品'))
	} finally {
		resUploading.value = false
		event.target.value = ''
	}
}

async function submitResource() {
	if (!resourceForm.fileId) {
		ElMessage.warning(t('请先选择文件'))
		return
	}
	resSaving.value = true
	try {
		await bindResource(form.id, {
			fileId: resourceForm.fileId,
			displayName: resourceForm.displayName || t('未命名资源'),
			version: resourceForm.version || 'v1',
			isLatest: resourceForm.isLatest,
			resourceType: 'DOC'
		})
		ElMessage.success(t('已绑定'))
		Object.assign(resourceForm, {fileId: null, displayName: '', version: 'v1', isLatest: true})
		await loadDetail(form.id)
	} finally {
		resSaving.value = false
	}
}

onMounted(() => {
	load()
	loadCategories()
})
</script>

<style scoped>
.thumb {
	width: 52px;
	height: 34px;
	object-fit: cover;
	border-radius: 4px;
	background: #f2f4f7;
}

.p-title {
	display: flex;
	align-items: center;
	gap: 6px;
	font-weight: 500;
}

.muted {
	color: #909399;
	font-size: 12px;
}

.mono {
	font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.amount {
	color: #c2410c;
	font-weight: 600;
}

.pager {
	margin-top: 14px;
	justify-content: flex-end;
}

.hint {
	color: #909399;
	font-size: 12px;
	line-height: 1.6;
	margin-top: 4px;
}

.deal-select {
	width: 200px;
}

.hint.inline {
	margin: 0 0 0 8px;
}

.inline-check {
	margin-left: 12px;
}

.inline-num {
	margin-left: 8px;
}

.empty {
	color: #909399;
	font-size: 13px;
	padding: 12px 0;
}
</style>
