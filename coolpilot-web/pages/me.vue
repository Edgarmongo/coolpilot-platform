<template>
	<div>
		<div class="head">
			<div>
				<h1 class="page-title">{{ t('me.title') }}</h1>
				<p class="page-desc">{{ t('me.desc') }}</p>
			</div>
		</div>

		<div class="tabs">
			<button v-for="item in tabList" :key="item.key"
			        :class="['tab', {active: tab === item.key}]" @click="tab = item.key">
				{{ item.label }}
				<span v-if="item.key === 'msg' && unread > 0" class="badge">{{ unread }}</span>
			</button>
		</div>

		<div class="card">
			<!-- 订单 -->
			<template v-if="tab === 'order'">
				<div v-if="!orders.length" class="empty">{{ t('me.emptyOrders') }}</div>
				<div v-for="order in orders" :key="order.id" class="item">
					<NuxtLink :to="localePath(`/order/${order.orderNo}`)" class="item-main">
						<strong>{{ order.titleSummary || order.orderNo }}</strong>
						<span class="muted">{{ formatDate(order.createTime) }}</span>
					</NuxtLink>
					<div class="item-side">
						<span class="amount">¥{{ price(order.payAmount) }}</span>
						<span class="status">{{ statusText(order.status) }}</span>
					</div>
				</div>
			</template>

			<!-- 我的需求 -->
			<template v-else-if="tab === 'demand'">
				<div v-if="!demands.length" class="empty">
					{{ t('me.emptyDemands') }}<NuxtLink :to="localePath('/service')" class="link">{{ t('me.browseServices') }}</NuxtLink>
				</div>
				<div v-for="item in demands" :key="item.id" class="item">
					<NuxtLink :to="localePath(`/demand/${item.demandNo}`)" class="item-main">
						<strong>{{ item.title }}</strong>
						<span class="muted">{{ item.demandNo }} · {{ formatDate(item.createTime) }}</span>
					</NuxtLink>
					<div class="item-side">
						<span class="status">{{ demandStatusText(item.status) }}</span>
					</div>
				</div>
			</template>

			<!-- 我的项目 -->
			<template v-else-if="tab === 'project'">
				<div v-if="!projects.length" class="empty">{{ t('me.emptyProjects') }}</div>
				<div v-for="item in projects" :key="item.id" class="item">
					<NuxtLink :to="localePath(`/project/${item.projectNo}`)" class="item-main">
						<strong>{{ item.name }}</strong>
						<span class="muted">
							{{ item.projectNo }} · {{ item.acceptedCount || 0 }}/{{ item.milestoneCount || 0 }} {{ t('projectPage.progress') }}
						</span>
					</NuxtLink>
					<div class="item-side">
						<span class="amount">¥{{ price(item.amount) }}</span>
						<span class="status">{{ projectStatusText(item.status) }}</span>
					</div>
				</div>
			</template>

			<!-- 权益 -->
			<template v-else-if="tab === 'ent'">
				<div v-if="!entitlements.length" class="empty">{{ t('me.emptyEnt') }}</div>
				<div v-for="ent in entitlements" :key="ent.id" class="item">
					<NuxtLink :to="localePath(`/knowledge/${ent.productNo}`)" class="item-main">
						<strong>{{ ent.productTitle }}</strong>
						<span class="muted">
							{{ ent.expireTime ? t('me.validUntil', {date: formatDate(ent.expireTime, 'YYYY-MM-DD')}) : t('me.permanent') }}
						</span>
					</NuxtLink>
					<div class="item-side"><span class="status">{{ t('me.accessible') }}</span></div>
				</div>
			</template>

			<!-- 收藏 -->
			<template v-else-if="tab === 'fav'">
				<div v-if="!favorites.length" class="empty">{{ t('me.emptyFav') }}</div>
				<div v-for="fav in favorites" :key="fav.id" class="item">
					<NuxtLink v-if="favLink(fav)" :to="localePath(favLink(fav))" class="item-main">
						<strong>{{ fav.targetTitle || t('me.contentDeleted') }}</strong>
						<span class="muted">{{ typeText(fav.targetType) }} · {{ t('me.savedAt', {date: formatDate(fav.createTime)}) }}</span>
					</NuxtLink>
					<div v-else class="item-main">
						<strong>{{ fav.targetTitle }}</strong>
						<span class="muted">{{ typeText(fav.targetType) }} · {{ formatDate(fav.createTime) }}</span>
					</div>
					<div class="item-side">
						<button class="link-btn" @click="removeFavorite(fav)">{{ t('me.unfavorite') }}</button>
					</div>
				</div>
			</template>

			<!-- 消息 -->
			<template v-else-if="tab === 'msg'">
				<div v-if="!messages.length" class="empty">{{ t('me.emptyMsg') }}</div>
				<div v-for="msg in messages" :key="msg.id" :class="['item', {unread: !msg.isRead}]"
				     @click="readMessage(msg)">
					<div class="item-main">
						<strong>
							<span class="msg-type">{{ msgTypeText(msg.msgType) }}</span>
							{{ msg.title }}
						</strong>
						<span class="muted">{{ msg.content }}</span>
					</div>
					<div class="item-side">
						<span class="muted">{{ formatDate(msg.createTime) }}</span>
						<span v-if="msg.linkUrl" class="muted go">{{ t('me.view') }}</span>
						<span v-if="!msg.isRead" class="dot"/>
					</div>
				</div>
			</template>

			<!-- 我的留言（分享页讨论） -->
			<template v-else-if="tab === 'talk'">
				<div v-if="!talks.length" class="empty">{{ t('me.emptyTalk') }}</div>
				<div v-for="item in talks" :key="item.id" class="fb">
					<div class="fb-head">
						<NuxtLink v-if="shareOpen(item)" :to="localePath(`/share/${item.shareToken}#discuss`)" class="cm-link">
							{{ item.question || t('me.sharedAnswer') }}
						</NuxtLink>
						<span v-else class="cm-link closed">{{ item.question || t('me.sharedAnswer') }}</span>
						<span class="muted">{{ formatDate(item.createTime) }}</span>
						<span v-if="!shareOpen(item)" class="status wait">{{ t('me.shareClosed') }}</span>
					</div>
					<p class="fb-content">{{ item.content }}</p>
					<div v-if="item.parentCommentId && item.parentCommentId !== -1" class="muted">{{ t('me.replyToTalk') }}</div>
				</div>
				<div v-if="talks.length < talkTotal" class="more-row">
					<button class="btn ghost" :disabled="loadingTalks" @click="loadMoreTalks">
						{{ loadingTalks ? t('common.loading') : t('me.loadMore', {n: talkTotal - talks.length}) }}
					</button>
				</div>
			</template>

			<!-- 我的评论 -->
			<template v-else-if="tab === 'comment'">
				<div v-if="!comments.length" class="empty">{{ t('me.emptyComment') }}</div>
				<div v-for="c in comments" :key="c.id" class="fb">
					<div class="fb-head">
						<NuxtLink v-if="c.page === 0 && c.blogId" :to="localePath(`/blog/${c.blogId}`)" class="cm-link">
							{{ c.blogTitle || t('me.postDeleted') }}
						</NuxtLink>
						<span v-else class="cm-link">{{ t('me.aboutPage') }}</span>
						<span class="muted">{{ formatDate(c.createTime) }}</span>
						<span v-if="!c.published" class="status wait">{{ t('me.pendingReview') }}</span>
					</div>
					<p class="fb-content">{{ c.content }}</p>
					<div v-if="c.parentCommentId && c.parentCommentId !== -1" class="muted">{{ t('me.replyToComment') }}</div>
				</div>
			</template>

			<!-- 反馈 -->
			<template v-else-if="tab === 'feedback'">
				<div class="feedback-form">
					<textarea v-model="feedback.content" rows="4" maxlength="2000"
					          :placeholder="t('me.feedbackPlaceholder')"/>
					<div class="form-row">
						<input v-model="feedback.contact" :placeholder="t('me.contactPlaceholder')"/>
						<button class="btn" :disabled="submitting" @click="submitFeedback">
							{{ submitting ? t('me.submitting') : t('me.submitFeedback') }}
						</button>
					</div>
				</div>

				<div class="sub-title">{{ t('me.myFeedback') }}</div>
				<div v-if="!feedbacks.length" class="empty">{{ t('me.emptyFeedback') }}</div>
				<div v-for="fb in feedbacks" :key="fb.id" class="fb">
					<div class="fb-head">
						<span class="muted">{{ formatDate(fb.createTime) }}</span>
						<span :class="['status', fb.status === 'RESOLVED' ? 'ok' : 'wait']">
							{{ fb.status === 'RESOLVED' ? t('me.resolved') : t('me.pending') }}
						</span>
					</div>
					<p class="fb-content">{{ fb.content }}</p>
					<div v-if="fb.reply" class="fb-reply">
						<strong>{{ t('me.officialReply') }}</strong>
						<p>{{ fb.reply }}</p>
					</div>
				</div>
			</template>

			<!-- 账户设置 -->
			<template v-else>
				<div class="sub-title">{{ t('me.profile') }}</div>
				<div class="set-row">
					<label>{{ t('me.nickname') }}</label>
					<input v-model="profile.nickname" maxlength="20" :placeholder="t('me.nicknamePlaceholder')">
				</div>
				<div class="set-row">
					<label>{{ t('me.email') }}</label>
					<input v-model="profile.email" :placeholder="t('me.emailPlaceholder')">
					<button v-if="emailChanged" class="btn ghost" :disabled="sendingCode" @click="sendEmailCode">
						{{ sendingCode ? t('me.sendingCode') : t('me.sendCode') }}
					</button>
				</div>
				<div v-if="emailChanged" class="set-row">
					<label>{{ t('me.emailCode') }}</label>
					<input v-model="emailCode" maxlength="6" inputmode="numeric"
					       :placeholder="t('me.emailCodePlaceholder')">
				</div>
				<div class="set-row">
					<label>{{ t('me.avatar') }}</label>
					<input v-model="profile.avatar" :placeholder="t('me.avatarPlaceholder')">
					<input ref="avatarInput" type="file" accept="image/*" hidden @change="uploadAvatar">
					<button class="btn ghost" :disabled="uploading" @click="$refs.avatarInput.click()">
						{{ uploading ? t('me.uploading') : t('me.upload') }}
					</button>
				</div>
				<div class="set-actions">
					<button class="btn" :disabled="savingProfile" @click="saveProfile">
						{{ savingProfile ? t('me.saving') : t('me.saveProfile') }}
					</button>
				</div>

				<div class="sub-title">{{ t('me.changePassword') }}</div>
				<div class="set-row">
					<label>{{ t('me.oldPassword') }}</label>
					<input v-model="pwd.oldPassword" type="password" :placeholder="t('me.oldPwdPlaceholder')">
				</div>
				<div class="set-row">
					<label>{{ t('me.newPassword') }}</label>
					<input v-model="pwd.newPassword" type="password" :placeholder="t('me.newPwdPlaceholder')">
				</div>
				<div class="set-actions">
					<button class="btn" :disabled="savingPwd" @click="changePassword">
						{{ savingPwd ? t('me.submitting') : t('me.changePassword') }}
					</button>
					<span class="muted">{{ t('me.pwdHint') }}</span>
				</div>
			</template>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call, isLogin, user, setSession, clearSession} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const TAB_KEYS = ['order', 'demand', 'project', 'ent', 'fav', 'msg', 'comment', 'talk', 'feedback', 'setting'] as const
