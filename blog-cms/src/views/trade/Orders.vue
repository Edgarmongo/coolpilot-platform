<template>
	<div>
		<!--搜索-->
		<el-form inline>
			<el-form-item label="状态">
				<el-select v-model="queryInfo.status" size="small" clearable placeholder="全部" style="width: 130px"
				           @change="search">
					<el-option label="待支付" value="PENDING_PAY"></el-option>
					<el-option label="待确认收款" value="PENDING_CONFIRM"></el-option>
					<el-option label="已完成" value="COMPLETED"></el-option>
					<el-option label="退款中" value="REFUNDING"></el-option>
					<el-option label="已退款" value="REFUNDED"></el-option>
					<el-option label="已关闭" value="CLOSED"></el-option>
					<el-option label="已取消" value="CANCELLED"></el-option>
				</el-select>
			</el-form-item>
			<el-form-item label="关键词">
				<el-input v-model="queryInfo.keyword" size="small" clearable placeholder="订单号 / 商品名"
				          style="width: 200px" @keyup.enter.native="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" size="small" icon="el-icon-search" @click="search">搜索</el-button>
				<el-button size="small" icon="el-icon-refresh" @click="reset">重置</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="orderList" v-loading="loading">
			<el-table-column type="expand">
				<template v-slot="props">
					<div class="order-expand">
						<div class="order-expand-col">
							<h4>商品</h4>
							<p v-for="item in props.row.items" :key="item.id">
								{{ item.titleSnapshot }} × {{ item.quantity }}
								<span class="muted">¥{{ item.priceSnapshot }}</span>
							</p>
						</div>
						<div class="order-expand-col">
							<h4>支付单</h4>
							<p v-if="!props.row.paymentList || !props.row.paymentList.length" class="muted">尚未创建支付单</p>
							<p v-for="pay in props.row.paymentList" :key="pay.paymentNo">
								{{ pay.paymentNo }} · {{ channelText(pay.channel) }} · {{ payStatusText(pay.status) }}
								<span v-if="pay.payerName" class="muted">付款人 {{ pay.payerName }}</span>
							</p>
						</div>
					</div>
				</template>
			</el-table-column>
			<el-table-column label="订单号" prop="orderNo" width="200"></el-table-column>
			<el-table-column label="内容" prop="titleSummary" show-overflow-tooltip></el-table-column>
			<el-table-column label="金额" width="110">
				<template v-slot="scope"><span class="amount">¥{{ scope.row.payAmount }}</span></template>
			</el-table-column>
			<el-table-column label="状态" width="120">
				<template v-slot="scope">
					<el-tag size="small" :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="下单时间" width="170">
				<template v-slot="scope">{{ scope.row.createTime | dateFormat }}</template>
			</el-table-column>
			<el-table-column label="操作" width="220">
				<template v-slot="scope">
					<el-button size="mini" type="text" @click="openDetail(scope.row.orderNo)">详情</el-button>
					<el-button v-if="canConfirm(scope.row)" size="mini" type="success" @click="confirmRow(scope.row)">
						确认收款
					</el-button>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
		               :current-page="queryInfo.pageNum" :page-sizes="[10, 20, 30, 50]"
		               :page-size="queryInfo.pageSize" :total="total"
		               layout="total, sizes, prev, pager, next, jumper" background>
		</el-pagination>

		<!--详情-->
		<el-dialog title="订单详情" :visible.sync="detailVisible" width="640px">
			<div v-if="detail">
				<div class="detail-grid">
					<div><span>订单号</span>{{ detail.order.orderNo }}</div>
					<div><span>状态</span>{{ statusText(detail.order.status) }}</div>
					<div><span>金额</span>¥{{ detail.order.payAmount }}</div>
					<div><span>支付时间</span>{{ detail.order.paidTime | dateFormat }}</div>
				</div>

				<h4 class="detail-title">支付记录</h4>
				<el-table :data="detail.payments" size="mini">
					<el-table-column prop="paymentNo" label="支付单号" width="190"></el-table-column>
					<el-table-column label="渠道" width="110">
						<template v-slot="scope">{{ channelText(scope.row.channel) }}</template>
					</el-table-column>
					<el-table-column label="状态" width="110">
						<template v-slot="scope">{{ payStatusText(scope.row.status) }}</template>
					</el-table-column>
					<el-table-column label="付款人" prop="payerName" width="100"></el-table-column>
					<el-table-column label="转账时间" width="150">
						<template v-slot="scope">{{ scope.row.transferTime | dateFormat }}</template>
					</el-table-column>
					<el-table-column label="凭证" width="80">
						<template v-slot="scope">
							<el-button v-if="scope.row.proofFileId" type="text" size="mini"
							           @click="viewProof(scope.row.proofFileId)">查看
							</el-button>
							<span v-else class="muted">无</span>
						</template>
					</el-table-column>
				</el-table>

				<h4 class="detail-title">权益</h4>
				<el-table :data="detail.entitlements" size="mini">
					<el-table-column prop="productTitle" label="商品"></el-table-column>
					<el-table-column prop="entType" label="类型" width="100"></el-table-column>
					<el-table-column prop="status" label="状态" width="100"></el-table-column>
				</el-table>
			</div>
		</el-dialog>
	</div>
