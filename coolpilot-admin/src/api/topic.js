import request from '@/util/request'

export function getTopicList(params) {
	return request({url: 'topics', method: 'GET', params})
}

export function getTopic(id) {
	return request({url: `topic/${id}`, method: 'GET'})
}

export function createTopic(data) {
	return request({url: 'topic', method: 'POST', data})
}

export function updateTopic(id, data) {
	return request({url: `topic/${id}`, method: 'PUT', data})
}

export function updateTopicStatus(id, published) {
	return request({url: `topic/${id}/status`, method: 'PUT', params: {published}})
}

export function deleteTopic(id) {
	return request({url: `topic/${id}`, method: 'DELETE'})
}
