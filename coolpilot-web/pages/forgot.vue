<template>
	<div class="forgot">
		<div class="card panel">
			<h1 class="page-title">{{ t('forgot.title') }}</h1>
			<p class="page-desc">{{ t('forgot.desc') }}</p>

			<!-- 第一步：填邮箱收验证码 -->
			<template v-if="step === 1">
				<label class="field">
					<span class="label">{{ t('forgot.email') }}</span>
					<input v-model="email" type="email" class="input" autocomplete="email"
					       :placeholder="t('forgot.emailPlaceholder')" @keyup.enter="sendCode"/>
				</label>
				<p class="hint">{{ t('forgot.emailHint') }}</p>
				<button class="btn full" :disabled="sending" @click="sendCode">
					{{ sending ? t('forgot.sending') : t('forgot.sendCode') }}
				</button>
			</template>

			<!-- 第二步：验证码 + 新密码 -->
			<template v-else>
				<p class="sent">{{ t('forgot.sentTo', {email}) }}</p>
				<label class="field">
					<span class="label">{{ t('forgot.code') }}</span>
					<input v-model="code" class="input" inputmode="numeric" maxlength="6"
					       :placeholder="t('forgot.codePlaceholder')"/>
				</label>
				<label class="field">
					<span class="label">{{ t('forgot.newPassword') }}</span>
					<input v-model="password" type="password" class="input" autocomplete="new-password"
					       :placeholder="t('forgot.passwordPlaceholder')"/>
				</label>
				<label class="field">
					<span class="label">{{ t('forgot.confirmPassword') }}</span>
					<input v-model="password2" type="password" class="input" autocomplete="new-password"
					       :placeholder="t('forgot.passwordPlaceholder')" @keyup.enter="reset"/>
				</label>

				<button class="btn full" :disabled="resetting" @click="reset">
					{{ resetting ? t('forgot.resetting') : t('forgot.reset') }}
				</button>
				<div class="row">
					<button class="link" @click="step = 1">{{ t('forgot.changeEmail') }}</button>
					<button class="link" :disabled="sending" @click="resend">
						{{ sending ? t('forgot.sending') : t('forgot.resend') }}
					</button>
				</div>
			</template>

			<p v-if="error" class="error">{{ error }}</p>
			<p v-if="done" class="ok">{{ done }}</p>

			<p class="foot">
				<NuxtLink :to="localePath('/login')">{{ t('forgot.backToLogin') }}</NuxtLink>
			</p>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()
const router = useRouter()

useHead({title: t('forgot.title')})

const step = ref(1)
const email = ref('')
const code = ref('')
const password = ref('')
const password2 = ref('')
const sending = ref(false)
const resetting = ref(false)
const error = ref('')
const done = ref('')

function validEmail(value: string) {
	return /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/.test(value.trim())
}

async function post(path: string, body: any) {
	const res: any = await call(path, {method: 'POST', body}).catch((e: any) => ({code: -1, msg: e?.message}))
	if (res && res.code === 200) {
		return res
	}
	throw new Error(res?.msg || t('forgot.failed'))
}

async function sendCode() {
	error.value = ''
	done.value = ''
	if (!validEmail(email.value)) {
		error.value = t('forgot.invalidEmail')
		return
	}
	sending.value = true
	try {
		await post('/auth/password/code', {email: email.value.trim()})
		step.value = 2
	} catch (e: any) {
		error.value = e.message
	} finally {
		sending.value = false
	}
}

async function resend() {
	error.value = ''
	sending.value = true
	try {
		await post('/auth/password/code', {email: email.value.trim()})
		done.value = t('forgot.codeResent')
	} catch (e: any) {
		error.value = e.message
	} finally {
		sending.value = false
	}
}

async function reset() {
	error.value = ''
	done.value = ''
	if (!/^\d{6}$/.test(code.value)) {
		error.value = t('forgot.invalidCode')
		return
	}
	if (password.value.length < 6 || password.value.length > 32) {
		error.value = t('forgot.invalidPassword')
		return
	}
	if (password.value !== password2.value) {
		error.value = t('forgot.passwordMismatch')
		return
	}
	resetting.value = true
	try {
		await post('/auth/password/reset', {
			email: email.value.trim(),
			code: code.value,
			password: password.value
		})
		done.value = t('forgot.success')
		setTimeout(() => router.push(localePath('/login')), 1200)
	} catch (e: any) {
		error.value = e.message
	} finally {
		resetting.value = false
	}
}
</script>

<style scoped>
.forgot {
	max-width: 460px;
	margin: 24px auto;
}

.panel {
	padding: 30px 32px 26px;
}

.field {
	display: block;
	margin-bottom: 14px;
}

.label {
	display: block;
	font-size: 12.5px;
	color: var(--muted);
	margin-bottom: 6px;
}

.input {
	width: 100%;
	height: 40px;
	padding: 0 12px;
	border: 1px solid var(--line);
	border-radius: 8px;
	background: #fff;
	font-size: 14px;
	outline: none;
}

.input:focus {
	border-color: var(--teal);
}

.btn.full {
	width: 100%;
	margin-top: 6px;
	padding: 10px 0;
}

.btn:disabled {
	opacity: .6;
	cursor: not-allowed;
}

.hint {
	margin: 0 0 16px;
	font-size: 12.5px;
	color: var(--muted);
	line-height: 1.7;
}

.sent {
	margin: 0 0 16px;
	padding: 10px 12px;
	background: #f2fbf7;
	border: 1px solid #d6efe4;
	border-radius: 8px;
	font-size: 13px;
	color: #0f6e56;
	word-break: break-all;
}

.row {
	display: flex;
	justify-content: space-between;
	margin-top: 12px;
}

.link {
	border: none;
	background: none;
	color: var(--muted);
	font-size: 12.5px;
	cursor: pointer;
	padding: 0;
}

.link:hover {
	color: var(--teal);
}

.error {
	margin: 14px 0 0;
	font-size: 13px;
	color: #c45656;
	line-height: 1.7;
}

.ok {
	margin: 14px 0 0;
	font-size: 13px;
	color: #0f6e56;
}

.foot {
	margin: 20px 0 0;
	padding-top: 14px;
	border-top: 1px dashed var(--line);
	font-size: 12.5px;
	text-align: center;
}

.foot a {
	color: var(--muted);
}

.foot a:hover {
	color: var(--teal);
}
</style>
