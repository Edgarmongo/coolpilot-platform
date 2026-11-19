<template>
	<div class="home">
		<!-- 1. 首屏 -->
		<section class="hero">
			<div class="hero-grid">
				<div class="hero-copy">
					<p class="eyebrow">{{ t('home.eyebrow') }}</p>
					<h1 class="hero-title">
						{{ t('home.titleA') }}<br/>{{ t('home.titleB') }}
					</h1>
					<p class="hero-lead">{{ t('home.lead') }}</p>
					<p class="hero-desc">{{ t('home.desc') }}</p>

					<div class="hero-actions">
						<NuxtLink :to="localePath('/archives')" class="btn">
							{{ t('home.browse') }}
							<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor"
							     stroke-width="2" stroke-linecap="round">
								<path d="M5 12h14M13 6l6 6-6 6"/>
							</svg>
						</NuxtLink>
						<NuxtLink :to="localePath('/knowledge')" class="btn btn-ghost">{{ t('home.ctaKnowledge') }}</NuxtLink>
						<NuxtLink :to="localePath('/service')" class="btn btn-ghost">{{ t('home.ctaService') }}</NuxtLink>
					</div>

					<div class="hero-note">
						<span class="dot"></span>{{ t('home.note') }}
					</div>

					<!-- 数字只做注脚，不单独占一屏；没有数据时整行不显示 -->
					<div v-if="stats.length" class="hero-stats">
						<span v-for="(item, i) in stats" :key="item.key">
							<strong>{{ item.value }}</strong>{{ item.unit }}
							<i v-if="i < stats.length - 1" class="sep">·</i>
						</span>
					</div>
				</div>

				<div class="hero-art" aria-hidden="true">
					<svg width="430" height="330" viewBox="0 0 430 330" fill="none" xmlns="http://www.w3.org/2000/svg">
						<defs>
							<linearGradient id="heroGlow" x1="0" y1="0" x2="1" y2="1">
								<stop offset="0" stop-color="#00875A" stop-opacity=".09"/>
								<stop offset="1" stop-color="#00875A" stop-opacity=".01"/>
							</linearGradient>
						</defs>
						<circle cx="215" cy="160" r="128" fill="url(#heroGlow)"/>
						<circle cx="215" cy="160" r="128" stroke="#00875A" stroke-opacity=".13" stroke-dasharray="3 6"/>

						<!-- 浏览器窗口 + 文章列表 -->
						<rect x="52" y="66" width="286" height="190" rx="12" fill="#fff" stroke="#00875A" stroke-opacity=".2"/>
						<path d="M52 78c0-6.6 5.4-12 12-12h262c6.6 0 12 5.4 12 12v14H52V78z" fill="#F2FAF8"/>
						<circle cx="70" cy="79" r="3.5" fill="#00875A" fill-opacity=".22"/>
						<circle cx="82" cy="79" r="3.5" fill="#00875A" fill-opacity=".14"/>
						<rect x="100" y="75" width="150" height="8" rx="4" fill="#00875A" fill-opacity=".08"/>

						<rect x="70" y="106" width="60" height="10" rx="5" fill="#00875A" fill-opacity=".3"/>
						<rect x="70" y="128" width="128" height="7" rx="3.5" fill="#00875A" fill-opacity=".16"/>
						<rect x="70" y="146" width="150" height="7" rx="3.5" fill="#00875A" fill-opacity=".1"/>
						<rect x="70" y="164" width="112" height="7" rx="3.5" fill="#00875A" fill-opacity=".1"/>
						<rect x="70" y="186" width="46" height="6" rx="3" fill="#00875A" fill-opacity=".18"/>
						<rect x="124" y="186" width="34" height="6" rx="3" fill="#00875A" fill-opacity=".1"/>
						<rect x="70" y="206" width="90" height="7" rx="3.5" fill="#00875A" fill-opacity=".1"/>
						<rect x="70" y="224" width="64" height="7" rx="3.5" fill="#00875A" fill-opacity=".1"/>

						<!-- 右侧图表 -->
						<rect x="216" y="106" width="102" height="122" rx="9" fill="#F2FAF8" stroke="#00875A" stroke-opacity=".12"/>
						<rect x="230" y="196" width="12" height="20" rx="3" fill="#00875A" fill-opacity=".22"/>
						<rect x="248" y="180" width="12" height="36" rx="3" fill="#00875A" fill-opacity=".35"/>
						<rect x="266" y="164" width="12" height="52" rx="3" fill="#00875A" fill-opacity=".5"/>
						<rect x="284" y="148" width="12" height="68" rx="3" fill="#00875A" fill-opacity=".7"/>
						<rect x="230" y="120" width="44" height="6" rx="3" fill="#00875A" fill-opacity=".18"/>

						<!-- 浮层：文章卡 -->
						<g>
							<rect x="300" y="42" width="104" height="70" rx="10" fill="#fff" stroke="#00875A" stroke-opacity=".2"/>
							<rect x="312" y="54" width="38" height="26" rx="5" fill="#00875A" fill-opacity=".1"/>
							<rect x="358" y="56" width="34" height="6" rx="3" fill="#00875A" fill-opacity=".28"/>
							<rect x="358" y="70" width="26" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>
							<rect x="312" y="90" width="80" height="6" rx="3" fill="#00875A" fill-opacity=".12"/>
						</g>

						<!-- 浮层：标签 -->
						<g>
							<rect x="28" y="188" width="86" height="30" rx="8" fill="#fff" stroke="#00875A" stroke-opacity=".2"/>
							<circle cx="44" cy="203" r="5" fill="#00875A" fill-opacity=".25"/>
							<rect x="56" y="199" width="42" height="7" rx="3.5" fill="#00875A" fill-opacity=".22"/>
						</g>
						<g>
							<rect x="44" y="232" width="72" height="26" rx="8" fill="#00875A" fill-opacity=".9"/>
							<rect x="56" y="241" width="46" height="7" rx="3.5" fill="#fff" fill-opacity=".85"/>
						</g>

						<circle cx="372" cy="150" r="4" fill="#00875A" fill-opacity=".28"/>
						<circle cx="60" cy="128" r="3.5" fill="#00875A" fill-opacity=".2"/>
					</svg>
				</div>
			</div>
		</section>

		<!-- 2. 最新文章 -->
		<section class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.latest') }}</h2>
					<p class="sec-sub">{{ t('home.latestSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/archives')" class="more">
					{{ t('home.viewAll') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div v-if="!posts.length" class="empty">{{ t('home.empty') }}</div>
			<template v-else>
				<!--
					文章数量不定：只有一篇时把三栏铺成三张卡会空出三分之二，
					所以第一篇单独做横版头条，其余按卡片排在下面。1 篇时是一个完整版块，
					3 篇时是「头条 + 两张卡」，都不用等内容攒够才好看。
				-->
				<NuxtLink :to="localePath(`/blog/${featured.slug || featured.id}`)" class="card featured">
					<div class="featured-cover">
						<AppCover :seed="`${featured.id}-${featured.title}`" :height="248"/>
					</div>
					<div class="featured-body">
						<div v-if="featured.category" class="tags">
							<span class="tag field">{{ featured.category.name }}</span>
						</div>
						<h3 class="featured-title">{{ featured.title }}</h3>
						<p class="featured-desc">{{ plain(featured.description) }}</p>
						<div class="card-foot featured-foot">
							<span>{{ formatDate(featured.createTime, 'YYYY-MM-DD') }}</span>
							<span v-if="featured.readTime">{{ t('home.minutes', {n: featured.readTime}) }}</span>
							<span v-else-if="featured.views">{{ t('home.reads', {n: featured.views}) }}</span>
							<span class="read-more">{{ t('home.browse') }}</span>
						</div>
					</div>
				</NuxtLink>

				<div v-if="rest.length" class="grid3 rest-grid">
					<NuxtLink v-for="post in rest" :key="post.id" :to="localePath(`/blog/${post.slug || post.id}`)"
					          class="card cover-card">
						<AppCover :seed="`${post.id}-${post.title}`" :height="148"/>
						<div class="card-pad">
							<div v-if="post.category" class="tags">
								<span class="tag field">{{ post.category.name }}</span>
							</div>
							<h3 class="card-title">{{ post.title }}</h3>
							<p class="card-desc">{{ plain(post.description) }}</p>
							<div class="card-foot">
								<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
								<span v-if="post.readTime">{{ t('home.minutes', {n: post.readTime}) }}</span>
								<span v-else-if="post.views">{{ t('home.reads', {n: post.views}) }}</span>
							</div>
						</div>
					</NuxtLink>
				</div>
			</template>
		</section>

		<!-- 3. 技术专题 -->
		<section v-if="topics.length" class="section">
			<div class="teaser">
				<div class="teaser-body">
					<div class="teaser-badges">
						<span class="badge">{{ t('home.entryTopic') }}</span>
						<span class="badge soft">{{ topics.length }} {{ t('home.topicUnit') }}</span>
					</div>
					<h2 class="teaser-title">{{ t('home.entryTopic') }}</h2>
					<p class="teaser-desc">{{ t('home.topicsSub') }}</p>
					<div class="tags teaser-tags">
						<NuxtLink v-for="item in topics.slice(0, 3)" :key="item.id"
						          :to="localePath(`/topic/${item.slug}`)" class="tag">
							{{ item.title }}<span class="tag-n">{{ item.articleCount || 0 }}</span>
						</NuxtLink>
					</div>
					<NuxtLink :to="localePath('/topic')" class="btn btn-sm teaser-cta">
						{{ t('home.viewTopics') }}
						<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
						     stroke-width="2" stroke-linecap="round">
							<path d="M5 12h14M13 6l6 6-6 6"/>
						</svg>
					</NuxtLink>
				</div>
				<div class="teaser-art" aria-hidden="true">
					<svg width="330" height="220" viewBox="0 0 330 220" fill="none" xmlns="http://www.w3.org/2000/svg">
						<rect x="30" y="26" width="112" height="168" rx="9" fill="#fff" stroke="#00875A" stroke-opacity=".18"/>
						<rect x="30" y="26" width="112" height="22" rx="9" fill="#00875A" fill-opacity=".07"/>
						<rect x="44" y="62" width="56" height="7" rx="3.5" fill="#00875A" fill-opacity=".28"/>
						<rect x="44" y="80" width="84" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="44" y="94" width="72" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="44" y="114" width="40" height="34" rx="5" fill="#00875A" fill-opacity=".08"/>
						<rect x="90" y="114" width="38" height="34" rx="5" fill="#00875A" fill-opacity=".08"/>
						<rect x="44" y="162" width="64" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<path d="M150 110h22" stroke="#00875A" stroke-opacity=".3" stroke-dasharray="4 4"/>
						<path d="M166 104l6 6-6 6" stroke="#00875A" stroke-opacity=".3" fill="none" stroke-width="1.6"/>
						<rect x="182" y="42" width="118" height="152" rx="9" fill="#fff" stroke="#00875A" stroke-opacity=".18"/>
						<rect x="196" y="58" width="46" height="6" rx="3" fill="#00875A" fill-opacity=".3"/>
						<rect x="196" y="76" width="88" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
						<rect x="196" y="92" width="88" height="20" rx="4" fill="#00875A" fill-opacity=".07"/>
						<rect x="204" y="99" width="38" height="6" rx="3" fill="#00875A" fill-opacity=".3"/>
						<rect x="196" y="118" width="88" height="20" rx="4" fill="#00875A" fill-opacity=".07"/>
						<rect x="204" y="125" width="30" height="6" rx="3" fill="#00875A" fill-opacity=".3"/>
						<rect x="196" y="144" width="88" height="20" rx="4" fill="#00875A" fill-opacity=".07"/>
						<rect x="204" y="151" width="44" height="6" rx="3" fill="#00875A" fill-opacity=".3"/>
						<rect x="196" y="172" width="60" height="5" rx="2.5" fill="#00875A" fill-opacity=".12"/>
					</svg>
				</div>
			</div>
		</section>

		<!-- 4. 知识产品 -->
		<section v-if="products.length" class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.entryKnowledge') }}</h2>
					<p class="sec-sub">{{ t('home.productsSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/knowledge')" class="more">
					{{ t('home.viewProducts') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div class="grid3">
				<NuxtLink v-for="item in products" :key="item.id" :to="localePath(`/knowledge/${item.productNo}`)"
				          class="card cover-card">
					<AppCover :seed="`${item.id}-${item.title}`" :height="120" :variant="3"/>
					<div class="card-pad">
						<h3 class="card-title">{{ item.title }}</h3>
						<p class="card-desc">{{ item.summary || item.subtitle }}</p>
						<div class="card-foot">
							<span class="price">{{ priceLabel(item) }}</span>
							<span class="tag">{{ typeLabel(item.productType) }}</span>
						</div>
					</div>
				</NuxtLink>
			</div>
		</section>

		<!-- 5. 技术服务 -->
		<section v-if="services.length" class="services">
			<div class="svc-inner">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.entryService') }}</h2>
					<p class="sec-sub">{{ t('home.servicesSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/service')" class="more">
					{{ t('home.entryServiceDesc') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div class="grid3">
				<NuxtLink v-for="item in services" :key="item.id" :to="localePath(`/knowledge/${item.productNo}`)"
				          class="svc">
					<span class="svc-ico">
						<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
						     stroke-width="1.8" stroke-linecap="round">
							<path d="M12 3l9 5-9 5-9-5 9-5z"/>
							<path d="M3 12l9 5 9-5"/>
						</svg>
					</span>
					<span class="svc-body">
						<strong>{{ item.title }}</strong>
						<span class="svc-desc">{{ item.summary || item.subtitle }}</span>
					</span>
				</NuxtLink>
			</div>

			<div class="svc-actions">
				<NuxtLink :to="localePath('/demand/new')" class="btn">{{ t('servicePage.submitDemand') }}</NuxtLink>
				<span class="svc-note">{{ t('home.serviceNote') }}</span>
			</div>
			</div>
		</section>

		<!-- 6. 知识星球 -->
		<section v-if="planet.enabled" class="planet-bar">
			<div class="planet-copy">
				<span class="badge soft">{{ t('planetPage.tag') }}</span>
				<h2>{{ planet.name || t('planetPage.title') }}</h2>
				<p>{{ planet.shortIntro }}</p>
			</div>
			<NuxtLink :to="localePath('/planet')" class="btn btn-ghost btn-sm">{{ t('home.ctaPlanetDetail') }}</NuxtLink>
		</section>

		<!-- 7. 关注我 -->
		<section v-if="socials.length" class="section follow">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.followTitle') }}</h2>
				</div>
				<NuxtLink :to="localePath('/follow')" class="more">{{ t('home.followAll') }}</NuxtLink>
			</div>
			<div class="social-row">
				<NuxtLink v-for="item in socials" :key="item.id" :to="localePath('/follow')" class="social-chip"
				          :title="item.accountName">
					<SocialIcon :platform="item.platform"/>
					<span>{{ item.accountName }}</span>
				</NuxtLink>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
// 首页不单独设 title：app.vue 的 titleTemplate 在没有 title 时直接用品牌名，
// 写成 t('site.brandTitle') 反而会造一个不存在的键
const {t, te, locale} = useI18n()
const localePath = useLocalePath()

const {data: blogRes} = await useAsyncData('home-blogs', () => call('/blogs?pageNum=1'))
const {data: archiveRes} = await useAsyncData('home-archive', () => call('/archives'))
const {data: productRes} = await useAsyncData('home-products', () => call('/products?pageNum=1&pageSize=6'))
const {data: topicRes} = await useAsyncData('home-topics', () => call('/topics'))
const {data: planetRes} = await useAsyncData('home-planet', () => call(`/planet?lang=${locale.value}`), {watch: [locale]})
const {data: socialRes} = await useAsyncData('home-socials', () => call('/social'))
const {data: serviceRes} = await useAsyncData('home-services', () => call('/products?type=SERVICE&pageNum=1&pageSize=3'))

/** 首页只放前三篇：列表再长，首屏也留不住人 */
const posts = computed(() => ((blogRes.value as any)?.data?.list || []).slice(0, 3))
const featured = computed(() => posts.value[0] || {})
const rest = computed(() => posts.value.slice(1))
const totalPosts = computed(() => (archiveRes.value as any)?.data?.count || 0)

const topics = computed<any[]>(() => (topicRes.value as any)?.data || [])

const products = computed(() => {
	const all = (productRes.value as any)?.data?.list || []
	// 服务类商品在下面有独立区块，这里只展示知识类，避免同一张卡片出现两次
	return all.filter((item: any) => item.productType !== 'SERVICE').slice(0, 3)
})

const services = computed(() => {
	const list = (serviceRes.value as any)?.data?.list || []
	return list.slice(0, 3)
})

const socials = computed(() => ((socialRes.value as any)?.data || []).slice(0, 6))

const planet = computed(() => {
	const d = (planetRes.value as any)?.data || {}
	const intro = d.intro || ''
	return {
		enabled: !!d.enabled,
		name: d.name || '',
		// 首页只放一行，取第一段并截断，完整介绍在 /planet
		shortIntro: intro.split('\n')[0].slice(0, 80) || t('home.planetFallbackDesc')
	}
})

const stats = computed(() => {
	const list: {key: string, value: number, unit: string}[] = []
	if (totalPosts.value) {
		list.push({key: 'posts', value: totalPosts.value, unit: t('home.blogUnit')})
	}
	if (products.value.length) {
		const total = (productRes.value as any)?.data?.total || products.value.length
		list.push({key: 'products', value: total, unit: t('home.productUnit')})
	}
	if (topics.value.length) {
		list.push({key: 'topics', value: topics.value.length, unit: t('home.topicUnit')})
	}
	return list
})

/** 摘要可能是 HTML（后台富文本），列表里只取纯文本，避免把标签当正文显示 */
function plain(html: string) {
	return (html || '').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}

function typeLabel(type: string) {
	const key = `knowledge.types.${type}`
	return te(key) ? t(key) : type
}

function priceLabel(item: any) {
	if (item.priceMode === 'CUSTOM') {
		return t('common.quote')
	}
	const value = Number(item.price || 0).toFixed(2).replace(/\.00$/, '')
	const amount = `¥${value}`
	return item.priceMode === 'STARTING' ? t('common.from', {price: amount}) : amount
}
</script>

<style scoped>
/* ============================================================
   首屏
   ============================================================ */
.hero {
	background: linear-gradient(180deg, var(--teal-050) 0%, #fff 84%);
	/* 负 margin 抵消 layout 给 main 的 22px 顶距，浅色背景直接贴住头部 */
	margin: -22px calc(50% - 50vw) 0;
	padding: 56px 0 40px;
}

.hero-grid {
	display: grid;
	grid-template-columns: minmax(0, 1fr) 430px;
	gap: 40px;
	align-items: center;
	max-width: var(--wrap);
	margin: 0 auto;
	padding: 0 24px;
}

.hero-title {
	font-size: 40px;
	font-weight: 800;
	line-height: 1.28;
	letter-spacing: -.9px;
	margin-bottom: 16px;
}

.hero-lead {
	font-size: 18px;
	font-weight: 500;
	color: var(--ink-2);
	margin-bottom: 12px;
}

.hero-desc {
	font-size: 14.5px;
	line-height: 1.8;
	color: var(--muted);
	max-width: 560px;
	margin-bottom: 26px;
}

.hero-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 12px;
}

.hero-note {
	display: flex;
	align-items: center;
	gap: 8px;
	margin-top: 18px;
	font-size: 12.5px;
	color: var(--muted);
}

.hero-stats {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 6px;
	margin-top: 14px;
	font-size: 12.5px;
	color: var(--faint);
}

.hero-stats strong {
	color: var(--teal);
	font-size: 14px;
	font-weight: 700;
	margin-right: 3px;
}

.hero-stats .sep {
	font-style: normal;
	margin: 0 8px;
	color: var(--line-3);
}

.hero-art {
	display: flex;
	justify-content: center;
}

/* ============================================================
   分区
   ============================================================ */
.section {
	padding: 44px 0 0;
}

/* ============================================================
   头条（横版）
   ============================================================ */
.featured {
	display: grid;
	grid-template-columns: 420px minmax(0, 1fr);
	padding: 0;
	overflow: hidden;
	transition: transform .18s ease, border-color .18s ease;
}

.featured:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.featured-cover {
	/* min-width:0 很关键：grid 项默认 min-width:auto，封面图会按内容宽撑开并盖住右侧文字 */
	position: relative;
	min-width: 0;
	overflow: hidden;
	border-right: 1px solid var(--line);
}

.featured-cover .cp-cover {
	width: 100%;
	height: 100%;
}

.featured-body {
	display: flex;
	flex-direction: column;
	padding: 28px 32px;
}

.featured-title {
	font-size: 24px;
	font-weight: 800;
	line-height: 1.4;
	letter-spacing: -.4px;
	margin: 10px 0 10px;
}

.featured:hover .featured-title {
	color: var(--teal);
}

.featured-desc {
	font-size: 14px;
	line-height: 1.75;
	color: var(--muted);
	flex: 1;
	display: -webkit-box;
	-webkit-line-clamp: 3;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.featured-foot {
	margin-top: 18px;
	padding-top: 14px;
}

.read-more {
	color: var(--teal);
	font-weight: 600;
}

.rest-grid {
	margin-top: 20px;
}

/* ============================================================
   卡片（带封面）
   ============================================================ */
.cover-card {
	padding: 0;
	overflow: hidden;
	display: flex;
	flex-direction: column;
	transition: transform .18s ease, border-color .18s ease;
}

.cover-card:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.card-title {
	font-size: 16.5px;
	font-weight: 700;
	line-height: 1.45;
	margin: 10px 0 8px;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.cover-card:hover .card-title {
	color: var(--teal);
}

.card-desc {
	font-size: 13px;
	line-height: 1.65;
	color: var(--muted);
	flex: 1;
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.card-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	margin-top: 14px;
	padding-top: 11px;
	border-top: 1px solid var(--line);
	font-size: 12px;
	color: var(--muted);
}

.price {
	color: var(--teal);
	font-size: 15px;
	font-weight: 700;
}

/* ============================================================
   专题横条
   ============================================================ */
.teaser {
	display: grid;
	grid-template-columns: 1.1fr .9fr;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
	background: linear-gradient(120deg, #f4fbf9, #eaf6f3);
	transition: transform .18s, border-color .18s;
}

.teaser:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.teaser-body {
	padding: 30px 34px;
}

.teaser-badges {
	display: flex;
	gap: 8px;
	margin-bottom: 14px;
}

.teaser-title {
	font-size: 25px;
	font-weight: 700;
	margin-bottom: 12px;
}

.teaser-desc {
	font-size: 14.5px;
	color: var(--ink-2);
	max-width: 520px;
	margin-bottom: 8px;
}

.teaser-tags {
	margin: 12px 0 0;
}

.tag-n {
	margin-left: 6px;
	color: var(--faint);
	font-size: 11px;
}

.teaser-cta {
	margin-top: 18px;
}

.teaser-art {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 16px;
}

/* ============================================================
   技术服务
   ============================================================ */
.services {
	margin: 48px calc(50% - 50vw) 0;
	padding: 44px 0 46px;
	background: var(--teal-050);
	border-top: 1px solid var(--line);
	border-bottom: 1px solid var(--line);
}

/* 区块通栏铺底色，内容仍然回到 1200 栅格里 */
.svc-inner {
	max-width: var(--wrap);
	margin: 0 auto;
	padding: 0 24px;
}

.svc {
	display: flex;
	align-items: flex-start;
	gap: 14px;
	padding: 18px 20px;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: 10px;
	transition: transform .18s, border-color .18s;
}

.svc:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.svc-ico {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 38px;
	height: 38px;
	border-radius: 9px;
	background: var(--teal-050);
	border: 1px solid var(--line);
	color: var(--teal);
	flex: 0 0 auto;
}

.svc-body {
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.svc-body strong {
	font-size: 15px;
	font-weight: 700;
}

.svc-desc {
	font-size: 13px;
	line-height: 1.6;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 2;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.svc-actions {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 16px;
	margin-top: 26px;
}

.svc-note {
	font-size: 13px;
	color: var(--muted);
}

/* ============================================================
   知识星球
   ============================================================ */
.planet-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	flex-wrap: wrap;
	gap: 18px;
	margin-top: 24px;
	padding: 22px 26px;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	background: #fff;
}

.planet-copy {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 12px;
}

.planet-copy h2 {
	font-size: 17px;
	font-weight: 700;
}

.planet-copy p {
	font-size: 13px;
	color: var(--muted);
	width: 100%;
}

/* ============================================================
   关注
   ============================================================ */
.follow {
	padding-bottom: 8px;
}

.social-row {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.social-chip {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 7px 14px 7px 8px;
	border: 1px solid var(--line);
	border-radius: 10px;
	background: #fff;
	font-size: 13px;
	color: var(--ink);
	transition: border-color .15s, transform .15s;
}

.social-chip:hover {
	border-color: var(--line-2);
	transform: translateY(-1px);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1100px) {
	.hero-grid {
		grid-template-columns: 1fr;
		gap: 24px;
	}

	.hero-art {
		display: none;
	}

	.teaser {
		grid-template-columns: 1fr;
	}

	.teaser-art {
		display: none;
	}

	.svc-inner {
		padding: 0 24px;
	}

	.featured {
		grid-template-columns: 1fr;
	}

	.featured-cover {
		border-right: 0;
		border-bottom: 1px solid var(--line);
	}

	.featured-body {
		padding: 22px;
	}

	.featured-title {
		font-size: 20px;
	}
}

@media (max-width: 640px) {
	.hero {
		padding: 36px 0 30px;
	}

	.hero-grid {
		padding: 0 18px;
	}

	.hero-title {
		font-size: 29px;
		letter-spacing: -.5px;
	}

	.hero-lead {
		font-size: 16px;
	}

	.section {
		padding-top: 34px;
	}

	.teaser-body {
		padding: 22px 20px;
	}

	.teaser-title {
		font-size: 21px;
	}

	.services {
		margin-top: 34px;
		padding-top: 30px;
		padding-bottom: 32px;
	}

	.planet-bar {
		flex-direction: column;
		align-items: flex-start;
	}
}
</style>
