<template>
	<div class="portal-knowledge">
		<div class="portal-section-head">
			<div>
				<h2 class="portal-section-title">知识付费</h2>
				<p class="portal-section-desc">课程、文档、专栏与代码资源</p>
			</div>
		</div>

		<!--类型筛选-->
		<div class="portal-type-tabs">
			<button
				v-for="tab in tabs"
				:key="tab.value"
				:class="['portal-type-tab', {active: type === tab.value}]"
				@click="changeType(tab.value)"
			>{{ tab.label }}</button>
		</div>

		<div class="portal-product-grid" v-loading="loading">
			<router-link
				v-for="item in list"
				:key="item.productNo"
				:to="`/knowledge/${item.productNo}`"
				class="portal-product-card"
			>
				<div class="portal-product-cover" :style="coverStyle(item)">
					<span class="portal-product-badge">{{ typeLabel(item.productType) }}</span>
				</div>
				<div class="portal-product-body">
					<h3 class="portal-product-title">{{ item.title }}</h3>
					<p class="portal-product-sub">{{ item.subtitle || item.summary }}</p>
					<div class="portal-product-foot">
						<span class="portal-product-price">
							<em>¥</em>{{ formatPrice(item.price) }}
							<s v-if="item.originalPrice && item.originalPrice > item.price">¥{{ formatPrice(item.originalPrice) }}</s>
						</span>
						<span class="portal-product-sales">{{ item.salesCount || 0 }} 人已购</span>
					</div>
				</div>
			</router-link>
		</div>

		<div class="portal-empty" v-if="!loading && list.length === 0">暂无商品，后续会陆续上架</div>

		<div class="portal-pager" v-if="totalPage > 1">
			<button :disabled="pageNum <= 1" @click="changePage(-1)">上一页</button>
			<span>{{ pageNum }} / {{ totalPage }}</span>
			<button :disabled="pageNum >= totalPage" @click="changePage(1)">下一页</button>
		</div>
	</div>
</template>

<script>
	import {getProducts} from "@/api/product";

	export default {
		name: "KnowledgeList",
		data() {
			return {
				tabs: [
					{label: '全部', value: ''},
					{label: '课程', value: 'COURSE'},
					{label: '文档', value: 'DOC'},
					{label: '专栏', value: 'COLUMN'},
					{label: '代码资源', value: 'CODE'}
				],
				type: '',
				list: [],
				loading: false,
				pageNum: 1,
				totalPage: 1
			}
		},
		created() {
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				getProducts({
					type: this.type || undefined,
					pageNum: this.pageNum,
					pageSize: 12
				}).then(res => {
					if (res.code === 200) {
						this.list = res.data.list
						this.totalPage = res.data.totalPage || 1
					}
				}).finally(() => {
					this.loading = false
				})
			},
			changeType(type) {
				this.type = type
				this.pageNum = 1
				this.load()
			},
			changePage(delta) {
				this.pageNum += delta
				this.load()
			},
			formatPrice(value) {
				if (value === null || value === undefined) {
					return '0'
				}
				return Number(value).toFixed(2).replace(/\.00$/, '')
			},
			typeLabel(type) {
				return {COURSE: '课程', DOC: '文档', COLUMN: '专栏', CODE: '代码', SERVICE: '服务'}[type] || type
			},
			coverStyle(item) {
				// 没有封面时用主题色渐变兜底，避免空白卡
				if (item.cover) {
					return {backgroundImage: `url(${item.cover})`, backgroundSize: 'cover', backgroundPosition: 'center'}
				}
				const palette = ['#0F766E', '#115E59', '#0D9488', '#B45309', '#1D4ED8']
				const idx = (item.productNo || '').split('').reduce((s, c) => s + c.charCodeAt(0), 0) % palette.length
				return {background: `linear-gradient(135deg, ${palette[idx]}, #0b4a45)`}
			}
		}
	}
</script>

<style scoped>
	.portal-knowledge {
		background: #fff;
		border: 1px solid rgba(15, 118, 110, .12);
		border-radius: 12px;
		padding: 22px 24px 26px;
	}

	.portal-type-tabs {
		display: flex;
		gap: 8px;
		margin: 14px 0 18px;
		flex-wrap: wrap;
	}

	.portal-type-tab {
		padding: 6px 14px;
		border: 1px solid #e5eaea;
		border-radius: 18px;
		background: #fff;
		font-size: 13px;
		color: #4b5563;
		cursor: pointer;
	}

	.portal-type-tab.active {
		color: #fff;
		background: #0F766E;
		border-color: #0F766E;
	}

	.portal-product-grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(262px, 1fr));
		gap: 16px;
	}

	.portal-product-card {
		display: flex;
		flex-direction: column;
		border: 1px solid #eef2f2;
		border-radius: 10px;
		overflow: hidden;
		text-decoration: none;
		transition: box-shadow .18s ease, transform .18s ease;
	}

	.portal-product-card:hover {
		box-shadow: 0 8px 24px rgba(15, 118, 110, .12);
		transform: translateY(-2px);
	}

	.portal-product-cover {
		position: relative;
		height: 128px;
	}

	.portal-product-badge {
		position: absolute;
		left: 10px;
		top: 10px;
		padding: 2px 8px;
		border-radius: 4px;
		background: rgba(255, 255, 255, .92);
		color: #0F766E;
		font-size: 11.5px;
		font-weight: 600;
	}

	.portal-product-body {
		padding: 13px 14px 15px;
		display: flex;
		flex-direction: column;
		flex: 1;
	}

	.portal-product-title {
		margin: 0 0 6px;
		font-size: 14.5px;
		line-height: 1.5;
		color: #111827;
	}

	.portal-product-sub {
		margin: 0 0 12px;
		font-size: 12.5px;
		line-height: 1.6;
		color: #6b7280;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		flex: 1;
	}

	.portal-product-foot {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}

	.portal-product-price {
		color: #B45309;
		font-size: 17px;
		font-weight: 700;
	}

	.portal-product-price em {
		font-style: normal;
		font-size: 12px;
		margin-right: 1px;
	}

	.portal-product-price s {
		margin-left: 6px;
		color: #9ca3af;
		font-size: 12px;
		font-weight: 400;
	}

	.portal-product-sales {
		color: #9ca3af;
		font-size: 12px;
	}

	.portal-empty {
		padding: 48px 0;
		text-align: center;
		color: #9ca3af;
		font-size: 13px;
	}

	.portal-pager {
		display: flex;
		justify-content: center;
		align-items: center;
		gap: 14px;
		margin-top: 20px;
		font-size: 13px;
		color: #6b7280;
	}

	.portal-pager button {
		padding: 5px 12px;
		border: 1px solid #d1d5db;
		border-radius: 6px;
		background: #fff;
		cursor: pointer;
		font-size: 13px;
	}

	.portal-pager button:disabled {
		opacity: .45;
		cursor: not-allowed;
	}
</style>
