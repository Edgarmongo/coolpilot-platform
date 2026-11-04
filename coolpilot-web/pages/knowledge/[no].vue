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
				<div class="price">{{ priceLabel }}</div>
				<template v-if="isService && !isFixed">
					<button class="btn buy-btn" @click="goDemand">提交需求</button>
					<p class="muted tip">评估后给出正式报价</p>
				</template>
				<template v-else>
					<button class="btn buy-btn" :disabled="buying" @click="onBuy">
						{{ owned ? '已拥有' : (buying ? '处理中…' : '立即购买') }}
					</button>
					<button v-if="isService" class="btn ghost buy-btn second" @click="goDemand">先提需求</button>
					<p class="muted tip">{{ isService ? '支付后进入实施与交付流程' : '人工确认到账后自动开通' }}</p>
				</template>
			</div>
		</div>

		<div class="article" v-html="detailHtml"></div>

		<!-- 服务类商品：交付周期与流程 -->
		<div v-if="serviceItem" class="service-info">
			<h3>服务说明</h3>
			<div class="service-grid">
				<div>
					<span class="muted">预计交付</span>
					<strong>{{ serviceItem.deliveryDays ? serviceItem.deliveryDays + ' 天' : '面议' }}</strong>
				</div>
				<div>
					<span class="muted">计价方式</span>
					<strong>{{ priceModeText }}</strong>
				</div>
			</div>
			<div v-if="serviceItem.scope" class="service-block">
				<h4>服务范围</h4>
				<p>{{ serviceItem.scope }}</p>
			</div>
			<div v-if="serviceItem.process" class="service-block">
				<h4>服务流程</h4>
				<p>{{ serviceItem.process }}</p>
			</div>
			<div v-if="serviceItem.faq" class="service-block">
				<h4>常见问题</h4>
				<p>{{ serviceItem.faq }}</p>
			</div>
		</div>

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
const serviceItem = computed(() => (data.value as any)?.data?.serviceItem || null)
const isService = computed(() => product.value?.productType === 'SERVICE')
const isFixed = computed(() => product.value?.priceMode === 'FIXED')
const priceModeText = computed(() => ({FIXED: '固定价', STARTING: '起步价', CUSTOM: '定制报价'}[product.value?.priceMode] || ''))
const priceLabel = computed(() => {
	const mode = product.value?.priceMode
	if (mode === 'CUSTOM') {
		return '按需报价'
	}
	const value = price(product.value?.price || 0)
	return mode === 'STARTING' ? `¥${value} 起` : `¥${value}`
})

function goDemand() {
	const query = product.value ? `?productNo=${product.value.productNo}` : ''
	router.push(`/demand/new${query}`)
}

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

.buy-btn.second {
	margin-top: 6px;
}

.service-info {
	margin-top: 18px;
	padding-top: 16px;
	border-top: 1px dashed var(--line);
}

.service-info h3 {
	margin: 0 0 10px;
	font-size: 15px;
}

.service-info h4 {
	margin: 12px 0 4px;
	font-size: 13px;
	color: var(--teal);
}

.service-info p {
	margin: 0;
	font-size: 13px;
	line-height: 1.8;
	white-space: pre-wrap;
	color: var(--text);
}

.service-grid {
	display: flex;
	gap: 28px;
	flex-wrap: wrap;
	font-size: 13px;
}

.service-grid div {
	display: flex;
	flex-direction: column;
	gap: 2px;
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
