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
							<p class="text">{{ msg.content }}</p>
							<div v-if="msg.citations && msg.citations.length" class="citations">
								<div class="cite-title">来源</div>
								<a v-for="cite in msg.citations" :key="cite.index" :href="cite.url" class="cite">
									<span class="cite-idx">[{{ cite.index }}]</span>
									<span class="cite-name">{{ cite.title }}</span>
									<span class="tag">{{ sourceText(cite.sourceType) }}</span>
								</a>
							</div>
						</div>
					</div>

					<div v-if="loading" class="msg assistant">
						<div class="bubble thinking">
							正在检索并组织回答…
						</div>
					</div>
				</div>

				<div class="composer">
					<textarea v-model="question" rows="2" maxlength="500"
					          placeholder="问问站内的内容，Enter 发送，Shift + Enter 换行"
					          @keydown.enter.exact.prevent="send"/>
					<button class="btn send" :disabled="loading || !question.trim()" @click="send">
						{{ loading ? '生成中…' : '发送' }}
					</button>
				</div>
				<p class="muted foot">
					回答由模型基于站内资料生成，可能有偏差；重要信息请点开来源核对。
				</p>
			</section>
		</div>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()

type Citation = {index: number, title: string, url: string, sourceType: string, snippet?: string}
type ChatMessage = {role: 'USER' | 'ASSISTANT', content: string, citations?: Citation[], degraded?: boolean, note?: string}

const messages = ref<ChatMessage[]>([])
const question = ref(String(route.query.q || ''))
const loading = ref(false)
const conversationId = ref<number | null>(null)
const history = ref<any[]>([])
const listRef = ref<HTMLElement | null>(null)

const samples = [
	'站内的付费内容都讲什么？',
	'这个站点支持哪些支付方式？',
	'技术服务的流程是怎样的？'
]

onMounted(() => {
	if (isLogin.value) {
		loadHistory()
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
	messages.value.push({role: 'USER', content: text})
	question.value = ''
	loading.value = true
	scrollToBottom()

	try {
		const res: any = await call('/ai/chat', {
			method: 'POST',
			body: {question: text, conversationId: conversationId.value}
		})
		if (res && res.code === 200 && res.data) {
			const data = res.data
			conversationId.value = data.conversationId || conversationId.value
			messages.value.push({
				role: 'ASSISTANT',
				content: data.answer || '（没有生成回答，可以先看下面的来源）',
				citations: data.citations || [],
				degraded: !!data.degraded,
				note: data.note || ''
			})
			if (isLogin.value) {
				loadHistory()
			}
		} else {
			messages.value.push({
				role: 'ASSISTANT',
				content: (res && res.msg) || '提问失败，请稍后再试'
			})
		}
	} catch (e: any) {
		messages.value.push({role: 'ASSISTANT', content: '网络异常，请稍后再试'})
	} finally {
		loading.value = false
		scrollToBottom()
	}
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
		citations: msg.citations || []
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
</style>
