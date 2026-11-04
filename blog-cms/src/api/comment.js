import request from '@/util/request'

export function getCommentList(params) {
	return request({url: 'comments', method: 'GET', params})
}

export function updateCommentPublished(id, published) {
	return request({url: 'comment/published', method: 'PUT', params: {id, published}})
}

export function deleteComment(id) {
	return request({url: 'comment', method: 'DELETE', params: {id}})
}
