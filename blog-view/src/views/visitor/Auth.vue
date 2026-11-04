<template>
	<div class="auth-wrap">
		<div class="auth-card">
			<!--品牌区-->
			<div class="auth-brand">
				<span class="auth-brand-icon">☕</span>
				<div>
					<div class="auth-brand-title">{{ siteName }}</div>
					<div class="auth-brand-desc">访客登录后可记住你的阅读身份</div>
				</div>
			</div>

			<!--切换-->
			<div class="auth-tabs">
				<button class="auth-tab" :class="{'is-active': mode === 'login'}" @click="switchMode('login')">登录</button>
				<button class="auth-tab" :class="{'is-active': mode === 'register'}" @click="switchMode('register')">注册</button>
			</div>

			<!--登录表单-->
			<el-form
				v-if="mode === 'login'"
				ref="loginFormRef"
				:model="loginForm"
				:rules="loginRules"
				class="auth-form"
				@submit.native.prevent
			>
				<el-form-item prop="username">
					<el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="el-icon-user" @keyup.enter.native="doLogin"/>
				</el-form-item>
				<el-form-item prop="password">
					<el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="el-icon-lock" show-password @keyup.enter.native="doLogin"/>
				</el-form-item>
				<button type="button" class="auth-submit" :disabled="submitting" @click="doLogin">
					{{ submitting ? '登录中...' : '登 录' }}
				</button>
				<p class="auth-switch-tip">
					还没有账号？<a @click.prevent="switchMode('register')">立即注册</a>
				</p>
			</el-form>

			<!--注册表单-->
			<el-form
				v-else
				ref="registerFormRef"
				:model="registerForm"
				:rules="registerRules"
				class="auth-form"
				@submit.native.prevent
			>
				<el-form-item prop="username">
					<el-input v-model="registerForm.username" placeholder="用户名（2-20 位，支持中文）" prefix-icon="el-icon-user"/>
				</el-form-item>
				<el-form-item prop="nickname">
					<el-input v-model="registerForm.nickname" placeholder="昵称（选填，默认同用户名）" prefix-icon="el-icon-postcard"/>
				</el-form-item>
				<el-form-item prop="email">
					<el-input v-model="registerForm.email" placeholder="邮箱（选填）" prefix-icon="el-icon-message"/>
				</el-form-item>
				<el-form-item prop="password">
					<el-input v-model="registerForm.password" type="password" placeholder="密码（6-32 位）" prefix-icon="el-icon-lock" show-password/>
				</el-form-item>
				<el-form-item prop="confirmPassword">
					<el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" prefix-icon="el-icon-lock" show-password @keyup.enter.native="doRegister"/>
				</el-form-item>
				<button type="button" class="auth-submit" :disabled="submitting" @click="doRegister">
					{{ submitting ? '注册中...' : '注 册' }}
				</button>
				<p class="auth-switch-tip">
					已有账号？<a @click.prevent="switchMode('login')">直接登录</a>
				</p>
			</el-form>

			<router-link to="/home" class="auth-back">← 返回首页</router-link>
		</div>
	</div>
</template>

<script>
	import {visitorLogin, visitorRegister} from "@/api/visitor";
	import {SAVE_VISITOR} from "@/store/mutations-types";
	import {portal} from "@/settings";

	export default {
		name: "VisitorAuth",
		data() {
			const confirmPasswordValidator = (rule, value, callback) => {
				if (value !== this.registerForm.password) {
					callback(new Error('两次输入的密码不一致'));
					return;
				}
				callback();
			};
			return {
				siteName: portal.siteName,
				mode: 'login',
				submitting: false,
				loginForm: {
					username: '',
					password: ''
				},
				registerForm: {
					username: '',
					nickname: '',
					email: '',
					password: '',
					confirmPassword: ''
				},
				loginRules: {
					username: [{required: true, message: '请输入用户名', trigger: 'blur'}],
					password: [{required: true, message: '请输入密码', trigger: 'blur'}]
				},
				registerRules: {
					username: [
						{required: true, message: '请输入用户名', trigger: 'blur'},
						{pattern: /^[\w\u4e00-\u9fa5.-]{2,20}$/, message: '2-20 位字母、数字、下划线、点或中文', trigger: 'blur'}
					],
					email: [
						{pattern: /^$|^[\w.+-]+@[\w-]+(\.[\w-]+)+$/, message: '邮箱格式不正确', trigger: 'blur'}
					],
					password: [
						{required: true, message: '请输入密码', trigger: 'blur'},
						{min: 6, max: 32, message: '密码长度需为 6-32 位', trigger: 'blur'}
					],
					confirmPassword: [
						{required: true, message: '请再次输入密码', trigger: 'blur'},
						{validator: confirmPasswordValidator, trigger: 'blur'}
					]
				}
			}
		},
		created() {
			// 支持 /login?mode=register 直达注册
			if (this.$route.query.mode === 'register') {
				this.mode = 'register';
			}
		},
		methods: {
			switchMode(mode) {
				this.mode = mode;
				this.$nextTick(() => {
					const form = mode === 'login' ? this.$refs.loginFormRef : this.$refs.registerFormRef;
					form && form.clearValidate();
				});
			},
			doLogin() {
				this.$refs.loginFormRef.validate(valid => {
					if (!valid) return;
					this.submitting = true;
					visitorLogin(this.loginForm).then(res => {
						if (res.code === 200) {
							this.$store.commit(SAVE_VISITOR, res.data);
							this.msgSuccess('登录成功');
							this.goBack();
						} else {
							this.msgError(res.msg);
						}
					}).catch(() => {
						this.msgError('登录失败，请稍后重试');
					}).finally(() => {
						this.submitting = false;
					});
				});
			},
			doRegister() {
				this.$refs.registerFormRef.validate(valid => {
					if (!valid) return;
					this.submitting = true;
					const data = {
						username: this.registerForm.username,
						nickname: this.registerForm.nickname,
						email: this.registerForm.email,
						password: this.registerForm.password
					};
					visitorRegister(data).then(res => {
						if (res.code === 200) {
							this.$store.commit(SAVE_VISITOR, res.data);
							this.msgSuccess('注册成功，已自动登录');
							this.goBack();
						} else {
							this.msgError(res.msg);
						}
					}).catch(() => {
						this.msgError('注册失败，请稍后重试');
					}).finally(() => {
						this.submitting = false;
					});
				});
			},
			// 登录/注册完成后回到来源页，没有来源则回首页
			goBack() {
				const redirect = this.$route.query.redirect;
				if (redirect && typeof redirect === 'string' && redirect.startsWith('/')) {
					this.$router.replace(redirect);
				} else {
					this.$router.replace('/home');
				}
			}
		}
	}
