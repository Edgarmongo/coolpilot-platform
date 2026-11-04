import {defineStore} from 'pinia'
import {login as loginApi} from '@/api/login'

export const useUserStore = defineStore('user', {
	state: () => ({
		token: localStorage.getItem('token') || '',
		user: JSON.parse(localStorage.getItem('user') || 'null')
	}),
	getters: {
		isLogin: state => !!state.token,
		username: state => (state.user && (state.user.nickname || state.user.username)) || ''
	},
	actions: {
		async login(form) {
			const res = await loginApi(form)
			this.token = res.data.token
			this.user = res.data.user
			localStorage.setItem('token', this.token)
			localStorage.setItem('user', JSON.stringify(this.user))
			return res
		},
		logout() {
			this.token = ''
			this.user = null
			localStorage.removeItem('token')
			localStorage.removeItem('user')
		}
	}
})
