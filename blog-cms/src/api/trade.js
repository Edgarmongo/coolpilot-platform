import axios from 'axios'
import request from '@/util/request'

/*
 * request 的 baseURL 是 /api/admin/，所以这里的路径不要再写 admin 前缀。
 * 文件凭证接口不在 /admin 下，单独用一个裸实例。
 */
const raw = axios.create({
	baseURL: '/api/',
	timeout: 5000
})

raw.interceptors.request.use(config => {
	const token = window.localStorage.getItem('token')
	if (token) {
		config.headers.Authorization = token
	}
	return config
})

raw.interceptors.response.use(response => response.data)

// ---------------- 订单 ----------------

export function getOrderList(query) {
	return request({
		url: 'orders',
		method: 'GET',
		params: query
	})
}

export function getOrderDetail(orderNo) {
	return request({
		url: `order/${orderNo}`,
		method: 'GET'
	})
}

// ---------------- 支付 ----------------

export function getPaymentList(query) {
	return request({
		url: 'payments',
		method: 'GET',
		params: query
	})
}

export function confirmPayment(paymentNo, params) {
	return request({
		url: `payment/${paymentNo}/confirm`,
		method: 'POST',
		params
	})
}

export function closePayment(paymentNo, remark) {
	return request({
		url: `payment/${paymentNo}/close`,
		method: 'POST',
		params: {remark}
	})
}

// ---------------- 退款 ----------------

export function getRefundList(query) {
	return request({
		url: 'refunds',
		method: 'GET',
		params: query
	})
}

export function approveRefund(refundNo, remark) {
	return request({
		url: `refund/${refundNo}/approve`,
		method: 'POST',
		params: {remark}
	})
}

export function rejectRefund(refundNo, remark) {
	return request({
		url: `refund/${refundNo}/reject`,
		method: 'POST',
		params: {remark}
	})
}

export function finishRefund(refundNo, remark) {
	return request({
		url: `refund/${refundNo}/finish`,
		method: 'POST',
		params: {remark}
	})
}

// ---------------- 权益 ----------------

export function getEntitlementList(query) {
	return request({
		url: 'entitlements',
		method: 'GET',
		params: query
	})
}

export function grantEntitlement(data) {
	return request({
		url: 'entitlement/grant',
		method: 'POST',
		data
	})
}

export function revokeEntitlement(data) {
	return request({
		url: 'entitlement/revoke',
		method: 'POST',
		data
	})
}

export function getEntitlementLogs(query) {
	return request({
		url: 'entitlement/logs',
		method: 'GET',
		params: query
	})
}

// ---------------- 统计 ----------------

export function getSalesStats() {
	return request({
		url: 'stats/sales',
		method: 'GET'
	})
}

// ---------------- 商品下拉（发放权益时选商品） ----------------

export function getProductOptions() {
	return request({
		url: 'products',
		method: 'GET',
		params: {pageNum: 1, pageSize: 100}
	})
}

// ---------------- 文件凭证 ----------------

export function getFileToken(fileId) {
	return raw.get('file/token', {params: {fileId}})
}
