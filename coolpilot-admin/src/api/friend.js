import request from '@/util/request'

export function getFriendList(params) {
	return request({url: 'friends', method: 'GET', params})
}

export function addFriend(data) {
	return request({url: 'friend', method: 'POST', data})
}

export function editFriend(data) {
	return request({url: 'friend', method: 'PUT', data})
}

export function updateFriendPublished(id, published) {
	return request({url: 'friend/published', method: 'PUT', params: {id, published}})
}

export function deleteFriend(id) {
	return request({url: 'friend', method: 'DELETE', params: {id}})
}
