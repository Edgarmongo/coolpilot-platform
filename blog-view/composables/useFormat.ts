/**
 * 轻量日期格式化，避免为几个页面引入 dayjs
 */
export function formatDate(value: string | number | Date | null | undefined, pattern = 'YYYY-MM-DD HH:mm'): string {
	if (!value) {
		return '-'
	}
	const date = new Date(value)
	if (Number.isNaN(date.getTime())) {
		return '-'
	}
	const pad = (n: number) => String(n).padStart(2, '0')
	const map: Record<string, string> = {
		YYYY: String(date.getFullYear()),
		MM: pad(date.getMonth() + 1),
		DD: pad(date.getDate()),
		HH: pad(date.getHours()),
		mm: pad(date.getMinutes())
	}
	return pattern.replace(/YYYY|MM|DD|HH|mm/g, token => map[token])
}
