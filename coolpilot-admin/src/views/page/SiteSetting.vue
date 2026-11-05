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

// 分组标题按 type 映射；后端按库里实际存在的 type 动态返回，
// 所以这里不再写死 1~4 —— 以后加新分组（比如支付设置）不用再改前端。
const TYPE_LABELS = {
	1: '基础设置',
	2: '资料卡',
	3: '徽标',
	4: '友链说明',
	5: '支付设置'
}

const groups = computed(() => {
	const grouped = new Map()
	for (const item of all.value) {
		if (item.type === null || item.type === undefined) {
			continue
		}
		if (!grouped.has(item.type)) {
			grouped.set(item.type, [])
		}
		grouped.get(item.type).push(item)
	}
	return [...grouped.entries()]
		.sort((a, b) => a[0] - b[0])
		.map(([type, items]) => ({
			type,
			label: TYPE_LABELS[type] || `其他设置（type ${type}）`,
			items
		}))
})

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
