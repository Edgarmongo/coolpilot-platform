<template>
	<!--
		通用卡片容器。

		只有一种视觉：白底 + 1px 青边 + 12px 圆角，hover 上浮 2px，**不加阴影**。
		需要强调时靠位置与尺寸，不靠阴影 —— 这是设计稿的约定，也是和旧 .card 的区别
		（旧 .card 是 14px 圆角 + 极淡阴影，两者不要混用在同一个区块里）。
	-->
	<component :is="tag" class="cp-card" :class="{'is-hoverable': hover}">
		<slot name="cover"/>
		<div v-if="$slots.default" class="cp-card-body" :class="{'is-flush': flushBody}">
			<slot/>
		</div>
		<slot name="footer"/>
	</component>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
	/** 渲染成什么标签，例如 a / article / li */
	tag?: string
	/** 是否启用 hover 上浮（列表项建议开，静态信息块建议关） */
	hover?: boolean
	/** 正文区去掉内边距，用于自行控制布局的场景 */
	flushBody?: boolean
}>(), {
	tag: 'div',
	hover: true,
	flushBody: false
})
</script>

<style scoped>
.cp-card {
	background: #fff;
	border: 1px solid var(--c-line);
	border-radius: var(--c-radius);
	overflow: hidden;
	display: flex;
	flex-direction: column;
	transition: transform .18s ease, border-color .18s ease;
}

.is-hoverable:hover {
	transform: translateY(-2px);
	border-color: var(--c-line-strong);
}

.cp-card-body {
	padding: 18px 20px 20px;
	display: flex;
	flex-direction: column;
	flex: 1;
}

.is-flush {
	padding: 0;
}
</style>
