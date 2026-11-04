<template>
	<div class="card" v-if="product">
		<div class="head">
			<div class="head-main">
				<span class="tag">{{ typeLabel(product.productType) }}</span>
				<h1 class="page-title">{{ product.title }}</h1>
				<p class="page-desc">{{ product.subtitle }}</p>
				<div class="post-meta">
					<span>{{ product.salesCount || 0 }} 人已购</span>
					<span>· {{ product.viewCount || 0 }} 次浏览</span>
				</div>
			</div>
			<div class="buy">
				<div class="price">¥{{ price(product.price) }}</div>
				<button class="btn buy-btn" :disabled="buying" @click="onBuy">
					{{ owned ? '已拥有' : (buying ? '处理中…' : '立即购买') }}
				</button>
				<p class="muted tip">人工确认到账后自动开通</p>
			</div>
		</div>

		<div class="article" v-html="detailHtml"></div>

		<div v-if="resources.length" class="resources">
			<h3>配套资源</h3>
			<p v-for="item in resources" :key="item.id" class="resource">
				<span>{{ item.displayName }}</span>
				<span class="muted">{{ item.version }}{{ owned ? '' : ' · 购买后可下载' }}</span>
			</p>
		</div>
	</div>
	<div v-else class="empty card">商品不存在或已下架</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call, isLogin} = useApi()

const buying = ref(false)
const owned = ref(false)

const {data} = await useAsyncData(`product-${route.params.no}`, () => call(`/product/${route.params.no}`))
const product = computed(() => (data.value as any)?.data?.product || null)
const detailHtml = computed(() => (data.value as any)?.data?.detailHtml || '')
const resources = computed(() => (data.value as any)?.data?.resources || [])

if (product.value) {
	useHead({title: `${product.value.title} - CoolSkyAI`})
	// 登录态下判断是否已购买，避免重复下单
	if (isLogin.value) {
		const access = await call(`/resource/access?productNo=${route.params.no}`).catch(() => null)
		owned.value = !!(access as any)?.data?.accessible
	}
}

function typeLabel(type: string) {
	return {COURSE: '视频课程', DOC: '付费文档', COLUMN: '知识专栏', CODE: '代码资源', SERVICE: '技术服务'}[type] || type
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

async function onBuy() {
	if (owned.value) {
		router.push('/me')
		return
	}
	if (!isLogin.value) {
		router.push(`/login?redirect=/knowledge/${route.params.no}`)
		return
	}
	buying.value = true
	try {
		const res: any = await call('/order', {method: 'POST', body: {productNo: route.params.no}})
		if (res.code === 200) {
			router.push(`/order/${res.data.order.orderNo}`)
		}
	} finally {
		buying.value = false
	}
}
</script>

<style scoped>
.head {
	display: flex;
	gap: 28px;
	justify-content: space-between;
	flex-wrap: wrap;
	padding-bottom: 18px;
	border-bottom: 1px solid var(--line);
	margin-bottom: 8px;
}

.head-main {
	flex: 1;
	min-width: 260px;
}

.buy {
	text-align: right;
	min-width: 160px;
}

.price {
	font-size: 28px;
	font-weight: 700;
	color: #b45309;
}

.buy-btn {
	width: 100%;
	margin-top: 8px;
}

.tip {
	margin: 6px 0 0;
}

.resources {
	margin-top: 26px;
	padding-top: 16px;
	border-top: 1px dashed var(--line);
}

.resources h3 {
	margin: 0 0 8px;
	font-size: 15px;
}

.resource {
	display: flex;
	justify-content: space-between;
	margin: 0;
	padding: 8px 0;
	border-bottom: 1px dashed var(--line);
	font-size: 13.5px;
}
</style>
