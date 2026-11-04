import request from '@/util/request'

export function getTagList(params) {
	return request({url: 'tags', method: 'GET', params})
}

export function addTag(data) {
	return request({url: 'tag', method: 'POST', data})
}

export function editTag(data) {
	return request({url: 'tag', method: 'PUT', data})
}

export function deleteTag(id) {
	return request({url: 'tag', method: 'DELETE', params: {id}})
}
