<template>
	<div class="ask">
		<div class="head">
			<h1 class="page-title">智能问答</h1>
			<p class="page-desc">基于站内文章、商品与课时内容回答，并给出可点击的来源</p>
		</div>

		<div class="body">
			<!-- 历史会话：仅登录可见 -->
			<aside v-if="isLogin" class="history">
				<button class="new-btn" @click="newChat">+ 新对话</button>
				<div v-if="!history.length" class="muted empty-his">还没有历史对话</div>
				<div v-for="item in history" :key="item.id"
				     :class="['his-item', {active: item.id === conversationId}]">
					<button class="his-main" @click="openConversation(item.id)">
						<span class="his-title">{{ item.title || '未命名对话' }}</span>
						<span class="muted his-time">{{ formatDate(item.updateTime, 'MM-DD HH:mm') }}</span>
					</button>
					<button class="his-del" title="删除" @click.stop="removeConversation(item.id)">×</button>
				</div>
			</aside>

			<section class="chat">
				<div ref="listRef" class="messages">
					<div v-if="!messages.length" class="intro">
						<p>可以直接问，例如：</p>
						<button v-for="sample in samples" :key="sample" class="sample" @click="askSample(sample)">
							{{ sample }}
						</button>
						<p class="muted small" v-if="!isLogin">未登录也能提问，登录后可保存对话记录</p>
					</div>

					<div v-for="(msg, index) in messages" :key="index" :class="['msg', msg.role.toLowerCase()]">
						<div class="bubble">
							<p v-if="msg.role === 'ASSISTANT' && msg.degraded" class="degraded">
								{{ msg.note || '当前未生成回答，先看下面的检索片段' }}
							</p>
							<p v-if="msg.stopped" class="stopped-note">已停止生成</p>
							<p v-if="msg.streaming && !msg.content" class="thinking">正在检索并组织回答…</p>
							<p v-else class="text" @click="onAnswerClick($event, msg)">
								<span v-html="renderAnswer(msg.content, msg.citations, msg.uid)"/>
								<span v-if="msg.streaming" class="caret"/>
							</p>
							<div v-if="msg.citations && msg.citations.length" class="citations">
								<div class="cite-title">来源</div>
								<a v-for="cite in msg.citations" :key="cite.index"
								   :id="`cite-${msg.uid}-${cite.index}`"
								   :class="['cite', {active: isHighlighted(msg.uid, cite.index)}]"
								   :href="cite.url" target="_blank" rel="noopener noreferrer">
									<span class="cite-idx">[{{ cite.index }}]</span>
									<span class="cite-name">{{ cite.title }}</span>
									<span class="tag">{{ sourceText(cite.sourceType) }}</span>
								</a>
							</div>
							<div v-if="msg.role === 'ASSISTANT' && !msg.streaming && msg.content" class="msg-actions">
								<button v-if="msg.id" :class="['mini', {on: msg.favorited}]" @click="toggleFavorite(msg)">
									{{ msg.favorited ? '已收藏' : '收藏' }}
								</button>
								<button v-if="msg.id" class="mini" @click="shareAnswer(msg)">分享</button>
								<button class="mini" @click="copyAnswer(msg)">复制</button>
							</div>
						</div>
					</div>

				</div>

				<div class="composer">
					<textarea v-model="question" rows="2" maxlength="500"
					          placeholder="问问站内的内容，Enter 发送，Shift + Enter 换行"
					          @keydown.enter.exact.prevent="send"/>
					<button v-if="loading" class="btn stop" @click="stop">停止</button>
					<button v-else class="btn send" :disabled="!question.trim()" @click="send">发送</button>
				</div>
				<p class="muted foot">
					回答由模型基于站内资料生成，可能有偏差；重要信息请点开来源核对。
				</p>
			</section>
		</div>

		<!-- 分享弹窗 -->
		<div v-if="shareVisible" class="mask" @click.self="shareVisible = false">
			<div class="dialog">
				<h3>分享这条回答</h3>
				<p class="muted small">拿到链接的人无需登录即可查看问答与来源，对话里的其他内容不会暴露</p>
				<input class="share-input" :value="shareLink" readonly @focus="($event.target as HTMLInputElement).select()"/>
				<div class="day-row">
					<span class="day-label">有效期</span>
					<div class="days">
						<button v-for="opt in shareDayOptions" :key="opt.value"
						        :class="['day', {on: shareDays === opt.value}]"
						        :disabled="shareChanging"
						        @click="changeShareDays(opt.value)">{{ opt.label }}</button>
					</div>
				</div>
				<p class="muted small expiry">{{ shareExpiryText }}</p>
				<div class="dialog-actions">
					<button class="btn" @click="copyShareLink">复制链接</button>
					<button class="btn ghost" @click="revokeShare">取消分享</button>
					<button class="btn ghost" @click="shareVisible = false">关闭</button>
				</div>
			</div>
		</div>

		<div v-if="hint" class="toast">{{ hint }}</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, token, isLogin} = useApi()

