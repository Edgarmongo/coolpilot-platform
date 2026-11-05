<template>
	<el-config-provider :locale="elementLocale">
		<router-view/>
	</el-config-provider>
</template>

<script setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const {locale} = useI18n()

// Element Plus 自己的文案（分页、空状态、日期选择器）也要跟着切
const elementLocale = computed(() => (locale.value === 'en' ? en : zhCn))
</script>
