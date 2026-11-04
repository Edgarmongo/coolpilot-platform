<template>
	<div class="card login">
		<h1 class="page-title">访客登录</h1>
		<p class="page-desc">登录后可收藏、下单与查看已购内容</p>

		<div class="form">
			<input v-model="form.username" placeholder="用户名" autocomplete="username">
			<input v-model="form.password" type="password" placeholder="密码" autocomplete="current-password"
			       @keyup.enter="submit">
			<button class="btn" :disabled="busy" @click="submit">{{ busy ? '登录中…' : '登录' }}</button>
		</div>
		<p class="hint">
			还没有账号？<NuxtLink to="/register" class="link">注册一个</NuxtLink>
		</p>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call, setSession} = useApi()

const busy = ref(false)
const form = reactive({username: '', password: ''})

async function submit() {
	if (!form.username || !form.password) {
		return
	}
	busy.value = true
	try {
		const res: any = await call('/visitor/login', {method: 'POST', body: {...form}})
		if (res.code === 200) {
			setSession({token: res.data.token, user: res.data.user})
			router.replace((route.query.redirect as string) || '/')
		} else {
			alert(res.msg || '登录失败')
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
