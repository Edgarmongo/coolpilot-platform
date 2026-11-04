<template>
	<div class="portal-product-detail" v-loading="loading">
		<template v-if="product">
			<!--头部-->
			<div class="portal-detail-head">
				<div class="portal-detail-main">
					<span class="portal-detail-type">{{ typeLabel(product.productType) }}</span>
					<h1 class="portal-detail-title">{{ product.title }}</h1>
					<p class="portal-detail-sub" v-if="product.subtitle">{{ product.subtitle }}</p>
					<p class="portal-detail-meta">
						<span>{{ product.salesCount || 0 }} 人已购</span>
						<span>·</span>
						<span>{{ product.viewCount || 0 }} 次浏览</span>
						<span v-if="course">·</span>
						<span v-if="course">{{ course.lessonCount }} 课时 / 约 {{ course.totalDuration }} 分钟</span>
					</p>
				</div>
				<div class="portal-detail-buy">
					<div class="portal-detail-price">
						<em>¥</em>{{ formatPrice(product.price) }}
						<s v-if="product.originalPrice && product.originalPrice > product.price">¥{{ formatPrice(product.originalPrice) }}</s>
					</div>
					<button class="portal-buy-btn" :class="{'is-owned': owned}" @click="onBuy">
						{{ owned ? '已拥有 · 查看权益' : '立即购买' }}
					</button>
					<p class="portal-buy-tip">{{ owned ? '你已拥有该商品，可直接学习与下载' : '支持微信 / 支付宝 / PayPal，人工确认到账后自动开通' }}</p>
				</div>
			</div>

			<!--标签页-->
			<div class="portal-detail-tabs">
				<button :class="['portal-detail-tab', {active: tab === 'detail'}]" @click="tab = 'detail'">商品详情</button>
				<button v-if="course" :class="['portal-detail-tab', {active: tab === 'catalog'}]" @click="tab = 'catalog'">
					课程目录
				</button>
				<button v-if="resources.length" :class="['portal-detail-tab', {active: tab === 'resource'}]" @click="tab = 'resource'">
					配套资源
				</button>
			</div>

			<!--详情-->
			<div v-show="tab === 'detail'" class="typo portal-article-content portal-detail-body" v-html="detailHtml"></div>

			<!--目录-->
			<ul v-show="tab === 'catalog'" class="portal-lesson-list">
				<li v-for="(lesson, index) in lessons" :key="lesson.id" class="portal-lesson-item">
					<span class="portal-lesson-index">{{ String(index + 1).padStart(2, '0') }}</span>
					<span class="portal-lesson-title">{{ lesson.title }}</span>
					<span class="portal-lesson-duration">{{ lesson.duration }} 分钟</span>
					<span class="portal-lesson-tag free" v-if="lesson.isFreePreview">试看</span>
					<span class="portal-lesson-tag" v-else>购买后可学</span>
				</li>
			</ul>

			<!--资源-->
			<ul v-show="tab === 'resource'" class="portal-resource-list">
				<li v-for="item in resources" :key="item.id" class="portal-resource-item">
					<span class="portal-resource-name">{{ item.displayName }}</span>
					<span class="portal-resource-ver">{{ item.version }}</span>
					<button class="portal-resource-btn" @click="download(item)">下载</button>
				</li>
			</ul>
		</template>

		<div v-else-if="!loading" class="portal-empty">商品不存在或已下架</div>
	</div>
</template>

<script>
	import {getDownloadToken, getProduct} from "@/api/product";
	import {createOrder, getResourceAccess} from "@/api/trade";

	export default {
		name: "KnowledgeDetail",
		data() {
			return {
				product: null,
				detailHtml: '',
				course: null,
				lessons: [],
				resources: [],
				tab: 'detail',
				loading: false,
				owned: false,
				buying: false
			}
		},
		created() {
			this.load()
		},
		methods: {
			load() {
				this.loading = true
				getProduct(this.$route.params.no).then(res => {
					if (res.code === 200) {
						this.product = res.data.product
						this.detailHtml = res.data.detailHtml
						this.course = res.data.course
						this.resources = res.data.resources || []
						this.checkAccess()
						// 目录单独请求，避免详情接口过重
						if (this.course) {
							this.loadCatalog()
						}
					}
				}).finally(() => {
					this.loading = false
				})
			},
			//是否已拥有（免费内容也算可访问）
			checkAccess() {
				getResourceAccess(this.$route.params.no).then(res => {
					if (res.code === 200) {
						this.owned = !!res.data.accessible
					}
				}).catch(() => {
					this.owned = false
				})
			},
			loadCatalog() {
				import('@/api/product').then(({getProductCatalog}) => {
					getProductCatalog(this.$route.params.no).then(res => {
						if (res.code === 200) {
							this.lessons = res.data.lessons || []
						}
					})
				})
			},
			formatPrice(value) {
				if (value === null || value === undefined) {
					return '0'
				}
				return Number(value).toFixed(2).replace(/\.00$/, '')
			},
			typeLabel(type) {
				return {COURSE: '视频课程', DOC: '付费文档', COLUMN: '知识专栏', CODE: '代码资源', SERVICE: '技术服务'}[type] || type
			},
			onBuy() {
				if (!this.$store.state.visitor) {
					this.$router.push(`/login?redirect=/knowledge/${this.$route.params.no}`)
					return
				}
				if (this.owned) {
					// 已拥有：直接去个人中心看权益
					this.$router.push('/orders')
					return
				}
				if (this.buying) {
					return
				}
				this.buying = true
				createOrder({productNo: this.$route.params.no}).then(res => {
					if (res.code === 200) {
						this.$router.push(`/order/${res.data.order.orderNo}`)
					} else {
						this.$message.error(res.msg || '下单失败')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '下单失败')
				}).finally(() => {
					this.buying = false
				})
			},
			download(item) {
				if (!this.$store.state.visitor) {
					this.$router.push(`/login?redirect=/knowledge/${this.$route.params.no}`)
					return
				}
				getDownloadToken(item.fileId).then(res => {
					if (res.code === 200) {
						window.open(`/api/file/download?token=${encodeURIComponent(res.data.token)}`)
					} else {
						this.$message.error(res.msg || '暂时无法下载')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '暂时无法下载')
				})
			}
		}
	}
