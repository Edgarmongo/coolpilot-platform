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

				<!-- 本篇所属专题：从单篇回到系列阅读路径 -->
				<div v-if="topics.length" class="topic-tip">
					<span class="muted">本篇属于专题</span>
					<NuxtLink v-for="item in topics" :key="item.id" :to="`/topic/${item.slug}`" class="tag">
						{{ item.title }}
					</NuxtLink>
					<span class="muted" v-if="topics[0].articleCount">共 {{ topics[0].articleCount }} 篇</span>
				</div>

				<!-- 关联付费内容：内容底部导流到商品 -->
				<div v-if="relatedProducts.length" class="related">
					<h3>相关付费内容</h3>
					<div class="related-grid">
						<NuxtLink v-for="item in relatedProducts" :key="item.id"
						          :to="`/knowledge/${item.productNo}`" class="related-item">
							<div class="ri-head">
								<span class="tag">{{ typeText(item.productType) }}</span>
								<span v-if="item.owned" class="owned">已拥有</span>
							</div>
							<strong>{{ item.title }}</strong>
							<p class="muted ri-summary">{{ item.summary }}</p>
							<span class="amount">{{ priceLabel(item) }}</span>
						</NuxtLink>
					</div>
				</div>

				<!--收藏与点赞：未登录时点击跳转登录-->
				<div class="actions">
					<button :class="['act', {active: favorited}]" @click="toggleFavorite">
						{{ favorited ? '已收藏' : '收藏' }}
					</button>
					<button :class="['act', {active: liked}]" @click="toggleLike">
						{{ liked ? '已赞' : '点赞' }}<span v-if="likeCount" class="cnt">{{ likeCount }}</span>
					</button>
				</div>
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

		<CommentSection v-if="blog" :blog-id="blogId"/>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const {call, isLogin} = useApi()

/**
 * 路由参数既可能是旧链接的 id，也可能是新链接的 slug。
 * 纯数字按 id 处理，其余按 slug 查——旧链接因此不会失效。
 */
const param = String(route.params.id)
const isIdParam = /^\d+$/.test(param)
const blogQuery = isIdParam ? `id=${param}` : `slug=${encodeURIComponent(param)}`
const blog = ref<any>(null)
// 评论、收藏、点赞都需要真实 id，slug 进来时由接口返回后再填
const blogId = ref<number>(isIdParam ? Number(param) : 0)
const locked = ref(false)
const message = ref('')
const password = ref('')
const unlocking = ref(false)
const favorited = ref(false)
const liked = ref(false)
const likeCount = ref(0)
const relatedProducts = ref<any[]>([])
const topics = ref<any[]>([])
// 密码凭证按文章维度存，30 天有效
const pwdToken = useCookie<string | null>(`cp_blog_pwd_${param}`, {maxAge: 60 * 60 * 24 * 30, sameSite: 'lax'})

async function fetchBlog() {
	const headers: Record<string, string> = {}
	if (pwdToken.value) {
		headers.authorization = pwdToken.value
	}
	// 密码文章只认自己的凭证，不能带访客 token（后端会把它当 blogId 解析）
	const res: any = await call(`/blog?${blogQuery}`, {headers}).catch(() => null)
	if (res && res.code === 200) {
		blog.value = res.data
		if (res.data && res.data.id) {
			blogId.value = res.data.id
			await Promise.all([loadRelated(), loadTopics()])
		}
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
			body: {blogId: blogId.value, password: password.value}
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

/**
 * 收藏/点赞状态只对已登录用户查询。
 * like/status 允许匿名调用（user 为 null 时 liked=false），但仍以登录态为准。
 */
if (blog.value && isLogin.value) {
	const [f, l]: any[] = await Promise.all([
		call(`/favorite/status?targetType=ARTICLE&targetId=${blogId.value}`).catch(() => null),
		call(`/like/status?targetType=ARTICLE&targetId=${blogId.value}`).catch(() => null)
	])
	favorited.value = !!(f && f.data && f.data.favorited)
	liked.value = !!(l && l.data && l.data.liked)
	likeCount.value = (l && l.data && l.data.count) || 0
}

function requireLogin() {
	navigateTo(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
	return false
}

async function toggleFavorite() {
	if (!isLogin.value) {
		requireLogin()
		return
	}
	const res: any = await call(`/favorite?targetType=ARTICLE&targetId=${blogId.value}`, {
		method: favorited.value ? 'DELETE' : 'POST'
	})
	if (res && res.code === 200) {
		favorited.value = !favorited.value
	}
}

async function toggleLike() {
	if (!isLogin.value) {
		requireLogin()
		return
	}
	const res: any = await call(`/like?targetType=ARTICLE&targetId=${blogId.value}`, {
		method: liked.value ? 'DELETE' : 'POST'
	})
	if (res && res.code === 200) {
		liked.value = !liked.value
		likeCount.value = Math.max(0, likeCount.value + (liked.value ? 1 : -1))
	}
}

async function loadTopics() {
	const res: any = await call(`/blog/${blogId.value}/topics`).catch(() => null)
	topics.value = (res && res.code === 200 && res.data) ? res.data : []
}

async function loadRelated() {
	const res: any = await call(`/blog/${blogId.value}/products`).catch(() => null)
	relatedProducts.value = (res && res.code === 200 && res.data) ? res.data : []
}

function typeText(type: string) {
	return {DOC: '文档', COURSE: '课程', COLUMN: '专栏', CODE: '代码', SERVICE: '服务'}[type] || type
}

function priceLabel(product: any) {
	if (product.priceMode === 'CUSTOM') {
		return '按需报价'
	}
	const value = Number(product.price || 0).toFixed(2).replace(/\.00$/, '')
	return product.priceMode === 'STARTING' ? `¥${value} 起` : `¥${value}`
}

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

.actions {
	display: flex;
	gap: 10px;
	margin-top: 26px;
	padding-top: 18px;
	border-top: 1px solid var(--line);
}

.act {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 7px 18px;
	border: 1px solid var(--line);
	border-radius: 999px;
	background: #fff;
	color: var(--muted);
	font-size: 13.5px;
	cursor: pointer;
	transition: all .15s;
}

.act:hover {
	border-color: var(--teal);
	color: var(--teal);
}

.act.active {
	border-color: var(--teal);
	background: #eef7f5;
	color: var(--teal);
	font-weight: 600;
}

.cnt {
	font-size: 12px;
	opacity: .8;
}

.related {
	margin: 18px 0 4px;
	padding-top: 14px;
	border-top: 1px dashed var(--line);
}

.related h3 {
	margin: 0 0 10px;
	font-size: 15px;
}

.related-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
	gap: 10px;
}

.related-item {
	display: flex;
	flex-direction: column;
	gap: 6px;
	border: 1px solid var(--line);
	border-radius: 10px;
	padding: 10px 12px;
	transition: box-shadow .15s, transform .15s;
}

.related-item:hover {
	transform: translateY(-2px);
	box-shadow: 0 8px 20px rgba(15, 118, 110, .08);
}

.ri-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.owned {
	font-size: 12px;
	color: var(--teal);
}

.ri-summary {
	margin: 0;
	font-size: 12px;
	line-height: 1.6;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.topic-tip {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
	margin: 18px 0 0;
	padding: 10px 12px;
	border-radius: 10px;
	background: #f4faf8;
	border: 1px solid rgba(15, 118, 110, .12);
}
</style>
