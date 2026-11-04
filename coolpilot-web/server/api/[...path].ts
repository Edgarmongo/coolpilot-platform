/**
 * 同源代理：前端只访问 /api/**，由 Nitro 转发到后端。
 * 这样浏览器端没有跨域问题，SSR 首屏也能直接取数。
 */
export default defineEventHandler(async (event) => {
	const config = useRuntimeConfig()
	const path = getRouterParam(event, 'path') || ''
	const method = getMethod(event)
	const query = getQuery(event)
	const body = ['POST', 'PUT', 'PATCH'].includes(method) ? await readBody(event).catch(() => undefined) : undefined

	const headers: Record<string, string> = {}
	const authorization = getHeader(event, 'authorization')
	if (authorization) {
		headers.authorization = authorization
	}
	const contentType = getHeader(event, 'content-type')
	if (contentType) {
		headers['content-type'] = contentType
	}

	const target = `${config.apiBase}/${path}`.replace(/([^:]\/)\/+/g, '$1')

	try {
		return await $fetch(target, {method, query, body, headers})
	} catch (error) {
		// 后端返回非 2xx 时，把业务体的 code/msg 原样透出去，避免前端只看到 500
		const response = error && error.response
		if (response && response._data) {
			setResponseStatus(event, response.status || 500)
			return response._data
		}
		setResponseStatus(event, 502)
		return {code: 502, msg: '后端服务不可用', data: null}
	}
})
