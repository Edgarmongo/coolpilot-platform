import axios from 'axios'
import {ElMessage} from 'element-plus'

/**
 * 后台请求实例
 * baseURL 已经包含 /admin/，所以 api 层写 'orders' 即可，不要再写 admin
 */
const request = axios.create({
	baseURL: '/api/admin/',
	timeout: 15000
})

request.interceptors.request.use(config => {
	const token = localStorage.getItem('token')
	if (token) {
		config.headers.Authorization = token
	}
	return config
}, error => Promise.reject(error))

request.interceptors.response.use(response => {
	const res = response.data
	if (res.code !== 200) {
		ElMessage.error(res.msg || '请求失败')
		return Promise.reject(new Error(res.msg || '请求失败'))
	}
	return res
}, error => {
	// 401 统一回登录页，避免过期 token 反复报错
	if (error.response && error.response.status === 401) {
		localStorage.removeItem('token')
		localStorage.removeItem('user')
		if (!location.hash.includes('/login')) {
			location.href = '/cms/login'
		}
	}
	ElMessage.error(error.message || '网络异常')
	return Promise.reject(error)
})

/**
 * 裸实例：用于 /file/** 这类不在 /admin 下的接口
 */
export const rawRequest = axios.create({
	baseURL: '/api/',
	timeout: 15000
})

rawRequest.interceptors.request.use(config => {
	const token = localStorage.getItem('token')
	if (token) {
		config.headers.Authorization = token
	}
	return config
})

rawRequest.interceptors.response.use(response => response.data)

export default request
