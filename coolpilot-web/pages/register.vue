<template>
	<div class="card login">
		<h1 class="page-title">注册访客账号</h1>
		<p class="page-desc">仅用于收藏、下单与查看已购内容</p>

		<div class="form">
			<input v-model="form.username" placeholder="用户名" autocomplete="username">
			<input v-model="form.password" type="password" placeholder="密码（至少 6 位）" autocomplete="new-password">
			<input v-model="form.nickname" placeholder="昵称（可选）">
			<input v-model="form.email" placeholder="邮箱（可选，用于找回）">
			<button class="btn" :disabled="busy" @click="submit">{{ busy ? '提交中…' : '注册' }}</button>
		</div>
		<p class="hint">已有账号？<NuxtLink to="/login" class="link">去登录</NuxtLink></p>
	</div>
</template>

<script setup lang="ts">
const router = useRouter()
const {call, setSession} = useApi()

const busy = ref(false)
const form = reactive({username: '', password: '', nickname: '', email: ''})

async function submit() {
	if (!form.username || !form.password) {
		alert('请填写用户名和密码')
		return
	}
	busy.value = true
	try {
		const res: any = await call('/visitor/register', {method: 'POST', body: {...form}})
		if (res.code === 200) {
			setSession({token: res.data.token, user: res.data.user})
			router.replace('/')
		} else {
			alert(res.msg || '注册失败')
		}
	} finally {
		busy.value = false
	}
}
</script>

<style scoped>
.login {
	max-width: 380px;
	margin: 40px auto;
}

.form {
	display: flex;
	flex-direction: column;
	gap: 12px;
}

.form input {
	padding: 10px 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	font-size: 14px;
	outline-color: var(--teal);
}

.hint {
	margin: 16px 0 0;
	font-size: 13px;
	color: var(--muted);
	text-align: center;
}

.link {
	color: var(--teal);
}
</style>