type Citation = {index: number, title: string, url: string, sourceType: string, snippet?: string}
type ChatMessage = {
	role: 'USER' | 'ASSISTANT'
	content: string
	citations?: Citation[]
	degraded?: boolean
	note?: string
	streaming?: boolean
	stopped?: boolean
	/** 服务端消息 id，收藏/分享依赖它 */
	id?: number
	favorited?: boolean
	shareToken?: string
	/** 本地唯一标识，用于引用卡片的锚点（历史消息用 id，新消息用序号） */
	uid: number
}

const messages = ref<ChatMessage[]>([])
const question = ref(String(route.query.q || ''))
const loading = ref(false)
const conversationId = ref<number | null>(null)
const history = ref<any[]>([])
const shareVisible = ref(false)
const shareLink = ref('')
const shareMessage = ref<ChatMessage | null>(null)
/** 分享有效期：默认 30 天，0 表示长期有效 */
const shareDays = ref(30)
const shareExpiresAt = ref<string | null>(null)
const shareChanging = ref(false)
const shareDayOptions = [
	{label: '7 天', value: 7},
	{label: '30 天', value: 30},
	{label: '90 天', value: 90},
	{label: '长期有效', value: 0}
]
const hint = ref('')
const listRef = ref<HTMLElement | null>(null)
/** 当前流式请求的中止句柄，点「停止」时用它掐断连接 */
let currentAbort: AbortController | null = null
let uidSeed = 1
const highlighted = ref('')

const samples = [
	'站内的付费内容都讲什么？',
	'这个站点支持哪些支付方式？',
	'技术服务的流程是怎样的？'
]

onMounted(() => {
	if (isLogin.value) {
		loadHistory()
	}
	// 从「我的收藏」点进来会带会话 id，直接打开那次对话
	const conv = Number(route.query.conversation || 0)
	if (conv > 0) {
		openConversation(conv)
	}
})

async function loadHistory() {
	const res: any = await call('/ai/conversations?pageNum=1&pageSize=20').catch(() => null)
	history.value = (res && res.code === 200 && res.data && res.data.list) ? res.data.list : []
}

async function send() {
	const text = question.value.trim()
	if (!text || loading.value) {
		return
	}
	messages.value.push({role: 'USER', content: text, uid: uidSeed++})
	question.value = ''
	loading.value = true

	// 先占位再流式填充：这样第一个字出现前用户就看到「正在…」，不会以为没反应
	const assistant: ChatMessage = {role: 'ASSISTANT', content: '', citations: [], streaming: true, uid: uidSeed++}
	messages.value.push(assistant)
	scrollToBottom()

	currentAbort = new AbortController()
	try {
		await streamAnswer(text, assistant, currentAbort.signal)
	} catch (e: any) {
		if (isAbortError(e)) {
			// 用户主动停止：已经收到的内容保留，不再走降级接口
			assistant.stopped = true
		} else {
			// 流式通道不通（代理缓冲、断网、浏览器不支持流）时退回一次性接口，别让用户白问
			try {
				await fallbackAnswer(text, assistant)
			} catch (err: any) {
				assistant.content = '提问失败，请稍后再试'
			}
		}
	} finally {
		currentAbort = null
		assistant.streaming = false
		loading.value = false
		if (isLogin.value) {
			loadHistory()
		}
		scrollToBottom()
	}
}

/**
 * SSE 流式提问
 *
 * 用 fetch 而不是 EventSource：EventSource 只支持 GET，发不了请求体
 * （问题可能很长，不适合塞进 URL）。
 */