type TabKey = typeof TAB_KEYS[number]

// 标签名跟语言走；key 保持不变，?tab=ent 这类深链仍然有效
const tabList = computed(() => TAB_KEYS.map(key => ({key, label: t(`me.tabs.${key}`)})))

const route = useRoute()
// 通知里的链接形如 /me?tab=ent，落地时直接打开对应标签
const initialTab = (): TabKey => {
	const key = String(route.query.tab || '')
	return (TAB_KEYS as readonly string[]).includes(key) ? key as TabKey : 'order'
}

const tab = ref<TabKey>(initialTab())
const orders = ref<any[]>([])
const demands = ref<any[]>([])
const projects = ref<any[]>([])
const entitlements = ref<any[]>([])
const favorites = ref<any[]>([])
const messages = ref<any[]>([])
const feedbacks = ref<any[]>([])
const comments = ref<any[]>([])
/** 分享页的留言：分页加载，避免留言多了首屏拖一长条 */
const TALK_PAGE_SIZE = 20
const talks = ref<any[]>([])
const talkTotal = ref(0)
const talkPage = ref(1)
const loadingTalks = ref(false)
const unread = ref(0)

const feedback = reactive({content: '', contact: ''})
const submitting = ref(false)

const profile = reactive({nickname: '', avatar: '', email: ''})
const pwd = reactive({oldPassword: '', newPassword: ''})
const savingProfile = ref(false)
const savingPwd = ref(false)
// 改邮箱需要验证码：记下原邮箱判断有没有改动，改动后才显示验证码输入
const originalEmail = ref('')
const emailCode = ref('')
const sendingCode = ref(false)

