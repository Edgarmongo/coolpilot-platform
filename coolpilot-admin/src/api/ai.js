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
