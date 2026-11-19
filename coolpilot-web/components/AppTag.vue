<template>
	<!--
		标签。两种语义：

		- default：技术标签（Python / RAG / SSH），描边、浅色
		- field：所属领域或类型（数据采集与搜索 / 商业分析），实底浅青、加重

		用 variant 而不是颜色 prop，是为了避免有人在页面里传随机色值，
		导致同一个「领域」在不同页面颜色不一样。
	-->
	<component
		:is="to ? 'NuxtLink' : 'span'"
		:to="to"
		class="cp-tag"
		:class="`is-${variant}`"
	>
		<slot/>
	</component>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
	variant?: 'default' | 'field'
	/** 传了则可点击跳转到对应标签页 */
	to?: string
}>(), {
	variant: 'default'
})
</script>

<style scoped>
.cp-tag {
	display: inline-flex;
	align-items: center;
	font-size: 11.5px;
	line-height: 1;
	padding: 5px 8px;
	border-radius: var(--c-radius-xs);
	border: 1px solid var(--c-line);
	background: #fff;
	color: var(--c-ink-2);
	white-space: nowrap;
}

a.cp-tag:hover {
	border-color: var(--c-line-strong);
	color: var(--c-brand);
}

.is-field {
	background: var(--c-brand-soft-2);
	border-color: transparent;
	color: var(--c-brand-strong);
	font-weight: 600;
}

a.is-field:hover {
	color: var(--c-brand-strong);
	background: var(--c-brand-soft-2);
}
</style>
