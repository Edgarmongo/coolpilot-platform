<template>
	<div class="portal-pay" v-loading="loading">
		<div class="portal-section-head">
			<div>
				<h2 class="portal-section-title">收银台</h2>
				<p class="portal-section-desc">人工收款模式：扫码转账 → 提交凭证 → 确认到账后自动开通</p>
			</div>
			<span :class="['portal-pay-status', statusClass]">{{ statusText }}</span>
		</div>

		<template v-if="order">
			<!--订单摘要-->
			<div class="portal-pay-card">
				<div class="portal-pay-item" v-for="item in items" :key="item.id">
					<span class="portal-pay-title">{{ item.titleSnapshot }}</span>
					<span class="portal-pay-price">¥{{ formatPrice(item.amount) }}</span>
				</div>
				<div class="portal-pay-total">
					<span>应付金额</span>
					<strong>¥{{ formatPrice(order.payAmount) }}</strong>
				</div>
				<p class="portal-pay-meta">
					订单号 <b>{{ order.orderNo }}</b>
					<span>·</span>
					<span>下单时间 {{ order.createTime | dateFormat('YYYY-MM-DD HH:mm') }}</span>
				</p>
			</div>

			<!--待支付：选渠道-->
			<template v-if="order.status === 'PENDING_PAY'">
				<div class="portal-pay-block" v-if="!payInfo">
					<h3>选择收款方式</h3>
					<div class="portal-channel-list">
						<button v-for="(name, code) in channels" :key="code"
						        :class="['portal-channel', {active: channel === code}]"
						        @click="channel = code">
							<span class="portal-channel-name">{{ name }}</span>
							<span class="portal-channel-desc">{{ channelDesc(code) }}</span>
						</button>
					</div>
					<button class="portal-pay-primary" :disabled="submitting" @click="startPay">
						{{ submitting ? '正在生成…' : '生成收款信息' }}
					</button>
				</div>

				<!--收款信息-->
				<div class="portal-pay-block" v-else>
					<h3>请完成转账</h3>
					<div class="portal-pay-qr">
						<div class="portal-pay-qr-box">
							<img v-if="payInfo.qrCodeUrl" :src="payInfo.qrCodeUrl" alt="收款码">
							<a v-else-if="payInfo.payUrl" :href="payInfo.payUrl" target="_blank" rel="noopener">打开 PayPal 付款页</a>
							<p v-else class="portal-pay-qr-empty">收款码尚未配置，请联系站长</p>
						</div>
						<ul class="portal-pay-info">
							<li><span>收款方式</span><b>{{ payInfo.channelName }}</b></li>
							<li><span>应付金额</span><b class="amount">¥{{ formatPrice(payInfo.amount) }}</b></li>
							<li><span>转账备注</span><b>{{ payInfo.transferRemark }}</b></li>
							<li v-if="payInfo.payeeName"><span>收款人</span><b>{{ payInfo.payeeName }}</b></li>
						</ul>
					</div>
					<p class="portal-pay-tip">{{ payInfo.tip }}</p>

					<div class="portal-pay-proof">
						<h4>已转账？填写付款信息</h4>
						<div class="portal-pay-form">
							<label>
								<span>付款人姓名</span>
								<input v-model.trim="proof.payerName" type="text" maxlength="32" placeholder="转账时的姓名或昵称">
							</label>
							<label>
								<span>转账时间</span>
								<input v-model="proof.transferTime" type="datetime-local">
							</label>
							<label class="portal-pay-upload">
								<span>付款截图（可选）</span>
								<input ref="file" type="file" accept="image/*" @change="onFile">
								<em v-if="proof.fileName">{{ proof.fileName }}</em>
								<em v-else>选择图片，最多 100MB</em>
							</label>
						</div>
						<button class="portal-pay-primary" :disabled="submitting" @click="submitProof">
							{{ submitting ? '提交中…' : '提交付款凭证' }}
						</button>
					</div>
				</div>
			</template>

			<!--待确认-->
			<div class="portal-pay-block" v-else-if="order.status === 'PENDING_CONFIRM'">
				<h3>已收到你的付款信息</h3>
				<p class="portal-pay-tip">我会在 24 小时内核对到账情况，确认后课程/文档自动开通，无需重复提交。</p>
			</div>

			<!--已完成-->
			<div class="portal-pay-block" v-else-if="order.status === 'COMPLETED'">
				<h3>开通成功</h3>
				<p class="portal-pay-tip">权益已发放，可直接查看内容；配套资源可在商品页下载。</p>
				<div class="portal-pay-actions">
					<router-link v-if="productNo" :to="`/knowledge/${productNo}`" class="portal-pay-primary">去查看商品</router-link>
					<router-link to="/orders" class="portal-pay-ghost">我的订单</router-link>
				</div>
			</div>

			<!--其它状态-->
			<div class="portal-pay-block" v-else>
				<h3>{{ statusText }}</h3>
				<p class="portal-pay-tip">
					{{ order.status === 'REFUNDING' ? '退款申请处理中，期间权益暂时不可用。' : '' }}
					{{ order.status === 'REFUNDED' ? '已完成退款，相关权益已撤销。' : '' }}
					{{ order.status === 'CANCELLED' || order.status === 'CLOSED' ? '该订单已关闭，可重新下单购买。' : '' }}
				</p>
				<div class="portal-pay-actions">
					<router-link to="/orders" class="portal-pay-ghost">返回我的订单</router-link>
				</div>
			</div>

			<!--可申请退款-->
			<div class="portal-pay-refund" v-if="canRefund">
				<button class="portal-pay-link" @click="refundVisible = true">申请退款</button>
			</div>
		</template>

		<el-dialog title="申请退款" :visible.sync="refundVisible" width="420px">
			<el-input v-model="refundReason" type="textarea" :rows="4" maxlength="200"
			          placeholder="请说明退款原因，便于核对处理"/>
			<span slot="footer">
				<el-button @click="refundVisible = false">取消</el-button>
				<el-button type="primary" @click="submitRefund">提交申请</el-button>
			</span>
		</el-dialog>
	</div>
