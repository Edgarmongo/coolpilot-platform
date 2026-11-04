<template>
	<div>
		<h1 class="page-title">友人帐</h1>
		<p class="page-desc">交换友链，欢迎来信</p>

		<div v-if="info" class="card intro article" v-html="info"></div>

		<div class="grid">
			<a v-for="friend in friends" :key="friend.website || friend.url" :href="friend.website || friend.url" target="_blank" rel="noopener"
			   class="card friend">
				<img v-if="friend.avatar" :src="friend.avatar" class="avatar" alt="">
				<div>
					<strong>{{ friend.nickname }}</strong>
					<p class="muted">{{ friend.description || friend.website }}</p>
				</div>
			</a>
		</div>

		<div v-if="!friends.length" class="empty card">暂无友链</div>
	</div>
</template>

<script setup lang="ts">
const {call} = useApi()

const {data} = await useAsyncData('friends', () => call('/friends'))
const info = computed(() => (data.value as any)?.data?.friendInfo?.content || '')
const friends = computed(() => (data.value as any)?.data?.friends || [])

useHead({title: '友人帐 - CoolSkyAI'})
</script>

<style scoped>
.intro {
	margin-bottom: 14px;
}

.grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
	gap: 12px;
}

.friend {
	display: flex;
	align-items: center;
	gap: 12px;
	padding: 14px 16px;
	transition: box-shadow .15s, transform .15s;
}

.friend:hover {
	transform: translateY(-2px);
	box-shadow: 0 8px 22px rgba(15, 118, 110, .09);
}

.avatar {
	width: 40px;
	height: 40px;
	border-radius: 50%;
	object-fit: cover;
	flex-shrink: 0;
}

.friend strong {
	font-size: 14px;
}

.friend p {
	margin: 2px 0 0;
	display: -webkit-box;
	-webkit-line-clamp: 1;
	-webkit-box-orient: vertical;
	overflow: hidden;
}
</style>
