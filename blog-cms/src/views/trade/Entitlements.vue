<template>
	<div>
		<el-form inline>
			<el-form-item label="用户ID">
				<el-input v-model="queryInfo.userId" size="small" clearable placeholder="全部" style="width: 110px"/>
			</el-form-item>
			<el-form-item label="状态">
				<el-select v-model="queryInfo.status" size="small" clearable placeholder="全部" style="width: 120px">
					<el-option label="生效中" value="ACTIVE"></el-option>
					<el-option label="已过期" value="EXPIRED"></el-option>
					<el-option label="已撤销" value="REVOKED"></el-option>
				</el-select>
			</el-form-item>
			<el-form-item>
				<el-button type="primary" size="small" icon="el-icon-search" @click="search">搜索</el-button>
				<el-button type="success" size="small" icon="el-icon-plus" @click="openGrant">人工发放</el-button>
			</el-form-item>
		</el-form>

		<el-table :data="entList" v-loading="loading">
			<el-table-column label="用户ID" prop="userId" width="90"></el-table-column>
			<el-table-column label="商品" prop="productTitle" show-overflow-tooltip></el-table-column>
			<el-table-column label="类型" width="100">
				<template v-slot="scope">{{ entTypeText(scope.row.entType) }}</template>
			</el-table-column>
			<el-table-column label="来源" width="90">
				<template v-slot="scope">{{ scope.row.source === 'GIFT' ? '赠送' : '购买' }}</template>
			</el-table-column>
			<el-table-column label="来源订单" prop="orderNo" width="190"></el-table-column>
			<el-table-column label="有效期" width="180">
				<template v-slot="scope">
					{{ scope.row.expireTime ? '至 ' + formatDate(scope.row.expireTime) : '永久' }}
				</template>
			</el-table-column>
			<el-table-column label="状态" width="100">
				<template v-slot="scope">
					<el-tag size="small" :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
				</template>
			</el-table-column>
			<el-table-column label="操作" width="100">
				<template v-slot="scope">
					<el-button v-if="scope.row.status === 'ACTIVE'" size="mini" type="danger"
					           @click="revoke(scope.row)">撤销
					</el-button>
					<span v-else class="muted">-</span>
				</template>
			</el-table-column>
		</el-table>

		<el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
		               :current-page="queryInfo.pageNum" :page-sizes="[10, 20, 30, 50]"
		               :page-size="queryInfo.pageSize" :total="total"
		               layout="total, sizes, prev, pager, next, jumper" background>
		</el-pagination>

		<!--人工发放-->
		<el-dialog title="人工发放权益" :visible.sync="grantVisible" width="460px">
			<el-form label-width="90px" size="small">
				<el-form-item label="用户ID">
					<el-input v-model="grantForm.userId" placeholder="访客用户ID，可在用户列表查"></el-input>
				</el-form-item>
				<el-form-item label="商品">
					<el-select v-model="grantForm.productId" filterable placeholder="选择商品" style="width: 100%">
						<el-option v-for="p in products" :key="p.id" :label="p.title" :value="p.id"></el-option>
					</el-select>
				</el-form-item>
				<el-form-item label="权益类型">
					<el-select v-model="grantForm.entType" style="width: 100%">
						<el-option label="阅读（ACCESS）" value="ACCESS"></el-option>
						<el-option label="下载（DOWNLOAD）" value="DOWNLOAD"></el-option>
						<el-option label="学习（LEARN）" value="LEARN"></el-option>
					</el-select>
				</el-form-item>
				<el-form-item label="过期时间">
					<el-date-picker v-model="grantForm.expireTime" type="datetime" placeholder="留空表示永久"
					                value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%"></el-date-picker>
				</el-form-item>
				<el-form-item label="原因">
					<el-input v-model="grantForm.reason" placeholder="例如：活动赠送"></el-input>
				</el-form-item>
			</el-form>
			<span slot="footer">
				<el-button @click="grantVisible = false">取消</el-button>
				<el-button type="primary" @click="submitGrant">确认发放</el-button>
			</span>
		</el-dialog>
	</div>
</template>

<script>
	import {getEntitlementList, getProductOptions, grantEntitlement, revokeEntitlement} from "@/api/trade";

	export default {
		name: "EntitlementManage",
		data() {
			return {
				loading: false,
				queryInfo: {userId: '', status: '', pageNum: 1, pageSize: 10},
				entList: [],
				total: 0,
				grantVisible: false,
				grantForm: {userId: '', productId: null, entType: 'ACCESS', expireTime: '', reason: ''},
				products: []
			}
		},
		created() {
			this.getData()
		},
		methods: {
			getData() {
				this.loading = true
				getEntitlementList(this.queryInfo).then(res => {
					this.entList = res.data.list || []
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
			openGrant() {
				this.grantForm = {userId: '', productId: null, entType: 'ACCESS', expireTime: '', reason: ''}
				this.grantVisible = true
				if (!this.products.length) {
					getProductOptions().then(res => {
						this.products = res.data.list || []
					})
				}
			},
			submitGrant() {
				if (!this.grantForm.userId) {
					this.$message.warning('请填写用户ID')
					return
				}
				if (!this.grantForm.productId) {
					this.$message.warning('请选择商品')
					return
				}
				grantEntitlement({
					userId: Number(this.grantForm.userId),
					productId: this.grantForm.productId,
					entType: this.grantForm.entType,
					expireTime: this.grantForm.expireTime || null,
					reason: this.grantForm.reason || '后台人工发放'
				}).then(() => {
					this.$message.success('发放成功')
					this.grantVisible = false
					this.getData()
				})
			},
			revoke(row) {
				this.$prompt('撤销原因（会写入流水，必填）', '撤销权益', {
					inputPlaceholder: '例如：违规分享',
					inputValidator: v => (v && v.trim() ? true : '原因不能为空')
				}).then(({value}) => revokeEntitlement({id: row.id, reason: value.trim()}))
					.then(() => {
						this.$message.success('已撤销')
						this.getData()
					}).catch(() => {
				})
			},
			entTypeText(type) {
				return {ACCESS: '阅读', DOWNLOAD: '下载', LEARN: '学习'}[type] || type
			},
			statusText(status) {
				return {ACTIVE: '生效中', EXPIRED: '已过期', REVOKED: '已撤销'}[status] || status
			},
			statusTag(status) {
				return {ACTIVE: 'success', EXPIRED: 'info', REVOKED: 'danger'}[status] || ''
			},
			formatDate(value) {
				if (!value) {
					return ''
				}
				const d = new Date(value)
				return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
			}
		}
	}
</script>

<style scoped>
	.el-pagination {
		margin-top: 16px;
	}

	.muted {
		color: #909399;
		font-size: 12px;
	}
</style>
