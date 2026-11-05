/**
 * 答案正文渲染
 *
 * 模型输出一律当不可信文本：先转义再替换引用标记，避免把回答里的 HTML 当成标签执行。
 * 只给「引用列表里真实存在」的编号生成链接，模型编出 [9] 时保持纯文本，避免死链。
 *
 * 返回的是给 v-html 用的字符串；点击行为由页面用事件委托处理
 * （v-html 里绑不了 Vue 事件，硬塞 onclick 既难看也不好测）。
 */
export type AnswerCitation = {
	index: number
	title?: string
	url?: string
	sourceType?: string
	snippet?: string
}

export function renderAnswer(text: string, citations?: AnswerCitation[], scope?: string | number): string {
	if (!text) {
		return ''
	}
	const safe = text
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')

	return safe.replace(/\[(\d{1,2})\]/g, (match, num) => {
		const index = Number(num)
		const cite = (citations || []).find(item => item.index === index)
		if (!cite) {
			return match
		}
		// href 只作为锚点标记，真正滚动到哪张卡片由页面决定
		const anchor = scope === undefined ? `#cite-${index}` : `#cite-${scope}-${index}`
		return `<a class="cite-ref" href="${anchor}" data-cite="${index}">[${index}]</a>`
	})
}

/** 把答案和来源拼成可复制的纯文本 */
export function answerToText(question: string, answer: string, citations?: AnswerCitation[]): string {
	const lines = [question ? `问：${question}` : '', answer || ''].filter(Boolean)
	if (citations && citations.length) {
		lines.push('', '来源：')
		for (const cite of citations) {
			lines.push(`[${cite.index}] ${cite.title || ''} ${cite.url || ''}`.trim())
		}
	}
	return lines.join('\n')
}
