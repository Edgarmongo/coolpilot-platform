<template>
	<div>
		<h1 class="page-title">{{ t('servicePage.title') }}</h1>
		<p class="page-desc">{{ t('servicePage.desc') }}</p>

		<div class="steps card">
			<span v-for="(step, index) in steps" :key="step" class="step">
				<span class="idx">{{ index + 1 }}</span>{{ step }}
			</span>
		</div>

		<div v-if="!products.length" class="empty card">{{ t('servicePage.empty') }}</div>

		<div class="grid">
			<NuxtLink v-for="product in products" :key="product.id" :to="localePath(`/knowledge/${product.productNo}`)" class="card item">
				<div class="item-head">
					<span class="tag">{{ t('servicePage.tag') }}</span>
					<span class="muted">{{ modeText(product) }}</span>
				</div>
				<h3>{{ product.title }}</h3>
				<p class="item-desc">{{ product.summary || product.subtitle }}</p>
				<div class="item-foot">
					<strong>{{ priceText(product) }}</strong>
					<span class="muted">{{ t('servicePage.viewDetail') }}</span>
				</div>
			</NuxtLink>
		</div>

		<div class="card cta">
			<div>
				<strong>{{ t('servicePage.noFit') }}</strong>
				<p class="muted">{{ t('servicePage.noFitDesc') }}</p>
			</div>
			<NuxtLink :to="localePath('/demand/new')" class="btn">{{ t('servicePage.submitDemand') }}</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t, te} = useI18n()
const localePath = useLocalePath()

// 注意顺序：t 是 const，useHead 必须放在它之后，否则 setup 阶段 TDZ 报错
useHead({title: t('servicePage.title')})

const steps = computed(() => [
	t('servicePage.step1'), t('servicePage.step2'), t('servicePage.step3'),
	t('servicePage.step4'), t('servicePage.step5'), t('servicePage.step6')
])

const {data} = await useAsyncData('service-products', () => call('/products?type=SERVICE&pageNum=1&pageSize=24'))
const products = computed(() => (data.value as any)?.data?.list || [])

function modeText(product: any) {
	const key = `knowledgeDetail.modes.${product.priceMode}`
	return te(key) ? t(key) : product.priceMode
}

function priceText(product: any) {
	if (product.priceMode === 'CUSTOM') {
		return t('common.quote')
	}
	const value = Number(product.price || 0).toFixed(2).replace(/\.00$/, '')
	const amount = `¥${value}`
	return product.priceMode === 'STARTING' ? t('common.from', {price: amount}) : amount
}
</script>

<style scoped>
.steps {
	display: flex;
	flex-wrap: wrap;
	gap: 10px 18px;
	margin-bottom: 16px;
	font-size: 13px;
	color: var(--muted);
}

.step {
	display: inline-flex;
	align-items: center;
	gap: 6px;
}

.idx {
	width: 18px;
	height: 18px;
	border-radius: 50%;
	background: rgba(15, 118, 110, .1);
	color: var(--teal);
	font-size: 11px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
}

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

.item-head,
.item-foot {
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
	min-height: 40px;
}

.cta {
	margin-top: 16px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 16px;
	flex-wrap: wrap;
}

.cta p {
	margin: 4px 0 0;
	font-size: 13px;
}
</style>
