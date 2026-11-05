<template>
	<div class="card login">
		<h1 class="page-title">{{ t('auth.registerTitle') }}</h1>
		<p class="page-desc">{{ t('auth.registerDesc') }}</p>

		<div class="form">
			<input v-model="form.username" :placeholder="t('auth.username')" autocomplete="username">
			<input v-model="form.password" type="password" :placeholder="t('auth.passwordHint')" autocomplete="new-password">
			<input v-model="form.nickname" :placeholder="t('auth.nickname')">
			<input v-model="form.email" :placeholder="t('auth.email')">
			<button class="btn" :disabled="busy" @click="submit">{{ busy ? t('auth.submitting') : t('auth.register') }}</button>
		</div>
		<p class="hint">{{ t('auth.hasAccount') }}<NuxtLink :to="localePath('/login')" class="link">{{ t('auth.toLogin') }}</NuxtLink></p>
	</div>
</template>

<script setup lang="ts">
const router = useRouter()
const {call, setSession} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const busy = ref(false)
const form = reactive({username: '', password: '', nickname: '', email: ''})

async function submit() {
	if (!form.username || !form.password) {
		alert(t('auth.needCreds'))
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
