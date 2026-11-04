import request from '@/util/request'

export function login(data) {
	return request({
		url: 'login',
		method: 'POST',
		data
	})
}
