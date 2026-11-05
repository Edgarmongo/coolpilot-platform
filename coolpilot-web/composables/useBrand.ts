/**
 * 站点品牌信息：顶栏品牌名/标记、页脚文案、备案号。
 *
 * 由 site_setting（type=1）投影而来，后台「站点设置」改完即生效。
 * 用固定 key 的 useAsyncData，layout 与页面共用同一次请求，不会重复拉取。
 */
export interface BrandInfo {
	brandMark: string
	brandName: string
	footerText: string
	footerTagline: string
	beian: string
}

/** 接口失败时的兜底：与改造前写死的文案一致，页面不会因为一次请求失败变空白 */
const FALLBACK: BrandInfo = {
	brandMark: 'CP',
	brandName: 'CoolSkyAI',
	footerText: 'CoolSkyAI 技术平台',
	footerTagline: '技术知识与服务',
	beian: ''
}

export function useBrand() {
	const {call} = useApi()

	const {data} = useAsyncData<Partial<BrandInfo> | null>('site-brand', async () => {
		const res: any = await call('/brand').catch(() => null)
		return res && res.code === 200 && res.data ? res.data as Partial<BrandInfo> : null
	})

	// 注意是「后端值覆盖兜底」，不是反过来：后端明确返回空串表示站长清了这项，应该照做
	return computed<BrandInfo>(() => ({...FALLBACK, ...(data.value || {})}))
}