async function streamAnswer(text: string, assistant: ChatMessage, signal: AbortSignal) {
	const headers: Record<string, string> = {'Content-Type': 'application/json'}
	if (token.value) {
		headers.authorization = token.value
	}
	const res = await fetch('/api/ai/chat/stream', {
		method: 'POST',
		headers,
		signal,
		body: JSON.stringify({question: text, conversationId: conversationId.value})
	})
	if (!res.ok || !res.body) {
		throw new Error('stream unavailable')
	}

	const reader = res.body.getReader()
	const decoder = new TextDecoder()
	let buffer = ''
	let received = false

	while (true) {
		const {done, value} = await reader.read()
		if (done) {
			break
		}
		buffer += decoder.decode(value, {stream: true})
		let index = buffer.indexOf('\n\n')
		while (index >= 0) {
			const chunk = buffer.slice(0, index)
			buffer = buffer.slice(index + 2)
			const event = parseEvent(chunk)
			if (event) {
				received = true
				if (event.name === 'meta') {
					assistant.citations = event.data.citations || []
					assistant.degraded = !!event.data.degraded
					if (event.data.note) {
						assistant.note = event.data.note
					}
					if (event.data.conversationId) {
						conversationId.value = event.data.conversationId
					}
				} else if (event.name === 'delta') {
					assistant.content += event.data.text || ''
					scrollToBottom()
				} else if (event.name === 'error') {
					assistant.degraded = true
					assistant.note = event.data.message || '生成中断'
				} else if (event.name === 'done') {
					if (!assistant.content && event.data.answer) {
						assistant.content = event.data.answer
					}
					if (event.data.note) {
						assistant.note = event.data.note
						assistant.degraded = true
					}
					if (event.data.conversationId) {
						conversationId.value = event.data.conversationId
					}
					if (event.data.answerMessageId) {
						assistant.id = event.data.answerMessageId
					}
				}
			}
			index = buffer.indexOf('\n\n')
		}
	}

	if (!received) {
		throw new Error('empty stream')
	}
}

/**
 * 停止生成：掐断 SSE 连接，后端感知断连后会一并停止上游模型
 */
function stop() {
	if (currentAbort) {
		currentAbort.abort()
	}
}

function isAbortError(error: any) {
	return error && (error.name === 'AbortError' || error.code === 20)
}

/** 正文点击：把 [n] 变成「滚到对应来源卡片并高亮」，而不是跳走 */
function onAnswerClick(event: MouseEvent, msg: ChatMessage) {
	const target = (event.target as HTMLElement | null)?.closest('a[data-cite]')
	if (!target) {
		return
	}
	event.preventDefault()
	const index = Number((target as HTMLElement).dataset.cite)
	if (!index) {
		return
	}
	const card = document.getElementById(`cite-${msg.uid}-${index}`)
	if (!card) {
		return
	}
	card.scrollIntoView({behavior: 'smooth', block: 'center'})
	const key = `${msg.uid}-${index}`
	highlighted.value = key
	window.setTimeout(() => {
		if (highlighted.value === key) {
			highlighted.value = ''
		}
	}, 1600)
}

function isHighlighted(uid: number, index: number) {
	return highlighted.value === `${uid}-${index}`
}

/** 收藏 / 取消收藏这条回答 */
async function toggleFavorite(msg: ChatMessage) {
	if (!isLogin.value) {
		navigateTo('/login')
		return
	}
	if (!msg.id) {
		return
	}
	const method = msg.favorited ? 'DELETE' : 'POST'
	const res: any = await call(`/favorite?targetType=AI_ANSWER&targetId=${msg.id}`, {method})
	if (res && res.code === 200) {
		msg.favorited = !msg.favorited
	}
}

/** 分享：后端生成/复用 token，弹窗里给出链接 */
async function shareAnswer(msg: ChatMessage) {
	if (!isLogin.value) {
		navigateTo('/login')
		return
	}
	if (!msg.id) {
		return
	}
	shareMessage.value = msg
	shareVisible.value = true
	await createShare(msg.id)
}

