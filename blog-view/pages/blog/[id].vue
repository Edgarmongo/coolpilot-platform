<template>
	<div>
		<div class="card">
			<!--已解锁 / 无密码-->
			<template v-if="blog">
				<h1 class="page-title">{{ blog.title }}</h1>
				<div class="post-meta meta">
					<span>{{ formatDate(blog.createTime, 'YYYY-MM-DD') }}</span>
					<span v-if="blog.category">· {{ blog.category.name }}</span>
					<span>· {{ blog.views || 0 }} 次阅读</span>
				</div>
				<div class="article" v-html="blog.content"></div>
			</template>

			<!--需要密码-->
			<template v-else-if="locked">
				<div class="lock">
					<h1 class="page-title">受密码保护</h1>
					<p class="page-desc">{{ message || '这篇文章需要密码才能查看' }}</p>
					<div class="lock-form">
						<input v-model="password" type="password" placeholder="请输入访问密码" @keyup.enter="unlock">
						<button class="btn" :disabled="unlocking" @click="unlock">
							{{ unlocking ? '验证中…' : '解锁' }}
						</button>
					</div>
					<p class="muted">验证一次后 30 天内再次访问无需重复输入</p>
				</div>
			</template>

			<div v-else class="empty">{{ message || '文章不存在或已被删除' }}</div>
		</div>

		<CommentSection v-if="blog" :blog-id="Number(id)"/>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call} = useApi()

const id = String(route.params.id)
const blog = ref<any>(null)
const locked = ref(false)
const message = ref('')
const password = ref('')
const unlocking = ref(false)
// 密码凭证按文章维度存，30 天有效
const pwdToken = useCookie<string | null>(`cp_blog_pwd_${id}`, {maxAge: 60 * 60 * 24 * 30, sameSite: 'lax'})

async function fetchBlog() {
	const headers: Record<string, string> = {}
	if (pwdToken.value) {
		headers.authorization = pwdToken.value
	}
	// 密码文章只认自己的凭证，不能带访客 token（后端会把它当 blogId 解析）
	const res: any = await call(`/blog?id=${id}`, {headers, skipAuth: !pwdToken.value}).catch(() => null)
	if (res && res.code === 200) {
		blog.value = res.data
		locked.value = false
		message.value = ''
	} else if (res && res.code === 403) {
		blog.value = null
		locked.value = true
		message.value = res.msg || '该文章受密码保护'
	} else {
		blog.value = null
		locked.value = false
		message.value = (res && res.msg) || '加载失败'
	}
}

async function unlock() {
	if (!password.value) {
		return
	}
	unlocking.value = true
	try {
		const res: any = await call('/checkBlogPassword', {
			method: 'POST',
			body: {blogId: Number(id), password: password.value}
		})
		if (res.code === 200) {
			pwdToken.value = res.data
			password.value = ''
			await fetchBlog()
		} else {
			alert(res.msg || '密码错误')
		}
	} finally {
		unlocking.value = false
	}
}

await fetchBlog()

if (blog.value) {
	useHead({title: `${blog.value.title} - CoolSkyAI`})
} else {
	useHead({title: '受密码保护 - CoolSkyAI'})
}
</script>

<style scoped>
.meta {
	margin: 0 0 18px;
	padding-bottom: 14px;
	border-bottom: 1px solid var(--line);
}

.lock {
	padding: 40px 0 30px;
	text-align: center;
}

.lock .page-desc {
	margin-bottom: 20px;
}

.lock-form {
	display: flex;
	gap: 10px;
	justify-content: center;
	max-width: 340px;
	margin: 0 auto 12px;
}

.lock-form input {
	flex: 1;
	padding: 9px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 14px;
	outline-color: var(--teal);
}
</style>
