<template>
	<div class="dash">

		<!-- 页头 -->
		<div class="page-header">
			<div>
				<h2 class="page-title">{{ t('管理驾驶舱') }}</h2>
				<p class="page-desc">{{ t('看经营 · 看内容 · 看用户 · 看商品 · 看服务 · 看待办 · 看系统') }}</p>
			</div>
			<div class="filters">
				<div class="seg">
					<span v-for="p in presets" :key="p.key"
					      :class="{on: activePreset === p.key}" @click="pickPreset(p.key)">{{ t(p.label) }}</span>
				</div>
				<el-date-picker v-if="activePreset === 'custom'" v-model="customRange"
				                type="daterange" value-format="YYYY-MM-DD"
				                :start-placeholder="t('开始')" :end-placeholder="t('结束')"
				                size="default" style="width: 240px" @change="load"/>
				<el-button :loading="loading" @click="load">{{ t('刷新') }}</el-button>
				<span class="stamp">{{ t('更新于') }} {{ updatedAt }}</span>
			</div>
		</div>

		<!-- 01 经营总览 -->
		<section class="sec first">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">01</span>{{ t('经营总览') }}</div>
				<span v-if="overview" class="stamp">{{ rangeLabel }}</span>
			</div>

			<div v-if="errors.overview" class="sec-error">{{ errors.overview }}</div>
			<div v-else class="cards" :class="{dim: loading && !overview}">
				<div v-for="c in overviewCards" :key="c.key" class="card" :class="c.tone">
					<div class="c-label">
						{{ c.label }}
						<span v-if="c.delta !== null" class="delta" :class="c.delta >= 0 ? 'up' : 'down'">
							{{ c.delta >= 0 ? '+' : '' }}{{ (c.delta * 100).toFixed(1) }}%
						</span>
					</div>
					<div class="c-val num"><small v-if="c.prefix">{{ c.prefix }}</small>{{ c.value }}</div>
					<div class="c-note">{{ c.note }}</div>
				</div>
			</div>
		</section>

		<!-- 02 经营趋势 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">02</span>{{ t('经营趋势') }}</div>
				<span class="stamp">{{ trendGranularityText }} · {{ t('缺失时间桶补零') }}</span>
			</div>
			<div v-if="errors.trends" class="sec-error">{{ errors.trends }}</div>
			<div v-else class="grid2">
				<div class="panel">
					<div class="panel-head">
						<h4>{{ t('成交 / 退款 / 净流入') }}</h4>
						<div class="legend">
							<span><i style="background:#00875A"></i>{{ t('成交金额') }}</span>
							<span><i style="background:#d97706"></i>{{ t('退款金额') }}</span>
							<span><i style="background:#2563eb"></i>{{ t('净资金流入') }}</span>
						</div>
					</div>
					<LineChart :series="moneySeries" :buckets="trendBuckets" :height="200"
					           :title="t('成交、退款与净流入趋势')" unit="money"/>
					<div class="footnote">{{ t('成交按支付确认时间、退款按退款完成时间各自归属；跨期退款减少退款发生期的净流入，不回溯原成交期。') }}</div>
				</div>
				<div class="panel">
					<div class="panel-head">
						<h4>{{ t('新增用户 / 新增订单') }}</h4>
						<div class="legend">
							<span><i style="background:#00875A"></i>{{ t('新增用户') }}</span>
							<span><i style="background:#a8b1bd"></i>{{ t('新增订单') }}</span>
						</div>
					</div>
					<LineChart :series="userSeries" :buckets="trendBuckets" :height="200"
					           :title="t('用户与订单增长趋势')"/>
					<div class="footnote">{{ t('用户按注册时间、订单按下单时间统计，与金额口径不同轴，请勿叠加比较。') }}</div>
				</div>
			</div>
		</section>

		<!-- 03 待办 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title">
					<span class="sec-no">03</span>{{ t('待办事项') }}
					<span v-if="todos" class="sub">· {{ t('共') }} <b class="num">{{ todos.total }}</b> {{ t('项') }}</span>
				</div>
			</div>
			<div v-if="errors.todos" class="sec-error">{{ errors.todos }}</div>
			<div v-else class="todos">
				<div v-for="item in todoItems" :key="item.key" class="todo" @click="go(item.path)">
					<div class="todo-l">
						<span class="todo-name">{{ t(item.label) }}</span>
						<span class="todo-meta">
							<template v-if="item.earliest">{{ t('最早') }} {{ dateTime(item.earliest) }} · </template>{{ item.hint }}
						</span>
					</div>
					<span class="badge" :class="badgeClass(item)">{{ item.count }}</span>
				</div>
			</div>
		</section>

		<!-- 04 内容运营 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">04</span>{{ t('内容运营') }}</div>
				<router-link to="/blog/list" class="link">{{ t('内容管理') }} →</router-link>
			</div>
			<div v-if="errors.content" class="sec-error">{{ errors.content }}</div>
			<div v-else class="grid2">
				<div class="panel">
					<div class="kpi-row">
						<div class="kpi"><div class="k-l">{{ t('内容总数') }}</div><div class="k-v num">{{ num(content?.blogTotal) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('已发布') }}</div><div class="k-v num">{{ num(content?.blogPublished) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('期间新增') }}</div><div class="k-v num">{{ num(content?.blogCreated) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('累计阅读') }}</div><div class="k-v num">{{ num(content?.totalViews) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('期间 PV') }}</div><div class="k-v num">{{ num(content?.pageViews) }}</div></div>
						<div class="kpi">
							<div class="k-l">{{ t('独立访客') }}</div>
							<div class="k-v unknown">{{ dash(content?.uniqueVisitors) }}</div>
						</div>
					</div>

					<div class="panel-head sm"><h4>{{ t('热门文章 TOP') }} 5</h4></div>
					<table v-if="content?.topArticles?.length">
						<thead><tr><th>{{ t('标题') }}</th><th class="r">{{ t('访问') }}</th></tr></thead>
						<tbody>
							<tr v-for="(a, i) in content.topArticles" :key="a.title">
								<td class="title"><span class="rank" :class="{top: i < 2}">{{ i + 1 }}</span>{{ a.title }}</td>
								<td class="r num">{{ num(a.pv) }}</td>
							</tr>
						</tbody>
					</table>
					<div v-else class="empty">{{ t('这个区间还没有文章访问记录') }}</div>

					<div v-if="content?.uniqueVisitorsReason" class="footnote warn-note">
						{{ t('独立访客显示「—」') }}：{{ content.uniqueVisitorsReason }}
					</div>
				</div>

				<div class="panel">
					<div class="panel-head sm"><h4>{{ t('站内搜索词 TOP 8') }}</h4></div>
					<div v-if="content?.topKeywords?.length" class="tags">
						<span v-for="(k, i) in content.topKeywords" :key="k.keyword"
						      class="tag" :class="i < 3 ? 'g' : 'n'">{{ k.keyword }} {{ k.pv }}</span>
					</div>
					<div v-else class="empty sm">{{ t('这个区间还没有搜索记录') }}</div>

					<div class="panel-head sm mt"><h4>{{ t('内容发布趋势') }}</h4></div>
					<BarChart :buckets="content?.publishTrend?.buckets || []"
					          :data="content?.publishTrend?.counts || []" :height="104"
					          :title="t('内容发布趋势')"/>
					<div class="footnote">{{ t('搜索词取自访问日志的 SEARCH 行为，按关键词字符串聚合。') }}</div>
				</div>
			</div>
		</section>

		<!-- 05 用户运营 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">05</span>{{ t('用户运营') }}</div>
				<router-link to="/user/list" class="link">{{ t('用户管理') }} →</router-link>
			</div>
			<div v-if="errors.users" class="sec-error">{{ errors.users }}</div>
			<div v-else class="grid2b">
				<div class="panel">
					<div class="kpi-row">
						<div class="kpi"><div class="k-l">{{ t('注册用户') }}</div><div class="k-v num">{{ num(users?.userTotal) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('期间新增') }}</div><div class="k-v num">{{ num(users?.newUsers) }}</div></div>
						<div class="kpi"><div class="k-l">{{ t('付费用户') }}</div><div class="k-v num">{{ num(users?.paidUsers) }}</div></div>
						<div class="kpi">
							<div class="k-l">{{ t('付费转化') }}</div>
							<div class="k-v num">{{ pct(users?.payRate, 1) }}</div>
						</div>
						<div class="kpi">
							<div class="k-l">{{ t('活跃用户') }}</div>
							<div class="k-v unknown">{{ dash(users?.activeUsers) }}</div>
						</div>
					</div>
					<div v-if="users?.activeUsersReason" class="footnote warn-note mt0">
						{{ t('活跃用户显示「—」') }}：{{ users.activeUsersReason }}
					</div>

					<div class="panel-head sm"><h4>{{ t('最近注册用户') }}</h4></div>
					<table v-if="users?.latestUsers?.length">
						<thead><tr><th>{{ t('用户') }}</th><th>{{ t('注册') }}</th><th class="r">{{ t('最后登录') }}</th></tr></thead>
						<tbody>
							<tr v-for="u in users.latestUsers" :key="u.username">
								<td class="title">{{ u.nickname || u.username }}</td>
								<td class="muted">{{ dateOnly(u.createTime) }}</td>
								<td class="r muted">{{ u.lastLoginTime ? dateTime(u.lastLoginTime) : '—' }}</td>
							</tr>
						</tbody>
					</table>
					<div v-else class="empty">{{ t('还没有注册用户') }}</div>
				</div>

				<div class="panel">
					<div class="panel-head sm"><h4>{{ t('用户增长趋势') }}</h4></div>
					<LineChart :series="[{label: t('新增用户'), data: trendNewUsers, color: '#00875A', area: true}]"
					           :buckets="trendBuckets" :height="132" :title="t('用户增长趋势')"/>

					<div class="panel-head sm mt"><h4>{{ t('付费用户 TOP 5') }}</h4></div>
					<table v-if="users?.topPayingUsers?.length">
						<thead><tr><th>{{ t('用户') }}</th><th class="r">{{ t('订单') }}</th><th class="r">{{ t('累计支付') }}</th></tr></thead>
						<tbody>
							<tr v-for="u in users.topPayingUsers" :key="u.username">
								<td class="title">{{ u.nickname || u.username }}</td>
								<td class="r num">{{ num(u.orderCount) }}</td>
								<td class="r num">{{ money(u.paidAmount, false) }}</td>
							</tr>
						</tbody>
					</table>
					<div v-else class="empty sm">{{ t('这个区间还没有成交用户') }}</div>
				</div>
			</div>
		</section>

		<!-- 06 商品与服务 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">06</span>{{ t('商品与服务') }}</div>
				<router-link to="/product/list" class="link">{{ t('商品管理') }} →</router-link>
			</div>
			<div class="grid3">
				<div class="panel">
					<div v-if="errors.products" class="sec-error in-panel">{{ errors.products }}</div>
					<template v-else>
						<div class="panel-head sm"><h4>{{ t('商品销售 TOP 5') }}</h4><span class="stamp">{{ t('按销售额') }}</span></div>
						<table v-if="products?.topProducts?.length">
							<thead><tr><th>{{ t('商品') }}</th><th class="r">{{ t('销量') }}</th><th class="r">{{ t('金额') }}</th></tr></thead>
							<tbody>
								<tr v-for="(p, i) in products.topProducts" :key="p.productId + '-' + p.title">
									<td class="title"><span class="rank" :class="{top: i < 2}">{{ i + 1 }}</span>{{ p.title }}</td>
									<td class="r num">{{ num(p.quantity) }}</td>
									<td class="r num">{{ money(p.amount, false) }}</td>
								</tr>
							</tbody>
						</table>
						<div v-else class="empty">{{ t('这个区间还没有商品成交') }}</div>
						<div class="mini-stats">
							<span>{{ t('在售') }} <b class="num">{{ num(products?.onSaleCount) }}</b> / {{ num(products?.productTotal) }}</span>
							<span>{{ t('累计转化') }} <b class="num">{{ pct(products?.convertRate, 2) }}</b></span>
						</div>
						<div class="footnote">{{ products?.convertRateNote || t('金额取自订单明细快照，不按商品当前价推算。') }}</div>
					</template>
				</div>

				<div class="panel">
					<div v-if="errors.services" class="sec-error in-panel">{{ errors.services }}</div>
					<template v-else>
						<div class="panel-head sm"><h4>{{ t('服务需求漏斗') }}</h4><span class="stamp">{{ t('当前存量') }}</span></div>
						<div class="funnel">
							<div v-for="f in services?.funnel || []" :key="f.name" class="fn-row">
								<span class="fn-name">{{ t(f.name) }}</span>
								<span class="fn-bar"><span class="fn-fill" :style="{width: funnelWidth(f.count)}"></span></span>
								<span class="fn-val num">{{ f.count }}</span>
							</div>
						</div>
						<div class="footnote">{{ t('各节点按需求当前状态归类，是存量而非同期转化；需求、报价、项目各自统计，不相加为客户数。') }}</div>
						<div class="mini-stats col">
							<div><span>{{ t('进行中项目') }}</span><b class="num">{{ num(services?.projectInProgress) }}</b></div>
							<div><span>{{ t('待验收') }}</span><b class="num">{{ num(services?.projectPendingAccept) }}</b></div>
							<div><span>{{ t('逾期里程碑') }}</span><b class="num danger">{{ num(services?.milestoneOverdue) }}</b></div>
							<div><span>{{ t('已验收交付额') }}</span><b class="num">{{ money(services?.acceptedAmount, false) }}</b></div>
						</div>
					</template>
				</div>

				<div class="panel">
					<div class="panel-head sm"><h4>{{ t('需要跟进的项目') }}</h4>
						<router-link to="/service/project" class="link">{{ t('全部') }} →</router-link>
					</div>
					<table v-if="services?.activeProjects?.length">
						<thead><tr><th>{{ t('项目') }}</th><th class="r">{{ t('状态') }}</th></tr></thead>
						<tbody>
							<tr v-for="p in services.activeProjects" :key="p.projectNo">
								<td class="title">
									{{ p.name }}
									<div class="sub-line">
										{{ t('里程碑') }} {{ p.acceptedMilestones }}/{{ p.totalMilestones }}
										<span v-if="p.endDate"> · {{ p.endDate }} {{ t('到期') }}</span>
									</div>
								</td>
								<td class="r"><span class="tag" :class="projectTag(p.status)">{{ projectText(p.status) }}</span></td>
							</tr>
						</tbody>
					</table>
					<div v-else class="empty">{{ t('当前没有进行中的项目') }}</div>
					<div class="footnote">{{ t('逾期按计划日期早于今天且未验收判定，未排期的不计入。') }}</div>
				</div>
			</div>
		</section>

		<!-- 07 平台健康 -->
		<section class="sec">
			<div class="sec-head">
				<div class="sec-title"><span class="sec-no">07</span>{{ t('平台健康') }}</div>
				<span class="stamp">{{ t('逐项真实探测，非页面存活推断') }}</span>
			</div>
			<div v-if="errors.health" class="sec-error">{{ errors.health }}</div>
			<div v-else class="health">
				<div v-for="h in healthItems" :key="h.key" class="h-item">
					<div class="h-top"><span class="dot" :class="h.status.toLowerCase()"></span>{{ t(h.label) }}</div>
					<div class="h-state" :class="h.status.toLowerCase()">{{ h.status }}</div>
					<div class="h-detail">{{ h.message }}</div>
				</div>
			</div>
			<div class="footnote">
				{{ t('四态：UP / WARN / DOWN / UNKNOWN。数据不足时显示 UNKNOWN，不显示「正常」——不知道比猜测更诚实。') }}
			</div>
		</section>

	</div>