</template>

<script>
	import {
		applyRefund,
		createPayment,
		getOrder,
		getPayChannels,
		submitTransferProof,
		uploadProof
	} from "@/api/trade";

	export default {
		name: "OrderPay",
		data() {
			return {
				loading: false,
				submitting: false,
				order: null,
				items: [],
				channels: {},
				channel: 'MANUAL_WECHAT',
				payInfo: null,
				proof: {payerName: '', transferTime: '', fileId: null, fileName: ''},
				refundVisible: false,
				refundReason: ''
			}
		},
		computed: {
			statusText() {
				const map = {
					PENDING_PAY: '待支付',
					PENDING_CONFIRM: '待确认收款',
					PAID: '已支付',
					COMPLETED: '已完成',
					CANCELLED: '已取消',
					CLOSED: '已关闭',
					REFUNDING: '退款中',
					REFUNDED: '已退款'
				};
				return this.order ? (map[this.order.status] || this.order.status) : ''
			},
			statusClass() {
				return this.order ? 'is-' + String(this.order.status).toLowerCase() : ''
			},
			productNo() {
				return this.items.length ? this.items[0].productNo : ''
			},
			canRefund() {
				return this.order && (this.order.status === 'COMPLETED' || this.order.status === 'PAID')
			}
		},
		created() {
			if (!this.$store.state.visitor) {
				this.$router.replace(`/login?redirect=${this.$route.fullPath}`)
				return
			}
			this.load()
			getPayChannels().then(res => {
				if (res.code === 200) {
					this.channels = res.data || {}
				}
			}).catch(() => {
			})
		},
		methods: {
			load() {
				this.loading = true
				getOrder(this.$route.params.orderNo).then(res => {
					if (res.code === 200) {
						this.order = res.data.order
						this.items = res.data.items || []
						// 已提交过的支付单直接展示收款信息
						const payments = res.data.payments || []
						const pending = payments.find(p => p.status === 'INIT')
						if (pending && this.order.status === 'PENDING_PAY') {
							this.payInfo = {
								paymentNo: pending.paymentNo,
								channel: pending.channel,
								channelName: this.channels[pending.channel] || pending.channel,
								amount: pending.amount,
								qrCodeUrl: '',
								transferRemark: this.order.orderNo.slice(-6),
								tip: '按上次选择的收款方式完成转账后提交凭证'
							}
							this.channel = pending.channel
						}
					} else {
						this.$message.error(res.msg || '订单不存在')
					}
				}).finally(() => {
					this.loading = false
				})
			},
			channelDesc(code) {
				return {
					MANUAL_WECHAT: '扫码后填写付款人姓名',
					MANUAL_ALIPAY: '扫码后填写付款人姓名',
					MANUAL_PAYPAL: '跳转 PayPal 完成付款'
				}[code] || ''
			},
			startPay() {
				this.submitting = true
				createPayment({orderNo: this.order.orderNo, channel: this.channel}).then(res => {
					if (res.code === 200) {
						this.payInfo = res.data
					} else {
						this.$message.error(res.msg || '生成收款信息失败')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '生成收款信息失败')
				}).finally(() => {
					this.submitting = false
				})
			},
			onFile(e) {
				const file = e.target.files && e.target.files[0]
				if (!file) {
					return
				}
				this.submitting = true
				uploadProof(file).then(res => {
					if (res.code === 200) {
						this.proof.fileId = res.data.fileId
						this.proof.fileName = res.data.fileName
					} else {
						this.$message.error(res.msg || '上传失败')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '上传失败')
				}).finally(() => {
					this.submitting = false
				})
			},
			submitProof() {
				if (!this.proof.payerName) {
					this.$message.warning('请填写付款人姓名')
					return
				}
				this.submitting = true
				const transferTime = this.proof.transferTime
					? this.proof.transferTime.replace('T', ' ') + ':00'
					: ''
				submitTransferProof(this.payInfo.paymentNo, {
					payerName: this.proof.payerName,
					transferTime,
					proofFileId: this.proof.fileId
				}).then(res => {
					if (res.code === 200) {
						this.$message.success('已提交，等待确认收款')
						this.load()
					} else {
						this.$message.error(res.msg || '提交失败')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '提交失败')
				}).finally(() => {
					this.submitting = false
				})
			},
			submitRefund() {
				if (!this.refundReason.trim()) {
					this.$message.warning('请填写退款原因')
					return
				}
				applyRefund({orderNo: this.order.orderNo, reason: this.refundReason.trim()}).then(res => {
					if (res.code === 200) {
						this.$message.success('退款申请已提交')
						this.refundVisible = false
						this.load()
					} else {
						this.$message.error(res.msg || '提交失败')
					}
				}).catch(err => {
					this.$message.error((err && err.msg) || '提交失败')
				})
			},
			formatPrice(value) {
				return Number(value || 0).toFixed(2).replace(/\.00$/, '')
			}
		}
	}