/** 按当前选择的有效期生成（或复用）链接 */
async function createShare(messageId: number) {
	const res: any = await call(`/ai/answer/${messageId}/share`, {
		method: 'POST',
		body: {days: shareDays.value}
	})
	if (res && res.code === 200 && res.data) {
		if (shareMessage.value) {
			shareMessage.value.shareToken = res.data.token
		}
		shareLink.value = `${location.origin}${res.data.path}`
		shareExpiresAt.value = res.data.expiresAt || null
	}
}

/**
 * 改有效期要重新生成链接：
 * 后端对「未过期的分享」是复用的，不会因为再调一次就改到期时间。
 */
async function changeShareDays(days: number) {
	if (days === shareDays.value || !shareMessage.value?.id) {
		shareDays.value = days
		return
	}
	shareDays.value = days
	if (!shareLink.value) {
		return
	}
	shareChanging.value = true
	await call(`/ai/answer/${shareMessage.value.id}/share`, {method: 'DELETE'})
	await createShare(shareMessage.value.id)
	shareChanging.value = false
	toast('已按新的有效期重新生成链接')
}

/** 弹窗里的一句说明：到期时间比「已分享」有用得多 */
const shareExpiryText = computed(() => {
	if (!shareExpiresAt.value) {
		return '长期有效 · 随时可以在这里取消分享'
	}
	return `链接将于 ${formatDate(shareExpiresAt.value, 'YYYY-MM-DD')} 失效，到期后自动关闭`
})

/** 撤销分享：链接立刻失效 */
async function revokeShare() {
	if (!shareMessage.value || !shareMessage.value.id) {
		return
	}
	const res: any = await call(`/ai/answer/${shareMessage.value.id}/share`, {method: 'DELETE'})
	if (res && res.code === 200) {
		shareMessage.value.shareToken = undefined
		shareExpiresAt.value = null
		shareLink.value = ''
		shareVisible.value = false
	}
}

/** 复制问答与来源，方便贴到聊天窗口 */
async function copyAnswer(msg: ChatMessage) {
	const question = [...messages.value].reverse()
		.find(item => item.role === 'USER' && item.uid < msg.uid)?.content || ''
	const text = answerToText(question, msg.content, msg.citations)
	try {
		await navigator.clipboard.writeText(text)
		toast('已复制')
	} catch (e) {
		toast('复制失败，请手动选择文本')
	}
}

async function copyShareLink() {
	try {
		await navigator.clipboard.writeText(shareLink.value)
		toast('链接已复制')
	} catch (e) {
		toast('复制失败，请手动复制')
	}
}

function toast(message: string) {
	hint.value = message
	window.setTimeout(() => {
		if (hint.value === message) {
			hint.value = ''
		}
	}, 1800)
}

function parseEvent(chunk: string): {name: string, data: any} | null {
	let name = 'message'
	const dataLines: string[] = []
	for (const rawLine of chunk.split('\n')) {
		const line = rawLine.replace(/\r$/, '')
		if (line.startsWith('event:')) {
			name = line.slice(6).trim()
		} else if (line.startsWith('data:')) {
			dataLines.push(line.slice(5).trim())
		}
	}
	if (!dataLines.length) {
		return null
	}
	try {
		return {name, data: JSON.parse(dataLines.join('\n'))}
	} catch (e) {
		return null
	}
}

async function fallbackAnswer(text: string, assistant: ChatMessage) {
	const res: any = await call('/ai/chat', {
		method: 'POST',
		body: {question: text, conversationId: conversationId.value}
	})
	if (res && res.code === 200 && res.data) {
		const data = res.data
		conversationId.value = data.conversationId || conversationId.value
		assistant.content = data.answer || ''
		assistant.citations = data.citations || []
		assistant.degraded = !!data.degraded
		assistant.note = data.note || ''
		assistant.id = data.answerMessageId || assistant.id
		return
	}
	throw new Error((res && res.msg) || 'failed')
}

function askSample(text: string) {
	question.value = text
	send()
}

function newChat() {
	messages.value = []
	conversationId.value = null
	question.value = ''
}

async function openConversation(id: number) {
	if (loading.value) {
		return
	}
	const res: any = await call(`/ai/conversation/${id}`).catch(() => null)
	if (!res || res.code !== 200) {
		return
	}
	conversationId.value = id
	messages.value = (res.data.messages || []).map((msg: any) => ({
		role: msg.role,
		content: msg.content,
		citations: msg.citations || [],
		id: msg.id,
		favorited: !!msg.favorited,
		shareToken: msg.shareToken || undefined,
		uid: msg.id || uidSeed++
	}))
	scrollToBottom()
}

