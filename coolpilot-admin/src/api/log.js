import request from '@/util/request'

export function getVisitLogs(params) {
	return request({url: 'visitLogs', method: 'GET', params})
}

export function getLoginLogs(params) {
	return request({url: 'loginLogs', method: 'GET', params})
}

export function getOperationLogs(params) {
	return request({url: 'operationLogs', method: 'GET', params})
}

export function getExceptionLogs(params) {
	return request({url: 'exceptionLogs', method: 'GET', params})
}

export function deleteLog(type, id) {
	return request({url: type, method: 'DELETE', params: {id}})
}
