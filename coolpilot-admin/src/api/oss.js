import request from '@/util/request'

/**
 * 开源项目商业观察（栏目后台）。
 *
 * 标签与关联文章随保存接口一起全量提交，没有单独的接口 ——
 * 后台编辑页是整表保存，多两个接口只会出现「正文存了、标签没存」的中间态。
 */
export function getOssProjectList(params) {
	return request({url: 'oss/projects', method: 'GET', params})
}

export function getOssProject(id) {
	return request({url: `oss/project/${id}`, method: 'GET'})
}

export function createOssProject(data) {
	return request({url: 'oss/project', method: 'POST', data})
}

export function updateOssProject(data) {
	return request({url: 'oss/project', method: 'PUT', data})
}

export function publishOssProject(id) {
	return request({url: `oss/project/${id}/publish`, method: 'POST'})
}

export function archiveOssProject(id) {
	return request({url: `oss/project/${id}/archive`, method: 'POST'})
}
