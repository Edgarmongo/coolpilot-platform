<template>
	<div class="card" v-if="product">
		<div class="head">
			<div class="head-main">
				<span class="tag">{{ typeLabel(product.productType) }}</span>
				<h1 class="page-title">{{ product.title }}</h1>
				<p class="page-desc">{{ product.subtitle }}</p>
				<div class="post-meta">
					<span>{{ t('knowledgeDetail.sold', {n: product.salesCount || 0}) }}</span>
					<span>· {{ t('knowledgeDetail.views', {n: product.viewCount || 0}) }}</span>
				</div>
			</div>
			<div class="buy">
				<div class="price">{{ priceLabel }}</div>
				<!-- 成交模式由商品配置决定：DIRECT 直接下单 / QUOTE 需求报价 / BOTH 两者都允许 -->
				<template v-if="dealMode === 'QUOTE'">
					<button class="btn buy-btn" @click="goDemand">{{ t('knowledgeDetail.submitDemand') }}</button>
					<p class="muted tip">{{ t('knowledgeDetail.quoteAfter') }}</p>
				</template>
				<template v-else>
					<button class="btn buy-btn" :disabled="buying" @click="onBuy">
						{{ owned ? t('knowledgeDetail.owned') : (buying ? t('knowledgeDetail.processing') : t('knowledgeDetail.buyNow')) }}
					</button>
					<button v-if="dealMode === 'BOTH'" class="btn ghost buy-btn second" @click="goDemand">{{ t('knowledgeDetail.demandFirst') }}</button>
					<p class="muted tip">{{ isService ? t('knowledgeDetail.tipService') : t('knowledgeDetail.tipProduct') }}</p>
				</template>
			</div>
		</div>

		<div class="article" v-html="detailHtml"></div>

		<!-- 服务类商品：交付周期与流程 -->
		<div v-if="serviceItem" class="service-info">
			<h3>{{ t('knowledgeDetail.serviceInfo') }}</h3>
			<div class="service-grid">
				<div>
					<span class="muted">{{ t('knowledgeDetail.delivery') }}</span>
					<strong>{{ serviceItem.deliveryDays ? t('knowledgeDetail.days', {n: serviceItem.deliveryDays}) : t('knowledgeDetail.negotiable') }}</strong>
				</div>
				<div>
					<span class="muted">{{ t('knowledgeDetail.priceMode') }}</span>
					<strong>{{ priceModeText }}</strong>
				</div>
			</div>
			<div v-if="serviceItem.scope" class="service-block">
				<h4>{{ t('knowledgeDetail.scope') }}</h4>
				<p>{{ serviceItem.scope }}</p>
			</div>
			<div v-if="serviceItem.process" class="service-block">
				<h4>{{ t('knowledgeDetail.process') }}</h4>
				<p>{{ serviceItem.process }}</p>
			</div>
			<div v-if="serviceItem.faq" class="service-block">
				<h4>{{ t('knowledgeDetail.faq') }}</h4>
				<p>{{ serviceItem.faq }}</p>
			</div>
		</div>

		<div v-if="resources.length" class="resources">
			<h3>{{ t('knowledgeDetail.resources') }}</h3>
			<p v-for="item in resources" :key="item.id" class="resource">
				<span>{{ item.displayName }}</span>
				<span class="muted">{{ item.version }}{{ owned ? '' : ' · ' + t('knowledgeDetail.downloadAfterBuy') }}</span>
			</p>
		</div>
	</div>
	<div v-else class="empty card">{{ t('knowledgeDetail.notFound') }}</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call, isLogin} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const buying = ref(false)
const owned = ref(false)

const {data} = await useAsyncData(`product-${route.params.no}`, () => call(`/product/${route.params.no}`))
const product = computed(() => (data.value as any)?.data?.product || null)
const detailHtml = computed(() => (data.value as any)?.data?.detailHtml || '')
const resources = computed(() => (data.value as any)?.data?.resources || [])
const serviceItem = computed(() => (data.value as any)?.data?.serviceItem || null)
const isService = computed(() => product.value?.productType === 'SERVICE')
/**
 * 成交模式：优先用商品上显式配的 dealMode；没配就按规则推导 ——
 * 非服务、或固定价服务 → 直接下单；其余服务 → 需求 → 报价 → 订单。
 * 推导结果与升级前的行为一致（存量固定价服务已由迁移写成 BOTH）。
 */
const dealMode = computed(() => {
	const item = product.value
	if (!item) {
		return 'DIRECT'
	}
	if (item.dealMode) {
		return item.dealMode
	}
	if (item.productType !== 'SERVICE') {
		return 'DIRECT'
	}
	return item.priceMode === 'FIXED' ? 'DIRECT' : 'QUOTE'
})
const priceModeText = computed(() => {
	const mode = product.value?.priceMode
	const key = `knowledgeDetail.modes.${mode}`
	return mode && te(key) ? t(key) : ''
})
const priceLabel = computed(() => {
	const mode = product.value?.priceMode
	if (mode === 'CUSTOM') {
		return t('common.quote')
	}
	const value = price(product.value?.price || 0)
	const amount = `¥${value}`
	return mode === 'STARTING' ? t('common.from', {price: amount}) : amount
})

function goDemand() {
	const query = product.value ? `?productNo=${product.value.productNo}` : ''
	router.push(localePath(`/demand/new${query}`))
}

if (product.value) {
	useHead({title: product.value.title})
	// 登录态下判断是否已购买，避免重复下单
	if (isLogin.value) {
		const access = await call(`/resource/access?productNo=${route.params.no}`).catch(() => null)
		owned.value = !!(access as any)?.data?.accessible
	}
}

function typeLabel(type: string) {
	const key = `knowledge.types.${type}`
	return te(key) ? t(key) : type
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

async function onBuy() {
	if (owned.value) {
		router.push(localePath('/me'))
		return
	}
	if (!isLogin.value) {
		router.push(localePath(`/login?redirect=/knowledge/${route.params.no}`))
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
