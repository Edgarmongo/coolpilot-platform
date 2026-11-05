import request, {rawRequest} from '@/util/request'

export function getAiStats() {
	return request({url: 'ai/stats', method: 'GET'})
}

/**
 * 全量重建知识库索引（文章 / 商品文案 / 课时正文）
 */
export function reindexKnowledge() {
	return request({url: 'ai/reindex', method: 'POST'})
}

/**
 * 检索自测：接口是公开的 /ai/search，不在 /admin 下，用裸实例
 */
export function searchAi(params) {
	return rawRequest({url: 'ai/search', method: 'GET', params})
}

/**
 * 当前问答模型配置（不返回密钥）
 */
export function getLlmStatus() {
	return request({url: 'ai/llm', method: 'GET'})
}

/**
 * 模型连通性自测
 */
export function testLlm() {
	return request({url: 'ai/llm/test', method: 'POST'})
}

/**
 * 问答会话列表（后台排查：用户问了什么、有没有命中）
 */
export function getChatLogs(params) {
	return request({url: 'ai/conversations', method: 'GET', params})
}

/**
 * 会话详情：问题与回答成对，附引用条数
 */
export function getChatLog(id) {
	return request({url: `ai/conversation/${id}`, method: 'GET'})
}


/**
 * 检索质量：0 引用占比、平均引用数、改写率，以及严格度建议
 */
export function getRetrievalQuality(days = 7) {
	return request({url: 'ai/quality', method: 'GET', params: {days}})
}

/**
 * 模型配置：当前生效值 + 配置文件默认值 + 哪些项被后台改过
 */
export function getAiConfig() {
	return request({url: 'ai/config', method: 'GET'})
}

/**
 * 保存模型配置（保存后热加载，不需要重启）
 */
export function saveAiConfig(values) {
	return request({url: 'ai/config', method: 'PUT', data: {values}})
}

/**
 * 用量统计：今日 + 区间汇总 + 按天 / 模型 / 场景
 */
export function getAiUsage(days) {
	return request({url: 'ai/usage', method: 'GET', params: {days}})
}