</template>

<script setup>
import {computed, onMounted, reactive, ref, toRefs} from 'vue'
import {useRouter} from 'vue-router'
import {useT} from '@/i18n/ui'
import LineChart from '@/components/LineChart.vue'
import BarChart from '@/components/BarChart.vue'
import {
	getContent, getHealth, getOverview, getProducts, getServices, getTodos, getTrends, getUsers
} from '@/api/dashboard'

const t = useT()
const router = useRouter()

const presets = [
	{key: 'today', label: '今日'},
	{key: 'yesterday', label: '昨日'},
	{key: 'last7', label: '近 7 天'},
	{key: 'last30', label: '近 30 天'},
	{key: 'thisMonth', label: '本月'},
	{key: 'lastMonth', label: '上月'},
	{key: 'custom', label: '自定义'}
]

const activePreset = ref('last30')
const customRange = ref([])
const loading = ref(false)
const updatedAt = ref('—')

const data = reactive({
	overview: null, trends: null, todos: null, content: null,
	users: null, products: null, services: null, health: null
})
// 拆成 ref 暴露给模板：八个区块各自独立判空，模板里直接写 overview / content 即可
const {overview, trends, todos, content, users, products, services, health} = toRefs(data)
const errors = reactive({})

// ------------------------------------------------------------------
// 区间
// ------------------------------------------------------------------

