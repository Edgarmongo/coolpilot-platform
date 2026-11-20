<template>
	<div ref="wrap" class="bc-wrap">
		<svg :viewBox="`0 0 ${W} ${H}`" :width="W" :height="H" role="img">
			<title>{{ title }}</title>
			<rect v-for="(b, i) in bars" :key="i" :x="b.x" :y="b.y" :width="b.w" :height="b.h"
			      rx="1.5" :fill="color" opacity=".75"/>
			<line class="baseline" :x1="padL" :y1="H - padB" :x2="W - padR" :y2="H - padB"/>
			<text class="axis" :x="padL" :y="H - 5">{{ shortBuckets[0] }}</text>
			<text class="axis" :x="W - padR" :y="H - 5" text-anchor="end">{{ shortBuckets[1] }}</text>
		</svg>
	</div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

/**
 * 轻量柱状图：内容发布量这类稀疏计数用柱比折线直观。
 * 与 LineChart 一样按容器实际宽度重绘，避免宽屏下被横向拉伸。
 */
const props = defineProps({
	buckets: {type: Array, default: () => []},
	data: {type: Array, default: () => []},
	title: {type: String, default: '柱状图'},
	height: {type: Number, default: 96},
	color: {type: String, default: '#00875A'}
})

const padL = 18
const padR = 6
const padT = 6
const padB = 18

const wrap = ref(null)
const W = ref(420)
const H = computed(() => props.height)

let observer = null

onMounted(() => {
	const el = wrap.value
	if (!el) return
	const sync = () => {
		const w = Math.round(el.clientWidth)
		if (w > 0 && Math.abs(w - W.value) > 1) W.value = w
	}
	sync()
	observer = new ResizeObserver(sync)
	observer.observe(el)
})

onBeforeUnmount(() => {
	if (observer) observer.disconnect()
})

const max = computed(() => Math.max(...props.data, 1))

const bars = computed(() => {
	const n = props.data.length
	if (n === 0) return []
	const slot = (W.value - padL - padR) / n
	return props.data.map((v, i) => {
		const h = (H.value - padT - padB) * (v / max.value)
		return {
			x: padL + i * slot + slot * 0.18,
			y: H.value - padB - h,
			w: Math.max(slot * 0.64, 1.5),
			h: Math.max(h, 0)
		}
	})
})

const shortBuckets = computed(() => {
	const b = props.buckets
	if (!b || b.length === 0) return ['', '']
	return [b[0].slice(5, 10), b[b.length - 1].slice(5, 10)]
})
</script>

<style scoped>
.bc-wrap {
	width: 100%;
	overflow: hidden;
}

.bc-wrap svg {
	display: block;
}

.baseline {
	stroke: #f0f2f5;
	stroke-width: 1;
}

.axis {
	font-size: 10px;
	fill: #b0b3b8;
}
</style>
