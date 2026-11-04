<template>
	<div>
		<el-form inline>
			<el-form-item label="状态">
				<el-select v-model="queryInfo.status" size="small" clearable placeholder="全部" style="width: 130px"
				           @change="search">
					<el-option label="待审核" value="APPLIED"></el-option>
					<el-option label="已同意待打款" value="APPROVED"></el-option>
					<el-option label="已退款" value="SUCCESS"></el-option>
					<el-option label="已拒绝" value="REJECTED"></el-option>
				</el-select>
			</el-form-item>
			<el-form-item label="关键词">
				<el-input v-model="queryInfo.keyword" size="small" clearable placeholder="退款单号 / 订单号"
				          style="width: 200px" @keyup.enter.native="search"/>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" size="small" icon="el-icon-search" @click="search">搜索</el-button>
			</el-form-item>
		</el-form>

		<el-alert type="info" :closable="false" show-icon
		          title="流程：同意申请 → 线下打款 → 确认已退款（确认后自动撤销该订单发放的权益并回滚销量）"
		          style="margin-bottom: 12px"/>

		<el-table :data="refundList" v-loading="loading">
			<el-table-column label="退款单号" prop="refundNo" width="190"></el-table-column>
			<el-table-column label="订单号" prop="bizNo" width="190"></el-table-column>
			<el-table-column label="金额" width="100">
				<template v-slot="scope"><span class="amount">¥{{ scope.row.amount }}</span></template>
			</el-table-column>
			<el-table-column label="原因" prop="reason" show-overflow-tooltip></el-table-column>
			<el-table-column label="状态" width="120">
				<template v-slot="scope">
					<el-tag size="small" :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="申请时间" width="170">
				<template v-slot="scope">{{ scope.row.applyTime | dateFormat }}</template>
			</el-table-column>
			<el-table-column label="操作" width="230">
				<template v-slot="scope">
					<template v-if="scope.row.status === 'APPLIED'">
						<el-button size="mini" type="success" @click="approve(scope.row)">同意</el-button>
						<el-button size="mini" type="danger" @click="reject(scope.row)">拒绝</el-button>
					</template>
					<el-button v-else-if="scope.row.status === 'APPROVED'" size="mini" type="primary"
					           @click="finish(scope.row)">确认已退款
					</el-button>
					<span v-else class="muted">{{ scope.row.remark || '-' }}</span>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
		               :current-page="queryInfo.pageNum" :page-sizes="[10, 20, 30, 50]"
		               :page-size="queryInfo.pageSize" :total="total"
		               layout="total, sizes, prev, pager, next, jumper" background>
		</el-pagination>
	</div>
</template>

<script>
	import {approveRefund, finishRefund, getRefundList, rejectRefund} from "@/api/trade";

	export default {
		name: "RefundManage",
		data() {
			return {
				loading: false,
				queryInfo: {status: '', keyword: '', pageNum: 1, pageSize: 10},
				refundList: [],
				total: 0
			}
		},
		created() {
			this.getData()
		},
		methods: {
			getData() {
				this.loading = true
				getRefundList(this.queryInfo).then(res => {
					this.refundList = res.data.list || []
					this.total = res.data.total || 0
				}).finally(() => {
					this.loading = false
				})
			},
			search() {
				this.queryInfo.pageNum = 1
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
			approve(row) {
				this.$confirm(`同意退款 ¥${row.amount}？同意后请尽快完成线下打款。`, '同意退款', {type: 'warning'})
					.then(() => approveRefund(row.refundNo, ''))
					.then(() => {
						this.$message.success('已同意，等待线下打款')
						this.getData()
					}).catch(() => {
				})
			},
			reject(row) {
				this.$prompt('请填写拒绝原因（会展示给用户）', '拒绝退款', {
					inputPlaceholder: '例如：已超出可退款期限',
					inputValidator: v => (v && v.trim() ? true : '原因不能为空')
				}).then(({value}) => rejectRefund(row.refundNo, value.trim()))
					.then(() => {
						this.$message.success('已拒绝')
						this.getData()
					}).catch(() => {
				})
			},
			finish(row) {
				this.$confirm(`确认已通过原渠道退款 ¥${row.amount}？确认后将撤销该订单的权益。`, '确认退款完成', {type: 'warning'})
					.then(() => finishRefund(row.refundNo, ''))
					.then(() => {
						this.$message.success('退款完成，权益已撤销')
						this.getData()
					}).catch(() => {
				})
			},
			statusText(status) {
				return {APPLIED: '待审核', APPROVED: '待打款', REFUNDING: '退款中', SUCCESS: '已退款', REJECTED: '已拒绝'}[status] || status
			},
			statusTag(status) {
				return {APPLIED: 'warning', APPROVED: 'primary', REFUNDING: 'primary', SUCCESS: 'success', REJECTED: 'danger'}[status] || ''
			}
		}
	}
</script>

<style scoped>
	.el-pagination {
		margin-top: 16px;
	}

	.amount {
		color: #B45309;
		font-weight: 600;
	}

	.muted {
		color: #909399;
		font-size: 12px;
	}
</style>
