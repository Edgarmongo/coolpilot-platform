import request from '@/util/request'

export function getBlogList(params) {
	return request({url: 'blogs', method: 'GET', params})
}

export function getBlog(id) {
	return request({url: 'blog', method: 'GET', params: {id}})
}

export function saveBlog(data) {
	return request({url: 'blog', method: 'POST', data})
}

export function updateBlog(data) {
	return request({url: 'blog', method: 'PUT', data})
}

export function deleteBlog(id) {
	return request({url: 'blog', method: 'DELETE', params: {id}})
}

export function getCategoryAndTag() {
	return request({url: 'categoryAndTag', method: 'GET'})
}

export function updateTop(id, top) {
	return request({url: 'blog/top', method: 'PUT', params: {id, top}})
}

export function updateRecommend(id, recommend) {
	return request({url: 'blog/recommend', method: 'PUT', params: {id, recommend}})
}

export function updateVisibility(id, data) {
	return request({url: `blog/${id}/visibility`, method: 'PUT', data})
}
