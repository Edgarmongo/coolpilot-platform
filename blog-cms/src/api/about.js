import request from '@/util/request'

export function getAbout() {
	return request({url: 'about', method: 'GET'})
}

export function updateAbout(data) {
	return request({url: 'about', method: 'PUT', data})
}
