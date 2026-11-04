<template>
	<section class="comments">
		<h3 class="title">评论 <span class="muted">({{ comments.length }})</span></h3>

		<div v-if="!comments.length" class="empty">还没有评论，来说两句</div>

		<ul class="list">
			<li v-for="item in comments" :key="item.id" class="item">
				<img class="avatar" :src="item.avatar || '/img/avatar.jpg'" alt="">
				<div class="body">
					<div class="meta">
						<strong>{{ item.nickname || '匿名' }}</strong>
						<span class="muted">{{ formatDate(item.createTime) }}</span>
					</div>
					<p class="content">{{ item.content }}</p>
					<ul v-if="item.replyComments && item.replyComments.length" class="replies">
						<li v-for="reply in item.replyComments" :key="reply.id">
							<strong>{{ reply.nickname }}</strong>：{{ reply.content }}
						</li>
					</ul>
				</div>
			</li>
		</ul>

		<div class="form">
			<h4>写下你的评论</h4>
			<div class="fields">
				<input v-model="form.nickname" placeholder="昵称" maxlength="20">
				<input v-model="form.email" placeholder="邮箱（不公开）" maxlength="50">
			</div>
			<textarea v-model="form.content" placeholder="说点什么…（最多 250 字）" maxlength="250"/>
			<div class="actions">
				<span class="muted">{{ form.content.length }}/250</span>
				<button class="btn" :disabled="submitting" @click="submit">
					{{ submitting ? '提交中…' : '提交评论' }}
				</button>
			</div>
		</div>
	</section>
</template>

<script setup lang="ts">
const props = defineProps<{ blogId: number }>()
const {call} = useApi()

const comments = ref<any[]>([])
const submitting = ref(false)
const form = reactive({nickname: '', email: '', content: ''})

async function load() {
	const res: any = await call(`/comments?page=0&blogId=${props.blogId}&pageNum=1&pageSize=50`).catch(() => null)
	comments.value = (res && res.data && res.data.comments && res.data.comments.list) || []
}

async function submit() {
	if (!form.content.trim()) {
		alert('请输入评论内容')
		return
	}
	if (!form.nickname.trim()) {
		alert('请输入昵称')
		return
	}
	submitting.value = true
	try {
		const res: any = await call('/comment', {
			method: 'POST',
			body: {
				content: form.content.trim(),
				page: 0,
				blogId: props.blogId,
				parentCommentId: -1,
				nickname: form.nickname.trim(),
				email: form.email.trim(),
				notice: true
			}
		})
		if (res.code === 200) {
			form.content = ''
			alert('评论已提交，审核通过后展示')
			await load()
		} else {
			alert(res.msg || '提交失败')
		}
	} finally {
		submitting.value = false
	}
}

await load()
</script>

<style scoped>
.comments {
	margin-top: 18px;
	padding: 22px 24px;
	background: #fff;
	border: 1px solid rgba(15, 118, 110, .12);
	border-radius: 12px;
}

.title {
	margin: 0 0 14px;
	font-size: 16px;
}

.list {
	list-style: none;
	margin: 0 0 22px;
	padding: 0;
}

.item {
	display: flex;
	gap: 12px;
	padding: 12px 0;
	border-bottom: 1px dashed var(--line);
}

.avatar {
	width: 34px;
	height: 34px;
	border-radius: 50%;
	object-fit: cover;
	flex-shrink: 0;
}

.body {
	flex: 1;
	min-width: 0;
}

.meta {
	display: flex;
	gap: 10px;
	align-items: baseline;
}

.meta strong {
	font-size: 13.5px;
}

.content {
	margin: 4px 0 0;
	font-size: 13.5px;
	white-space: pre-wrap;
}

.replies {
	list-style: none;
	margin: 8px 0 0;
	padding: 8px 12px;
	background: #fafafa;
	border-radius: 8px;
	font-size: 13px;
	color: #4b5563;
}

.replies li {
	padding: 2px 0;
}

.form h4 {
	margin: 0 0 10px;
	font-size: 14px;
}

.fields {
	display: flex;
	gap: 10px;
	margin-bottom: 10px;
}

.fields input {
	flex: 1;
	padding: 8px 10px;
	border: 1px solid var(--line);
	border-radius: 7px;
	font-size: 13px;
	outline-color: var(--teal);
}

.form textarea {
	width: 100%;
	min-height: 92px;
	padding: 10px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 13.5px;
	font-family: inherit;
	resize: vertical;
	outline-color: var(--teal);
}

.actions {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 10px;
}
</style>
