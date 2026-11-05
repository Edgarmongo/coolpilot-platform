<template>
	<div v-if="demand">
		<div class="card">
			<div class="head">
				<div>
					<div class="crumbs">
						<span class="tag">{{ t('demandDetail.tag') }}</span>
						<span class="mono muted">{{ demand.demandNo }}</span>
						<span :class="['status', statusClass(demand.status)]">{{ statusText(demand.status) }}</span>
					</div>
					<h1 class="page-title">{{ demand.title }}</h1>
					<p class="page-desc">
						{{ typeText(demand.projectType) }} · {{ t('demandDetail.submittedAt', {date: formatDate(demand.createTime)}) }}
						<span v-if="demand.productTitle"> · {{ t('demandDetail.related', {name: demand.productTitle}) }}</span>
					</p>
				</div>
				<div class="head-actions">
					<button v-if="cancelable" class="btn ghost" :disabled="busy" @click="cancel">{{ t('demandDetail.cancel') }}</button>
				</div>
			</div>

			<div class="meta">
				<div><span class="muted">{{ t('demandDetail.budget') }}</span><strong>{{ budgetText }}</strong></div>
				<div><span class="muted">{{ t('demandDetail.expect') }}</span><strong>{{ demand.expectDate || t('demandDetail.notFilled') }}</strong></div>
				<div><span class="muted">{{ t('demandDetail.tech') }}</span><strong>{{ demand.techStack || t('demandDetail.notFilled') }}</strong></div>
				<div v-if="demand.contact"><span class="muted">{{ t('demandDetail.contact') }}</span><strong>{{ demand.contact }}</strong></div>
			</div>

			<div class="desc">{{ demand.description }}</div>

			<div v-if="demand.attachmentFileId" class="attach">
				<span class="muted">{{ t('demandDetail.attachUploaded') }}</span>
			</div>

			<p v-if="demand.rejectReason" class="reject">{{ t('demandDetail.rejected', {reason: demand.rejectReason}) }}</p>
		</div>

		<!-- 报价 -->
		<div class="card section">
			<h2 class="sub-title">{{ t('demandDetail.quotes') }}</h2>
			<div v-if="!quotes.length" class="empty">{{ t('demandDetail.noQuotes') }}</div>
			<div v-for="quote in quotes" :key="quote.id" class="quote">
				<div class="quote-head">
					<div>
						<strong>{{ t('demandDetail.version', {n: quote.version}) }}</strong>
						<span class="mono muted">{{ quote.quoteNo }}</span>
					</div>
					<div class="quote-right">
						<span class="amount">¥{{ price(quote.amount) }}</span>
						<span :class="['status', quote.status === 'SENT' ? 'wait' : '']">{{ quoteStatusText(quote.status) }}</span>
					</div>
				</div>
				<p v-if="quote.scope" class="scope">{{ quote.scope }}</p>
				<p class="muted small">
					{{ t('demandDetail.validUntil', {date: quote.validUntil || t('demandDetail.notSet')}) }}
					<span v-if="quote.status === 'SENT'"> · {{ t('demandDetail.afterConfirm') }}</span>
				</p>
				<div v-if="quote.status === 'SENT' && canAct" class="quote-actions">
					<button class="btn" :disabled="busy" @click="acceptQuote(quote)">{{ t('demandDetail.accept') }}</button>
					<button class="btn ghost" :disabled="busy" @click="rejectQuote(quote)">{{ t('demandDetail.reject') }}</button>
				</div>
			</div>
		</div>

		<!-- 项目 -->
		<div v-if="projects.length" class="card section">
			<h2 class="sub-title">{{ t('demandDetail.projects') }}</h2>
			<NuxtLink v-for="project in projects" :key="project.id" :to="localePath(`/project/${project.projectNo}`)" class="project">
				<div>
					<strong>{{ project.name }}</strong>
					<span class="mono muted">{{ project.projectNo }}</span>
				</div>
				<div class="project-right">
					<span class="amount">¥{{ price(project.amount) }}</span>
					<span class="status">{{ projectStatusText(project.status) }}</span>
					<span class="muted">{{ t('demandDetail.viewProgress') }}</span>
				</div>
			</NuxtLink>
		</div>

		<!-- 流水 -->
		<div class="card section">
			<h2 class="sub-title">{{ t('demandDetail.timeline') }}</h2>
			<div v-if="!timeline.length" class="empty">{{ t('demandDetail.noEvents') }}</div>
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
					<p class="muted small">{{ event.operatorName || t('demandDetail.system') }} · {{ formatDate(event.createTime) }}</p>
				</div>
			</div>
		</div>
	</div>
	<div v-else class="empty card">{{ t('demandDetail.notFound') }}</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

useHead({title: t('demandDetail.head')})

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
		return t('demandDetail.budgetNegotiable')
	}
	if (d.budgetMin && d.budgetMax) {
		return `¥${price(d.budgetMin)} - ¥${price(d.budgetMax)}`
	}
	if (d.budgetMax) {
		return t('demandDetail.budgetMax', {amount: `¥${price(d.budgetMax)}`})
	}
	if (d.budgetMin) {
		return t('demandDetail.budgetMin', {amount: `¥${price(d.budgetMin)}`})
	}
	return t('demandDetail.budgetNegotiable')
})

async function acceptQuote(quote: any) {
	if (!confirm(t('demandDetail.acceptConfirm', {amount: `¥${price(quote.amount)}`}))) {
		return
	}
	busy.value = true
	try {
		const res: any = await call(`/quote/${quote.id}/accept`, {method: 'POST'})
		if (res.code === 200) {
			await navigateTo(localePath(`/project/${res.data.project.projectNo}`))
		} else {
			alert(res.msg || t('demandDetail.failed'))
		}
	} finally {
		busy.value = false
	}
}

async function rejectQuote(quote: any) {
	const reason = prompt(t('demandDetail.rejectPrompt')) ?? ''
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
	if (!confirm(t('demandDetail.cancelConfirm'))) {
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
	const key = `demandNew.types.${type}`
	return te(key) ? t(key) : type
}

function statusText(status: string) {
	if (!status) {
		return ''
	}
	const key = `demandDetail.status.${status}`
	return te(key) ? t(key) : status
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
	const key = `demandDetail.quoteStatus.${status}`
	return te(key) ? t(key) : status
}

function projectStatusText(status: string) {
	const key = `demandDetail.projectStatus.${status}`
	return te(key) ? t(key) : status
}

function actionText(action: string) {
	const key = `demandDetail.action.${action}`
	return te(key) ? t(key) : action
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
