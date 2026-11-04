module.exports = {
	/**
	 * @description 旧版视差首页背景图
	 * 门户版首页已改用 PortalHero，不再需要这三张大图。
	 * 如需恢复旧版首页（components/index/Header.vue），在此重新加入：
	 *   bg1: require('@/assets/img/bg1.png'),
	 *   bg2: require('@/assets/img/bg2.png'),
	 *   bg3: require('@/assets/img/bg3.png'),
	 * 注意：三张图各约 1.5MB，会显著拖慢首屏，不建议在门户版首页启用。
	 */
	bg1: '',
	bg2: '',
	bg3: '',

	/**
	 * @type {string}
	 * @description 首页故障风文字（旧版视差首页使用）
	 */
	malfunctionText: '酷天AI.CoolSkyAI',

	/**
	 * @description 门户首页 Hero 区文案，可按需修改
	 */
	portal: {
		//站点名，显示在标签与 Logo 旁
		siteName: 'CoolSkyAI',
		//副标语
		slogan: '技术笔记与实践记录',
		//大标题
		title: '记录工程实践，也记录踩过的坑',
		//描述
		description: '这里收录我在后端架构、前端工程与 AI 工具实践中的经验沉淀。每篇文章都来自真实项目的复盘，包含可复现的代码、踩坑记录与方案取舍思路，希望能帮同样在路上的你少走一些弯路。'
	}
}
