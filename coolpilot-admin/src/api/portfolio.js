import request from '@/util/request'

export function getPortfolios() {
	return request({url: 'portfolios', method: 'GET'})
}

/** 列表不带 content，编辑时按 id 取全文 */
export function getPortfolio(id) {
	return request({url: `portfolio/${id}`, method: 'GET'})
}

export function createPortfolio(data) {
	return request({url: 'portfolio', method: 'POST', data})
}

export function updatePortfolio(id, data) {
	return request({url: `portfolio/${id}`, method: 'PUT', data})
}

/** @param direction up / down */
export function movePortfolio(id, direction) {
	return request({url: `portfolio/${id}/move`, method: 'PUT', params: {direction}})
}

export function changePortfolioVisible(id, visible) {
	return request({url: `portfolio/${id}/visible`, method: 'PUT', params: {visible}})
}

export function deletePortfolio(id) {
	return request({url: `portfolio/${id}`, method: 'DELETE'})
}
