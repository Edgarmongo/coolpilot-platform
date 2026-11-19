<template>
	<!--
		筛选胶囊。用于分类/标签/时间范围等单选筛选。

		有 to 时渲染成 NuxtLink（可被收录、可分享），
		没有 to 时渲染成 button（纯前端筛选，不产生历史记录）。
	-->
	<component
		:is="to ? 'NuxtLink' : 'button'"
		:to="to"
		:type="to ? undefined : 'button'"
		v-bind="to ? {} : { 'aria-pressed': active }"
		class="cp-chip"
		:class="{'is-on': active}"
	>
		<slot/>
	</component>
</template>

<script setup lang="ts">
defineProps<{
	/** 选中态 */
	active?: boolean
	/** 传了就是链接，不传就是按钮 */
	to?: string
}>()
</script>

<style scoped>
.cp-chip {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 7px;
	height: 36px;
	padding: 0 16px;
	border-radius: var(--c-radius-sm);
	border: 1px solid var(--c-line);
	background: #fff;
	font-size: 13.5px;
	color: var(--c-ink-2);
	cursor: pointer;
	font-family: inherit;
	line-height: 1;
	white-space: nowrap;
	transition: border-color .15s, color .15s, background .15s;
}

.cp-chip:hover {
	border-color: var(--c-line-strong);
	color: var(--c-brand);
}

.is-on {
	background: var(--c-brand);
	border-color: var(--c-brand);
	color: #fff;
	font-weight: 600;
}

.is-on:hover {
	color: #fff;
}
</style>
