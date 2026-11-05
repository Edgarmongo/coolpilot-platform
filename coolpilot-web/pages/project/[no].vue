<template>
	<div v-if="project">
		<div class="card">
			<div class="head">
				<div>
					<div class="crumbs">
						<span class="tag">{{ t('projectPage.tag') }}</span>
						<span class="mono muted">{{ project.projectNo }}</span>
						<span :class="['status', statusClass(project.status)]">{{ statusText(project.status) }}</span>
					</div>
					<h1 class="page-title">{{ project.name }}</h1>
					<p class="page-desc">
						{{ t('projectPage.contract', {amount: `¥${price(project.amount)}`}) }}
						· {{ t('projectPage.start', {date: project.startDate || t('projectPage.undecided')}) }}
						<span v-if="project.endDate"> · {{ t('projectPage.end', {date: project.endDate}) }}</span>
					</p>
				</div>
				<div class="progress">
					<strong>{{ accepted }}/{{ milestones.length }}</strong>
					<span class="muted">{{ t('projectPage.progress') }}</span>
				</div>
			</div>
		</div>

		<div class="card section">
			<h2 class="sub-title">{{ t('projectPage.milestones') }}</h2>
			<div v-if="!milestones.length" class="empty">{{ t('projectPage.noMilestones') }}</div>

			<div v-for="milestone in milestones" :key="milestone.id" class="milestone">
				<div class="ms-head">
					<div>
						<strong>{{ milestone.name }}</strong>
						<span :class="['status', statusClass(milestone.status)]">{{ milestoneStatusText(milestone.status) }}</span>
					</div>
					<div class="ms-right">
						<span class="amount">¥{{ price(milestone.amount) }}</span>
						<span class="muted">{{ t('projectPage.due', {date: milestone.dueDate || t('projectPage.undecided')}) }}</span>
					</div>
				</div>

				<p v-if="milestone.remark" class="muted small">{{ milestone.remark }}</p>

				<div v-if="latest[milestone.id]" class="delivery">
					<div class="delivery-head">
						<span class="tag">{{ deliveryTypeText(latest[milestone.id].deliveryType) }}</span>
						<span class="muted small">{{ formatDate(latest[milestone.id].deliveredTime) }}</span>
						<span :class="['status', latest[milestone.id].status === 'ACCEPTED' ? 'ok' : (latest[milestone.id].status === 'REJECTED' ? 'bad' : 'wait')]">
							{{ deliveryStatusText(latest[milestone.id].status) }}
						</span>
					</div>
					<p v-if="latest[milestone.id].content" class="delivery-content">{{ latest[milestone.id].content }}</p>
					<div v-if="latest[milestone.id].fileId" class="delivery-file">
						<span>{{ latest[milestone.id].fileName || t('projectPage.deliveryFile') }}</span>
						<button class="btn ghost btn-sm" @click="download(latest[milestone.id])">{{ t('projectPage.download') }}</button>
					</div>
				</div>
				<p v-else class="muted small">{{ t('projectPage.waitingDelivery') }}</p>

				<div v-if="milestone.status === 'DELIVERED' && canAccept" class="ms-actions">
					<button class="btn" :disabled="busy" @click="accept(milestone)">{{ t('projectPage.accept') }}</button>
					<button class="btn ghost" :disabled="busy" @click="reject(milestone)">{{ t('projectPage.needChanges') }}</button>
				</div>
			</div>
		</div>

		<div class="card section">
			<h2 class="sub-title">{{ t('projectPage.timeline') }}</h2>
			<div v-if="!events.length" class="empty">{{ t('projectPage.noEvents') }}</div>
			<div v-for="event in events" :key="event.id" class="event">
				<span class="dot"/>
				<div>
					<div class="event-title">
						{{ actionText(event.action) }}
						<span v-if="event.toStatus" class="muted">
							{{ statusText(event.fromStatus) || '—' }} → {{ statusText(event.toStatus) }}
						</span>
					</div>
					<p v-if="event.remark" class="muted small">{{ event.remark }}</p>
					<p class="muted small">{{ event.operatorName || t('projectPage.system') }} · {{ formatDate(event.createTime) }}</p>
				</div>
			</div>
		</div>
	</div>
	<div v-else class="empty card">{{ t('projectPage.notFound') }}</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()
