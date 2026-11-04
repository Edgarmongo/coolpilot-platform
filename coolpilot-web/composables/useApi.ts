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

	/**
	 * @param options.skipAuth 不注入登录 token（密码文章用独立凭证）
	 */
	async function call<T = any>(path: string, options: Record<string, any> = {}): Promise<T> {
		const {skipAuth, ...rest} = options
		const headers: Record<string, string> = {...(options.headers || {})}
		if (!skipAuth && token.value && !headers.authorization) {
			headers.authorization = token.value
		}
		try {
			return await $fetch<T>(`/api${path}`, {...rest, headers})
		} catch (error: any) {
			/*
			 * 后端业务错误（403 密码文章、400 参数、500 异常）会被 Nitro 原样透传状态码，
			 * $fetch 对非 2xx 会抛异常。这里把业务体当正常结果返回，
			 * 调用方看 code 即可，不必到处 try/catch。
			 */
			if (error && error.data && typeof error.data.code !== 'undefined') {
				return error.data as T
			}
			throw error
		}
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