</script>

<style scoped>
	.portal-pay {
		background: #fff;
		border: 1px solid rgba(15, 118, 110, .12);
		border-radius: 12px;
		padding: 24px 26px 30px;
		min-height: 320px;
	}

	.portal-pay-status {
		padding: 4px 12px;
		border-radius: 999px;
		background: #eef7f5;
		color: #0F766E;
		font-size: 12.5px;
		height: 24px;
		line-height: 24px;
	}

	.portal-pay-status.is-pending_pay {
		background: #fef3c7;
		color: #B45309;
	}

	.portal-pay-status.is-pending_confirm {
		background: #e0f2fe;
		color: #0369a1;
	}

	.portal-pay-status.is-refunded,
	.portal-pay-status.is-cancelled,
	.portal-pay-status.is-closed {
		background: #f3f4f6;
		color: #6b7280;
	}

	.portal-pay-card {
		border: 1px solid #eef2f2;
		border-radius: 10px;
		padding: 16px 18px;
		background: #fbfdfd;
	}

	.portal-pay-item {
		display: flex;
		justify-content: space-between;
		gap: 16px;
		font-size: 14px;
		color: #111827;
		padding: 4px 0;
	}

	.portal-pay-price {
		color: #6b7280;
	}

	.portal-pay-total {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		margin-top: 10px;
		padding-top: 10px;
		border-top: 1px dashed #e5e7eb;
		font-size: 13.5px;
		color: #6b7280;
	}

	.portal-pay-total strong {
		font-size: 24px;
		color: #B45309;
	}

	.portal-pay-meta {
		margin: 8px 0 0;
		font-size: 12px;
		color: #9ca3af;
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}

	.portal-pay-block {
		margin-top: 20px;
	}

	.portal-pay-block h3 {
		margin: 0 0 12px;
		font-size: 15px;
		color: #111827;
	}

	.portal-channel-list {
		display: flex;
		gap: 10px;
		flex-wrap: wrap;
		margin-bottom: 16px;
	}

	.portal-channel {
		flex: 1;
		min-width: 150px;
		text-align: left;
		padding: 10px 14px;
		border: 1px solid #e5e7eb;
		border-radius: 9px;
		background: #fff;
		cursor: pointer;
	}

	.portal-channel.active {
		border-color: #0F766E;
		background: #eef7f5;
	}

	.portal-channel-name {
		display: block;
		font-size: 13.5px;
		color: #111827;
		font-weight: 600;
	}

	.portal-channel-desc {
		display: block;
		margin-top: 3px;
		font-size: 11.5px;
		color: #9ca3af;
	}

	.portal-pay-primary {
		display: inline-block;
		padding: 9px 20px;
		border: none;
		border-radius: 8px;
		background: #0F766E;
		color: #fff;
		font-size: 13.5px;
		font-weight: 600;
		cursor: pointer;
		text-decoration: none;
	}

	.portal-pay-primary:hover {
		background: #115E59;
	}

	.portal-pay-primary:disabled {
		opacity: .6;
		cursor: not-allowed;
	}

	.portal-pay-ghost {
		display: inline-block;
		margin-left: 10px;
		padding: 8px 18px;
		border: 1px solid #d1d5db;
		border-radius: 8px;
		color: #374151;
		font-size: 13.5px;
		text-decoration: none;
	}

	.portal-pay-qr {
		display: flex;
		gap: 22px;
		align-items: flex-start;
		flex-wrap: wrap;
	}

	.portal-pay-qr-box {
		width: 190px;
		height: 190px;
		border: 1px dashed #d1d5db;
		border-radius: 10px;
		display: flex;
		align-items: center;
		justify-content: center;
		background: #fbfdfd;
		overflow: hidden;
	}

	.portal-pay-qr-box img {
		width: 100%;
		height: 100%;
		object-fit: contain;
	}

	.portal-pay-qr-box a {
		color: #0F766E;
		font-size: 13px;
		padding: 0 12px;
		text-align: center;
	}

	.portal-pay-qr-empty {
		color: #9ca3af;
		font-size: 12px;
		padding: 0 14px;
		text-align: center;
		margin: 0;
	}

	.portal-pay-info {
		list-style: none;
		margin: 0;
		padding: 0;
		flex: 1;
		min-width: 200px;
	}

	.portal-pay-info li {
		display: flex;
		justify-content: space-between;
		padding: 7px 0;
		border-bottom: 1px dashed #eef2f2;
		font-size: 13px;
		color: #6b7280;
	}

	.portal-pay-info b {
		color: #111827;
	}

	.portal-pay-info b.amount {
		color: #B45309;
		font-size: 16px;
	}

	.portal-pay-tip {
		margin: 14px 0 0;
		padding: 10px 12px;
		background: #fffbeb;
		border-radius: 8px;
		color: #92400e;
		font-size: 12.5px;
		line-height: 1.7;
	}

	.portal-pay-proof {
		margin-top: 20px;
		padding-top: 16px;
		border-top: 1px solid #eef2f2;
	}

	.portal-pay-proof h4 {
		margin: 0 0 12px;
		font-size: 14px;
		color: #111827;
	}

	.portal-pay-form {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
		gap: 12px;
		margin-bottom: 14px;
	}

	.portal-pay-form label {
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 12.5px;
		color: #6b7280;
	}

	.portal-pay-form input[type=text],
	.portal-pay-form input[type=datetime-local] {
		padding: 8px 10px;
		border: 1px solid #e5e7eb;
		border-radius: 7px;
		font-size: 13px;
		outline-color: #0F766E;
	}

	.portal-pay-upload input {
		padding: 6px 0;
		font-size: 12px;
	}

	.portal-pay-upload em {
		font-style: normal;
		color: #9ca3af;
		font-size: 11.5px;
	}

	.portal-pay-actions {
		margin-top: 14px;
		display: flex;
		align-items: center;
	}

	.portal-pay-refund {
		margin-top: 18px;
		padding-top: 14px;
		border-top: 1px solid #eef2f2;
		text-align: right;
	}

	.portal-pay-link {
		border: none;
		background: none;
		color: #9ca3af;
		font-size: 12.5px;
		cursor: pointer;
		text-decoration: underline;
	}
</style>
