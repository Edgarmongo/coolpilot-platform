<template>
	<div>
		<div class="page-head">
			<h1 class="page-title">{{ t('knowledge.title') }}</h1>
			<p class="page-desc">{{ t('knowledge.desc') }}</p>
		</div>

		<div v-if="!products.length" class="empty">{{ t('knowledge.empty') }}</div>

		<div v-else class="grid3 section">
			<NuxtLink v-for="product in products" :key="product.id" :to="localePath(`/knowledge/${product.productNo}`)"
			          class="card cover-card">
				<AppCover :seed="`${product.id}-${product.title}`" :height="132" :variant="3"/>
				<div class="card-pad">
					<div class="item-head">
						<span class="tag field">{{ typeLabel(product.productType) }}</span>
						<span v-if="product.salesCount" class="muted">{{ t('knowledge.sold', {n: product.salesCount}) }}</span>
					</div>
					<h3 class="item-title">{{ product.title }}</h3>
					<p class="item-desc">{{ product.summary || product.subtitle }}</p>
					<div class="item-foot">
						<strong>{{ priceLabel(product) }}</strong>
						<s v-if="product.originalPrice && product.originalPrice > product.price">¥{{ price(product.originalPrice) }}</s>
					</div>
				</div>
			</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const {data} = await useAsyncData('products', () => call('/products?pageNum=1&pageSize=24'))
const products = computed(() => (data.value as any)?.data?.list || [])

function typeLabel(type: string) {
	const key = `knowledge.types.${type}`
	return te(key) ? t(key) : type
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}

/** 服务类商品按需报价，直接显示 ¥0 会误导 */
function priceLabel(product: any) {
	if (product.priceMode === 'CUSTOM') {
		return t('common.quote')
	}
	const amount = `¥${price(product.price)}`
	return product.priceMode === 'STARTING' ? t('common.from', {price: amount}) : amount
}

useHead({title: t('knowledge.title')})
</script>

<style scoped>
.section {
	padding-top: 26px;
}

.cover-card {
	padding: 0;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	transition: transform .18s ease, border-color .18s ease;
}

.cover-card:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.item-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	margin-bottom: 10px;
}

.item-title {
	font-size: 16.5px;
	font-weight: 700;
	line-height: 1.45;
	margin-bottom: 8px;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.cover-card:hover .item-title {
	color: var(--teal);
}

.item-desc {
	flex: 1;
	font-size: 13px;
	line-height: 1.65;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
	min-height: 42px;
}

.item-foot {
	display: flex;
	align-items: baseline;
	gap: 8px;
	margin-top: 14px;
	padding-top: 12px;
	border-top: 1px solid var(--line);
}

.item-foot strong {
	color: var(--teal);
	font-size: 18px;
	font-weight: 700;
}

.item-foot s {
	color: var(--faint);
	font-size: 12.5px;
}
</style>
