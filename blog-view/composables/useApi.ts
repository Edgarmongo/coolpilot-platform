/**
 * 统一的接口调用入口。
 * token 存 cookie，服务端渲染时也能读到，避免首屏出现「未登录」闪烁。
 */
export function useApi() {
	const token = useCookie<string | null>('cp_token', {
		maxAge: 60 * 60 * 24 * 30,
		sameSite: 'lax'
	})
	const user = useCookie<any | null>('cp_user', {
		maxAge: 60 * 60 * 24 * 30,
		sameSite: 'lax'
	})

	async function call<T = any>(path: string, options: Record<string, any> = {}): Promise<T> {
		const headers: Record<string, string> = {...(options.headers || {})}
		if (token.value) {
			headers.authorization = token.value
		}
		return await $fetch<T>(`/api${path}`, {...options, headers})
	}

	function setSession(data: {token: string, user: any}) {
		token.value = data.token
		user.value = data.user
	}

	function clearSession() {
		token.value = null
		user.value = null
	}

	const isLogin = computed(() => !!token.value)

	return {call, token, user, isLogin, setSession, clearSession}
}
