<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('账号设置') }}</h2>
				<p class="page-desc">{{ t('修改昵称、邮箱、头像与登录密码') }}</p>
			</div>
		</div>

		<div class="page-card form-card">
			<el-form label-width="90px" size="default">
				<el-form-item :label="t('用户名')">
					<el-input v-model="form.username" disabled/>
				</el-form-item>
				<el-form-item :label="t('昵称')">
					<el-input v-model="form.nickname" maxlength="20"/>
				</el-form-item>
				<el-form-item :label="t('邮箱')">
					<el-input v-model="form.email" maxlength="50"/>
				</el-form-item>
				<el-form-item :label="t('头像')">
					<el-input v-model="form.avatar" :placeholder="t('/img/avatar.jpg 或图片地址')"/>
				</el-form-item>
				<el-form-item :label="t('新密码')">
					<el-input v-model="form.password" type="password" show-password
					          :placeholder="t('留空则不修改密码，至少 6 位')"/>
				</el-form-item>
				<el-form-item>
					<el-button type="primary" :loading="saving" @click="submit">{{ t('保存') }}</el-button>
				</el-form-item>
			</el-form>
			<p class="muted tip">{{ t('修改密码会立即让所有已登录的 token 失效（含当前后台），保存后需要重新登录。') }}</p>
		</div>
	</div>
</template>

<script setup>
import {useT} from '@/i18n/ui'
const t = useT()

import {onMounted, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {updateAccount} from '@/api/account'
import {useUserStore} from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const saving = ref(false)
const form = reactive({username: '', nickname: '', email: '', avatar: '', password: ''})

async function submit() {
	if (form.password && form.password.length < 6) {
		ElMessage.warning(t('密码至少 6 位'))
		return
	}
	saving.value = true
	try {
		await updateAccount({
			username: form.username,
			nickname: form.nickname,
			email: form.email,
			avatar: form.avatar,
			password: form.password || undefined
		})
		// 改密码后旧 token 已被服务端吊销，必须重新登录
		if (form.password) {
			ElMessage.success(t('密码已修改，请重新登录'))
			userStore.logout()
			router.push('/login')
			return
		}
		ElMessage.success(t('修改成功'))
		// 同步本地缓存的展示信息
		const user = {...(userStore.user || {}), nickname: form.nickname, email: form.email, avatar: form.avatar}
		userStore.user = user
		localStorage.setItem('user', JSON.stringify(user))
		form.password = ''
	} finally {
		saving.value = false
	}
}

onMounted(() => {
	const user = userStore.user || {}
	Object.assign(form, {
		username: user.username || '',
		nickname: user.nickname || '',
		email: user.email || '',
		avatar: user.avatar || ''
	})
})
</script>

<style scoped>
.form-card {
	max-width: 520px;
}

.tip {
	margin: 4px 0 0;
}
</style>
