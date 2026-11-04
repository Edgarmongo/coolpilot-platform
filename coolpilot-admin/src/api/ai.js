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
