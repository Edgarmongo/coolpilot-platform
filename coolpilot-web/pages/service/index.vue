<template>
	<div>
		<h1 class="page-title">技术服务</h1>
		<p class="page-desc">后端开发、AI 应用、部署运维与咨询，从评估到验收全程可追踪</p>

		<div class="steps card">
			<span v-for="(step, index) in steps" :key="step" class="step">
				<span class="idx">{{ index + 1 }}</span>{{ step }}
			</span>
		</div>

		<div v-if="!products.length" class="empty card">暂无服务，可直接提交需求</div>

		<div class="grid">
			<NuxtLink v-for="product in products" :key="product.id" :to="`/knowledge/${product.productNo}`" class="card item">
				<div class="item-head">
					<span class="tag">技术服务</span>
					<span class="muted">{{ modeText(product) }}</span>
				</div>
				<h3>{{ product.title }}</h3>
				<p class="item-desc">{{ product.summary || product.subtitle }}</p>
				<div class="item-foot">
					<strong>{{ priceText(product) }}</strong>
					<span class="muted">查看详情 →</span>
				</div>
			</NuxtLink>
		</div>

		<div class="card cta">
			<div>
				<strong>没有合适的服务？</strong>
				<p class="muted">把需求写清楚，评估后给你一份正式报价。</p>
			</div>
			<NuxtLink to="/demand/new" class="btn">提交需求</NuxtLink>
		</div>
	</div>
</template>

<script setup lang="ts">
useHead({title: '技术服务 - CoolSkyAI'})

const {call} = useApi()

const steps = ['提交需求', '需求评估', '正式报价', '确认立项', '实施交付', '验收完成']

const {data} = await useAsyncData('service-products', () => call('/products?type=SERVICE&pageNum=1&pageSize=24'))
const products = computed(() => (data.value as any)?.data?.list || [])

function modeText(product: any) {
	return {FIXED: '固定价', STARTING: '起步价', CUSTOM: '定制报价'}[product.priceMode] || product.priceMode
}

function priceText(product: any) {
	if (product.priceMode === 'CUSTOM') {
		return '按需报价'
	}
	const value = Number(product.price || 0).toFixed(2).replace(/\.00$/, '')
	return product.priceMode === 'STARTING' ? `¥${value} 起` : `¥${value}`
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
