import request from '@/util/request'

export function getSiteSettings() {
	return request({url: 'siteSettings', method: 'GET'})
}

/**
 * @param data {settings: 全量配置数组, deleteIds: 要删除的 id 数组}
 */
export function updateSiteSettings(data) {
	return request({url: 'siteSettings', method: 'POST', data})
}
