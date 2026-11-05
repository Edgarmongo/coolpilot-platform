import {nextTick, ref} from 'vue'
import {ElMessage} from 'element-plus'
import request from '@/util/request'
import {useT} from '@/i18n/ui'

// 单张上限：截图通常几百 KB，10 MB 足够，也挡得住手机原图
const MAX_IMAGE_MB = 10

/**
 * 给多行编辑器加上「粘贴 / 拖拽图片 → 自动上传 → 插入图片语法」的能力。
 *
 * 不持有元素 ref：直接用事件源里的 textarea，所以原生 <textarea> 和
 * el-input 内部的 textarea 都能用。调用方只需提供读写文本的两个函数，
 * 与 v-model 绑定的字段解耦。
 *
 * @param {object} options
 * @param {() => string} options.getValue 读取当前文本
 * @param {(value: string) => void} options.setValue 写回文本
 * @param {string} [options.bizType] 文件业务类型，默认 ARTICLE
 * @param {'markdown'|'html'} [options.syntax] markdown 插 ![]()，html 插 <img>
 */
export function useImagePaste({getValue, setValue, bizType = 'ARTICLE', syntax = 'markdown'}) {
	const t = useT()
	const uploading = ref(false)
	const dragging = ref(false)

	/**
	 * el-input 把事件绑在包装 div 上（事件冒泡过去），拖到内边距时
	 * 事件源可能是 div 本身，这里两种情况都兜住。
	 */
	function textareaOf(event) {
		const target = event && event.target
		if (!target) {
			return null
		}
		if (typeof target.selectionStart === 'number') {
			return target
		}
		return target.querySelector ? target.querySelector('textarea') : null
	}

	/**
	 * 在光标处插入文本。
	 * 文本由 v-model 受控，改完浏览器会把光标顶到末尾，
	 * 所以插入后要把光标移回插入内容之后，连续粘贴多张图才不会次序错乱。
	 */
	function insertText(target, text) {
		const value = getValue() || ''
		const start = target ? target.selectionStart : value.length
		const end = target ? target.selectionEnd : start
		setValue(value.slice(0, start) + text + value.slice(end))
		nextTick(() => {
			if (!target || document.activeElement !== target) {
				return
			}
			const pos = start + text.length
			try {
				target.setSelectionRange(pos, pos)
			} catch (e) {
				// 元素不可见时部分浏览器不允许设置选区，忽略
			}
		})
	}

	/**
	 * 把占位标记换成真实内容，并按替换前后的长度差把光标平移回去。
	 * 用户已经切走输入框时不去抢焦点。
	 */
	function replaceMarker(target, marker, replacement) {
		const value = getValue() || ''
		const index = value.indexOf(marker)
		if (index < 0) {
			return
		}
		const pos = target ? target.selectionStart : 0
		const active = target === document.activeElement
		setValue(value.replace(marker, replacement))
		if (!active) {
			return
		}
		const next = pos > index ? pos - marker.length + replacement.length : pos
		nextTick(() => {
			try {
				target.setSelectionRange(next, next)
			} catch (e) {
				// 同上
			}
		})
	}

	function imageSyntax(url) {
		return syntax === 'html' ? `<img src="${url}" alt=""/>` : `![](${url})`
	}

	/**
	 * 上传一张图。先插 HTML 注释占位（不显示，也不像裂图那样难看），
	 * 成功换成图片语法、失败删掉 —— 用户上传期间继续打字也不会插错位置。
	 */
	async function uploadOne(target, file) {
		const marker = `<!--img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}-->`
		insertText(target, `\n${marker}\n`)
		const formData = new FormData()
		// 截图拿到的文件名通常就是 image.png，后端按时间戳重命名，这里只借扩展名
		formData.append('file', file, file.name || 'image.png')
		formData.append('bizType', bizType)
		formData.append('isPublic', 'true')
		try {
			const res = await request({
				url: 'file/upload',
				method: 'POST',
				data: formData,
				headers: {'Content-Type': 'multipart/form-data'}
			})
			replaceMarker(target, marker, imageSyntax(res.data.url))
			return true
		} catch (e) {
			// 请求拦截器已经弹过错误，这里只把占位清掉
			replaceMarker(target, marker, '')
			return false
		}
	}

	async function uploadFiles(files, target) {
		const images = Array.from(files || []).filter(file => file.type && file.type.startsWith('image/'))
		if (!images.length) {
			return
		}
		if (uploading.value) {
			ElMessage.warning(t('还有图片正在上传，请稍候'))
			return
		}
		uploading.value = true
		let ok = 0
		try {
			for (const file of images) {
				if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
					ElMessage.warning(t('图片超过 {n} MB，请先压缩', {n: MAX_IMAGE_MB}))
					continue
				}
				if (await uploadOne(target, file)) {
					ok++
				}
			}
		} finally {
			uploading.value = false
		}
		if (ok) {
			ElMessage.success(t('已插入 {n} 张图片', {n: ok}))
		}
	}

	function onPaste(event) {
		const items = (event.clipboardData && event.clipboardData.items) || []
		const files = []
		for (const item of items) {
			if (item.kind === 'file' && item.type.startsWith('image/')) {
				const file = item.getAsFile()
				if (file) {
					files.push(file)
				}
			}
		}
		// 没有图片就走浏览器默认粘贴，纯文本、代码片段照常
		if (!files.length) {
			return
		}
		event.preventDefault()
		uploadFiles(files, textareaOf(event))
	}

	function onDrop(event) {
		dragging.value = false
		uploadFiles((event.dataTransfer && event.dataTransfer.files) || [], textareaOf(event))
	}

	function onDragOver() {
		dragging.value = true
	}

	function onDragLeave() {
		dragging.value = false
	}

	return {uploading, dragging, onPaste, onDrop, onDragOver, onDragLeave, uploadFiles}
}
