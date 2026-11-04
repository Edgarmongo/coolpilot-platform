import request, {rawRequest} from '@/util/request'

// ---------------- 订单 ----------------

export function getOrderList(params) {
	return request({url: 'orders', method: 'GET', params})
}

export function getOrderDetail(orderNo) {
	return request({url: `order/${orderNo}`, method: 'GET'})
}

export function closeExpiredOrders() {
	return request({url: 'order/closeExpired', method: 'POST'})
}

// ---------------- 支付 ----------------

export function getPaymentList(params) {
	return request({url: 'payments', method: 'GET', params})
}

export function confirmPayment(paymentNo, params = {}) {
	return request({url: `payment/${paymentNo}/confirm`, method: 'POST', params})
}

export function closePayment(paymentNo, remark = '') {
	return request({url: `payment/${paymentNo}/close`, method: 'POST', params: {remark}})
}

// ---------------- 退款 ----------------

export function getRefundList(params) {
	return request({url: 'refunds', method: 'GET', params})
}

export function approveRefund(refundNo, remark = '') {
	return request({url: `refund/${refundNo}/approve`, method: 'POST', params: {remark}})
}

export function rejectRefund(refundNo, remark) {
	return request({url: `refund/${refundNo}/reject`, method: 'POST', params: {remark}})
}

export function finishRefund(refundNo, remark = '') {
	return request({url: `refund/${refundNo}/finish`, method: 'POST', params: {remark}})
}

// ---------------- 权益 ----------------

export function getEntitlementList(params) {
	return request({url: 'entitlements', method: 'GET', params})
}

export function grantEntitlement(data) {
	return request({url: 'entitlement/grant', method: 'POST', data})
}

export function revokeEntitlement(data) {
	return request({url: 'entitlement/revoke', method: 'POST', data})
}

export function getEntitlementLogs(params) {
	return request({url: 'entitlement/logs', method: 'GET', params})
}

// ---------------- 统计 ----------------

export function getSalesStats() {
	return request({url: 'stats/sales', method: 'GET'})
}

// ---------------- 下拉数据 ----------------

export function getProductOptions() {
	return request({url: 'products', method: 'GET', params: {pageNum: 1, pageSize: 100}})
}

// ---------------- 文件 ----------------

export function getFileToken(fileId) {
	return rawRequest.get('file/token', {params: {fileId}})
}
