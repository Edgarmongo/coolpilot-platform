import request from '@/util/request'

/**
 * 反馈列表。status 可选：PENDING 待处理 / RESOLVED 已回复
 */
export function getFeedbackList(params) {
	return request({url: 'feedbacks', method: 'GET', params})
}

/**
 * 回复反馈。注意后端把 reply / status 定义为 query 参数（不是 body），
 * axios 的 params 会自动编码中文，不要改成 data。
 */
export function replyFeedback(id, reply, status = 'RESOLVED') {
	return request({url: `feedback/${id}/reply`, method: 'PUT', params: {reply, status}})
}