const {t, te} = useI18n()

useHead({title: t('projectPage.head')})

if (!isLogin.value) {
	await navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
}

const {data, refresh} = await useAsyncData(`project-${route.params.no}`,
	() => call(`/project/${route.params.no}`).catch(() => null))

const project = computed(() => (data.value as any)?.data?.project || null)
const milestones = computed(() => (data.value as any)?.data?.milestones || [])
const latest = computed(() => (data.value as any)?.data?.latestDeliveries || {})
const events = computed(() => (data.value as any)?.data?.events || [])

const busy = ref(false)
const accepted = computed(() => milestones.value.filter((m: any) => m.status === 'ACCEPTED').length)
const canAccept = computed(() => ['IN_PROGRESS', 'PENDING_ACCEPT'].includes(project.value?.status))

async function accept(milestone: any) {
	if (!confirm(t('projectPage.acceptConfirm', {name: milestone.name}))) {
		return
	}
	await act(`/milestone/${milestone.id}/accept`, {})
}

async function reject(milestone: any) {
	const reason = prompt(t('projectPage.rejectPrompt')) ?? ''
	if (!reason.trim()) {
		return
	}
	await act(`/milestone/${milestone.id}/reject`, {reason})
}

async function act(url: string, body: any) {
	busy.value = true
	try {
		const res: any = await call(url, {method: 'POST', body})
		if (res.code === 200) {
			await refresh()
		} else {
			alert(res.msg || '操作失败')
		}
	} finally {
		busy.value = false
	}
}

async function download(delivery: any) {
	const res: any = await call(`/file/token?fileId=${delivery.fileId}`)
	if (res.code !== 200) {
		alert(res.msg || '获取下载凭证失败')
		return
	}
	window.open(`/api/file/download?token=${encodeURIComponent(res.data.token || res.data)}`, '_blank')
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function statusText(status: string) {
	if (!status) {
		return ''
	}
	const key = `projectPage.status.${status}`
	return te(key) ? t(key) : status
}

function statusClass(status: string) {
	if (['COMPLETED', 'ACCEPTED', 'IN_PROGRESS'].includes(status)) {
		return 'ok'
	}
	if (['REJECTED', 'ARCHIVED'].includes(status)) {
		return 'bad'
	}
	return 'wait'
}

function milestoneStatusText(status: string) {
	const key = `projectPage.status.${status}`
	return te(key) ? t(key) : status
}

function deliveryTypeText(type: string) {
	const key = `projectPage.deliveryType.${type}`
	return te(key) ? t(key) : type
}

function deliveryStatusText(status: string) {
	const key = `projectPage.status.${status}`
	return te(key) ? t(key) : status
}

function actionText(action: string) {
	const key = `projectPage.action.${action}`
	if (te(key)) {
		return t(key)
	}
	return action
}
</script>

<style scoped>
.head {
	display: flex;
	justify-content: space-between;
	gap: 16px;
	flex-wrap: wrap;
}

.crumbs {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 12px;
	margin-bottom: 6px;
}

.progress {
	text-align: right;
	display: flex;
	flex-direction: column;
	gap: 2px;
}

.progress strong {
	font-size: 22px;
	color: var(--teal);
}

.section {
	margin-top: 14px;
}

.sub-title {
	margin: 0 0 10px;
	font-size: 15px;
}

.milestone {
	border: 1px solid var(--line);
	border-radius: 10px;
	padding: 12px;
	margin-bottom: 10px;
}

.ms-head {
	display: flex;
	justify-content: space-between;
	gap: 12px;
	flex-wrap: wrap;
}

.ms-head div {
	display: flex;
	align-items: center;
	gap: 8px;
}

.ms-right {
	font-size: 13px;
}

.delivery {
	margin-top: 10px;
	background: rgba(15, 118, 110, .04);
	border-radius: 8px;
	padding: 10px;
}

.delivery-head {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}

.delivery-content {
	margin: 8px 0 0;
	font-size: 13px;
	white-space: pre-wrap;
	word-break: break-all;
}

.delivery-file {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-top: 8px;
	font-size: 13px;
}

.ms-actions {
	display: flex;
	gap: 10px;
	margin-top: 10px;
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

.small {
	font-size: 12px;
}

.btn-sm {
	padding: 4px 10px;
	font-size: 12px;
}
</style>
