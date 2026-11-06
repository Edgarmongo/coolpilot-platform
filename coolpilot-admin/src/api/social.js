import request from '@/util/request'

export function getSocialAccounts() {
	return request({url: 'socials', method: 'GET'})
}

export function createSocialAccount(data) {
	return request({url: 'social', method: 'POST', data})
}

export function updateSocialAccount(id, data) {
	return request({url: `social/${id}`, method: 'PUT', data})
}

/** @param direction up / down */
export function moveSocialAccount(id, direction) {
	return request({url: `social/${id}/move`, method: 'PUT', params: {direction}})
}

export function changeSocialVisible(id, visible) {
	return request({url: `social/${id}/visible`, method: 'PUT', params: {visible}})
}

export function deleteSocialAccount(id) {
	return request({url: `social/${id}`, method: 'DELETE'})
}
