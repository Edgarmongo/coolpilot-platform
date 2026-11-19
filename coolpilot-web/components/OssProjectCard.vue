<template>
	<!--
		项目卡片。栏目首页、列表页、领域/标签页、相关项目共用。

		封面优先用 coverUrl；没有就用 AppCover 按 slug 生成确定性线稿 ——
		不做「首字母色块」，那是加载失败的样子。
	-->
	<NuxtLink :to="localePath(`/open-source/projects/${project.slug}`)" class="oss-card">
		<div class="cover">
			<img v-if="project.coverUrl" :src="project.coverUrl" :alt="project.name" loading="lazy">
			<AppCover v-else :seed="`oss-${project.slug}`" :height="132" :variant="1"/>
			<span v-if="project.researchStatus === 'todo'" class="state">{{ t('oss.researching') }}</span>
		</div>

		<div class="body">
			<div class="head">
				<h3>{{ project.name }}</h3>
				<span v-if="project.verdict" class="verdict" :class="`is-${project.verdict}`">
					{{ t(`oss.verdicts.${project.verdict}`) }}
				</span>
			</div>
			<p class="summary">{{ project.summary }}</p>

			<div class="tags">
				<span class="tag field">{{ t(`oss.cats.${project.category}`) }}</span>
				<span v-for="tag in (project.tags || []).slice(0, 2)" :key="tag" class="tag">{{ tag }}</span>
			</div>

			<div class="foot">
				<span v-if="project.articleCount">{{ t('oss.articleCount', {n: project.articleCount}) }}</span>
				<span v-else>{{ t('oss.noAnalysis') }}</span>
				<span v-if="project.license">{{ project.license }}</span>
			</div>
		</div>
	</NuxtLink>
</template>

<script setup lang="ts">
const {t} = useI18n()
const localePath = useLocalePath()

defineProps<{
	project: any
}>()
</script>

<style scoped>
.oss-card {
	display: flex;
	flex-direction: column;
	background: #fff;
	border: 1px solid var(--line);
	border-radius: var(--radius);
	overflow: hidden;
	transition: transform .18s ease, border-color .18s ease;
}

.oss-card:hover {
	transform: translateY(-2px);
	border-color: var(--line-2);
}

.cover {
	position: relative;
	overflow: hidden;
	background: var(--teal-050);
	height: 132px;
}

.cover img {
	width: 100%;
	height: 100%;
	object-fit: cover;
	display: block;
}

.state {
	position: absolute;
	top: 10px;
	left: 10px;
	padding: 3px 8px;
	border-radius: 5px;
	background: var(--warn-bg);
	border: 1px solid var(--warn-line);
	color: var(--warn-text);
	font-size: 11.5px;
	font-weight: 600;
}

.body {
	display: flex;
	flex-direction: column;
	flex: 1;
	padding: 16px 18px 18px;
}

.head {
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
	gap: 10px;
	margin-bottom: 8px;
}

.head h3 {
	font-size: 16.5px;
	font-weight: 700;
	line-height: 1.4;
}

.oss-card:hover h3 {
	color: var(--teal);
}

.verdict {
	flex: 0 0 auto;
	padding: 3px 7px;
	border-radius: 5px;
	font-size: 11px;
	font-weight: 600;
	background: var(--teal-100);
	color: var(--teal-700);
	white-space: nowrap;
}

/* 结论档用极浅的色差区分，不做红黄绿灯：档位是判断，不是评分 */
.verdict.is-hold {
	background: #f3f4f6;
	color: #6b7280;
}

.verdict.is-watch {
	background: #eef6ff;
	color: #2c5d8f;
}

.summary {
	flex: 1;
	font-size: 13px;
	line-height: 1.65;
	color: var(--muted);
	display: -webkit-box;
	-webkit-line-clamp: 3;
	-webkit-box-orient: vertical;
	overflow: hidden;
}

.tags {
	display: flex;
	flex-wrap: wrap;
	gap: 6px;
	margin: 12px 0 0;
}

.foot {
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
</style>
