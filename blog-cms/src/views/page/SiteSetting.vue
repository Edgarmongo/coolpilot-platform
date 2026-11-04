<template>
	<div>
		<div class="page-header">
			<div>
				<h2 class="page-title">站点设置</h2>
				<p class="page-desc">站点名、资料卡、徽标等展示信息，保存后前台即时生效</p>
			</div>
			<el-button type="primary" :loading="saving" @click="save">保存全部</el-button>
		</div>

		<el-tabs v-model="tab">
			<el-tab-pane v-for="group in groups" :key="group.type" :label="group.label" :name="String(group.type)">
				<div v-loading="loading" class="settings">
					<div v-for="item in group.items" :key="item.id" class="row">
						<label>{{ item.nameZh || item.nameEn }}</label>
						<el-input v-if="isLong(item)" v-model="item.value" type="textarea" :rows="3"/>
						<el-input v-else v-model="item.value"/>
					</div>
				</div>
			</el-tab-pane>
		</el-tabs>
	</div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {getSiteSettings, updateSiteSettings} from '@/api/siteSetting'

const loading = ref(false)
const saving = ref(false)
const tab = ref('1')
const all = ref([])

const groups = computed(() => [
	{type: 1, label: '基础设置', items: all.value.filter(item => item.type === 1)},
	{type: 2, label: '资料卡', items: all.value.filter(item => item.type === 2)},
	{type: 3, label: '徽标', items: all.value.filter(item => item.type === 3)},
	{type: 4, label: '友链说明', items: all.value.filter(item => item.type === 4)}
].filter(group => group.items.length > 0))

function isLong(item) {
	return (item.value || '').length > 60
}

async function load() {
	loading.value = true
	try {
		const res = await getSiteSettings()
		// 后端按 type 分组返回，拍平成一个数组再编辑
		all.value = Object.values(res.data || {}).flat()
	} finally {
		loading.value = false
	}
}

async function save() {
	saving.value = true
	try {
		await updateSiteSettings({
			settings: all.value.map(item => ({
				id: item.id,
				nameEn: item.nameEn,
				nameZh: item.nameZh,
				type: item.type,
				value: item.value
			})),
			deleteIds: []
		})
		ElMessage.success('已保存')
	} finally {
		saving.value = false
	}
}

onMounted(load)
</script>

<style scoped>
.settings {
	padding: 4px 2px;
}

.row {
	display: grid;
	grid-template-columns: 150px 1fr;
	align-items: start;
	gap: 14px;
	padding: 8px 0;
	border-bottom: 1px dashed #eef2f2;
}

.row label {
	padding-top: 6px;
	font-size: 13px;
	color: #606266;
}
</style>
