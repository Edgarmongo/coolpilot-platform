<template>
	<div class="planet">
		<!-- 未开放：站长还没配置好，或者主动关掉了 -->
		<div v-if="!isOpen" class="card closed">
			<h1 class="closed-title">{{ t('planetPage.closedTitle') }}</h1>
			<p class="closed-desc">{{ t('planetPage.closedDesc') }}</p>
			<NuxtLink :to="localePath('/')" class="btn">{{ t('planetPage.backHome') }}</NuxtLink>
		</div>

		<template v-else>
			<section class="hero">
				<div class="hero-main">
					<span class="tag">{{ t('planetPage.tag') }}</span>
					<h1 class="hero-title">{{ planet.name }}</h1>
					<p v-if="planet.intro" class="hero-intro">{{ planet.intro }}</p>
					<div class="hero-actions">
						<a v-if="planet.link" :href="planet.link" target="_blank" rel="noopener noreferrer"
						   class="btn btn-lg">{{ t('planetPage.join') }}</a>
						<span v-else class="muted">{{ t('planetPage.noLink') }}</span>
					</div>
				</div>
				<div v-if="planet.cover" class="hero-cover">
					<img :src="planet.cover" :alt="planet.name"/>
				</div>
			</section>

			<section class="qr-block card">
				<div class="qr-text">
					<h2>{{ t('planetPage.scanTitle') }}</h2>
					<p class="muted">{{ t('planetPage.scanDesc') }}</p>
					<p class="notice">{{ t('planetPage.notice') }}</p>
				</div>
				<div v-if="planet.qr" class="qr-img">
					<img :src="planet.qr" :alt="t('planetPage.scanTitle')"/>
				</div>
				<div v-else class="qr-img qr-empty">
					<span class="muted">{{ t('planetPage.noQr') }}</span>
				</div>
			</section>
		</template>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()
const {locale} = useI18n()

// 注意：t 是 const，useHead 必须放在它之后，否则 setup 阶段 TDZ 报错
useHead({title: t('planetPage.title')})

const {data} = await useAsyncData('planet', () => call(`/planet?lang=${locale.value}`), {watch: [locale]})

const planet = computed(() => {
	const d = (data.value as any)?.data || {}
	return {
		enabled: !!d.enabled,
		name: d.name || '',
		cover: d.cover || '',
		intro: d.intro || '',
		link: d.link || '',
		qr: d.qr || ''
	}
})

const isOpen = computed(() => planet.value.enabled)
</script>

<style scoped>
.planet {
	max-width: 900px;
}

.closed {
	text-align: center;
	padding: 56px 24px;
}

.closed-title {
	margin: 0 0 10px;
	font-size: 22px;
}

.closed-desc {
	margin: 0 0 22px;
	color: var(--muted);
	font-size: 14px;
}

/* ---------------- 首屏 ---------------- */
.hero {
	display: flex;
	gap: 32px;
	align-items: center;
	background: linear-gradient(135deg, #ffffff 0%, #eef8f3 100%);
	border: 1px solid rgba(0, 135, 90, .12);
	border-radius: 16px;
	padding: 40px 36px;
	margin-bottom: 20px;
}

.hero-main {
	flex: 1;
	min-width: 0;
}

.hero-title {
	margin: 12px 0 12px;
	font-size: 30px;
	line-height: 1.35;
	letter-spacing: -.4px;
	word-break: break-word;
}

.hero-intro {
	margin: 0 0 22px;
	color: var(--muted);
	font-size: 14.5px;
	line-height: 1.9;
	white-space: pre-wrap;
}

.hero-actions {
	display: flex;
	align-items: center;
	gap: 12px;
	flex-wrap: wrap;
}

.btn-lg {
	padding: 11px 26px;
	font-size: 14.5px;
	border-radius: 10px;
}

.hero-cover {
	flex-shrink: 0;
	width: 220px;
	border-radius: 12px;
	overflow: hidden;
	border: 1px solid rgba(0, 135, 90, .12);
}

.hero-cover img {
	display: block;
	width: 100%;
	height: auto;
}

/* ---------------- 二维码 ---------------- */
.qr-block {
	display: flex;
	align-items: center;
	gap: 28px;
	justify-content: space-between;
	flex-wrap: wrap;
}

.qr-text {
	flex: 1;
	min-width: 220px;
}

.qr-text h2 {
	margin: 0 0 8px;
	font-size: 17px;
}

.qr-text p {
	margin: 0 0 6px;
	font-size: 13px;
}

.notice {
	color: var(--muted);
	font-size: 12.5px;
	margin-top: 12px !important;
	padding-top: 10px;
	border-top: 1px dashed var(--line);
}

.qr-img {
	width: 150px;
	height: 150px;
	border-radius: 10px;
	overflow: hidden;
	border: 1px solid var(--line);
	flex-shrink: 0;
}

.qr-img img {
	display: block;
	width: 100%;
	height: 100%;
	object-fit: contain;
}

.qr-empty {
	display: flex;
	align-items: center;
	justify-content: center;
	background: #fafbfb;
}

@media (max-width: 720px) {
	.hero {
		flex-direction: column-reverse;
		align-items: flex-start;
		padding: 28px 22px;
	}

	.hero-cover {
		width: 100%;
	}

	.hero-title {
		font-size: 24px;
	}

	.qr-block {
		gap: 18px;
	}

	.qr-img {
		width: 120px;
		height: 120px;
	}
}
</style>
