<template>
	<div ref="wrap" class="lc-wrap">
		<svg :viewBox="`0 0 ${W} ${H}`" :width="W" :height="H" role="img">
			<title>{{ title }}</title>

			<!-- 横向网格与 y 轴刻度 -->
			<g v-for="(t, i) in ticks" :key="'t' + i">
				<line class="gridline" :x1="padL" :y1="t.y" :x2="W - padR" :y2="t.y"/>
				<text class="axis" :x="padL - 7" :y="t.y + 3.5" text-anchor="end">{{ t.label }}</text>
			</g>

			<!-- 序列：先画面积再画线，避免线被面积盖住 -->
			<template v-for="(s, si) in drawn" :key="'s' + si">
				<path v-if="s.area" :d="s.areaPath" :fill="s.color" opacity=".07"/>
				<polyline :points="s.points" fill="none" :stroke="s.color" :stroke-width="s.width"
				          stroke-linejoin="round" stroke-linecap="round"
				          :stroke-dasharray="s.dashed ? '3 3' : undefined"/>
				<circle v-if="s.dot" :cx="s.lastX" :cy="s.lastY" r="3" fill="#fff"
				        :stroke="s.color" stroke-width="2"/>
			</template>

			<!-- x 轴：只标首、中、尾，避免 30 个日期挤成一团 -->
			<text v-for="(b, i) in xLabels" :key="'x' + i" class="axis"
			      :x="b.x" :y="H - 7" :text-anchor="b.anchor">{{ b.text }}</text>
		</svg>
	</div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

/**
 * 轻量折线图。
 *
 * <p>为什么不引 ECharts：整个后台只有这个页面画图，为它拉一个 1MB 的库不划算。
 *
 * <p>为什么宽度要实测而不是用 viewBox 缩放：viewBox 等比缩放会让宽屏下的图被横向拉伸，
 * 坐标文字跟着变形。这里按容器实际像素重绘，1:1 输出，任何屏幕宽度都清晰。
 */
const props = defineProps({
	// [{label, data: number[], color, area?, dashed?}]
	series: {type: Array, default: () => []},
	buckets: {type: Array, default: () => []},
	title: {type: String, default: '趋势图'},
	height: {type: Number, default: 200},
	unit: {type: String, default: 'count'} // count | money
})

const padL = 46
const padR = 10
const padT = 12
const padB = 24

const wrap = ref(null)
const W = ref(620)
const H = computed(() => props.height)

let observer = null

onMounted(() => {
	const el = wrap.value
	if (!el) return
	const sync = () => {
		const w = Math.round(el.clientWidth)
		// 变化小于 2px 不重绘，避免拖拽窗口时疯狂触发
		if (w > 0 && Math.abs(w - W.value) > 1) W.value = w
	}
	sync()
	observer = new ResizeObserver(sync)
	observer.observe(el)
})

onBeforeUnmount(() => {
	if (observer) observer.disconnect()
})

/** 取一个好看的整数上限（1/2/5 × 10^n），避免刻度出现 1.37k 这种数 */
const max = computed(() => {
	const all = props.series.flatMap(s => s.data || [])
	const peak = Math.max(...all, 0)
	if (peak <= 0) return 1
	const exp = Math.floor(Math.log10(peak))
	const base = Math.pow(10, exp)
	const n = peak / base
	const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10
	let result = step * base
	// 刻度取两段，若中点不是整数，抬到下一个偶数，避免出现 2.5→3 的错位标签
	if (result < 10 && result % 2 !== 0) {
		result += 1
	}
	return result
})

const ticks = computed(() => {
	const result = []
	for (let i = 0; i <= 2; i++) {
		const value = max.value * i / 2
		result.push({
			y: H.value - padB - (H.value - padT - padB) * (value / max.value),
			label: formatTick(value)
		})
	}
	return result.reverse()
})

function formatTick(v) {
	if (v === 0) return '0'
	// 只有上千才用 k，否则 200 会显示成「0.2k」，读起来别扭
	if (v >= 1000) {
		const k = v / 1000
		return (k >= 10 ? Math.round(k) : k.toFixed(1).replace(/\.0$/, '')) + 'k'
	}
	return String(Math.round(v))
}

function xAt(i, n) {
	if (n <= 1) return padL
	return padL + (W.value - padL - padR) * (i / (n - 1))
}

function yAt(v) {
	return H.value - padB - (H.value - padT - padB) * (v / max.value)
}

const drawn = computed(() =>
	props.series.map((s, idx) => {
		const data = s.data || []
		const n = data.length
		const points = data.map((v, i) => `${xAt(i, n).toFixed(1)},${yAt(v).toFixed(1)}`).join(' ')
		const areaPath = n === 0 ? '' :
			`M${xAt(0, n).toFixed(1)},${(H.value - padB).toFixed(1)} L` +
			points.split(' ').join(' L') +
			` L${xAt(n - 1, n).toFixed(1)},${(H.value - padB).toFixed(1)} Z`
		return {
			...s,
			points,
			areaPath,
			width: s.width || 2,
			lastX: n ? xAt(n - 1, n) : 0,
			lastY: n ? yAt(data[n - 1]) : 0,
			// 只给第一条线画端点，多条线各画一个点会显得乱
			dot: s.dot !== false && idx === 0
		}
	})
)

const xLabels = computed(() => {
	const n = props.buckets.length
	if (n === 0) return []
	const pick = n === 1 ? [0] : [0, Math.floor((n - 1) / 2), n - 1]
	return pick.map((i, idx) => ({
		x: xAt(i, n),
		text: shortBucket(props.buckets[i]),
		anchor: idx === 0 ? 'start' : (idx === pick.length - 1 ? 'end' : 'middle')
	}))
})

/** 桶 key 是 "yyyy-MM-dd HH:mm:ss"，日粒度只显示月-日，小时粒度带上时间 */
function shortBucket(b) {
	if (!b) return ''
	const time = b.slice(11, 16)
	if (time && time !== '00:00') {
		return b.slice(5, 10) + ' ' + time
	}
	return b.slice(5, 10)
}
</script>

<style scoped>
.lc-wrap {
	width: 100%;
	overflow: hidden;
}

.lc-wrap svg {
	display: block;
}

.gridline {
	stroke: #f0f2f5;
	stroke-width: 1;
}

.axis {
	font-size: 10px;
	fill: #b0b3b8;
}
</style>
