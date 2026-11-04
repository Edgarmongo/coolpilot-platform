import axios from '@/plugins/axios'

/**
 * 收藏/点赞/消息/反馈
 * 说明：需要登录的接口由后端 SecurityConfig 校验，未登录返回 code=403
 */

export function addFavorite(targetType, targetId) {
	return axios({
		url: 'favorite',
		method: 'POST',
		params: {targetType, targetId}
	})
}

export function removeFavorite(targetType, targetId) {
	return axios({
		url: 'favorite',
		method: 'DELETE',
		params: {targetType, targetId}
	})
}

export function getFavoriteStatus(targetType, targetId) {
	return axios({
		url: 'favorite/status',
		method: 'GET',
		params: {targetType, targetId}
	})
}

export function getFavorites(pageNum) {
	return axios({
		url: 'favorites',
		method: 'GET',
		params: {pageNum, pageSize: 10}
	})
}

export function addLike(targetType, targetId) {
	return axios({
		url: 'like',
		method: 'POST',
		params: {targetType, targetId}
	})
}

export function removeLike(targetType, targetId) {
	return axios({
		url: 'like',
		method: 'DELETE',
		params: {targetType, targetId}
	})
}

export function getLikeStatus(targetType, targetId) {
	return axios({
		url: 'like/status',
		method: 'GET',
		params: {targetType, targetId}
	})
}

export function getMessages(pageNum) {
	return axios({
		url: 'messages',
		method: 'GET',
		params: {pageNum, pageSize: 10}
	})
}

export function getUnreadCount() {
	return axios({
		url: 'messages/unread-count',
		method: 'GET'
	})
}

export function readMessage(id) {
	return axios({
		url: `message/${id}/read`,
		method: 'PUT'
	})
}

export function submitFeedback(content, contact) {
	return axios({
		url: 'feedback',
		method: 'POST',
		params: {content, contact}
	})
}
