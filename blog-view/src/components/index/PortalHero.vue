<template>
	<section class="portal-hero">
		<div class="portal-hero-content">
			<!--胶囊标签-->
			<p class="portal-hero-chip">
				<span>🤖</span>
				<span>{{ siteName }} · {{ slogan }}</span>
			</p>

			<!--主标题-->
			<h1 class="portal-hero-title">{{ title }}</h1>

			<!--描述-->
			<p class="portal-hero-desc">{{ description }}</p>

			<!--主按钮组-->
			<div class="portal-hero-actions" v-if="actions.length > 0">
				<router-link
					v-for="(action, index) in actions"
					:key="index"
					:to="action.path"
					class="portal-button"
					:class="{'portal-button--primary': index === 0}"
				>{{ action.label }}</router-link>
			</div>

			<!--统计卡-->
			<div class="portal-stats" v-if="stats.length > 0" :style="{'--cols': Math.min(stats.length, 4)}">
				<router-link
					v-for="(stat, index) in stats"
					:key="index"
					:to="stat.path || '/'"
					class="portal-stat"
				>
					<strong>{{ stat.value }}</strong>
					<span>{{ stat.label }}</span>
				</router-link>
			</div>
		</div>
	</section>
</template>

<script>
	export default {
		name: "PortalHero",
		props: {
			//站点名
			siteName: {
				type: String,
				default: '个人站'
			},
			//副标语
			slogan: {
				type: String,
				default: '记录与分享'
			},
			//大标题
			title: {
				type: String,
				default: '欢迎来到我的个人网站'
			},
			//描述
			description: {
				type: String,
				default: '这里记录技术笔记、项目实践与生活随想。'
			},
			//主按钮组，首项为主按钮
			actions: {
				type: Array,
				default: () => []
			},
			//统计卡
			stats: {
				type: Array,
				default: () => []
			}
		}
	}
</script>

<style scoped>

</style>