const pad = n => String(n).padStart(2, '0')
const fmt = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`

function dayStart(offsetDays = 0) {
	const d = new Date()
	d.setHours(0, 0, 0, 0)
	d.setDate(d.getDate() + offsetDays)
	return d
}

/** 区间一律左闭右开：end 取「次日 00:00」，与后端口径一致 */
function rangeOf(key) {
	const now = new Date()
	switch (key) {
		case 'today':
			return {startTime: fmt(dayStart(0)), endTime: fmt(dayStart(1))}
		case 'yesterday':
			return {startTime: fmt(dayStart(-1)), endTime: fmt(dayStart(0))}
		case 'last7':
			return {startTime: fmt(dayStart(-6)), endTime: fmt(dayStart(1))}
		case 'thisMonth':
			return {startTime: fmt(new Date(now.getFullYear(), now.getMonth(), 1)), endTime: fmt(dayStart(1))}
		case 'lastMonth':
			return {
				startTime: fmt(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
				endTime: fmt(new Date(now.getFullYear(), now.getMonth(), 1))
			}
		case 'custom': {
			if (!customRange.value || customRange.value.length !== 2) {
				return {startTime: fmt(dayStart(-29)), endTime: fmt(dayStart(1))}
			}
			const [s, e] = customRange.value
			// 结束日包含当天白天，所以取「结束日 + 1 天 00:00」
			const end = new Date(e + 'T00:00:00')
			end.setDate(end.getDate() + 1)
			return {startTime: s + ' 00:00:00', endTime: fmt(end)}
		}
		default:
			return {startTime: fmt(dayStart(-29)), endTime: fmt(dayStart(1))}
	}
}

const currentRange = computed(() => rangeOf(activePreset.value))

const rangeLabel = computed(() => {
	const r = currentRange.value
	// endTime 是左闭右开的上界（次日 00:00），展示时回退一天，避免看起来多算了一天
	const end = new Date(r.endTime.replace(' ', 'T'))
	end.setDate(end.getDate() - 1)
	return `${r.startTime.slice(5, 10)} 至 ${pad(end.getMonth() + 1)}-${pad(end.getDate())}`
})

function pickPreset(key) {
	activePreset.value = key
	if (key !== 'custom') load()
}

/** 跨度大时自动切粒度，避免日粒度画出 300 个点 */
function granularityOf(range) {
	const hours = (new Date(range.endTime.replace(' ', 'T')) - new Date(range.startTime.replace(' ', 'T'))) / 3600000
	if (hours <= 48) return 'HOUR'
	if (hours <= 24 * 90) return 'DAY'
	if (hours <= 24 * 366) return 'WEEK'
	return 'MONTH'
}

const trendGranularityText = computed(() => {
	const g = data.trends?.granularity || granularityOf(currentRange.value)
	return {HOUR: t('小时粒度'), DAY: t('日粒度'), WEEK: t('周粒度'), MONTH: t('月粒度')}[g] || t('日粒度')
})

// ------------------------------------------------------------------
// 加载：八个请求并发，任一失败只影响自己那个区块
// ------------------------------------------------------------------

async function load() {
	loading.value = true
	const range = currentRange.value
	const params = {startTime: range.startTime, endTime: range.endTime}

	const jobs = [
		['overview', () => getOverview({...params, comparePeriod: true})],
		['trends', () => getTrends({...params, granularity: granularityOf(range)})],
		['todos', () => getTodos()],
		['content', () => getContent({...params, limit: 5})],
		['users', () => getUsers({...params, limit: 5})],
		['products', () => getProducts({...params, limit: 5})],
		['services', () => getServices({...params, limit: 5})],
		['health', () => getHealth()]
	]

	const results = await Promise.allSettled(jobs.map(([, fn]) => fn()))
	jobs.forEach(([key], i) => {
		const r = results[i]
		if (r.status === 'fulfilled') {
			data[key] = r.value.data
			errors[key] = ''
		} else {
			errors[key] = r.reason?.message || t('加载失败')
		}
	})

	updatedAt.value = new Date().toTimeString().slice(0, 5)
	loading.value = false
}

onMounted(load)

// ------------------------------------------------------------------
// 展示层
// ------------------------------------------------------------------

const money = (v, withSymbol = true) => (withSymbol ? '¥' : '') + Number(v || 0).toFixed(2)

// 「算不出来」要显示成破折号，不能用 0 冒充；同时避免在 null 上取属性把渲染搞崩
const dash = v => (v === null || v === undefined ? '—' : v)
const pct = (v, digits = 1) => (v === null || v === undefined ? '—' : (v * 100).toFixed(digits) + '%')
const num = v => (v === null || v === undefined ? '0' : Number(v).toLocaleString('zh-CN'))

const overviewCards = computed(() => {
	const o = data.overview
	if (!o) return []
	const cmp = o.comparison || {}
	return [
		{key: 'sales', label: t('成交金额'), value: money(o.salesAmount, false), prefix: '¥',
			note: t('按支付时间 · 含已退款'), tone: '', delta: cmp.salesAmountDelta ?? null},
		{key: 'net', label: t('实际净收入'), value: money(o.netAmount, false), prefix: '¥',
			note: t('成交 − 退款 · 归属不同'), tone: 't-green', delta: null},
		{key: 'refund', label: t('退款金额'), value: money(o.refundAmount, false), prefix: '¥',
			note: t('按退款时间 · 仅成功'), tone: 't-warn', delta: null},
		{key: 'wait', label: t('待确认收款'), value: num(o.pendingPaymentCount), prefix: '',
			note: t('数支付单，不数订单'), tone: 't-danger', delta: null},
		{key: 'orders', label: t('新增订单'), value: num(o.newOrderCount), prefix: '',
			note: t('按下单时间 · 含未付'), tone: 't-info', delta: cmp.newOrderCountDelta ?? null},
		{key: 'aov', label: t('客单价'), value: dash(o.averageOrderValue === null ? null : money(o.averageOrderValue, false)),
			prefix: o.averageOrderValue === null ? '' : '¥',
			note: t('成交额 ÷ 成交单数'), tone: 't-mute', delta: null}
	]
})

const trendBuckets = computed(() => data.trends?.buckets || [])
const trendNewUsers = computed(() => data.trends?.series?.newUserCount || [])

const moneySeries = computed(() => {
	const s = data.trends?.series
	if (!s) return []
	return [
		{label: t('成交金额'), data: s.salesAmount || [], color: '#00875A', area: true},
		{label: t('退款金额'), data: s.refundAmount || [], color: '#d97706', dashed: true, dot: false},
		{label: t('净资金流入'), data: s.netAmount || [], color: '#2563eb'}
	]
})

const userSeries = computed(() => {
	const s = data.trends?.series
	if (!s) return []
	return [
		{label: t('新增用户'), data: s.newUserCount || [], color: '#00875A', area: true},
		{label: t('新增订单'), data: s.newOrderCount || [], color: '#a8b1bd', dot: false}
	]
})

const todoItems = computed(() => data.todos?.items || [])
const healthItems = computed(() => data.health?.items || [])

function badgeClass(item) {
	if (!item.count) return 'zero'
	return item.level === 'danger' ? '' : item.level === 'warn' ? 'warn' : 'info'
}

function funnelWidth(count) {
	const list = data.services?.funnel || []
	const max = Math.max(...list.map(f => f.count), 1)
	return Math.round((count / max) * 100) + '%'
}

function projectText(status) {
	return {
		NOT_STARTED: t('未开始'), IN_PROGRESS: t('进行中'),
		PENDING_ACCEPT: t('待验收'), COMPLETED: t('已完成'), ARCHIVED: t('已归档')
	}[status] || status
}

function projectTag(status) {
	return {
		NOT_STARTED: 'n', IN_PROGRESS: 'g', PENDING_ACCEPT: 'a', COMPLETED: 'b', ARCHIVED: 'n'
	}[status] || 'n'
}

function dateOnly(value) {
	if (!value) return '—'
	return String(value).slice(5, 10)
}

function dateTime(value) {
	if (!value) return '—'
	return String(value).slice(5, 16).replace('T', ' ')
}

function go(path) {
	router.push(path)
}
</script>

<style scoped>
.dash {
	padding: 18px 20px 48px;
	/* 不设宽度上限：宽屏就撑满，避免右侧留一大块空白。
	   超宽屏靠网格断点控制列数，而不是靠缩窄整页 */
	width: 100%;
}

.num {
	font-variant-numeric: tabular-nums;
}

.filters {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}

.seg {
	display: flex;
	background: #fff;
	border: 1px solid #ebeef5;
	border-radius: 8px;
	padding: 3px;
	gap: 2px;
}

.seg span {
	padding: 4px 10px;
	font-size: 12.5px;
	color: #606266;
	border-radius: 6px;
	cursor: pointer;
	white-space: nowrap;
}

.seg span:hover {
	background: #f5f7fa;
}

.seg span.on {
	background: #F4FAF7;
	color: #00875A;
	font-weight: 500;
}

.stamp {
	font-size: 12px;
	color: #b0b3b8;
}

.sec {
	margin-top: 20px;
}

.sec.first {
	margin-top: 4px;
}

.sec-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 10px;
}

.sec-title {
	display: flex;
	align-items: center;
	gap: 8px;
	font-size: 14px;
	font-weight: 600;
}

.sec-no {
	font-size: 11.5px;
	color: #00875A;
	background: #E7F5EE;
	border-radius: 4px;
	padding: 2px 6px;
	font-weight: 500;
}

.sec-title .sub {
	font-size: 11.5px;
	color: #909399;
	font-weight: 400;
}

.link {
	font-size: 12.5px;
	color: #00875A;
}

.sec-error {
	background: #fef0f0;
	border: 1px solid #fde2e2;
	color: #c45656;
	font-size: 12.5px;
	padding: 10px 14px;
	border-radius: 8px;
}

.sec-error.in-panel {
	margin-bottom: 10px;
}

.cards {
	display: grid;
	grid-template-columns: repeat(6, 1fr);
	gap: 12px;
}

.cards.dim {
	opacity: .55;
}

.card {
	background: #fff;
	border-radius: 8px;
	padding: 14px 16px;
	border-left: 3px solid #00875A;
}

.card.t-green { border-left-color: #16a34a; }
.card.t-warn { border-left-color: #d97706; }
.card.t-danger { border-left-color: #dc2626; }
.card.t-info { border-left-color: #2563eb; }
.card.t-mute { border-left-color: #d4d7de; }

.c-label {
	font-size: 12.5px;
	color: #909399;
	display: flex;
	align-items: center;
	gap: 4px;
}

.delta {
	font-size: 11px;
	font-weight: 500;
}

.delta.up { color: #d03050; }
.delta.down { color: #18a058; }

.c-val {
	font-size: 23px;
	font-weight: 600;
	margin: 7px 0 5px;
	letter-spacing: -.4px;
	color: #303133;
}

.c-val small {
	font-size: 13px;
	font-weight: 500;
	color: #606266;
	margin-right: 2px;
}

.c-note {
	font-size: 11.5px;
	color: #b0b3b8;
	line-height: 1.45;
	min-height: 33px;
}

.grid2 {
	display: grid;
	grid-template-columns: 1.35fr 1fr;
	gap: 12px;
}

.grid2b {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 12px;
}

.grid3 {
	display: grid;
	grid-template-columns: 1fr 1fr 1fr;
	gap: 12px;
}

.panel {
	background: #fff;
	border-radius: 8px;
	padding: 16px 18px;
	min-width: 0;
}

.panel-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 12px;
}

.panel-head.sm {
	margin-bottom: 8px;
}

.panel-head.mt {
	margin-top: 16px;
}

.panel-head h4 {
	margin: 0;
	font-size: 13.5px;
	font-weight: 600;
	color: #303133;
}

.legend {
	display: flex;
	gap: 12px;
	font-size: 11.5px;
	color: #909399;
}

.legend i {
	display: inline-block;
	width: 8px;
	height: 8px;
	border-radius: 2px;
	margin-right: 5px;
	vertical-align: -1px;
}

.todos {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 10px;
}

.todo {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	padding: 11px 13px;
	background: #fff;
	border: 1px solid #ebeef5;
	border-radius: 7px;
	cursor: pointer;
	transition: background .15s, border-color .15s;
}

.todo:hover {
	background: #F4FAF7;
	border-color: rgba(0, 135, 90, .22);
}

.todo-l {
	display: flex;
	flex-direction: column;
	gap: 3px;
	min-width: 0;
}

.todo-name {
	font-size: 13px;
	font-weight: 500;
	color: #303133;
}

.todo-meta {
	font-size: 11px;
	color: #b0b3b8;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.badge {
	min-width: 22px;
	height: 20px;
	padding: 0 6px;
	border-radius: 10px;
	background: #fdeaea;
	color: #dc2626;
	font-size: 11.5px;
	font-weight: 600;
	display: flex;
	align-items: center;
	justify-content: center;
	flex: none;
}

.badge.warn {
	background: #fdf0e0;
	color: #d97706;
}

.badge.info {
	background: #e8f0fd;
	color: #2563eb;
}

.badge.zero {
	background: #f2f3f5;
	color: #b0b3b8;
	font-weight: 500;
}

.kpi-row {
	display: flex;
	gap: 24px;
	margin-bottom: 14px;
	flex-wrap: wrap;
}

.kpi .k-l {
	font-size: 11.5px;
	color: #909399;
	margin-bottom: 4px;
}

.kpi .k-v {
	font-size: 17px;
	font-weight: 600;
	color: #303133;
}

.kpi .unknown {
	color: #9aa5b1;
}

table {
	width: 100%;
	border-collapse: collapse;
}

th {
	font-size: 11.5px;
	color: #909399;
	font-weight: 400;
	text-align: left;
	padding: 0 0 8px;
	border-bottom: 1px solid #ebeef5;
}

td {
	font-size: 12.5px;
	padding: 9px 0;
	border-bottom: 1px solid #f5f6f8;
	color: #606266;
	vertical-align: middle;
}

tr:last-child td {
	border-bottom: none;
}

td.title {
	color: #303133;
}

td.r, th.r {
	text-align: right;
}

td.muted {
	color: #b0b3b8;
}

.sub-line {
	font-size: 11px;
	color: #b0b3b8;
	margin-top: 2px;
}

.rank {
	display: inline-flex;
	width: 17px;
	height: 17px;
	border-radius: 4px;
	background: #f2f3f5;
	color: #909399;
	font-size: 10.5px;
	align-items: center;
	justify-content: center;
	margin-right: 7px;
}

.rank.top {
	background: #E7F5EE;
	color: #00875A;
	font-weight: 600;
}

.tags {
	display: flex;
	flex-wrap: wrap;
	gap: 7px;
	margin-bottom: 6px;
}

.tag {
	display: inline-block;
	padding: 4px 9px;
	border-radius: 4px;
	font-size: 11px;
	line-height: 1.5;
}

.tag.g { background: #e8f5ee; color: #0b7a4b; }
.tag.a { background: #fdf0e0; color: #96600a; }
.tag.r { background: #fdeaea; color: #b02a37; }
.tag.b { background: #e8f0fd; color: #1d4ed8; }
.tag.n { background: #f2f3f5; color: #6b7280; }

.funnel {
	display: flex;
	flex-direction: column;
	gap: 7px;
}

.fn-row {
	display: flex;
	align-items: center;
	gap: 10px;
	font-size: 12px;
}

.fn-name {
	width: 58px;
	color: #909399;
	flex: none;
}

.fn-bar {
	flex: 1;
	height: 20px;
	background: #f2f4f6;
	border-radius: 4px;
	overflow: hidden;
	display: block;
}

.fn-fill {
	display: block;
	height: 20px;
	background: #00875A;
	opacity: .85;
	border-radius: 4px;
	transition: width .3s;
}

.fn-val {
	width: 26px;
	text-align: right;
	color: #606266;
	flex: none;
}

.mini-stats {
	display: flex;
	gap: 16px;
	margin-top: 12px;
	padding-top: 10px;
	border-top: 1px solid #ebeef5;
	font-size: 12px;
	color: #909399;
}

.mini-stats b {
	color: #303133;
	margin-left: 4px;
}

.mini-stats.col {
	flex-direction: column;
	gap: 7px;
}

.mini-stats.col div {
	display: flex;
	justify-content: space-between;
}

.mini-stats .danger {
	color: #dc2626;
}

.health {
	display: grid;
	grid-template-columns: repeat(8, 1fr);
	gap: 9px;
}

.h-item {
	border: 1px solid #ebeef5;
	border-radius: 7px;
	padding: 10px 11px;
	background: #fff;
}

.h-top {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 12px;
	color: #606266;
	margin-bottom: 6px;
}

.dot {
	width: 7px;
	height: 7px;
	border-radius: 50%;
	flex: none;
}

.dot.up { background: #18a058; }
.dot.warn { background: #d97706; }
.dot.down { background: #dc2626; }
.dot.unknown { background: #9aa5b1; }

.h-state {
	font-size: 12.5px;
	font-weight: 600;
}

.h-state.up { color: #18a058; }
.h-state.warn { color: #d97706; }
.h-state.down { color: #dc2626; }
.h-state.unknown { color: #9aa5b1; }

.h-detail {
	font-size: 10.5px;
	color: #b0b3b8;
	margin-top: 3px;
	line-height: 1.45;
}

.footnote {
	margin-top: 8px;
	font-size: 11px;
	color: #b0b3b8;
	line-height: 1.6;
}

.footnote.mt0 {
	margin-top: 0;
}

.warn-note {
	color: #96600a;
}

.empty {
	text-align: center;
	padding: 22px 0;
	font-size: 12.5px;
	color: #b0b3b8;
}

.empty.sm {
	padding: 12px 0;
}

/* 宽屏（2K/4K）下待办从 3 列变 4 列：8 项正好两行，
   否则每项被拉得很长，名称和角标离得老远 */
@media (min-width: 1900px) {
	.todos { grid-template-columns: repeat(4, 1fr); }
}

/* 断点按「可用宽度」推算，不是拍脑袋的数字：
   6 张指标卡每张至少要 170px 才放得下金额和口径说明，
   加上侧栏 216 与页面留白 40，窗口小于 1300 就该降成 3 列 */
@media (max-width: 1300px) {
	.cards { grid-template-columns: repeat(3, 1fr); }
	.health { grid-template-columns: repeat(4, 1fr); }
}

@media (max-width: 1100px) {
	.grid2, .grid2b, .grid3 { grid-template-columns: 1fr; }
	.todos { grid-template-columns: repeat(2, 1fr); }
	.cards { grid-template-columns: repeat(2, 1fr); }
	.health { grid-template-columns: repeat(3, 1fr); }
}
</style>
