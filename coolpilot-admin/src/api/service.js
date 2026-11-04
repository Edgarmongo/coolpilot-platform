import request from '@/util/request'

// ---------------- 服务商品扩展 ----------------

export function getServiceItem(productId) {
	return request({url: `serviceItem/${productId}`, method: 'GET'})
}

export function saveServiceItem(productId, data) {
	return request({url: `serviceItem/${productId}`, method: 'PUT', data})
}

// ---------------- 需求 ----------------

export function getDemands(params) {
	return request({url: 'demands', method: 'GET', params})
}

export function getDemand(demandNo) {
	return request({url: `demand/${demandNo}`, method: 'GET'})
}

export function reviewDemand(demandNo, data) {
	return request({url: `demand/${demandNo}/review`, method: 'POST', data})
}

export function moveDemand(demandNo, data) {
	return request({url: `demand/${demandNo}/status`, method: 'PUT', data})
}

// ---------------- 报价 ----------------

export function createQuote(data) {
	return request({url: 'quote', method: 'POST', data})
}

export function updateQuote(id, data) {
	return request({url: `quote/${id}`, method: 'PUT', data})
}

export function sendQuote(id) {
	return request({url: `quote/${id}/send`, method: 'POST'})
}

// ---------------- 项目 / 里程碑 ----------------

export function getProjects(params) {
	return request({url: 'projects', method: 'GET', params})
}

export function getProject(projectNo) {
	return request({url: `project/${projectNo}`, method: 'GET'})
}

export function createProject(data) {
	return request({url: 'project', method: 'POST', data})
}

export function moveProject(projectNo, data) {
	return request({url: `project/${projectNo}/status`, method: 'PUT', data})
}

export function addMilestone(data) {
	return request({url: 'milestone', method: 'POST', data})
}

export function updateMilestone(id, data) {
	return request({url: `milestone/${id}`, method: 'PUT', data})
}

export function deliverMilestone(id, data) {
	return request({url: `milestone/${id}/deliver`, method: 'POST', data})
}
