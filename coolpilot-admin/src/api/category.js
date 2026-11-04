import request from '@/util/request'

export function getCategoryList(params) {
	return request({url: 'categories', method: 'GET', params})
}

export function addCategory(data) {
	return request({url: 'category', method: 'POST', data})
}

export function editCategory(data) {
	return request({url: 'category', method: 'PUT', data})
}

export function deleteCategory(id) {
	return request({url: 'category', method: 'DELETE', params: {id}})
}
