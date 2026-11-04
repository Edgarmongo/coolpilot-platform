import request, {rawRequest} from '@/util/request'

export function updateAccount(data) {
	return request({url: 'account', method: 'POST', data})
}

/**
 * 登出：让服务端吊销当前身份的 token。
 * 接口在 /me 下（不在 /admin），baseURL 已带 /api/admin/，所以用裸实例。
 */
export function logout() {
	return rawRequest({url: 'me/logout', method: 'POST'})
}
