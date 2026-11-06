<template>
	<div class="follow">
		<h1 class="page-title">{{ t('followPage.title') }}</h1>
		<p class="page-desc">{{ t('followPage.desc') }}</p>

		<div v-if="!accounts.length" class="card empty-box">
			<p class="empty">{{ t('followPage.empty') }}</p>
		</div>

		<div v-else class="grid">
			<div v-for="item in accounts" :key="item.id" class="account card">
				<div class="account-head">
					<SocialIcon :platform="item.platform"/>
					<div class="account-name">
						<strong>{{ item.accountName }}</strong>
						<span v-if="platformText(item.platform, item.accountName)" class="platform">
							{{ platformText(item.platform, item.accountName) }}
						</span>
					</div>
				</div>

				<p v-if="item.intro" class="account-intro">{{ item.intro }}</p>

				<div class="account-body">
					<div v-if="item.qrUrl" class="qr">
						<img :src="item.qrUrl" :alt="item.accountName" loading="lazy"/>
						<span class="qr-tip">{{ t('followPage.scan') }}</span>
					</div>
					<a v-if="item.homeUrl" :href="item.homeUrl" target="_blank" rel="noopener noreferrer"
					   class="account-link">
						{{ t('followPage.visit') }} →
					</a>
				</div>
			</div>
		</div>

		<p v-if="accounts.length" class="foot-tip">{{ t('followPage.tip') }}</p>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t, te} = useI18n()

useHead({title: t('followPage.title')})

const {data} = await useAsyncData('social-accounts', () => call('/social'))

const accounts = computed(() => (data.value as any)?.data || [])

/**
 * 平台中文名走 i18n；认不出的平台不显示副标题，
 * 而不是把 GITHUB 这种枚举值直接摊给访客看。
 *
 * 另外：账号名和平台名一样时（迁移来的存量就叫「GitHub」「邮箱」）不再重复一次，
 * 站长把账号名改成真实 ID 后，副标题会自然出现。
 */
function platformText(platform: string, name: string) {
	const key = `followPage.platforms.${platform}`
	const text = te(key) ? t(key) : ''
	return text && text !== name ? text : ''
}
</script>

<style scoped>
.follow {
	max-width: 960px;
}

.grid {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 16px;
}

.account {
	display: flex;
	flex-direction: column;
	gap: 12px;
	padding: 18px 18px 16px;
}

.account-head {
	display: flex;
	align-items: center;
	gap: 12px;
}

.account-name {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.account-name strong {
	font-size: 14.5px;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.platform {
	font-size: 12px;
	color: var(--muted);
}

.account-intro {
	margin: 0;
	color: var(--muted);
	font-size: 12.5px;
	line-height: 1.7;
	min-height: 36px;
}

.account-body {
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 12px;
	margin-top: auto;
}

.qr {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 4px;
}

.qr img {
	width: 84px;
	height: 84px;
	object-fit: contain;
	border-radius: 8px;
	border: 1px solid var(--line);
	background: #fff;
}

.qr-tip {
	font-size: 11.5px;
	color: var(--muted);
}

.account-link {
	font-size: 12.5px;
	color: var(--teal);
	font-weight: 600;
	white-space: nowrap;
	padding-bottom: 4px;
}

.account-link:hover {
	text-decoration: underline;
}

.empty-box {
	padding: 48px 24px;
	text-align: center;
}

.foot-tip {
	margin: 18px 0 0;
	color: var(--muted);
	font-size: 12.5px;
}

@media (max-width: 860px) {
	.grid {
		grid-template-columns: repeat(2, 1fr);
	}
}

@media (max-width: 560px) {
	.grid {
		grid-template-columns: 1fr;
	}
}
</style>