</script>

<style scoped>
	.auth-wrap {
		display: flex;
		align-items: center;
		justify-content: center;
		min-height: 100vh;
		padding: 40px 20px;
	}

	.auth-card {
		position: relative;
		width: 100%;
		max-width: 420px;
		padding: 32px 32px 26px;
		background: var(--portal-surface);
		border: 1px solid var(--portal-border);
		border-radius: var(--portal-radius);
		box-shadow: var(--portal-shadow);
	}

	.auth-brand {
		display: flex;
		align-items: center;
		gap: 12px;
		margin-bottom: 22px;
	}

	.auth-brand-icon {
		width: 40px;
		height: 40px;
		display: flex;
		align-items: center;
		justify-content: center;
		font-size: 20px;
		background: var(--portal-primary-soft);
		border-radius: 10px;
	}

	.auth-brand-title {
		font-size: 16px;
		font-weight: 760;
		color: var(--portal-ink);
	}

	.auth-brand-desc {
		margin-top: 2px;
		font-size: 12.5px;
		color: var(--portal-muted);
	}

	.auth-tabs {
		display: flex;
		gap: 4px;
		padding: 4px;
		margin-bottom: 20px;
		background: var(--portal-surface-alt);
		border-radius: 8px;
	}

	.auth-tab {
		flex: 1;
		height: 34px;
		font-size: 13.5px;
		font-weight: 600;
		color: var(--portal-muted);
		background: transparent;
		border: none;
		border-radius: 6px;
		cursor: pointer;
		transition: all var(--portal-ease);
	}

	.auth-tab.is-active {
		color: var(--portal-primary-strong);
		background: var(--portal-surface);
		box-shadow: var(--portal-shadow-sm);
	}

	.auth-form >>> .el-input__inner {
		height: 40px;
		line-height: 40px;
		border-radius: 7px;
		border-color: var(--portal-border);
	}

	.auth-form >>> .el-input__inner:focus {
		border-color: var(--portal-primary);
	}

	.auth-form >>> .el-form-item {
		margin-bottom: 16px;
	}

	/*统一的主按钮，避开 element 默认蓝色，贴合门户绿*/
	.auth-submit {
		width: 100%;
		height: 42px;
		margin-top: 4px;
		font-size: 14px;
		font-weight: 700;
		letter-spacing: 2px;
		color: #fff;
		background: var(--portal-primary);
		border: 1px solid var(--portal-primary);
		border-radius: 7px;
		cursor: pointer;
		transition: background var(--portal-ease);
	}

	.auth-submit:hover:not(:disabled) {
		background: var(--portal-primary-strong);
		border-color: var(--portal-primary-strong);
	}

	.auth-submit:disabled {
		opacity: .7;
		cursor: not-allowed;
	}

	.auth-switch-tip {
		margin: 14px 0 0;
		font-size: 12.5px;
		color: var(--portal-muted);
		text-align: center;
	}

	.auth-switch-tip a {
		color: var(--portal-primary);
		cursor: pointer;
	}

	.auth-back {
		display: block;
		margin-top: 18px;
		font-size: 12.5px;
		color: var(--portal-muted);
		text-align: center;
		text-decoration: none;
	}

	.auth-back:hover {
		color: var(--portal-primary);
	}
</style>
