import axios from '@/plugins/axios'

/**
 * 访客登录（前台读者身份，与后台博主登录完全分开）
 */
export function visitorLogin(loginForm) {
	return axios({
		url: 'visitor/login',
		method: 'POST',
		data: {...loginForm}
	})
}

/**
 * 访客自助注册
 */
export function visitorRegister(registerForm) {
	return axios({
		url: 'visitor/register',
		method: 'POST',
		data: {...registerForm}
	})
}
