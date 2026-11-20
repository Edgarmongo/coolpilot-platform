<template>
	<div class="home">
		<!-- 1. 首屏 -->
		<section class="hero">
			<div class="hero-grid">
				<div class="hero-copy">
					<p class="eyebrow">{{ t('home.eyebrowNew') }}</p>
					<h1 class="hero-title">
						{{ t('home.titleNew') }}<br/>{{ t('home.titleNewB') }}
					</h1>
					<p class="hero-lead">{{ t('home.leadNew') }}</p>
					<p class="hero-desc">{{ t('home.descNew') }}</p>

					<div class="hero-actions">
						<NuxtLink :to="localePath('/archives')" class="btn">
							{{ t('home.browse') }}
							<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor"
							     stroke-width="2" stroke-linecap="round">
								<path d="M5 12h14M13 6l6 6-6 6"/>
							</svg>
						</NuxtLink>
						<NuxtLink :to="localePath('/open-source')" class="btn btn-ghost">{{ t('home.ctaOss') }}</NuxtLink>
						<NuxtLink :to="localePath('/service')" class="btn btn-ghost">{{ t('home.ctaService') }}</NuxtLink>
					</div>

					<!-- 平台内容体量，只做注脚；全为 0 时整行不显示 -->
					<div v-if="stats.length" class="hero-stats">
						<span v-for="(item, i) in stats" :key="item.key">
							<strong>{{ item.value }}</strong>{{ item.unit }}
							<i v-if="i < stats.length - 1" class="sep">·</i>
						</span>
					</div>
				</div>

				<!--
					右侧放「开源研究」而不是个人介绍：首页只展示平台产出，
					项目背书比自我描述更有说服力，个人信息统一在 /about。
				-->
				<NuxtLink v-if="featuredOss" :to="localePath(`/open-source/projects/${featuredOss.slug}`)"
				          class="research-card">
					<div class="rc-top">
						<div>
							<div class="rc-name">{{ featuredOss.name }}</div>
							<div class="rc-tags">
								<span v-for="tag in ossTags" :key="tag" class="rc-tag">{{ tag }}</span>
							</div>
						</div>
						<span class="rc-verdict">{{ t('home.ossVerified') }}</span>
					</div>
					<div class="rc-body">
						<p class="rc-quote">{{ featuredOss.summary }}</p>
						<div class="rc-foot">
							<div v-if="featuredOss.articleCount">
								<b>{{ featuredOss.articleCount }}</b>{{ t('home.ossStudyUnit') }}
							</div>
							<div v-if="featuredOss.lastVerifiedAt">
								<b>{{ formatDate(featuredOss.lastVerifiedAt, 'MM-DD') }}</b>{{ t('home.ossLastCheck') }}
							</div>
							<div v-if="featuredOss.verdict">
								<b>{{ verdictLabel(featuredOss.verdict) }}</b>{{ t('home.ossFit') }}
							</div>
						</div>
					</div>
				</NuxtLink>

				<!-- 还没有研究项目时，用「研究方法」撑住这一栏，不显示空白 -->
				<div v-else class="research-card">
					<div class="rc-top">
						<div>
							<div class="rc-name">{{ t('home.ossTitle') }}</div>
							<div class="rc-tags">
								<span class="rc-tag">{{ t('home.ossMethodSub') }}</span>
							</div>
						</div>
					</div>
					<div class="rc-body">
						<ol class="rc-steps">
							<li v-for="n in 3" :key="n">{{ t('home.ossStep' + n) }}</li>
						</ol>
					</div>
				</div>
			</div>
		</section>

		<!-- 2. 技术领域 -->
		<section class="section domains">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.domainsTitle') }}</h2>
					<p class="sec-sub">{{ t('home.domainsSub') }}</p>
				</div>
			</div>
			<div class="domain-grid">
				<div v-for="d in domains" :key="d.key" class="domain">
					<span class="domain-ico">{{ d.ico }}</span>
					<h3>{{ t(d.title) }}</h3>
					<p>{{ t(d.desc) }}</p>
					<ul>
						<li v-for="point in d.points" :key="point">{{ t(point) }}</li>
					</ul>
				</div>
			</div>
		</section>

		<!-- 3. 精选技术内容 -->
		<section class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.contentTitle') }}</h2>
					<p class="sec-sub">{{ t('home.contentSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/archives')" class="more">
					{{ t('home.viewAll') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div v-if="!posts.length" class="writing-panel">
				<h4>{{ t('home.writingPlan') }}</h4>
				<p class="wp-sub">{{ t('home.contentSub') }}</p>
				<ul>
					<li v-for="plan in writingPlan" :key="plan.title">
						<span class="wp-title">{{ t(plan.title) }}<span class="wp-badge">{{ t(plan.status) }}</span></span>
						<span class="wp-desc">{{ t(plan.desc) }}</span>
					</li>
				</ul>
			</div>
			<div v-else class="content-grid">
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

				<div class="content-side">
					<template v-if="rest.length">
						<NuxtLink v-for="post in rest" :key="post.id"
						          :to="localePath(`/blog/${post.slug || post.id}`)" class="card side-post">
							<AppCover :seed="`${post.id}-${post.title}`" :height="120"/>
							<div class="card-pad">
								<div v-if="post.category" class="tags">
									<span class="tag field">{{ post.category.name }}</span>
								</div>
								<h3 class="card-title">{{ post.title }}</h3>
								<div class="card-foot">
									<span>{{ formatDate(post.createTime, 'YYYY-MM-DD') }}</span>
									<span v-if="post.readTime">{{ t('home.minutes', {n: post.readTime}) }}</span>
								</div>
							</div>
						</NuxtLink>
					</template>

					<!-- 文章不足 3 篇时，右侧用写作计划补位，避免首页出现空洞 -->
					<div v-else class="writing-panel compact">
						<h4>{{ t('home.writingPlan') }}</h4>
						<ul>
							<li v-for="plan in writingPlan" :key="plan.title">
								<span class="wp-title">{{ t(plan.title) }}<span class="wp-badge">{{ t(plan.status) }}</span></span>
								<span class="wp-desc">{{ t(plan.desc) }}</span>
							</li>
						</ul>
					</div>
				</div>
			</div>
		</section>

		<!-- 4. 开源项目与技术研究 -->
		<section class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.ossTitle') }}</h2>
					<p class="sec-sub">{{ t('home.ossSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/open-source/projects')" class="more">
					{{ t('home.ossAll') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div class="oss-grid">
				<NuxtLink v-if="featuredOss" :to="localePath(`/open-source/projects/${featuredOss.slug}`)"
				          class="oss-main">
					<div class="oss-cover">
						<span class="oss-initials">{{ initialsOf(featuredOss.name) }}</span>
						<h3>{{ featuredOss.name }}</h3>
						<div class="oss-langs">
							<span v-if="featuredOss.language" class="rc-tag">{{ featuredOss.language }}</span>
							<span v-if="featuredOss.license" class="rc-tag">{{ featuredOss.license }}</span>
						</div>
					</div>
					<div class="oss-body">
						<p>{{ featuredOss.summary }}</p>
						<div class="oss-points">
							<div class="oss-point">
								<span class="k">{{ t('home.ossDeploy') }}</span>
								<span class="v">
									{{ featuredOss.articleCount
										? featuredOss.articleCount + ' ' + t('home.ossStudyUnit')
										: t('home.ossVerified') }}
								</span>
							</div>
							<div class="oss-point">
								<span class="k">{{ t('home.ossFit') }}</span>
								<span class="v">{{ verdictLabel(featuredOss.verdict) }}</span>
							</div>
							<div class="oss-point">
								<span class="k">{{ t('home.ossLastCheck') }}</span>
								<span class="v">{{ featuredOss.lastVerifiedAt ? formatDate(featuredOss.lastVerifiedAt, 'YYYY-MM-DD') : '—' }}</span>
							</div>
						</div>
					</div>
				</NuxtLink>

				<div :class="featuredOss ? 'method' : 'method full'">
					<h4>{{ t('home.ossMethodTitle') }}</h4>
					<p class="m-sub">{{ t('home.ossMethodSub') }}</p>
					<ol>
						<li v-for="n in 6" :key="n">{{ t('home.ossStep' + n) }}</li>
					</ol>
				</div>
			</div>
		</section>

		<!-- 5. 知识产品 -->
		<section class="section">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.prodTitle') }}</h2>
					<p class="sec-sub">{{ t('home.prodSub') }}</p>
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
				<template v-if="products.length">
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
				</template>

				<!--
					商品为 0 时不再隐藏整块：改成「筹备中 + 预约通知」。
					既能让首页保持商业信号，也能提前收集真实需求。
				-->
				<div v-for="(p, i) in (products.length ? [] : plannedProducts)" :key="p.title" class="prod-planned">
					<div class="pp-cover">
						<span class="pp-no">{{ String(i + 1).padStart(2, '0') }}</span>
						<span class="pp-soon">{{ t('home.comingSoon') }}</span>
					</div>
					<div class="pp-body">
						<h3>{{ t(p.title) }}</h3>
						<p>{{ t(p.desc) }}</p>
						<div class="pp-foot">
							<span class="pp-price">{{ t('home.priceTbd') }}</span>
							<NuxtLink :to="localePath('/demand/new')" class="pp-btn">{{ t('home.notifyMe') }}</NuxtLink>
						</div>
					</div>
				</div>
			</div>
		</section>

		<!-- 6. 技术服务 -->
		<section class="section svc-block">
			<div class="sec-head">
				<div>
					<h2 class="sec-title"><span class="bar"></span>{{ t('home.svcTitle') }}</h2>
					<p class="sec-sub">{{ t('home.svcSub') }}</p>
				</div>
				<NuxtLink :to="localePath('/demand/new')" class="more">
					{{ t('servicePage.submitDemand') }}
					<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
					     stroke-width="2" stroke-linecap="round">
						<path d="M5 12h14M13 6l6 6-6 6"/>
					</svg>
				</NuxtLink>
			</div>

			<div class="grid3 svc-grid">
				<!-- 有上架的服务商品时，优先展示真实商品 -->
				<NuxtLink v-for="item in services" :key="item.id" :to="localePath(`/knowledge/${item.productNo}`)"
				          class="card cover-card">
					<div class="card-pad">
						<h3 class="card-title">{{ item.title }}</h3>
						<p class="card-desc">{{ item.summary || item.subtitle }}</p>
						<div class="card-foot">
							<span class="price">{{ priceLabel(item) }}</span>
						</div>
					</div>
				</NuxtLink>

				<!--
					服务商品为 0 时展示服务目录，而不是让整块消失。
					这段内容本身既是转化入口，也是搜索引擎能收录的服务说明。
				-->
				<template v-if="!services.length">
					<div v-for="(s, i) in serviceCatalog" :key="s.title" class="svc-card">
						<span class="svc-no">{{ String(i + 1).padStart(2, '0') }}</span>
						<h3>{{ t(s.title) }}</h3>
						<p>{{ t(s.desc) }}</p>
						<span class="svc-scope">{{ t(s.scope) }}</span>
					</div>
				</template>

				<!--
					CTA 卡始终显示：它是这一块唯一的转化出口。
					放在 v-if 里面的话，一旦上架了服务商品，咨询入口就会消失。
				-->
				<NuxtLink :to="localePath('/demand/new')" class="svc-card svc-cta">
					<div>
						<h3>{{ t('home.svcNotSure') }}</h3>
						<p>{{ t('home.svcNotSureDesc') }}</p>
					</div>
					<span class="btn btn-sm">{{ t('servicePage.submitDemand') }}</span>
				</NuxtLink>
			</div>
		</section>

		<!-- 7. 订阅与社区 -->
		<section v-if="planet.enabled || socials.length" class="section subscribe">
			<div class="sub-card">
				<div class="sub-copy">
					<h3>{{ t('home.subTitle') }}</h3>
					<p>{{ t('home.subDesc') }}</p>
				</div>
				<div class="sub-actions">
					<NuxtLink v-if="planet.enabled" :to="localePath('/planet')" class="chip">
						{{ planet.name || t('planetPage.title') }}
					</NuxtLink>
					<NuxtLink v-for="item in socials.slice(0, 3)" :key="item.id" :to="localePath('/follow')" class="chip">
						<SocialIcon :platform="item.platform"/>
						<span>{{ item.accountName }}</span>
					</NuxtLink>
					<NuxtLink v-if="socials.length" :to="localePath('/follow')" class="chip">
						{{ t('home.subMail') }}
					</NuxtLink>
				</div>
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
const {data: planetRes} = await useAsyncData('home-planet', () => call(`/planet?lang=${locale.value}`), {watch: [locale]})
const {data: socialRes} = await useAsyncData('home-socials', () => call('/social'))
const {data: serviceRes} = await useAsyncData('home-services', () => call('/products?type=SERVICE&pageNum=1&pageSize=3'))
// 开源研究：featured 取主推项目，列表只取 total 用于首页注脚
const {data: ossFeaturedRes} = await useAsyncData('home-oss-featured', () => call('/open-source/featured'))
const {data: ossListRes} = await useAsyncData('home-oss-list', () => call('/open-source/projects?pageSize=1'))

/** 首页只放前三篇：列表再长，首屏也留不住人 */
const posts = computed(() => ((blogRes.value as any)?.data?.list || []).slice(0, 3))
const featured = computed(() => posts.value[0] || {})
const rest = computed(() => posts.value.slice(1))
const totalPosts = computed(() => (archiveRes.value as any)?.data?.count || 0)

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

/**
 * 首页注脚只统计「平台有什么」，不放个人履历类数字。
 * 全为 0 时整行不显示，避免出现「0 篇文章」这种自曝其短的写法。
 */
const stats = computed(() => {
	const list: {key: string, value: number, unit: string}[] = []
	if (totalPosts.value) {
		list.push({key: 'posts', value: totalPosts.value, unit: t('home.blogUnit')})
	}
	const ossTotal = (ossListRes.value as any)?.data?.total || 0
	if (ossTotal) {
		list.push({key: 'oss', value: ossTotal, unit: t('home.statOss')})
	}
	const svcCount = services.value.length || serviceCatalog.length
	if (svcCount) {
		list.push({key: 'services', value: svcCount, unit: t('home.statSvc')})
	}
	return list
})

/** 研究卡标签：语言、许可证、tags 会互相重复（Python 既可能是 language 也在 tags 里），去重后再展示 */
const ossTags = computed(() => {
	const p = featuredOss.value
	if (!p) return []
	const list = [p.language, p.license, ...(p.tags || [])].filter(Boolean) as string[]
	return Array.from(new Set(list)).slice(0, 3)
})

/** 主推的开源研究项目；接口没数据时返回 null，由模板降级成「研究方法」卡 */
const featuredOss = computed(() => {
	const d = (ossFeaturedRes.value as any)?.data
	if (!d || !d.project) {
		return null
	}
	return {...d.project, articleCount: d.project.articleCount ?? 0}
})

/**
 * 以下四组内容是「平台固定说明」，不依赖后台数据：
 * 数据为空时用它们撑住区块，而不是把整块 v-if 掉。
 * 文案只存 i18n key，模板里再 t()，切语言才会跟着变。
 */
const domains = [
	{key: 'java', ico: 'J', title: 'home.domainJava', desc: 'home.domainJavaDesc',
		points: ['home.dJava1', 'home.dJava2', 'home.dJava3']},
	{key: 'ai', ico: 'AI', title: 'home.domainAi', desc: 'home.domainAiDesc',
		points: ['home.dAi1', 'home.dAi2', 'home.dAi3']},
	{key: 'eng', ico: '工', title: 'home.domainEng', desc: 'home.domainEngDesc',
		points: ['home.dEng1', 'home.dEng2', 'home.dEng3']},
	{key: 'ent', ico: '数', title: 'home.domainEnt', desc: 'home.domainEntDesc',
		points: ['home.dEnt1', 'home.dEnt2', 'home.dEnt3']}
]

const serviceCatalog = [
	{title: 'home.svcJava', desc: 'home.svcJavaDesc', scope: 'home.svcJavaScope'},
	{title: 'home.svcAi', desc: 'home.svcAiDesc', scope: 'home.svcAiScope'},
	{title: 'home.svcKb', desc: 'home.svcKbDesc', scope: 'home.svcKbScope'},
	{title: 'home.svcOss', desc: 'home.svcOssDesc', scope: 'home.svcOssScope'},
	{title: 'home.svcOps', desc: 'home.svcOpsDesc', scope: 'home.svcOpsScope'}
]

const plannedProducts = [
	{title: 'home.planProd1', desc: 'home.planProd1Desc'},
	{title: 'home.planProd2', desc: 'home.planProd2Desc'},
	{title: 'home.planProd3', desc: 'home.planProd3Desc'}
]

const writingPlan = [
	{title: 'home.plan1', desc: 'home.plan1Desc', status: 'home.writing'},
	{title: 'home.plan2', desc: 'home.plan2Desc', status: 'home.writing'}
]

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
/** 研究结论标签：后台存的是 deploy / watch / skip 这类枚举 */
function verdictLabel(verdict?: string) {
	const map: Record<string, string> = {
		deploy: t('home.verdictDeploy'),
		watch: t('home.verdictWatch'),
		skip: t('home.verdictSkip')
	}
	return map[verdict || ''] || verdict || '—'
}

/** 项目封面水印用的缩写：Crawl4AI → CR，multi-word 取首字母 */
function initialsOf(name?: string) {
	if (!name) return 'OS'
	const parts = name.split(/[\s_-]+/).filter(Boolean)
	if (parts.length >= 2) {
		return (parts[0][0] + parts[1][0]).toUpperCase()
	}
	return name.slice(0, 2).toUpperCase()
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
/* ============================================================
   v2 新增区块
   布局原则：容器仍是 --wrap（1200），内容型页面不靠撑满取胜；
   窄屏靠断点降列，而不是把整块藏起来。
   ============================================================ */

/* Hero 右侧：开源研究卡（替代原来的插图） */
.research-card {
	display: block;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
	transition: border-color .16s, transform .16s;
}

.research-card:hover {
	border-color: var(--line-2);
	transform: translateY(-2px);
}

.rc-top {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 12px;
	padding: 16px 20px;
	background: var(--teal-050);
	border-bottom: 1px solid var(--line);
}

.rc-top > div {
	min-width: 0;
}

.rc-name {
	font-size: 16px;
	font-weight: 700;
	color: var(--ink);
	letter-spacing: .2px;
}

.rc-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin-top: 6px;
}

.rc-tag {
	font-size: 10.5px;
	color: var(--teal-700);
	background: #fff;
	border: 1px solid var(--line);
	padding: 1px 7px;
	border-radius: 4px;
	white-space: nowrap;
}

.rc-verdict {
	font-size: 11px;
	color: #0b7a4b;
	background: #e8f5ee;
	border: 1px solid rgba(11, 122, 75, .18);
	padding: 3px 9px;
	border-radius: var(--radius-xs);
	white-space: nowrap;
	flex: none;
}

.rc-body {
	padding: 18px 20px 20px;
}

.rc-quote {
	margin: 0;
	padding-left: 12px;
	border-left: 2px solid var(--line-2);
	font-size: 13.5px;
	line-height: 1.8;
	color: var(--ink-2);
}

.rc-foot {
	display: flex;
	flex-wrap: wrap;
	gap: 24px;
	margin-top: 16px;
	padding-top: 14px;
	border-top: 1px dashed var(--line);
}

.rc-foot > div {
	font-size: 11.5px;
	color: var(--faint);
	line-height: 1.5;
}

.rc-foot b {
	display: block;
	font-size: 15px;
	font-weight: 600;
	color: var(--ink);
	margin-bottom: 1px;
}

.rc-steps {
	margin: 0;
	padding-left: 18px;
	font-size: 13px;
	line-height: 2;
	color: var(--ink-2);
}

/* 技术领域 */
.domain-grid {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 14px;
}

.domain {
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	padding: 22px 20px 20px;
}

.domain-ico {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 36px;
	height: 36px;
	border-radius: 9px;
	background: var(--teal-100);
	color: var(--teal);
	font-size: 15px;
	font-weight: 700;
	margin-bottom: 13px;
}

.domain h3 {
	margin: 0 0 8px;
	font-size: 15.5px;
	font-weight: 600;
}

.domain p {
	margin: 0;
	font-size: 12.5px;
	line-height: 1.7;
	color: var(--muted);
}

.domain ul {
	margin: 12px 0 0;
	padding: 0;
	list-style: none;
}

.domain li {
	position: relative;
	padding: 3px 0 3px 13px;
	font-size: 12px;
	color: var(--faint);
}

.domain li::before {
	content: "";
	position: absolute;
	left: 0;
	top: 9px;
	width: 4px;
	height: 4px;
	border-radius: 50%;
	background: var(--line-2);
}

/* 精选内容：左主推 + 右侧栏 */
.content-grid {
	display: grid;
	grid-template-columns: 1.3fr .7fr;
	gap: 20px;
	align-items: stretch;
}

.content-side {
	display: flex;
	flex-direction: column;
	gap: 12px;
	min-width: 0;
}

.content-side > * {
	flex: 1;
	min-width: 0;
}

.side-post {
	display: block;
	overflow: hidden;
}

/* 写作计划：文章不够时补位，虚线框表示「还没发」 */
.writing-panel {
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	padding: 22px 24px;
}

.writing-panel.compact {
	height: 100%;
	display: flex;
	flex-direction: column;
}

.writing-panel h4 {
	margin: 0 0 4px;
	font-size: 14px;
	font-weight: 600;
}

.wp-sub {
	margin: 0 0 14px;
	font-size: 12px;
	color: var(--faint);
}

.writing-panel ul {
	margin: 0;
	padding: 0;
	list-style: none;
	display: flex;
	flex-direction: column;
	gap: 10px;
}

.writing-panel li {
	border: 1px dashed var(--line);
	border-radius: var(--radius-sm);
	padding: 12px 14px;
}

.wp-title {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 13.5px;
	font-weight: 500;
	color: var(--ink-2);
	line-height: 1.5;
}

.wp-badge {
	flex: none;
	font-size: 10.5px;
	color: var(--warn-text);
	background: var(--warn-bg);
	border-radius: 4px;
	padding: 1px 6px;
}

.wp-desc {
	display: block;
	margin-top: 4px;
	font-size: 11.5px;
	color: var(--faint);
}

/* 开源项目与技术研究 */
.oss-grid {
	display: grid;
	grid-template-columns: 1.24fr .76fr;
	gap: 20px;
	align-items: stretch;
}

.oss-main {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
	transition: border-color .16s, transform .16s;
}

.oss-main:hover {
	border-color: var(--line-2);
	transform: translateY(-2px);
}

.oss-cover {
	position: relative;
	display: flex;
	align-items: flex-end;
	height: 150px;
	padding: 0 24px 16px;
	background: linear-gradient(135deg, var(--teal-100) 0%, var(--teal-050) 60%, #fbfdfc 100%);
	border-bottom: 1px solid var(--line);
}

.oss-initials {
	position: absolute;
	right: 24px;
	top: 14px;
	font-size: 56px;
	font-weight: 800;
	line-height: 1;
	letter-spacing: -2px;
	color: var(--teal);
	opacity: .1;
}

.oss-cover h3 {
	margin: 0;
	font-size: 21px;
	font-weight: 700;
	letter-spacing: -.2px;
}

.oss-langs {
	display: flex;
	gap: 6px;
	margin-left: auto;
}

.oss-body {
	padding: 18px 24px 20px;
	flex: 1;
}

.oss-body p {
	margin: 0 0 16px;
	font-size: 13.5px;
	line-height: 1.85;
	color: var(--muted);
}

.oss-points {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 10px;
}

.oss-point {
	background: var(--teal-050);
	border-radius: var(--radius-sm);
	padding: 10px 12px;
}

.oss-point .k {
	display: block;
	font-size: 10.5px;
	color: var(--faint);
	margin-bottom: 4px;
}

.oss-point .v {
	display: block;
	font-size: 12.5px;
	font-weight: 500;
	color: var(--ink-2);
	line-height: 1.5;
}

.method {
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	padding: 22px 24px;
}

.method.full {
	grid-column: 1 / -1;
}

.method h4 {
	margin: 0 0 4px;
	font-size: 14px;
	font-weight: 600;
}

.m-sub {
	margin: 0 0 14px;
	font-size: 12px;
	color: var(--faint);
}

.method ol {
	margin: 0;
	padding: 0;
	list-style: none;
	counter-reset: step;
}

.method li {
	counter-increment: step;
	position: relative;
	padding: 7px 0 7px 30px;
	font-size: 12.5px;
	color: var(--ink-2);
	border-bottom: 1px dashed var(--line);
}

.method li:last-child {
	border-bottom: none;
}

.method li::before {
	content: counter(step);
	position: absolute;
	left: 0;
	top: 8px;
	display: flex;
	align-items: center;
	justify-content: center;
	width: 18px;
	height: 18px;
	border-radius: 5px;
	background: var(--teal-100);
	color: var(--teal-700);
	font-size: 10.5px;
	font-weight: 600;
}

/* 知识产品：筹备中占位卡 */
.prod-planned {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
}

.pp-cover {
	position: relative;
	display: flex;
	align-items: center;
	justify-content: center;
	height: 130px;
	background: linear-gradient(135deg, var(--teal-100), var(--teal-050));
	border-bottom: 1px solid var(--line);
}

.pp-no {
	position: absolute;
	left: 16px;
	top: 8px;
	font-size: 32px;
	font-weight: 800;
	line-height: 1;
	letter-spacing: -1px;
	color: var(--teal);
	opacity: .12;
}

.pp-soon {
	font-size: 12px;
	letter-spacing: 1.5px;
	color: var(--teal-700);
	opacity: .6;
}

.pp-body {
	display: flex;
	flex-direction: column;
	flex: 1;
	padding: 16px 18px 18px;
}

.pp-body h3 {
	margin: 0 0 8px;
	font-size: 15px;
	font-weight: 600;
}

.pp-body p {
	margin: 0;
	flex: 1;
	font-size: 12.5px;
	line-height: 1.7;
	color: var(--muted);
}

.pp-foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-top: 14px;
}

.pp-price {
	font-size: 13px;
	color: var(--faint);
}

.pp-btn {
	font-size: 12.5px;
	color: var(--teal);
	border: 1px solid var(--line-2);
	padding: 5px 12px;
	border-radius: var(--radius-xs);
}

/* 技术服务：通栏浅底，内容回到 1200 */
.svc-block {
	margin: 48px calc(50% - 50vw) 0;
	padding: 44px 0 46px;
	background: var(--teal-050);
	border-top: 1px solid var(--line);
	border-bottom: 1px solid var(--line);
}

.svc-block > .sec-head,
.svc-block > .svc-grid {
	max-width: var(--wrap);
	margin-left: auto;
	margin-right: auto;
	padding-left: 24px;
	padding-right: 24px;
}

.svc-card {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	padding: 20px 20px 18px;
}

.svc-no {
	font-size: 11.5px;
	font-weight: 700;
	letter-spacing: .5px;
	color: var(--teal);
}

.svc-card h3 {
	margin: 8px 0;
	font-size: 15px;
	font-weight: 600;
}

.svc-card p {
	margin: 0;
	font-size: 12.5px;
	line-height: 1.7;
	color: var(--muted);
}

.svc-scope {
	margin-top: 12px;
	padding-top: 11px;
	border-top: 1px dashed var(--line);
	font-size: 11.5px;
	color: var(--faint);
}

.svc-cta {
	justify-content: space-between;
	border-color: var(--line-2);
}

.svc-cta h3 {
	margin-top: 0;
}

.svc-cta .btn {
	margin-top: 14px;
	justify-content: center;
}

/* 订阅与社区 */
.subscribe {
	padding-bottom: 64px;
}

.sub-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 32px;
	padding: 30px 34px;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
}

.sub-copy h3 {
	margin: 0 0 8px;
	font-size: 19px;
	font-weight: 600;
}

.sub-copy p {
	margin: 0;
	font-size: 13px;
	color: var(--muted);
}

.sub-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
	flex: none;
}

.chip {
	display: inline-flex;
	align-items: center;
	gap: 7px;
	height: 40px;
	padding: 0 18px;
	border-radius: var(--radius-sm);
	border: 1px solid var(--line-2);
	background: #fff;
	font-size: 13.5px;
	color: var(--ink-2);
}

.chip:hover {
	border-color: var(--teal);
	color: var(--teal);
}

/* ============================================================
   v2 响应式：新增区块的降列规则
   ============================================================ */
@media (max-width: 1100px) {
	.research-card {
		max-width: 520px;
	}

	.domain-grid {
		grid-template-columns: repeat(2, 1fr);
	}

	.content-grid {
		grid-template-columns: 1fr;
	}

	.oss-grid {
		grid-template-columns: 1fr;
	}

	.method.full {
		grid-column: auto;
	}

	.sub-card {
		flex-direction: column;
		align-items: flex-start;
	}
}

@media (max-width: 900px) {
	.grid3 {
		grid-template-columns: repeat(2, 1fr);
	}
}

@media (max-width: 640px) {
	.domain-grid {
		grid-template-columns: 1fr;
	}

	.grid3 {
		grid-template-columns: 1fr;
	}

	.oss-cover {
		height: 120px;
		padding: 0 18px 14px;
	}

	.oss-initials {
		right: 18px;
		font-size: 44px;
	}

	.oss-points {
		grid-template-columns: 1fr;
	}

	.rc-foot {
		gap: 18px;
	}

	.sub-actions {
		width: 100%;
	}

	.chip {
		flex: 1 1 auto;
		justify-content: center;
	}

	.writing-panel {
		padding: 18px;
	}
}
</style>