</script>

<style scoped>
	.portal-product-detail {
		background: #fff;
		border: 1px solid rgba(15, 118, 110, .12);
		border-radius: 12px;
		padding: 24px 26px 28px;
		min-height: 360px;
	}

	.portal-detail-head {
		display: flex;
		justify-content: space-between;
		gap: 28px;
		padding-bottom: 18px;
		border-bottom: 1px solid #eef2f2;
		flex-wrap: wrap;
	}

	.portal-detail-main {
		flex: 1;
		min-width: 260px;
	}

	.portal-detail-type {
		display: inline-block;
		padding: 2px 9px;
		border-radius: 4px;
		background: #eef7f5;
		color: #0F766E;
		font-size: 12px;
		margin-bottom: 8px;
	}

	.portal-detail-title {
		margin: 0 0 8px;
		font-size: 22px;
		line-height: 1.45;
		color: #111827;
	}

	.portal-detail-sub {
		margin: 0 0 10px;
		color: #6b7280;
		font-size: 13.5px;
		line-height: 1.7;
	}

	.portal-detail-meta {
		margin: 0;
		color: #9ca3af;
		font-size: 12.5px;
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}

	.portal-detail-buy {
		width: 200px;
		flex-shrink: 0;
		text-align: right;
	}

	.portal-detail-price {
		font-size: 28px;
		font-weight: 700;
		color: #B45309;
		line-height: 1.2;
	}

	.portal-detail-price em {
		font-style: normal;
		font-size: 15px;
	}

	.portal-detail-price s {
		margin-left: 8px;
		font-size: 13px;
		font-weight: 400;
		color: #9ca3af;
	}

	.portal-buy-btn {
		margin-top: 10px;
		width: 100%;
		padding: 9px 0;
		border: none;
		border-radius: 8px;
		background: #0F766E;
		color: #fff;
		font-size: 14px;
		font-weight: 600;
		cursor: pointer;
	}

	.portal-buy-btn:hover {
		background: #115E59;
	}

	.portal-buy-btn.is-owned {
		background: #fff;
		color: #0F766E;
		border: 1px solid #0F766E;
	}

	.portal-buy-btn.is-owned:hover {
		background: #eef7f5;
	}

	.portal-buy-tip {
		margin: 8px 0 0;
		font-size: 11.5px;
		color: #9ca3af;
	}

	.portal-detail-tabs {
		display: flex;
		gap: 6px;
		margin: 18px 0 8px;
		border-bottom: 1px solid #eef2f2;
	}

	.portal-detail-tab {
		padding: 8px 14px;
		border: none;
		background: none;
		font-size: 14px;
		color: #6b7280;
		cursor: pointer;
		border-bottom: 2px solid transparent;
	}

	.portal-detail-tab.active {
		color: #0F766E;
		border-bottom-color: #0F766E;
		font-weight: 600;
	}

	.portal-detail-body {
		padding-top: 12px;
	}

	.portal-lesson-list,
	.portal-resource-list {
		list-style: none;
		margin: 10px 0 0;
		padding: 0;
	}

	.portal-lesson-item,
	.portal-resource-item {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 6px;
		border-bottom: 1px dashed #eef2f2;
		font-size: 13.5px;
	}

	.portal-lesson-index {
		color: #9ca3af;
		font-variant-numeric: tabular-nums;
	}

	.portal-lesson-title,
	.portal-resource-name {
		flex: 1;
		color: #111827;
	}

	.portal-lesson-duration,
	.portal-resource-ver {
		color: #9ca3af;
		font-size: 12px;
	}

	.portal-lesson-tag {
		padding: 1px 7px;
		border-radius: 3px;
		background: #f3f4f6;
		color: #9ca3af;
		font-size: 11.5px;
	}

	.portal-lesson-tag.free {
		background: #eef7f5;
		color: #0F766E;
	}

	.portal-resource-btn {
		padding: 4px 12px;
		border: 1px solid #0F766E;
		border-radius: 6px;
		background: #fff;
		color: #0F766E;
		font-size: 12.5px;
		cursor: pointer;
	}

	.portal-resource-btn:hover {
		background: #0F766E;
		color: #fff;
	}

	.portal-empty {
		padding: 60px 0;
		text-align: center;
		color: #9ca3af;
		font-size: 13px;
	}
</style>
