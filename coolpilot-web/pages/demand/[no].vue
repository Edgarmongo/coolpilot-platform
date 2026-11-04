<template>
	<div v-if="demand">
		<div class="card">
			<div class="head">
				<div>
					<div class="crumbs">
						<span class="tag">需求</span>
						<span class="mono muted">{{ demand.demandNo }}</span>
						<span :class="['status', statusClass(demand.status)]">{{ statusText(demand.status) }}</span>
					</div>
					<h1 class="page-title">{{ demand.title }}</h1>
					<p class="page-desc">
						{{ typeText(demand.projectType) }} · 提交于 {{ formatDate(demand.createTime) }}
						<span v-if="demand.productTitle"> · 关联服务「{{ demand.productTitle }}」</span>
					</p>
				</div>
				<div class="head-actions">
					<button v-if="cancelable" class="btn ghost" :disabled="busy" @click="cancel">取消需求</button>
				</div>
			</div>

			<div class="meta">
				<div><span class="muted">预算</span><strong>{{ budgetText }}</strong></div>
				<div><span class="muted">期望交付</span><strong>{{ demand.expectDate || '未填写' }}</strong></div>
				<div><span class="muted">技术栈</span><strong>{{ demand.techStack || '未填写' }}</strong></div>
				<div v-if="demand.contact"><span class="muted">联系方式</span><strong>{{ demand.contact }}</strong></div>
			</div>

			<div class="desc">{{ demand.description }}</div>

			<div v-if="demand.attachmentFileId" class="attach">
				<span class="muted">附件已上传</span>
			</div>

			<p v-if="demand.rejectReason" class="reject">未通过原因：{{ demand.rejectReason }}</p>
		</div>

		<!-- 报价 -->
		<div class="card section">
			<h2 class="sub-title">报价单</h2>
			<div v-if="!quotes.length" class="empty">平台评估后会在这里给出报价</div>
			<div v-for="quote in quotes" :key="quote.id" class="quote">
				<div class="quote-head">
					<div>
						<strong>第 {{ quote.version }} 版报价</strong>
						<span class="mono muted">{{ quote.quoteNo }}</span>
					</div>
					<div class="quote-right">
						<span class="amount">¥{{ price(quote.amount) }}</span>
						<span :class="['status', quote.status === 'SENT' ? 'wait' : '']">{{ quoteStatusText(quote.status) }}</span>
					</div>
				</div>
				<p v-if="quote.scope" class="scope">{{ quote.scope }}</p>
				<p class="muted small">
					有效期至 {{ quote.validUntil || '未设置' }}
					<span v-if="quote.status === 'SENT'"> · 确认后平台会立即立项</span>
				</p>
				<div v-if="quote.status === 'SENT' && canAct" class="quote-actions">
					<button class="btn" :disabled="busy" @click="acceptQuote(quote)">接受报价</button>
					<button class="btn ghost" :disabled="busy" @click="rejectQuote(quote)">不合适</button>
				</div>
			</div>
		</div>

		<!-- 项目 -->
		<div v-if="projects.length" class="card section">
			<h2 class="sub-title">项目</h2>
			<NuxtLink v-for="project in projects" :key="project.id" :to="`/project/${project.projectNo}`" class="project">
				<div>
					<strong>{{ project.name }}</strong>
					<span class="mono muted">{{ project.projectNo }}</span>
				</div>
				<div class="project-right">
					<span class="amount">¥{{ price(project.amount) }}</span>
					<span class="status">{{ projectStatusText(project.status) }}</span>
					<span class="muted">查看进度 →</span>
				</div>
			</NuxtLink>
		</div>

		<!-- 流水 -->
		<div class="card section">
			<h2 class="sub-title">进度记录</h2>
			<div v-if="!timeline.length" class="empty">暂无记录</div>
			<div v-for="(event, index) in timeline" :key="event.id" class="event">
				<span class="dot"/>
				<div class="event-main">
					<div class="event-title">
						{{ actionText(event.action) }}
						<span v-if="event.fromStatus || event.toStatus" class="muted">
							{{ statusText(event.fromStatus) || '—' }} → {{ statusText(event.toStatus) }}
						</span>
					</div>
					<p v-if="event.remark" class="muted small">{{ event.remark }}</p>
					<p class="muted small">{{ event.operatorName || '系统' }} · {{ formatDate(event.createTime) }}</p>
				</div>
			</div>
		</div>
	</div>
	<div v-else class="empty card">需求不存在或无权查看</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()

useHead({title: '需求详情 - CoolSkyAI'})

if (!isLogin.value) {
	await navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
}

const {data, refresh} = await useAsyncData(`demand-${route.params.no}`,
	() => call(`/demand/${route.params.no}`).catch(() => null))

const demand = computed(() => (data.value as any)?.data?.demand || null)
const quotes = computed(() => (data.value as any)?.data?.quotes || [])
const projects = computed(() => (data.value as any)?.data?.projects || [])
const timeline = computed(() => (data.value as any)?.data?.timeline || [])

const busy = ref(false)
const canAct = computed(() => demand.value?.status === 'PENDING_CONFIRM')
const cancelable = computed(() =>
	['PENDING_REVIEW', 'PENDING_CONTACT', 'EVALUATING', 'PENDING_QUOTE', 'PENDING_CONFIRM'].includes(demand.value?.status))

const budgetText = computed(() => {
	const d = demand.value
	if (!d) {
		return ''
	}
	if (d.budgetNegotiable) {
		return '面议'
	}
	if (d.budgetMin && d.budgetMax) {
		return `¥${price(d.budgetMin)} - ¥${price(d.budgetMax)}`
	}
	if (d.budgetMax) {
		return `不超过 ¥${price(d.budgetMax)}`
	}
	if (d.budgetMin) {
		return `¥${price(d.budgetMin)} 起`
	}
	return '面议'
})

