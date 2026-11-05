<template>
	<div>
		<h1 class="page-title">{{ t('moments.title') }}</h1>
		<p class="page-desc">{{ t('moments.desc') }}</p>

		<div v-if="!moments.length" class="empty card">{{ t('moments.empty') }}</div>

		<div v-for="moment in moments" :key="moment.id" class="card moment">
			<div class="content" v-html="moment.content"/>
			<div class="foot">
				<span class="muted">{{ formatDate(moment.createTime) }}</span>
				<button class="like" :class="{liked: liked.includes(moment.id)}" @click="like(moment)">
					♥ {{ moment.likes || 0 }}
				</button>
			</div>
		</div>

		<div class="pager" v-if="totalPage > 1">
			<button :disabled="pageNum <= 1" @click="go(pageNum - 1)">{{ t('common.prev') }}</button>
			<span>{{ pageNum }} / {{ totalPage }}</span>
			<button :disabled="pageNum >= totalPage" @click="go(pageNum + 1)">{{ t('common.next') }}</button>
		</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()
const {t} = useI18n()

const pageNum = ref(1)
const moments = ref<any[]>([])
const totalPage = ref(1)
const liked = ref<number[]>([])

async function load(page = 1) {
	const res: any = await call(`/moments?pageNum=${page}`)
	if (res.code === 200) {
		moments.value = res.data.list || []
		totalPage.value = res.data.totalPage || 1
		pageNum.value = page
	}
}

async function go(page: number) {
	await load(page)
	window.scrollTo({top: 0, behavior: 'smooth'})
}

async function like(moment: any) {
	if (liked.value.includes(moment.id)) {
		return
	}
	await call(`/moment/like/${moment.id}`, {method: 'POST'}).catch(() => null)
	liked.value.push(moment.id)
	moment.likes = (moment.likes || 0) + 1
}

await load(1)
useHead({title: t('moments.title')})
</script>

<style scoped>
.moment {
	margin-bottom: 12px;
}

.content {
	margin: 0 0 12px;
	font-size: 14.5px;
	line-height: 1.85;
}

/* 后端已把正文转成 HTML，这里补上段落与图片的排版 */
.content :deep(p) {
	margin: 0 0 8px;
}

.content :deep(p:last-child) {
	margin-bottom: 0;
}

.content :deep(img) {
	max-width: 100%;
	border-radius: 8px;
}

.foot {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.like {
	border: 1px solid var(--line);
	background: #fff;
	color: var(--muted);
	border-radius: 999px;
	padding: 3px 12px;
	font-size: 12.5px;
	cursor: pointer;
}

.like.liked {
	color: #e11d48;
	border-color: #fecdd3;
	background: #fff1f2;
}

.pager {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 14px;
	margin-top: 16px;
	font-size: 13px;
	color: var(--muted);
}

.pager button {
	padding: 5px 14px;
	border: 1px solid var(--line);
	border-radius: 7px;
	background: #fff;
	cursor: pointer;
}

.pager button:disabled {
	opacity: .45;
	cursor: not-allowed;
}
</style>