const emailChanged = computed(() => {
	const now = (profile.email || '').trim().toLowerCase()
	const original = (originalEmail.value || '').trim().toLowerCase()
	return now !== '' && now !== original
})

if (!isLogin.value) {
	await navigateTo('/login?redirect=/me')
} else {
	const [meRes, orderRes, demandRes, projectRes, entRes, favRes, msgRes, badgeRes, fbRes, cmRes, talkRes]: any[] = await Promise.all([
		call('/me').catch(() => null),
		call('/orders?pageNum=1&pageSize=50').catch(() => null),
		call('/demands?pageNum=1&pageSize=50').catch(() => null),
		call('/projects?pageNum=1&pageSize=50').catch(() => null),
		call('/entitlements').catch(() => null),
		call('/favorites?pageNum=1&pageSize=50').catch(() => null),
		call('/messages?pageNum=1&pageSize=50').catch(() => null),
		call('/messages/unread-count').catch(() => null),
		call('/me/feedbacks?pageNum=1&pageSize=20').catch(() => null),
		call('/me/comments?pageNum=1&pageSize=50').catch(() => null),
		call(`/me/share-comments?pageNum=1&pageSize=${TALK_PAGE_SIZE}`).catch(() => null)
	])
	orders.value = orderRes?.data?.list || []
	demands.value = demandRes?.data?.list || []
	projects.value = projectRes?.data?.list || []
	entitlements.value = entRes?.data || []
	favorites.value = favRes?.data?.list || []
	messages.value = msgRes?.data?.list || []
	unread.value = badgeRes?.data?.count || 0
	feedbacks.value = fbRes?.data?.list || []
	comments.value = cmRes?.data?.list || []
	talks.value = talkRes?.data?.list || []
	talkTotal.value = Number(talkRes?.data?.total || 0)
	const me = meRes?.data || user.value || {}
	profile.nickname = me.nickname || ''
	profile.avatar = me.avatar || ''
	profile.email = me.email || ''
	// 记下原邮箱：改邮箱必须走验证码，这里用它判断有没有改动
	originalEmail.value = me.email || ''
}

