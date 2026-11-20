import request from '@/util/request'

/**
 * 管理驾驶舱接口。
 *
 * 八个接口按业务域聚合，不按指标拆。
 * 统一传 silent，由页面自己决定怎么展示错误 —— 仪表盘一次并发 8 个请求，
 * 如果每个失败都弹一次 ElMessage，用户会被 8 个提示糊住。
 */

function dash(name, params) {
	return request({url: `dashboard/${name}`, method: 'GET', params, silent: true})
}

/** 经营总览：成交、退款、净收入、待确认、订单数、客单价 */
export function getOverview(params) {
	return dash('overview', params)
}

/** 经营趋势：成交/退款/净流入/订单/用户，缺失时间桶已在服务端补零 */
export function getTrends(params) {
	return dash('trends', params)
}

/** 业务待办：八类待处理事项 */
export function getTodos() {
	return dash('todos')
}

/** 内容运营 */
export function getContent(params) {
	return dash('content', params)
}

/** 用户运营 */
export function getUsers(params) {
	return dash('users', params)
}

/** 商品运营 */
export function getProducts(params) {
	return dash('products', params)
}

/** 服务交付 */
export function getServices(params) {
	return dash('services', params)
}

/** 平台健康 */
export function getHealth() {
	return dash('health')
}
