<template>
	<div v-if="project">
		<div class="card">
			<div class="head">
				<div>
					<div class="crumbs">
						<span class="tag">项目</span>
						<span class="mono muted">{{ project.projectNo }}</span>
						<span :class="['status', statusClass(project.status)]">{{ statusText(project.status) }}</span>
					</div>
					<h1 class="page-title">{{ project.name }}</h1>
					<p class="page-desc">
						合同金额 ¥{{ price(project.amount) }}
						· 开始 {{ project.startDate || '未定' }}
						<span v-if="project.endDate"> · 结束 {{ project.endDate }}</span>
					</p>
				</div>
				<div class="progress">
					<strong>{{ accepted }}/{{ milestones.length }}</strong>
					<span class="muted">里程碑已验收</span>
				</div>
			</div>
		</div>

		<div class="card section">
			<h2 class="sub-title">里程碑</h2>
			<div v-if="!milestones.length" class="empty">还没有里程碑，平台会尽快补充</div>

			<div v-for="milestone in milestones" :key="milestone.id" class="milestone">
				<div class="ms-head">
					<div>
						<strong>{{ milestone.name }}</strong>
						<span :class="['status', statusClass(milestone.status)]">{{ milestoneStatusText(milestone.status) }}</span>
					</div>
					<div class="ms-right">
						<span class="amount">¥{{ price(milestone.amount) }}</span>
						<span class="muted">截止 {{ milestone.dueDate || '未定' }}</span>
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
						<span>{{ latest[milestone.id].fileName || '交付文件' }}</span>
						<button class="btn ghost btn-sm" @click="download(latest[milestone.id])">下载</button>
					</div>
				</div>
				<p v-else class="muted small">等待交付</p>

				<div v-if="milestone.status === 'DELIVERED' && canAccept" class="ms-actions">
					<button class="btn" :disabled="busy" @click="accept(milestone)">验收通过</button>
					<button class="btn ghost" :disabled="busy" @click="reject(milestone)">需要修改</button>
				</div>
			</div>
		</div>

		<div class="card section">
			<h2 class="sub-title">进度记录</h2>
			<div v-if="!events.length" class="empty">暂无记录</div>
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
					<p class="muted small">{{ event.operatorName || '系统' }} · {{ formatDate(event.createTime) }}</p>
				</div>
			</div>
		</div>
	</div>
	<div v-else class="empty card">项目不存在或无权查看</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()

useHead({title: '项目进度 - CoolSkyAI'})

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
	if (!confirm(`确认验收「${milestone.name}」？`)) {
		return
	}
	await act(`/milestone/${milestone.id}/accept`, {})
}

async function reject(milestone: any) {
	const reason = prompt('请说明需要修改的地方') ?? ''
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
	return {
		NOT_STARTED: '未开始', IN_PROGRESS: '实施中', PENDING_ACCEPT: '待验收', COMPLETED: '已完成', ARCHIVED: '已归档',
		PENDING: '待交付', DELIVERING: '交付中', DELIVERED: '待验收', ACCEPTED: '已验收', REJECTED: '已打回'
	}[status] || status || ''
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
	return {PENDING: '待交付', DELIVERING: '交付中', DELIVERED: '待验收', ACCEPTED: '已验收', REJECTED: '已打回'}[status] || status
}

function deliveryTypeText(type: string) {
	return {FILE: '文件', LINK: '链接', TEXT: '说明'}[type] || type
}

function deliveryStatusText(status: string) {
	return {DELIVERED: '待验收', ACCEPTED: '已验收', REJECTED: '已打回'}[status] || status
}

function actionText(action: string) {
	return {
		CREATE: '项目创建', DELIVER: '提交交付', COMPLETE: '项目完成', MOVE: '状态调整',
		ADD_MILESTONE: '新增里程碑', UPDATE: '里程碑调整', ACCEPT: '验收通过', REJECT: '验收打回'
	}[action] || action
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