/**
 * 加载更多留言
 *
 * 发新留言会让分页整体后移，所以按 id 去重，避免同一页里出现重复项
 */
async function loadMoreTalks() {
	if (loadingTalks.value || talks.value.length >= talkTotal.value) {
		return
	}
	loadingTalks.value = true
	try {
		const res: any = await call(`/me/share-comments?pageNum=${talkPage.value + 1}&pageSize=${TALK_PAGE_SIZE}`)
		if (res && res.code === 200) {
			const seen = new Set(talks.value.map((item: any) => item.id))
			const fresh = (res.data?.list || []).filter((item: any) => !seen.has(item.id))
			talks.value = talks.value.concat(fresh)
			talkTotal.value = Number(res.data?.total || talkTotal.value)
			talkPage.value += 1
		}
	} finally {
		loadingTalks.value = false
	}
}

/** 分享还在有效期内才能点回原页面；撤销/过期后只展示内容 */
function shareOpen(item: any) {
	if (!item.shareToken) {
		return false
	}
	if (!item.shareExpiresAt) {
		return true
	}
	return new Date(item.shareExpiresAt).getTime() > Date.now()
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function statusText(status: string) {
	const key = `order.status.${status}`
	return te(key) ? t(key) : status
}

function demandStatusText(status: string) {
	const key = `demandDetail.status.${status}`
	return te(key) ? t(key) : status
}

function projectStatusText(status: string) {
	const key = `projectPage.status.${status}`
	return te(key) ? t(key) : status
}

function typeText(type: string) {
	const key = `me.favTypes.${type}`
	return te(key) ? t(key) : type
}

/**
 * 收藏跳转：文章走 /blog/:id，动态回列表。
 * 商品收藏暂时没有精确路由（favorite 只存 id，详情页用 productNo），先不给链接。
 */
function favLink(fav: any) {
	if (fav.targetType === 'ARTICLE') return `/blog/${fav.targetId}`
	if (fav.targetType === 'MOMENT') return '/moments'
	// 问答收藏：跳到对应会话，targetRef 是会话 id
	if (fav.targetType === 'AI_ANSWER' && fav.targetRef) return `/ask?conversation=${fav.targetRef}`
	return ''
}

async function removeFavorite(fav: any) {
	const res: any = await call(`/favorite?targetType=${fav.targetType}&targetId=${fav.targetId}`, {method: 'DELETE'})
	if (res && res.code === 200) {
		favorites.value = favorites.value.filter(f => f.id !== fav.id)
	}
}

function msgTypeText(type: string) {
	const key = `me.msgTypes.${type}`
	return te(key) ? t(key) : t('me.notification')
}

async function readMessage(msg: any) {
	if (msg.isRead) {
		if (msg.linkUrl) await navigateTo(msg.linkUrl)
		return
	}
	const res: any = await call(`/message/${msg.id}/read`, {method: 'PUT'})
	if (res && res.code === 200) {
		msg.isRead = true
		unread.value = Math.max(0, unread.value - 1)
	}
	if (msg.linkUrl) await navigateTo(msg.linkUrl)
}

async function submitFeedback() {
	if (feedback.content.trim().length < 5) {
		alert('反馈内容至少 5 个字')
		return
	}
	submitting.value = true
	try {
		const res: any = await call('/feedback', {
			method: 'POST',
			params: {content: feedback.content.trim(), contact: feedback.contact.trim()}
		})
		if (res && res.code === 200) {
			feedback.content = ''
			feedback.contact = ''
			const list: any = await call('/me/feedbacks?pageNum=1&pageSize=20').catch(() => null)
			feedbacks.value = list?.data?.list || []
			alert('已提交，处理后会在这里显示回复')
		} else {
			alert((res && res.msg) || '提交失败')
		}
	} finally {
		submitting.value = false
	}
}

// ---------------- 账户设置 ----------------
const uploading = ref(false)

async function saveProfile() {
	if (!profile.nickname.trim()) {
		alert('昵称不能为空')
		return
	}
	if (emailChanged.value && !/^\d{6}$/.test(emailCode.value)) {
		alert(t('me.needEmailCode'))
		return
	}
	savingProfile.value = true
	try {
		const res: any = await call('/me/profile', {
			method: 'PUT',
			body: {
				nickname: profile.nickname.trim(),
				avatar: profile.avatar.trim(),
				email: profile.email.trim(),
				// 邮箱没改就不传，避免后端要求一个用户根本没申请的验证码
				code: emailChanged.value ? emailCode.value : undefined
			}
		})
		if (res && res.code === 200) {
			// 同步顶栏展示用的 cookie，否则要刷新页面昵称才变
			user.value = {...(user.value || {}), ...res.data}
			originalEmail.value = profile.email.trim()
			emailCode.value = ''
			alert('资料已保存')
		} else {
			alert((res && res.msg) || '保存失败')
		}
	} finally {
		savingProfile.value = false
	}
}

/**
 * 改邮箱前先发验证码到新邮箱。
 * 邮箱是找回密码的唯一凭证，填错就意味着永久失去找回能力。
 */
async function sendEmailCode() {
	if (!emailChanged.value) {
		return
	}
	sendingCode.value = true
	try {
		const res: any = await call('/me/email/code', {
			method: 'POST',
			body: {email: profile.email.trim()}
		})
		if (res && res.code === 200) {
			alert(t('me.codeSent'))
		} else {
			alert((res && res.msg) || t('me.codeFailed'))
		}
	} finally {
		sendingCode.value = false
	}
}

async function uploadAvatar(event: any) {
	const file = event.target.files && event.target.files[0]
	if (!file) {
		return
	}
	uploading.value = true
	try {
		const fd = new FormData()
		fd.append('file', file)
		fd.append('bizType', 'AVATAR')
		fd.append('isPublic', 'true')
		const res: any = await call('/file/upload', {method: 'POST', body: fd})
		if (res && res.code === 200) {
			profile.avatar = res.data.url
		} else {
			alert((res && res.msg) || '上传失败')
		}
	} finally {
		uploading.value = false
		event.target.value = ''
	}
}

async function changePassword() {
	if (!pwd.oldPassword || !pwd.newPassword) {
		alert('请填写原密码与新密码')
		return
	}
	if (pwd.newPassword.length < 6 || pwd.newPassword.length > 32) {
		alert('新密码需为 6-32 位')
		return
	}
	savingPwd.value = true
	try {
		const res: any = await call('/me/password', {
			method: 'PUT',
			body: {oldPassword: pwd.oldPassword, newPassword: pwd.newPassword}
		})
		if (res && res.code === 200) {
			pwd.oldPassword = ''
			pwd.newPassword = ''
			clearSession()
			alert('密码已修改，请用新密码重新登录')
			await navigateTo('/login')
		} else {
			alert((res && res.msg) || '修改失败')
		}
	} finally {
		savingPwd.value = false
	}
}
</script>

<style scoped>
.tabs {
	display: flex;
	gap: 8px;
	margin-bottom: 12px;
	flex-wrap: wrap;
}

.tab {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 6px 16px;
	border: 1px solid var(--line);
	border-radius: 8px;
	background: #fff;
	font-size: 13.5px;
	cursor: pointer;
	color: var(--muted);
}

.tab.active {
	border-color: var(--teal);
	color: var(--teal);
	font-weight: 600;
	background: #eef7f5;
}

.badge {
	min-width: 17px;
	height: 17px;
	padding: 0 5px;
	border-radius: 999px;
	background: #d97706;
	color: #fff;
	font-size: 11px;
	line-height: 17px;
	text-align: center;
}

.item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	padding: 12px 0;
	border-bottom: 1px dashed var(--line);
}

