<template>
	<!--
		商业价值评估卡。

		七行固定维度，每行三件东西：档位、一句话条件、证据等级。
		刻意不给总分：一个数字无法表达「在什么条件下值得做什么」。

		评估 JSON 是人工在后台填的，格式可能出错。
		解析失败只让这一块显示「评估数据异常」，页面其余部分照常 ——
		不能因为一次手滑让整个档案页打不开。
	-->
	<div class="assessment">
		<div class="assess-head">
			<h2>{{ t('oss.assessmentTitle') }}</h2>
			<span class="hint">{{ t('oss.evidenceTip') }}</span>
		</div>

		<p v-if="invalid" class="invalid">{{ t('oss.assessmentInvalid') }}</p>

		<ul v-else class="rows">
			<li v-for="field in fields" :key="field.key" class="row">
				<span class="label">{{ t(`oss.fields.${field.key}`) }}</span>
				<span class="level" :class="`is-${levelOf(field.key)}`">{{ levelText(field.key) }}</span>
				<span class="note">{{ noteOf(field.key) }}</span>
				<span class="evidence" :class="evidenceClass(field.key)" :title="t(`oss.evidence.${evidenceOf(field.key)}`)">
					{{ evidenceOf(field.key) }}
				</span>
			</li>
		</ul>

		<p v-if="verdict" class="verdict-line">
			<span>{{ t('oss.verdictLabel') }}</span>
			<strong>{{ t(`oss.verdicts.${verdict}`) }}</strong>
		</p>
	</div>
</template>

<script setup lang="ts">
const {t} = useI18n()

const props = defineProps<{
	/** 评估卡原始 JSON */
	json?: string | null
	/** 结论档，来自项目本身 */
	verdict?: string | null
}>()

const FIELDS = [
	{key: 'techMaturity'},
	{key: 'marketDemand'},
	{key: 'commercialSpace'},
	{key: 'deployCost'},
	{key: 'competition'},
	{key: 'devValue'},
	{key: 'insightFit'}
]
const fields = FIELDS

const parsed = computed(() => {
	if (!props.json) {
		return null
	}
	try {
		const value = JSON.parse(props.json)
		return value && typeof value === 'object' ? value : null
	} catch {
		return null
	}
})

/** 有原文但解析不出来 = 数据异常；完全没填 = 全部显示待验证 */
const invalid = computed(() => !!props.json && !parsed.value)

function cell(key: string): any {
	return (parsed.value && parsed.value[key]) || {}
}

function levelOf(key: string) {
	const level = cell(key).level
	return level === 'high' || level === 'mid' || level === 'low' ? level : 'pending'
}

function levelText(key: string) {
	const level = levelOf(key)
	return level === 'pending' ? t('oss.pending') : t(`oss.levels.${level}`)
}

function noteOf(key: string) {
	return cell(key).note || ''
}

/** 未知证据用 ev-unknown，不能拼成 ev-?（? 不是合法的 CSS 类名，构建会报 css-syntax-error） */
function evidenceClass(key: string) {
	const ev = evidenceOf(key)
	return ev === '?' ? 'ev-unknown' : `ev-${ev}`
}

function evidenceOf(key: string) {
	const ev = cell(key).evidence
	return ['F', 'S', 'T', 'M', 'A'].includes(ev) ? ev : '?'
}
</script>

<style scoped>
.assessment {
	border: 1px solid var(--line);
	border-radius: var(--radius);
	padding: 20px 22px 18px;
	background: #fff;
}

.assess-head {
	display: flex;
	align-items: baseline;
	justify-content: space-between;
	gap: 12px;
	flex-wrap: wrap;
	margin-bottom: 14px;
}

.assess-head h2 {
	font-size: 17px;
	font-weight: 700;
}

.hint {
	font-size: 11.5px;
	color: var(--faint);
}

.rows {
	list-style: none;
	margin: 0;
	padding: 0;
}

.row {
	display: grid;
	grid-template-columns: 116px 54px minmax(0, 1fr) 26px;
	align-items: baseline;
	gap: 10px;
	padding: 10px 0;
	border-top: 1px solid var(--line);
}

.row:first-child {
	border-top: none;
}

.label {
	font-size: 13px;
	color: var(--ink-2);
}

.level {
	font-size: 12.5px;
	font-weight: 700;
	color: var(--teal-700);
}

.level.is-pending {
	color: var(--faint);
	font-weight: 500;
}

.note {
	font-size: 12.5px;
	color: var(--muted);
	line-height: 1.6;
}

.evidence {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 20px;
	height: 20px;
	border-radius: 4px;
	font-size: 11px;
	font-weight: 700;
	background: var(--teal-100);
	color: var(--teal-700);
	cursor: help;
}

.evidence.ev-unknown {
	background: #f3f4f6;
	color: #9ca3af;
}

.invalid {
	padding: 12px 14px;
	border: 1px solid var(--warn-line);
	background: var(--warn-bg);
	color: var(--warn-text);
	border-radius: 8px;
	font-size: 13px;
}

.verdict-line {
	display: flex;
	align-items: baseline;
	flex-wrap: wrap;
	gap: 8px;
	margin-top: 16px;
	padding-top: 14px;
	border-top: 1px solid var(--line);
	font-size: 13px;
	color: var(--muted);
}

.verdict-line strong {
	color: var(--teal-700);
	font-size: 15px;
}

@media (max-width: 640px) {
	.row {
		grid-template-columns: 92px 46px minmax(0, 1fr) 22px;
		gap: 8px;
	}
}
</style>
