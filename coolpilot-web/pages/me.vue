<template>
	<div>
		<div class="head">
			<div>
				<h1 class="page-title">我的</h1>
				<p class="page-desc">订单、已购内容与个人互动</p>
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
				<div v-if="!orders.length" class="empty">还没有订单</div>
				<div v-for="order in orders" :key="order.id" class="item">
					<NuxtLink :to="`/order/${order.orderNo}`" class="item-main">
						<strong>{{ order.titleSummary || order.orderNo }}</strong>
						<span class="muted">{{ formatDate(order.createTime) }}</span>
					</NuxtLink>
					<div class="item-side">
						<span class="amount">¥{{ price(order.payAmount) }}</span>
						<span class="status">{{ statusText(order.status) }}</span>
					</div>
				</div>
			</template>

			<!-- 权益 -->
			<template v-else-if="tab === 'ent'">
				<div v-if="!entitlements.length" class="empty">还没有已购内容</div>
				<div v-for="ent in entitlements" :key="ent.id" class="item">
					<NuxtLink :to="`/knowledge/${ent.productNo}`" class="item-main">
						<strong>{{ ent.productTitle }}</strong>
						<span class="muted">
							{{ ent.expireTime ? '有效期至 ' + formatDate(ent.expireTime, 'YYYY-MM-DD') : '永久有效' }}
						</span>
					</NuxtLink>
					<div class="item-side"><span class="status">可访问</span></div>
				</div>
			</template>

			<!-- 收藏 -->
			<template v-else-if="tab === 'fav'">
				<div v-if="!favorites.length" class="empty">还没有收藏，在文章底部点「收藏」即可</div>
				<div v-for="fav in favorites" :key="fav.id" class="item">
					<NuxtLink v-if="favLink(fav)" :to="favLink(fav)" class="item-main">
						<strong>{{ fav.targetTitle || '内容已删除' }}</strong>
						<span class="muted">{{ typeText(fav.targetType) }} · 收藏于 {{ formatDate(fav.createTime) }}</span>
					</NuxtLink>
					<div v-else class="item-main">
						<strong>{{ fav.targetTitle }}</strong>
						<span class="muted">{{ typeText(fav.targetType) }} · {{ formatDate(fav.createTime) }}</span>
					</div>
					<div class="item-side">
						<button class="link-btn" @click="removeFavorite(fav)">取消收藏</button>
					</div>
				</div>
			</template>

			<!-- 消息 -->
			<template v-else-if="tab === 'msg'">
				<div v-if="!messages.length" class="empty">暂无消息</div>
				<div v-for="msg in messages" :key="msg.id" :class="['item', {unread: !msg.isRead}]"
				     @click="readMessage(msg)">
					<div class="item-main">
						<strong>{{ msg.title }}</strong>
						<span class="muted">{{ msg.content }}</span>
					</div>
					<div class="item-side">
						<span class="muted">{{ formatDate(msg.createTime) }}</span>
						<span v-if="!msg.isRead" class="dot"/>
					</div>
				</div>
			</template>

			<!-- 我的评论 -->
			<template v-else-if="tab === 'comment'">
				<div v-if="!comments.length" class="empty">还没有发表过评论</div>
				<div v-for="c in comments" :key="c.id" class="fb">
					<div class="fb-head">
						<NuxtLink v-if="c.page === 0 && c.blogId" :to="`/blog/${c.blogId}`" class="cm-link">
							{{ c.blogTitle || '文章已删除' }}
						</NuxtLink>
						<span v-else class="cm-link">关于我</span>
						<span class="muted">{{ formatDate(c.createTime) }}</span>
						<span v-if="!c.published" class="status wait">待审核</span>
					</div>
					<p class="fb-content">{{ c.content }}</p>
					<div v-if="c.parentCommentId && c.parentCommentId !== -1" class="muted">这是对某条评论的回复</div>
				</div>
			</template>

			<!-- 反馈 -->
			<template v-else-if="tab === 'feedback'">
				<div class="feedback-form">
					<textarea v-model="feedback.content" rows="4" maxlength="2000"
					          placeholder="遇到的问题、想要的功能，都可以写在这里（至少 5 个字）"/>
					<div class="form-row">
						<input v-model="feedback.contact" placeholder="联系方式（选填，方便回复你）"/>
						<button class="btn" :disabled="submitting" @click="submitFeedback">
							{{ submitting ? '提交中…' : '提交反馈' }}
						</button>
					</div>
				</div>

				<div class="sub-title">我的反馈</div>
				<div v-if="!feedbacks.length" class="empty">还没有提交过反馈</div>
				<div v-for="fb in feedbacks" :key="fb.id" class="fb">
					<div class="fb-head">
						<span class="muted">{{ formatDate(fb.createTime) }}</span>
						<span :class="['status', fb.status === 'RESOLVED' ? 'ok' : 'wait']">
							{{ fb.status === 'RESOLVED' ? '已回复' : '待处理' }}
						</span>
					</div>
					<p class="fb-content">{{ fb.content }}</p>
					<div v-if="fb.reply" class="fb-reply">
						<strong>官方回复</strong>
						<p>{{ fb.reply }}</p>
					</div>
				</div>
			</template>

			<!-- 账户设置 -->
			<template v-else>
				<div class="sub-title">基本资料</div>
				<div class="set-row">
					<label>昵称</label>
					<input v-model="profile.nickname" maxlength="20" placeholder="1-20 个字符">
				</div>
				<div class="set-row">
					<label>邮箱</label>
					<input v-model="profile.email" placeholder="用于接收通知，可留空">
				</div>
				<div class="set-row">
					<label>头像</label>
					<input v-model="profile.avatar" placeholder="/img/avatar.jpg 或图片地址">
					<input ref="avatarInput" type="file" accept="image/*" hidden @change="uploadAvatar">
					<button class="btn ghost" :disabled="uploading" @click="$refs.avatarInput.click()">
						{{ uploading ? '上传中…' : '上传' }}
					</button>
				</div>
				<div class="set-actions">
					<button class="btn" :disabled="savingProfile" @click="saveProfile">
						{{ savingProfile ? '保存中…' : '保存资料' }}
					</button>
				</div>

				<div class="sub-title">修改密码</div>
				<div class="set-row">
					<label>原密码</label>
					<input v-model="pwd.oldPassword" type="password" placeholder="请输入当前密码">
				</div>
				<div class="set-row">
					<label>新密码</label>
					<input v-model="pwd.newPassword" type="password" placeholder="6-32 位">
				</div>
				<div class="set-actions">
					<button class="btn" :disabled="savingPwd" @click="changePassword">
						{{ savingPwd ? '提交中…' : '修改密码' }}
					</button>
					<span class="muted">修改后需要用新密码重新登录</span>
				</div>
			</template>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call, isLogin, user, setSession, clearSession} = useApi()