.item:last-child {
	border-bottom: none;
}

.item.unread {
	cursor: pointer;
}

.item-main {
	display: flex;
	flex-direction: column;
	gap: 4px;
	flex: 1;
	min-width: 0;
}

.item-main strong {
	font-size: 14px;
	font-weight: 600;
}

.item-side {
	display: flex;
	align-items: center;
	gap: 12px;
}

.amount {
	color: #b45309;
	font-weight: 600;
}

.status {
	padding: 2px 9px;
	border-radius: 999px;
	background: #eef7f5;
	color: var(--teal);
	font-size: 12px;
}

.status.wait {
	background: #fef3c7;
	color: #b45309;
}

.status.ok {
	background: #eef7f5;
	color: var(--teal);
}

.dot {
	width: 7px;
	height: 7px;
	border-radius: 50%;
	background: #d97706;
}

.link-btn {
	border: none;
	background: none;
	color: #9ca3af;
	font-size: 12.5px;
	cursor: pointer;
}

.link-btn:hover {
	color: #dc2626;
}

.feedback-form textarea {
	width: 100%;
	padding: 10px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 14px;
	font-family: inherit;
	resize: vertical;
	outline-color: var(--teal);
}

.form-row {
	display: flex;
	gap: 10px;
	margin-top: 10px;
}

