<template>
	<div>
		<h1 class="page-title">{{ t('knowledge.title') }}</h1>
		<p class="page-desc">{{ t('knowledge.desc') }}</p>

		<div v-if="!products.length" class="empty card">{{ t('knowledge.empty') }}</div>

		<div class="grid">
			<NuxtLink v-for="product in products" :key="product.id" :to="localePath(`/knowledge/${product.productNo}`)"
			          class="card item">
				<div class="item-head">
					<span class="tag">{{ typeLabel(product.productType) }}</span>
					<span v-if="product.salesCount" class="muted">{{ t('knowledge.sold', {n: product.salesCount}) }}</span>
				</div>
				<h3>{{ product.title }}</h3>
				<p class="item-desc">{{ product.summary || product.subtitle }}</p>
				<div class="item-foot">
					<strong>¥{{ price(product.price) }}</strong>
					<s v-if="product.originalPrice && product.originalPrice > product.price">¥{{ price(product.originalPrice) }}</s>
				</div>
			</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

const {data} = await useAsyncData('products', () => call('/products?pageNum=1&pageSize=12'))
const products = computed(() => (data.value as any)?.data?.list || [])

function typeLabel(type: string) {
	const key = `knowledge.types.${type}`
	return te(key) ? t(key) : type
}

function price(value: number) {
	return Number(value || 0).toFixed(2).replace(/\.00$/, '')
}
</script>

<style scoped>
.grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
	gap: 14px;
}

.item {
	display: flex;
	flex-direction: column;
	gap: 8px;
	transition: transform .15s, box-shadow .15s;
}

.item:hover {
	transform: translateY(-2px);
	box-shadow: 0 8px 24px rgba(15, 118, 110, .09);
}

.item-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.item h3 {
	margin: 0;
	font-size: 16px;
	line-height: 1.5;
}

.item-desc {
	margin: 0;
	color: var(--muted);
	font-size: 13px;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
	min-height: 44px;
}

.item-foot {
	display: flex;
	align-items: baseline;
	gap: 8px;
	margin-top: auto;
}

.item-foot strong {
	color: #b45309;
	font-size: 19px;
}

.item-foot s {
	color: var(--muted);
	font-size: 12.5px;
}
</style>