</template>

<script>
	import {confirmPayment, getFileToken, getOrderDetail, getOrderList} from "@/api/trade";

	export default {
		name: "OrderManage",
		data() {
			return {
				loading: false,
				queryInfo: {status: '', keyword: '', pageNum: 1, pageSize: 10},
				orderList: [],
				total: 0,
				detailVisible: false,
				detail: null
			}
		},
		created() {
			this.getData()
		},
		methods: {
			getData() {
				this.loading = true
				getOrderList(this.queryInfo).then(res => {
					const list = res.data.list || []
					// 行内展示支付单：额外拉一次详情，订单量小，实现简单可靠
					Promise.all(list.map(o => getOrderDetail(o.orderNo).then(r => r.data).catch(() => null)))
						.then(details => {
							list.forEach((order, i) => {
								order.paymentList = details[i] ? details[i].payments : []
							})
							this.orderList = list
						})
					this.total = res.data.total || 0
				}).finally(() => {
					this.loading = false
				})
			},
			search() {
				this.queryInfo.pageNum = 1
				this.getData()
			},
			reset() {
				this.queryInfo = {status: '', keyword: '', pageNum: 1, pageSize: 10}
				this.getData()
			},
			handleSizeChange(size) {
				this.queryInfo.pageSize = size
				this.getData()
			},
			handleCurrentChange(page) {
				this.queryInfo.pageNum = page
				this.getData()
			},
			openDetail(orderNo) {
				getOrderDetail(orderNo).then(res => {
					this.detail = res.data
					this.detailVisible = true
				})
			},
			canConfirm(order) {
				return order.status === 'PENDING_CONFIRM'
			},
			confirmRow(order) {
				getOrderDetail(order.orderNo).then(res => {
					const payments = res.data.payments || []
					const pay = payments.find(p => p.status === 'WAIT_CONFIRM') || payments.find(p => p.status === 'INIT')
					if (!pay) {
						this.$message.warning('该订单没有待确认的支付单')
						return
					}
					const who = pay.payerName ? `付款人：${pay.payerName}` : '用户尚未填写付款人'
					this.$confirm(`确认已收到 ¥${pay.amount}？<br/>${who}<br/>支付单：${pay.paymentNo}`, '确认收款', {
						dangerouslyUseHTMLString: true,
						type: 'warning'
					}).then(() => {
						confirmPayment(pay.paymentNo, {}).then(r => {
							this.$message.success(r.data.message || '已确认')
							this.getData()
						})
					}).catch(() => {
					})
				})
			},
			viewProof(fileId) {
				getFileToken(fileId).then(res => {
					window.open(`/api/file/download?token=${encodeURIComponent(res.data.token)}`)
				})
			},
			statusText(status) {
				return {
					PENDING_PAY: '待支付', PENDING_CONFIRM: '待确认收款', PAID: '已支付', COMPLETED: '已完成',
					CANCELLED: '已取消', CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款'
				}[status] || status
			},
			statusTag(status) {
				return {
					PENDING_PAY: 'info', PENDING_CONFIRM: 'warning', PAID: 'success',
					COMPLETED: 'success', CANCELLED: 'info', CLOSED: 'info', REFUNDING: 'danger', REFUNDED: 'danger'
				}[status] || ''
			},
			payStatusText(status) {
				return {INIT: '待付款', WAIT_CONFIRM: '待确认', SUCCESS: '已成功', FAIL: '失败', CLOSED: '已关闭'}[status] || status
			},
			channelText(channel) {
				return {
					MANUAL_WECHAT: '微信转账', MANUAL_ALIPAY: '支付宝转账', MANUAL_PAYPAL: 'PayPal'
				}[channel] || channel
			}
		}
	}
</script>

<style scoped>
	.el-pagination {
		margin-top: 16px;
	}

	.order-expand {
		display: flex;
		gap: 40px;
		padding: 4px 20px 10px;
		flex-wrap: wrap;
	}

	.order-expand-col {
		flex: 1;
		min-width: 240px;
	}

	.order-expand-col h4 {
		margin: 0 0 6px;
		font-size: 13px;
		color: #606266;
	}

	.order-expand-col p {
		margin: 3px 0;
		font-size: 13px;
		color: #303133;
	}

	.muted {
		color: #909399;
		margin-left: 6px;
	}

	.amount {
		color: #B45309;
		font-weight: 600;
	}

	.detail-title {
		margin: 18px 0 8px;
		font-size: 13px;
		color: #606266;
	}

	.detail-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px 20px;
		padding: 12px 14px;
		background: #fafafa;
		border-radius: 6px;
		font-size: 13px;
		color: #303133;
	}

	.detail-grid span {
		display: inline-block;
		width: 70px;
		color: #909399;
	}
</style>