.form-row input {
	flex: 1;
	padding: 9px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 13.5px;
	outline-color: var(--teal);
}

.btn {
	padding: 9px 20px;
	border: none;
	border-radius: 8px;
	background: var(--teal);
	color: #fff;
	font-size: 13.5px;
	cursor: pointer;
}

.btn:disabled {
	opacity: .6;
	cursor: default;
}

.sub-title {
	margin: 22px 0 10px;
	font-size: 13px;
	font-weight: 600;
	color: #606266;
}

.fb {
	padding: 12px 0;
	border-bottom: 1px dashed var(--line);
}

.fb:last-child {
	border-bottom: none;
}

.fb-head {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 6px;
}

.fb-content {
	margin: 0;
	line-height: 1.7;
	font-size: 14px;
	white-space: pre-wrap;
}

.fb-reply {
	margin-top: 8px;
	padding: 10px 12px;
	border-radius: 8px;
	background: #f6f8f9;
	font-size: 13px;
}

.fb-reply strong {
	color: var(--teal);
	font-size: 12.5px;
}

.fb-reply p {
	margin: 4px 0 0;
	line-height: 1.7;
	white-space: pre-wrap;
}

.cm-link {
	font-size: 13px;
	font-weight: 600;
	color: var(--teal);
	text-decoration: none;
}

.set-row {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-bottom: 12px;
}

.set-row label {
	width: 62px;
	flex: none;
	font-size: 13px;
	color: #606266;
}

.set-row input {
	flex: 1;
	padding: 9px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 13.5px;
	outline-color: var(--teal);
}

.set-actions {
	display: flex;
	align-items: center;
	gap: 12px;
	margin: 4px 0 8px 72px;
}

.btn.ghost {
	background: #fff;
	color: var(--teal);
	border: 1px solid var(--teal);
	padding: 9px 14px;
}

.msg-type {
	display: inline-block;
	padding: 0 6px;
	margin-right: 6px;
	border-radius: 4px;
	background: #eef7f5;
	color: var(--teal);
	font-size: 11.5px;
	font-weight: 500;
	vertical-align: 1px;
}

.go {
	white-space: nowrap;
}

.cm-link.closed {
	color: var(--muted);
	font-weight: 500;
	cursor: default;
}

.more-row {
	display: flex;
	justify-content: center;
	margin-top: 12px;
}
</style>
