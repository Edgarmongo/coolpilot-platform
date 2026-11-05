import request from '@/util/request'

export function getNavMenus() {
	return request({url: 'navs', method: 'GET'})
}

export function createNavMenu(data) {
	return request({url: 'nav', method: 'POST', data})
}

export function updateNavMenu(id, data) {
	return request({url: `nav/${id}`, method: 'PUT', data})
}

/** @param direction up / down */
export function moveNavMenu(id, direction) {
	return request({url: `nav/${id}/move`, method: 'PUT', params: {direction}})
}

export function changeNavVisible(id, visible) {
	return request({url: `nav/${id}/visible`, method: 'PUT', params: {visible}})
}

export function deleteNavMenu(id) {
	return request({url: `nav/${id}`, method: 'DELETE'})
}
