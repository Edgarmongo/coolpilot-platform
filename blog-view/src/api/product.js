import axios from '@/plugins/axios'

export function getProducts(params) {
	return axios({
		url: 'products',
		method: 'GET',
		params
	})
}

export function getProductCategories(type) {
	return axios({
		url: 'productCategories',
		method: 'GET',
		params: {type}
	})
}

export function getProduct(productNo) {
	return axios({
		url: `product/${productNo}`,
		method: 'GET'
	})
}

export function getProductCatalog(productNo) {
	return axios({
		url: `product/${productNo}/catalog`,
		method: 'GET'
	})
}

export function getDownloadToken(fileId) {
	return axios({
		url: 'file/token',
		method: 'GET',
		params: {fileId}
	})
}
