<template>
	<div class="card login">
		<h1 class="page-title">{{ t('auth.loginTitle') }}</h1>
		<p class="page-desc">{{ t('auth.loginDesc') }}</p>

		<div class="form">
			<input v-model="form.username" :placeholder="t('auth.username')" autocomplete="username">
			<input v-model="form.password" type="password" :placeholder="t('auth.password')" autocomplete="current-password"
			       @keyup.enter="submit">
			<button class="btn" :disabled="busy" @click="submit">{{ busy ? t('auth.signingIn') : t('auth.login') }}</button>
		</div>
		<p class="hint">
			{{ t('auth.noAccount') }}<NuxtLink :to="localePath('/register')" class="link">{{ t('auth.registerLink') }}</NuxtLink>
		</p>
		<p class="hint forgot-line">
			<NuxtLink :to="localePath('/forgot')" class="link">{{ t('auth.forgot') }}</NuxtLink>
		</p>
	</div>
</template>

<script setup lang="ts">
const route = useRoute()
const router = useRouter()
const {call, setSession} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

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
