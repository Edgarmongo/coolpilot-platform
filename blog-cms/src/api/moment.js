import request from '@/util/request'

export function getMomentList(params) {
	return request({url: 'moments', method: 'GET', params})
}

export function saveMoment(data) {
	return request({url: 'moment', method: 'POST', data})
}

export function updateMoment(data) {
	return request({url: 'moment', method: 'PUT', data})
}

export function updateMomentPublished(id, published) {
	return request({url: 'moment/published', method: 'PUT', params: {id, published}})
}

export function deleteMoment(id) {
	return request({url: 'moment', method: 'DELETE', params: {id}})
}
