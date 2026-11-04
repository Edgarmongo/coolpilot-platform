<template>
	<div class="wrap">
		<h1 class="page-title">提交需求</h1>
		<p class="page-desc">描述越具体，报价越准确。提交后平台会先评估，再给你一份正式报价。</p>

		<div v-if="product" class="card picked">
			<span class="tag">已选服务</span>
			<strong>{{ product.title }}</strong>
			<span class="muted">{{ product.subtitle }}</span>
		</div>

		<div class="card">
			<div class="field">
				<label>需求标题 <em>*</em></label>
				<input v-model="form.title" maxlength="120" placeholder="例如：内部知识库问答系统">
			</div>

			<div class="row">
				<div class="field">
					<label>项目类型 <em>*</em></label>
					<select v-model="form.projectType">
						<option v-for="item in projectTypes" :key="item.value" :value="item.value">{{ item.label }}</option>
					</select>
				</div>
				<div class="field">
					<label>期望交付日期</label>
					<input v-model="form.expectDate" type="date">
				</div>
			</div>

			<div class="field">
				<label>技术要求</label>
				<input v-model="form.techStack" maxlength="500" placeholder="技术栈、系统规模、需要对接的系统等">
			</div>

			<div class="field">
				<label>预算区间</label>
				<div class="budget">
					<input v-model.number="form.budgetMin" type="number" min="0" placeholder="最低">
					<span class="muted">—</span>
					<input v-model.number="form.budgetMax" type="number" min="0" placeholder="最高">
					<label class="check"><input v-model="form.budgetNegotiable" type="checkbox"> 面议</label>
				</div>
			</div>

			<div class="field">
				<label>需求描述 <em>*</em></label>
				<textarea v-model="form.description" rows="8"
				          placeholder="背景、目标、功能点、交付物要求、验收标准……"/>
			</div>

			<div class="field">
				<label>附件</label>
				<input ref="fileInput" type="file" hidden @change="uploadAttachment">
				<div class="attach">
					<button class="btn ghost" type="button" :disabled="uploading" @click="$refs.fileInput.click()">
						{{ uploading ? '上传中…' : (attachmentName || '上传需求文档') }}
					</button>
					<span class="muted">可选，支持 pdf / docx / zip / 图片，100MB 以内</span>
				</div>
			</div>

			<div class="field">
				<label>联系方式 <em>*</em></label>
				<input v-model="form.contact" maxlength="120" placeholder="手机 / 微信 / 邮箱，仅平台可见">
			</div>

			<div class="actions">
				<button class="btn" :disabled="submitting" @click="submit">
					{{ submitting ? '提交中…' : '提交需求' }}
				</button>
				<span v-if="error" class="error">{{ error }}</span>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call, isLogin, token} = useApi()

useHead({title: '提交需求 - CoolSkyAI'})

if (!isLogin.value) {
	await navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
}

const projectTypes = [
	{value: 'BACKEND', label: '后端开发'},
	{value: 'AI', label: 'AI 应用'},
	{value: 'DEPLOY', label: '部署运维'},
	{value: 'CONSULT', label: '技术咨询'},
	{value: 'OTHER', label: '其他'}
]

const form = reactive({
	productId: null as number | null,
	title: '',
	projectType: 'BACKEND',
	techStack: '',
	budgetMin: null as number | null,
	budgetMax: null as number | null,
	budgetNegotiable: false,
	expectDate: '',
	description: '',
	attachmentFileId: null as number | null,
	contact: ''
})

const product = ref<any>(null)
const attachmentName = ref('')
const uploading = ref(false)
const submitting = ref(false)
const error = ref('')

if (route.query.productNo) {
	const res: any = await call(`/product/${route.query.productNo}`).catch(() => null)
	if (res?.code === 200) {
		product.value = res.data.product
		form.productId = res.data.product.id
		form.title = res.data.product.title
	}
}

async function uploadAttachment(event: any) {
	const file = event.target.files?.[0]
	if (!file) {
		return
	}
	uploading.value = true
	try {
		const body = new FormData()
		body.append('file', file)
		const res: any = await call('/file/upload', {method: 'POST', body})
		if (res.code === 200) {
			form.attachmentFileId = res.data.fileId
			attachmentName.value = file.name
		} else {
			alert(res.msg || '上传失败')
		}
	} finally {
		uploading.value = false
	}
}

async function submit() {
	error.value = ''
	if (!form.title.trim()) {
		error.value = '请填写需求标题'
		return
	}
	if (!form.description.trim()) {
		error.value = '请填写需求描述'
		return
	}
	if (!form.contact.trim()) {
		error.value = '请填写联系方式，便于平台评估后联系你'
		return
	}
	submitting.value = true
	try {
		const res: any = await call('/demand', {
			method: 'POST',
			body: {
				...form,
				expectDate: form.expectDate || null,
				budgetMin: form.budgetNegotiable ? null : form.budgetMin,
				budgetMax: form.budgetNegotiable ? null : form.budgetMax
			}
		})
		if (res.code === 200) {
			await router.push(`/demand/${res.data.demandNo}`)
		} else {
			error.value = res.msg || '提交失败'
		}
	} finally {
		submitting.value = false
	}
}
</script>

<style scoped>
.wrap {
	max-width: 760px;
	margin: 0 auto;
}

.picked {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 14px;
	font-size: 14px;
}

.field {
	margin-bottom: 14px;
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.field label {
	font-size: 13px;
	color: var(--muted);
}

.field em {
	color: #dc2626;
	font-style: normal;
}

.field input,
.field select,
.field textarea {
	padding: 10px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 14px;
	outline-color: var(--teal);
	background: #fff;
	font-family: inherit;
}

.field textarea {
	resize: vertical;
}

.row {
	display: flex;
	gap: 14px;
}

.row .field {
	flex: 1;
}

.budget {
	display: flex;
	align-items: center;
	gap: 8px;
}

.budget input {
	flex: 1;
}

.check {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	font-size: 13px;
	color: var(--muted);
	white-space: nowrap;
}

.attach {
	display: flex;
	align-items: center;
	gap: 10px;
	flex-wrap: wrap;
}

.actions {
	display: flex;
	align-items: center;
	gap: 12px;
	margin-top: 6px;
}

.error {
	color: #dc2626;
	font-size: 13px;
}
</style>
