import {createI18n} from 'vue-i18n'
import zhCN from './messages/zh-CN'
import en from './messages/en'

/**
 * 后台只有站长自己用，语言选择存 localStorage 即可，不需要进 URL。
 * 与前台不同：前台英文要能被搜索引擎收录，所以用 /en 前缀；
 * 后台是登录后的工作台，没有 SEO 诉求，也更不该让书签指向 /en/cms/...。
 */
export const LOCALE_KEY = 'cms_locale'
export const LOCALES = [
	{code: 'zh-CN', short: '中'},
	{code: 'en', short: 'EN'}
]

const saved = localStorage.getItem(LOCALE_KEY)
const initial = LOCALES.some(item => item.code === saved) ? saved : 'zh-CN'

export const i18n = createI18n({
	legacy: false,
	globalInjection: true,
	locale: initial,
	fallbackLocale: 'zh-CN',
	messages: {
		'zh-CN': zhCN,
		en
	}
})

export function setLocale(code) {
	i18n.global.locale.value = code
	localStorage.setItem(LOCALE_KEY, code)
}

export function currentLocale() {
	return i18n.global.locale.value
}
