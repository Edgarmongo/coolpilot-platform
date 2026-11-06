import request from '@/util/request'

/**
 * 注册用户列表
 * @param params {keyword, status, pageNum, pageSize}
 */
export function getUserList(params) {
	return request({url: 'users', method: 'GET', params})
}

/**
 * 启用 / 禁用账号：禁用会同时让该账号已登录的会话失效
 * @param status 0 启用，1 禁用
 */
export function updateUserStatus(id, status) {
	return request({url: `user/${id}/status`, method: 'PUT', params: {status}})
}

/**
 * 重置前台用户密码（邮件服务不可用时的兜底）
 * 后端会同时让该账号已登录的会话失效
 */
export function resetUserPassword(id, password) {
	return request({url: `user/${id}/password`, method: 'PUT', data: {password}})
}
