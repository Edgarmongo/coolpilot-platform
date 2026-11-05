<template>
	<div>
		<h1 class="page-title">{{ t('archives.title') }}</h1>
		<p class="page-desc">{{ t('archives.desc', {n: totalCount}) }}</p>

		<div class="card">
			<div v-if="!groups.length" class="empty">{{ t('archives.empty') }}</div>
			<section v-for="group in groups" :key="group.month" class="group">
				<h2 class="month">{{ group.month }}<span class="muted">{{ t('archives.count', {n: group.items.length}) }}</span></h2>
				<ul class="list">
					<li v-for="item in group.items" :key="item.id">
						<span class="day">{{ item.day }}</span>
						<NuxtLink :to="localePath(`/blog/${item.id}`)" class="title">{{ item.title }}</NuxtLink>
					</li>
				</ul>
			</section>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()
const localePath = useLocalePath()

const {data} = await useAsyncData('archives', () => call('/archives'))
const blogMap = computed(() => (data.value as any)?.data?.blogMap || {})

const groups = computed(() => Object.keys(blogMap.value).map(month => ({month, items: blogMap.value[month]})))
const totalCount = computed(() => groups.value.reduce((sum, group) => sum + group.items.length, 0))

useHead({title: '归档 - CoolSkyAI'})
</script>

<style scoped>
.group {
	margin-bottom: 22px;
}

.group:last-child {
	margin-bottom: 0;
}

.month {
	margin: 0 0 8px;
	font-size: 15px;
	font-weight: 600;
	color: var(--teal);
}

.list {
	list-style: none;
	margin: 0;
	padding: 0 0 0 2px;
	border-left: 2px solid #eef2f2;
}

.list li {
	position: relative;
	display: flex;
	align-items: baseline;
	gap: 12px;
	padding: 6px 0 6px 16px;
}

.list li::before {
	content: '';
	position: absolute;
	left: -5px;
	top: 14px;
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: #cfe7e3;
}

.day {
	width: 46px;
	flex-shrink: 0;
	color: var(--muted);
	font-size: 12.5px;
}

.title {
	font-size: 14px;
	color: var(--ink);
}

.title:hover {
	color: var(--teal);
}
</style>