async function acceptQuote(quote: any) {
	if (!confirm(`确认接受 ¥${price(quote.amount)} 的报价？确认后项目立即立项。`)) {
		return
	}
	busy.value = true
	try {
		const res: any = await call(`/quote/${quote.id}/accept`, {method: 'POST'})
		if (res.code === 200) {
			await navigateTo(`/project/${res.data.project.projectNo}`)
		} else {
			alert(res.msg || '操作失败')
		}
	} finally {
		busy.value = false
	}
}

async function rejectQuote(quote: any) {
	const reason = prompt('方便说一下哪里不合适吗？（可留空，平台会重新报价）') ?? ''
	busy.value = true
	try {
		const res: any = await call(`/quote/${quote.id}/reject`, {method: 'POST', body: {reason}})
		if (res.code === 200) {
			await refresh()
		} else {
			alert(res.msg || '操作失败')
		}
	} finally {
		busy.value = false
	}
}

async function cancel() {
	if (!confirm('确定取消这个需求？')) {
		return
	}
	busy.value = true
	try {
		const res: any = await call(`/demand/${route.params.no}/cancel`, {method: 'POST'})
		if (res.code === 200) {
			await refresh()
		} else {
			alert(res.msg || '操作失败')
		}
	} finally {
		busy.value = false
	}
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function typeText(type: string) {
	return {BACKEND: '后端开发', AI: 'AI 应用', DEPLOY: '部署运维', CONSULT: '技术咨询', OTHER: '其他'}[type] || type
}

function statusText(status: string) {
	return {
		PENDING_REVIEW: '待审核', PENDING_CONTACT: '待联系', EVALUATING: '评估中', PENDING_QUOTE: '待报价',
		PENDING_CONFIRM: '待确认报价', IN_PROGRESS: '实施中', PENDING_ACCEPT: '待验收', COMPLETED: '已完成',
		REJECTED: '未通过', CANCELLED: '已取消', DISPUTED: '有争议',
		DRAFT: '草稿', SENT: '待确认', ACCEPTED: '已接受', EXPIRED: '已过期'
	}[status] || status || ''
}

function statusClass(status: string) {
	if (['COMPLETED', 'ACCEPTED', 'IN_PROGRESS'].includes(status)) {
		return 'ok'
	}
	if (['REJECTED', 'CANCELLED', 'DISPUTED'].includes(status)) {
		return 'bad'
	}
	return 'wait'
}

function quoteStatusText(status: string) {
	return {DRAFT: '草稿', SENT: '待确认', ACCEPTED: '已接受', REJECTED: '已拒绝', EXPIRED: '已失效'}[status] || status
}

function projectStatusText(status: string) {
	return {
		NOT_STARTED: '未开始', IN_PROGRESS: '实施中', PENDING_ACCEPT: '待验收',
		COMPLETED: '已完成', ARCHIVED: '已归档'
	}[status] || status
}

function actionText(action: string) {
	return {
		SUBMIT: '提交需求', APPROVE: '审核通过', REJECT: '拒绝', MOVE: '状态调整',
		QUOTE: '创建报价', UPDATE: '修改报价', SEND: '发送报价', SEND_QUOTE: '报价已发出',
		ACCEPT: '接受报价', ACCEPT_QUOTE: '确认报价', REJECT_QUOTE: '拒绝报价', CANCEL: '取消需求'
	}[action] || action
}
</script>

<style scoped>
.head {
	display: flex;
	justify-content: space-between;
	gap: 16px;
	flex-wrap: wrap;
	border-bottom: 1px solid var(--line);
	padding-bottom: 14px;
}

.crumbs {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 12px;
	margin-bottom: 6px;
}

.meta {
	display: grid;
	grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
	gap: 12px;
	padding: 14px 0;
	border-bottom: 1px dashed var(--line);
}

.meta div {
	display: flex;
	flex-direction: column;
	gap: 4px;
	font-size: 14px;
}

.desc {
	white-space: pre-wrap;
	line-height: 1.8;
	padding: 14px 0 4px;
	font-size: 14px;
}

.attach {
	font-size: 13px;
}

.reject {
	margin: 10px 0 0;
	color: #b91c1c;
	font-size: 13px;
	background: #fef2f2;
	padding: 8px 10px;
	border-radius: 8px;
}

.section {
	margin-top: 14px;
}

.sub-title {
	margin: 0 0 10px;
	font-size: 15px;
}

.quote {
	border: 1px solid var(--line);
	border-radius: 10px;
	padding: 12px;
	margin-bottom: 10px;
}

.quote-head,
.project {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	flex-wrap: wrap;
}

.quote-right,
.project-right {
	display: flex;
	align-items: center;
	gap: 10px;
	font-size: 13px;
}

.scope {
	margin: 8px 0 4px;
	font-size: 13px;
	white-space: pre-wrap;
}

.small {
	font-size: 12px;
}

.quote-actions {
	display: flex;
	gap: 10px;
	margin-top: 10px;
}

.project {
	border: 1px solid var(--line);
	border-radius: 10px;
	padding: 12px;
	margin-bottom: 10px;
}

.project div {
	display: flex;
	align-items: center;
	gap: 10px;
}

.event {
	display: flex;
	gap: 10px;
	padding: 8px 0;
}

.dot {
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: var(--teal);
	margin-top: 7px;
	flex: none;
}

.event-title {
	font-size: 14px;
	display: flex;
	gap: 8px;
	flex-wrap: wrap;
}

.event p {
	margin: 2px 0 0;
}
</style>