async function removeConversation(id: number) {
	const res: any = await call(`/ai/conversation/${id}`, {method: 'DELETE'}).catch(() => null)
	if (res && res.code === 200) {
		if (conversationId.value === id) {
			newChat()
		}
		loadHistory()
	}
}

function sourceText(type: string) {
	return {BLOG: '文章', PRODUCT: '商品', LESSON: '课时'}[type] || '内容'
}

function scrollToBottom() {
	nextTick(() => {
		const el = listRef.value
		if (el) {
			window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'})
		}
	})
}

useHead({title: '智能问答 - CoolSkyAI'})
</script>

<style scoped>
.body {
	display: flex;
	gap: 16px;
	align-items: flex-start;
}

.history {
	width: 220px;
	flex: none;
	position: sticky;
	top: 80px;
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
	border-radius: 12px;
	padding: 10px;
}

.new-btn {
	width: 100%;
	padding: 8px;
	border: 1px dashed var(--teal);
	border-radius: 8px;
	background: #fff;
	color: var(--teal);
	font-size: 13px;
	cursor: pointer;
	margin-bottom: 8px;
}

.new-btn:hover {
	background: #eef7f5;
}

.empty-his {
	padding: 8px 4px;
}

.his-item {
	display: flex;
	align-items: center;
	border-radius: 8px;
}

.his-item:hover,
.his-item.active {
	background: #f2f7f6;
}

.his-main {
	flex: 1;
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 1px;
	padding: 7px 8px;
	background: none;
	border: none;
	text-align: left;
	cursor: pointer;
}

