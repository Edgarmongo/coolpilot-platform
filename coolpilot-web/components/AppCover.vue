<template>
	<!--
		文章 / 产品的封面插画。

		为什么不留灰块或随机图片：站内文章大多没有配图，直接空着会让列表显得没做完；
		随机图又会让人以为图跟内容有关。这里按 seed 生成稳定的几何线稿 ——
		同一篇文章每次渲染的图案一致，换设备也不变，而且明确是「装饰」而不是「配图」。

		seed 建议传文章 id + 标题，这样标题改了图案也会换，不会出现两篇同图。
	-->
	<div class="cp-cover" :style="{height: height + 'px'}">
		<svg viewBox="0 0 400 160" preserveAspectRatio="xMidYMid slice" role="img" aria-hidden="true">
			<rect width="400" height="160" fill="#F2FAF8"/>

			<!-- 0 终端窗口：偏运维 / 部署 -->
			<g v-if="variant === 0">
				<rect x="52" y="30" width="296" height="100" rx="8" fill="#0B3F3A"/>
				<rect x="52" y="30" width="296" height="20" rx="8" fill="#0A3733"/>
				<circle cx="68" cy="40" r="3" fill="#F87171" fill-opacity=".85"/>
				<circle cx="78" cy="40" r="3" fill="#FBBF24" fill-opacity=".85"/>
				<circle cx="88" cy="40" r="3" fill="#34D399" fill-opacity=".85"/>
				<rect x="66" y="62" width="104" height="6" rx="3" fill="#5EEAD4" fill-opacity=".55"/>
				<rect x="66" y="78" width="196" height="6" rx="3" fill="#E2E8F0" fill-opacity=".2"/>
				<rect x="66" y="94" width="140" height="6" rx="3" fill="#E2E8F0" fill-opacity=".14"/>
				<rect x="66" y="110" width="72" height="6" rx="3" fill="#F87171" fill-opacity=".55"/>
				<rect x="148" y="110" width="86" height="6" rx="3" fill="#34D399" fill-opacity=".4"/>
			</g>

			<!-- 1 文档到结果：偏数据 / 采集 -->
			<g v-else-if="variant === 1">
				<rect x="58" y="26" width="128" height="108" rx="8" fill="#fff" stroke="#0F766E" stroke-opacity=".2"/>
				<rect x="72" y="44" width="66" height="6" rx="3" fill="#0F766E" fill-opacity=".26"/>
				<rect x="72" y="60" width="96" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<rect x="72" y="74" width="82" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<rect x="72" y="88" width="90" height="5" rx="2.5" fill="#0F766E" fill-opacity=".1"/>
				<rect x="72" y="102" width="56" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<path d="M196 80h26" stroke="#0F766E" stroke-opacity=".3" stroke-dasharray="4 4"/>
				<path d="M216 74l7 6-7 6" stroke="#0F766E" stroke-opacity=".3" fill="none" stroke-width="1.6"/>
				<rect x="232" y="36" width="112" height="88" rx="8" fill="#fff" stroke="#0F766E" stroke-opacity=".2"/>
				<rect x="246" y="52" width="44" height="6" rx="3" fill="#0F766E" fill-opacity=".3"/>
				<rect x="246" y="68" width="82" height="18" rx="4" fill="#0F766E" fill-opacity=".08"/>
				<rect x="246" y="94" width="60" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<rect x="246" y="106" width="76" height="5" rx="2.5" fill="#0F766E" fill-opacity=".08"/>
			</g>

			<!-- 2 放射结构：偏架构 / Agent -->
			<g v-else-if="variant === 2">
				<circle cx="200" cy="80" r="42" fill="#0F766E" fill-opacity=".06"/>
				<circle cx="200" cy="80" r="22" fill="#0F766E" fill-opacity=".13"/>
				<circle cx="200" cy="80" r="8" fill="#0F766E" fill-opacity=".5"/>
				<g stroke="#0F766E" stroke-opacity=".22" stroke-dasharray="4 5">
					<path d="M200 80L132 40M200 80L268 40M200 80L132 120M200 80L268 120"/>
				</g>
				<g fill="#fff" stroke="#0F766E" stroke-opacity=".2">
					<rect x="106" y="26" width="52" height="28" rx="6"/>
					<rect x="242" y="26" width="52" height="28" rx="6"/>
					<rect x="106" y="106" width="52" height="28" rx="6"/>
					<rect x="242" y="106" width="52" height="28" rx="6"/>
				</g>
				<g fill="#0F766E" fill-opacity=".18">
					<rect x="118" y="37" width="28" height="5" rx="2.5"/>
					<rect x="254" y="37" width="28" height="5" rx="2.5"/>
					<rect x="118" y="117" width="28" height="5" rx="2.5"/>
					<rect x="254" y="117" width="28" height="5" rx="2.5"/>
				</g>
			</g>

			<!-- 3 书本：偏知识产品 -->
			<g v-else-if="variant === 3">
				<rect x="150" y="26" width="76" height="108" rx="6" fill="#fff" stroke="#0F766E" stroke-opacity=".24"/>
				<rect x="178" y="26" width="8" height="108" fill="#0F766E" fill-opacity=".1"/>
				<rect x="162" y="46" width="26" height="5" rx="2.5" fill="#0F766E" fill-opacity=".3"/>
				<rect x="162" y="58" width="20" height="4" rx="2" fill="#0F766E" fill-opacity=".14"/>
				<rect x="162" y="70" width="24" height="4" rx="2" fill="#0F766E" fill-opacity=".1"/>
				<rect x="236" y="52" width="88" height="8" rx="4" fill="#0F766E" fill-opacity=".13"/>
				<rect x="236" y="70" width="62" height="8" rx="4" fill="#0F766E" fill-opacity=".09"/>
				<rect x="236" y="88" width="74" height="8" rx="4" fill="#0F766E" fill-opacity=".07"/>
			</g>

			<!-- 4 柱状图：偏实验 / 数据结论 -->
			<g v-else-if="variant === 4">
				<rect x="86" y="24" width="228" height="112" rx="9" fill="#fff" stroke="#0F766E" stroke-opacity=".18"/>
				<rect x="104" y="42" width="56" height="6" rx="3" fill="#0F766E" fill-opacity=".2"/>
				<rect x="104" y="98" width="18" height="24" rx="3" fill="#0F766E" fill-opacity=".18"/>
				<rect x="132" y="86" width="18" height="36" rx="3" fill="#0F766E" fill-opacity=".3"/>
				<rect x="160" y="72" width="18" height="50" rx="3" fill="#0F766E" fill-opacity=".45"/>
				<rect x="188" y="58" width="18" height="64" rx="3" fill="#0F766E" fill-opacity=".62"/>
				<rect x="216" y="48" width="18" height="74" rx="3" fill="#0F766E" fill-opacity=".78"/>
				<rect x="244" y="70" width="18" height="52" rx="3" fill="#0F766E" fill-opacity=".38"/>
				<path d="M104 92L132 80L160 66L188 52L216 42L244 64" fill="none" stroke="#0B3F3A" stroke-opacity=".25"
				      stroke-width="1.6" stroke-linecap="round"/>
			</g>

			<!-- 5 模块拼装：偏工程结构 -->
			<g v-else>
				<rect x="70" y="34" width="88" height="40" rx="7" fill="#fff" stroke="#0F766E" stroke-opacity=".2"/>
				<rect x="70" y="86" width="88" height="40" rx="7" fill="#fff" stroke="#0F766E" stroke-opacity=".2"/>
				<rect x="242" y="34" width="88" height="92" rx="7" fill="#0F766E" fill-opacity=".07"
				      stroke="#0F766E" stroke-opacity=".18"/>
				<path d="M158 54h44v52h-44" fill="none" stroke="#0F766E" stroke-opacity=".22" stroke-dasharray="4 4"/>
				<rect x="84" y="48" width="44" height="5" rx="2.5" fill="#0F766E" fill-opacity=".24"/>
				<rect x="84" y="100" width="56" height="5" rx="2.5" fill="#0F766E" fill-opacity=".18"/>
				<rect x="256" y="50" width="48" height="6" rx="3" fill="#0F766E" fill-opacity=".26"/>
				<rect x="256" y="68" width="60" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<rect x="256" y="84" width="60" height="5" rx="2.5" fill="#0F766E" fill-opacity=".12"/>
				<rect x="256" y="100" width="40" height="5" rx="2.5" fill="#0F766E" fill-opacity=".1"/>
			</g>
		</svg>
	</div>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
	/** 决定图案的种子；同一 seed 永远得到同一张图 */
	seed?: string | number
	/** 指定版式；不传则按 seed 散列选一种 */
	variant?: number
	height?: number
}>(), {
	seed: '',
	height: 148
})

/** FNV-1a：够散、够短，不需要引第三方哈希 */
function hash(input: string) {
	let h = 2166136261
	for (let i = 0; i < input.length; i++) {
		h ^= input.charCodeAt(i)
		h = Math.imul(h, 16777619)
	}
	return Math.abs(h)
}

const variant = computed(() => {
	if (typeof props.variant === 'number') {
		return props.variant % 6
	}
	return hash(String(props.seed || 'coolskyai')) % 6
})
</script>

<style scoped>
.cp-cover {
	position: relative;
	overflow: hidden;
	background: var(--teal-050);
	flex: 0 0 auto;
}

.cp-cover svg {
	width: 100%;
	height: 100%;
	display: block;
}
</style>