const tabList = [
	{key: 'order', label: '订单记录'},
	{key: 'ent', label: '我的权益'},
	{key: 'fav', label: '我的收藏'},
	{key: 'msg', label: '消息通知'},
	{key: 'comment', label: '我的评论'},
	{key: 'feedback', label: '意见反馈'},
	{key: 'setting', label: '账户设置'}
] as const

const tab = ref<'order' | 'ent' | 'fav' | 'msg' | 'comment' | 'feedback' | 'setting'>('order')
const orders = ref<any[]>([])
const entitlements = ref<any[]>([])
const favorites = ref<any[]>([])
const messages = ref<any[]>([])
const feedbacks = ref<any[]>([])
const comments = ref<any[]>([])
const unread = ref(0)

const feedback = reactive({content: '', contact: ''})
const submitting = ref(false)

const profile = reactive({nickname: '', avatar: '', email: ''})
const pwd = reactive({oldPassword: '', newPassword: ''})
const savingProfile = ref(false)
const savingPwd = ref(false)

if (!isLogin.value) {
	await navigateTo('/login?redirect=/me')
} else {
	const [meRes, orderRes, entRes, favRes, msgRes, badgeRes, fbRes, cmRes]: any[] = await Promise.all([
		call('/me').catch(() => null),
		call('/orders?pageNum=1&pageSize=50').catch(() => null),
		call('/entitlements').catch(() => null),
		call('/favorites?pageNum=1&pageSize=50').catch(() => null),
		call('/messages?pageNum=1&pageSize=50').catch(() => null),
		call('/messages/unread-count').catch(() => null),
		call('/me/feedbacks?pageNum=1&pageSize=20').catch(() => null),
		call('/me/comments?pageNum=1&pageSize=50').catch(() => null)
	])
	orders.value = orderRes?.data?.list || []
	entitlements.value = entRes?.data || []
	favorites.value = favRes?.data?.list || []
	messages.value = msgRes?.data?.list || []
	unread.value = badgeRes?.data?.count || 0
	feedbacks.value = fbRes?.data?.list || []
	comments.value = cmRes?.data?.list || []
	const me = meRes?.data || user.value || {}
	profile.nickname = me.nickname || ''
	profile.avatar = me.avatar || ''
	profile.email = me.email || ''
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

function statusText(status: string) {
	return {
		PENDING_PAY: '待支付', PENDING_CONFIRM: '待确认', PAID: '已支付', COMPLETED: '已完成',
		CANCELLED: '已取消', CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款'
	}[status] || status
}

function typeText(type: string) {
	return {ARTICLE: '文章', MOMENT: '动态', PRODUCT: '商品'}[type] || type
}

/**
 * 收藏跳转：文章走 /blog/:id，动态回列表。
 * 商品收藏暂时没有精确路由（favorite 只存 id，详情页用 productNo），先不给链接。
 */
function favLink(fav: any) {
	if (fav.targetType === 'ARTICLE') return `/blog/${fav.targetId}`
	if (fav.targetType === 'MOMENT') return '/moments'
	return ''
}

async function removeFavorite(fav: any) {
	const res: any = await call(`/favorite?targetType=${fav.targetType}&targetId=${fav.targetId}`, {method: 'DELETE'})
	if (res && res.code === 200) {
		favorites.value = favorites.value.filter(f => f.id !== fav.id)
	}
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
	savingProfile.value = true
	try {
		const res: any = await call('/me/profile', {
			method: 'PUT',
			body: {
				nickname: profile.nickname.trim(),
				avatar: profile.avatar.trim(),
				email: profile.email.trim()
			}
		})
		if (res && res.code === 200) {
			// 同步顶栏展示用的 cookie，否则要刷新页面昵称才变
			user.value = {...(user.value || {}), ...res.data}
			alert('资料已保存')
		} else {
			alert((res && res.msg) || '保存失败')
		}
	} finally {
		savingProfile.value = false
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
</style>
