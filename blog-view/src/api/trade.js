import axios from '@/plugins/axios'

// ---------------- 订单 ----------------

export function createOrder(data) {
	return axios({
		url: 'order',
		method: 'POST',
		data
	})
}

export function getOrder(orderNo) {
	return axios({
		url: `order/${orderNo}`,
		method: 'GET'
	})
}

export function getOrders(params) {
	return axios({
		url: 'orders',
		method: 'GET',
		params
	})
}

export function cancelOrder(orderNo) {
	return axios({
		url: `order/${orderNo}/cancel`,
		method: 'POST'
	})
}

// ---------------- 支付 ----------------

export function getPayChannels() {
	return axios({
		url: 'payment/channels',
		method: 'GET'
	})
}

export function createPayment(data) {
	return axios({
		url: 'payment',
		method: 'POST',
		data
	})
}

export function getPayment(paymentNo) {
	return axios({
		url: `payment/${paymentNo}`,
		method: 'GET'
	})
}

export function submitTransferProof(paymentNo, data) {
	return axios({
		url: `payment/${paymentNo}/manual-confirm`,
		method: 'POST',
		data
	})
}

// ---------------- 退款 ----------------

export function applyRefund(data) {
	return axios({
		url: 'refund',
		method: 'POST',
		data
	})
}

// ---------------- 权益 ----------------

export function getMyEntitlements() {
	return axios({
		url: 'entitlements',
		method: 'GET'
	})
}

export function getResourceAccess(productNo) {
	return axios({
		url: 'resource/access',
		method: 'GET',
		params: {productNo}
	})
}

// ---------------- 上传（付款凭证） ----------------

export function uploadProof(file) {
	const formData = new FormData()
	formData.append('file', file)
	formData.append('bizType', 'PROOF')
	return axios({
		url: 'file/upload',
		method: 'POST',
		data: formData,
		headers: {'Content-Type': 'multipart/form-data'}
	})
}
