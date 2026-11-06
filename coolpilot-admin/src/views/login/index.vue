<template>
	<div class="login">
		<div class="login-card">
			<div class="login-brand">
				<span class="login-mark">CP</span>
				<div>
					<h1>{{ t('layout.brand') }}</h1>
					<p>{{ t('login.subtitle') }}</p>
				</div>
			</div>

			<el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
				<el-form-item prop="username">
					<el-input v-model="form.username" :placeholder="t('login.username')" :prefix-icon="User" clearable/>
				</el-form-item>
				<el-form-item prop="password">
					<el-input v-model="form.password" type="password" :placeholder="t('login.password')" :prefix-icon="Lock"
					          show-password/>
				</el-form-item>
				<el-button type="primary" size="large" class="login-submit" :loading="loading" @click="submit">
					{{ t('login.submit') }}
				</el-button>
			</el-form>

			<p class="login-tip">{{ t('login.tip') }}</p>
		</div>
	</div>
</template>

<script setup>

import {reactive, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {Lock, User} from '@element-plus/icons-vue'
import {useUserStore} from '@/store/user'
import {useI18n} from 'vue-i18n'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const {t} = useI18n()

const formRef = ref()
const loading = ref(false)
const form = reactive({username: '', password: ''})
const rules = {
	username: [{required: true, message: t('login.requiredUser'), trigger: 'blur'}],
	password: [{required: true, message: t('login.requiredPwd'), trigger: 'blur'}]
}

async function submit() {
	await formRef.value.validate()
	loading.value = true
	try {
		await userStore.login({username: form.username, password: form.password})
		ElMessage.success(t('login.success'))
		router.replace(route.query.redirect || '/dashboard')
	} catch (e) {
		// 错误提示已由请求拦截器统一处理
	} finally {
		loading.value = false
	}
}
</script>

<style scoped>
.login {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 100%;
	background: linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%);
}

.login-card {
	width: 360px;
	padding: 34px 32px 26px;
	background: #fff;
	border-radius: 14px;
	box-shadow: 0 18px 48px rgba(0, 0, 0, .28);
}

.login-brand {
	display: flex;
	align-items: center;
	gap: 12px;
	margin-bottom: 26px;
}

.login-mark {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 40px;
	height: 40px;
	border-radius: 10px;
	background: #0f766e;
	color: #fff;
	font-weight: 700;
	font-size: 15px;
}

.login-brand h1 {
	margin: 0;
	font-size: 17px;
	letter-spacing: .5px;
}

.login-brand p {
	margin: 2px 0 0;
	font-size: 12px;
	color: #909399;
}

.login-submit {
	width: 100%;
	margin-top: 4px;
}

.login-tip {
	margin: 18px 0 0;
	text-align: center;
	font-size: 12px;
	color: #b0b3b8;
}
</style>
