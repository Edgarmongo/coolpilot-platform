<template>
	<NuxtLayout>
		<NuxtPage/>
	</NuxtLayout>
</template>

<script setup lang="ts">
const brand = useBrand()

/**
 * 各页面 useHead 里仍写着「关于 - CoolSkyAI」，品牌名一改就会前后不一致。
 * 这里统一把硬编码后缀替换成配置里的品牌名：只动以「 - CoolSkyAI」结尾的标题，
 * 其它标题原样保留，避免误伤。页面本身不用改。
 */
useHead({
	titleTemplate: (title?: string) => {
		if (!title) {
			return brand.value.brandName
		}
		return title.replace(/ - CoolSkyAI$/, ` - ${brand.value.brandName}`)
	}
})
</script>