.his-title {
	font-size: 13px;
	color: var(--ink);
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.his-time {
	font-size: 11.5px;
}

.his-del {
	flex: none;
	width: 22px;
	height: 22px;
	border: none;
	background: none;
	color: var(--muted);
	cursor: pointer;
	border-radius: 6px;
	font-size: 14px;
}

.his-del:hover {
	background: #fdecea;
	color: #b42318;
}

.chat {
	flex: 1;
	min-width: 0;
}

.messages {
	display: flex;
	flex-direction: column;
	gap: 12px;
	min-height: 240px;
}

.intro {
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
	border-radius: 12px;
	padding: 18px 20px;
	display: flex;
	flex-wrap: wrap;
	gap: 8px;
	align-items: center;
	font-size: 13.5px;
}

.intro p {
	margin: 0;
	width: 100%;
	color: var(--muted);
}

.sample {
	border: 1px solid var(--line);
	background: #fff;
	border-radius: 999px;
	padding: 5px 12px;
	font-size: 12.5px;
	color: var(--ink);
	cursor: pointer;
}

.sample:hover {
	border-color: var(--teal);
	color: var(--teal);
}

.small {
	font-size: 12px;
}

.msg {
	display: flex;
}

.msg.user {
	justify-content: flex-end;
}

.bubble {
	max-width: 86%;
	border-radius: 12px;
	padding: 11px 14px;
	font-size: 14px;
	line-height: 1.8;
}

.msg.user .bubble {
	background: var(--teal);
	color: #fff;
}

.msg.assistant .bubble {
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
}

.text {
	margin: 0;
	white-space: pre-wrap;
	word-break: break-word;
}

.thinking {
	color: var(--muted);
}

.degraded {
	margin: 0 0 6px;
	font-size: 12.5px;
	color: #b45309;
	background: #fff7ed;
	border-radius: 6px;
	padding: 4px 8px;
}

.citations {
	margin-top: 10px;
	padding-top: 8px;
	border-top: 1px dashed var(--line);
	display: flex;
	flex-direction: column;
	gap: 5px;
}

.cite-title {
	font-size: 12px;
	color: var(--muted);
}

.cite {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 13px;
	color: var(--ink);
}

.cite:hover .cite-name {
	color: var(--teal);
}

.cite-idx {
	color: var(--teal);
	font-size: 12px;
}

.cite-name {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.composer {
	margin-top: 14px;
	display: flex;
	gap: 8px;
	align-items: flex-end;
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .16);
	border-radius: 12px;
	padding: 10px;
}

.composer textarea {
	flex: 1;
	border: none;
	outline: none;
	resize: none;
	font-size: 14px;
	line-height: 1.7;
	font-family: inherit;
	background: transparent;
}

.send {
	flex: none;
	padding: 8px 18px;
}

.send:disabled {
	opacity: .55;
	cursor: not-allowed;
}

.foot {
	margin-top: 8px;
}

@media (max-width: 860px) {
	.body {
		flex-direction: column;
	}

	.history {
		width: 100%;
		position: static;
	}
}

.caret {
	display: inline-block;
	width: 7px;
	height: 15px;
	margin-left: 2px;
	vertical-align: -2px;
	background: var(--teal);
	border-radius: 1px;
	animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
	to {
		visibility: hidden;
	}
}

.cite-ref {
	color: var(--teal);
	font-weight: 600;
	text-decoration: none;
	border-bottom: 1px dotted rgba(15, 118, 110, .5);
}

.cite-ref:hover {
	background: #eef7f5;
	border-bottom-style: solid;
}

.stopped-note {
	margin: 0 0 6px;
	font-size: 12.5px;
	color: var(--muted);
}

.btn.stop {
	background: #fff;
	color: #b42318;
	border: 1px solid #f3c6c1;
}

.btn.stop:hover {
	background: #fdecea;
}

.msg-actions {
	display: flex;
	gap: 6px;
	margin-top: 8px;
	padding-top: 6px;
	border-top: 1px dashed var(--line);
}

.mini {
	border: 1px solid var(--line);
	background: #fff;
	color: var(--muted);
	font-size: 12px;
	padding: 3px 10px;
	border-radius: 999px;
	cursor: pointer;
}

.mini:hover {
	border-color: var(--teal);
	color: var(--teal);
}

.mini.on {
	border-color: var(--teal);
	color: var(--teal);
	background: #eef7f5;
}

.cite.active {
	background: #fff7ed;
	box-shadow: 0 0 0 2px #fed7aa;
	border-radius: 6px;
}

.mask {
	position: fixed;
	inset: 0;
	background: rgba(17, 24, 39, .38);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 20px;
	z-index: 50;
}

.dialog {
	width: 440px;
	max-width: 100%;
	background: #fff;
	border-radius: 12px;
	padding: 20px 22px;
	box-shadow: 0 24px 60px rgba(0, 0, 0, .18);
}

.dialog h3 {
	margin: 0 0 6px;
	font-size: 16px;
}

.dialog .small {
	font-size: 12.5px;
	margin: 0 0 12px;
}

.share-input {
	width: 100%;
	box-sizing: border-box;
	border: 1px solid var(--line);
	border-radius: 8px;
	padding: 9px 10px;
	font-size: 13px;
	color: var(--ink);
	background: #f9fbfa;
}

.day-row {
	display: flex;
	align-items: center;
	gap: 10px;
	margin-top: 10px;
	font-size: 12.5px;
	color: var(--muted);
}

.day-label {
	flex: none;
}

.days {
	display: flex;
	gap: 6px;
	flex-wrap: wrap;
}

.day {
	border: 1px solid var(--line);
	background: #fff;
	color: var(--muted);
	border-radius: 999px;
	padding: 3px 11px;
	font-size: 12px;
	cursor: pointer;
	transition: all .15s;
}

.day:hover:not(:disabled) {
	border-color: var(--teal);
	color: var(--teal);
}

.day.on {
	border-color: var(--teal);
	background: #eef7f5;
	color: var(--teal);
	font-weight: 600;
}

.day:disabled {
	opacity: .6;
	cursor: default;
}

.expiry {
	margin: 8px 0 0 !important;
	font-size: 12px;
}

.dialog-actions {
	display: flex;
	gap: 8px;
	margin-top: 14px;
	flex-wrap: wrap;
}

.btn.ghost {
	background: #fff;
	color: var(--teal);
	border: 1px solid var(--teal);
}

.toast {
	position: fixed;
	left: 50%;
	bottom: 32px;
	transform: translateX(-50%);
	background: rgba(17, 24, 39, .9);
	color: #fff;
	font-size: 13px;
	padding: 8px 16px;
	border-radius: 999px;
	z-index: 60;
}
</style>
