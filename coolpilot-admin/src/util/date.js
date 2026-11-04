import dayjs from 'dayjs'

/**
 * Vue 3 移除了 filters，统一用函数格式化
 * @param {string|number|Date} value
 * @param {string} pattern
 */
export function formatDate(value, pattern = 'YYYY-MM-DD HH:mm:ss') {
	if (!value) {
		return '-'
	}
	return dayjs(value).format(pattern)
}
