import request from '@/util/request'

export function getFiles(params) {
	return request({url: 'files', method: 'GET', params})
}

export function getFileStats() {
	return request({url: 'files/stats', method: 'GET'})
}

export function getFileReferences(id) {
	return request({url: `files/${id}/references`, method: 'GET'})
}

export function deleteFile(id) {
	return request({url: `files/${id}`, method: 'DELETE'})
}
