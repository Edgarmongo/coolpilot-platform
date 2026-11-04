import request from '@/util/request'

export function updateAccount(data) {
	return request({url: 'account', method: 'POST', data})
}
