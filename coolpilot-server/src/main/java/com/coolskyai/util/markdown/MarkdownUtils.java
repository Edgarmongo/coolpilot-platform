package com.coolskyai.util.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.AttributeProviderContext;
import org.commonmark.renderer.html.AttributeProviderFactory;
import org.commonmark.renderer.html.HtmlRenderer;
import com.coolskyai.util.markdown.ext.heimu.HeimuExtension;
import com.coolskyai.util.markdown.ext.cover.CoverExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Description: Markdown转换
 * @Author: CoolSkyAI
 * @Date: 2020-04-29
 */
public class MarkdownUtils {
	//为h标签生成id 供tocbot目录生成
	private static final Set<Extension> headingAnchorExtensions = Collections.singleton(HeadingAnchorExtension.create());
	//转换table的HTML
	private static final List<Extension> tableExtension = Arrays.asList(TablesExtension.create());
	//任务列表
	private static final Set<Extension> taskListExtension = Collections.singleton(TaskListItemsExtension.create());
	//删除线
	private static final Set<Extension> delExtension = Collections.singleton(StrikethroughExtension.create());
	//黑幕
	private static final Set<Extension> heimuExtension = Collections.singleton(HeimuExtension.create());
	//遮盖层
	private static final Set<Extension> coverExtension = Collections.singleton(CoverExtension.create());

	private static final Parser parser = Parser.builder()
			.extensions(tableExtension)
			.extensions(taskListExtension)
			.extensions(delExtension)
			.extensions(heimuExtension)
			.extensions(coverExtension)
			.build();

	private static final HtmlRenderer renderer = HtmlRenderer.builder()
			.extensions(headingAnchorExtensions)
			.extensions(tableExtension)
			.extensions(taskListExtension)
			.extensions(delExtension)
			.extensions(heimuExtension)
			.extensions(coverExtension)
			.attributeProviderFactory(new AttributeProviderFactory() {
				@Override
				public AttributeProvider create(AttributeProviderContext context) {
					return new CustomAttributeProvider();
				}
			})
			.build();

	/**
	 * 自定义标签的属性
	 */
	private static class CustomAttributeProvider implements AttributeProvider {
		@Override
		public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
			// 图片：保留 src，用原生 loading="lazy" 做懒加载
			//
			// 这里原来把 src 删掉、只留 data-src，本意是交给前端懒加载库，
			// 但 Nuxt 前台并没有对应的实现，结果所有 Markdown 图片都渲染不出来
			// （img 没有 src 就没有内容）。原生 loading="lazy" 由浏览器负责，
			// 不需要任何 JS，且不阻塞首屏。
			if (node instanceof Image) {
				attributes.put("loading", "lazy");
				attributes.put("decoding", "async");
			}
			//改变a标签的target属性为_blank
			if (node instanceof Link) {
				Link n = (Link) node;
				String destination = n.getDestination();
				//判断是否页内锚点跳转
				if (destination.startsWith("#")) {
					attributes.put("class", "toc-link");//针对tocbot锚点跳转的class属性
				} else {
					//外部链接
					attributes.put("target", "_blank");
					attributes.put("rel", "external nofollow noopener");
				}
			}
			if (node instanceof TableBlock) {
				attributes.put("class", "ui celled table");//针对 semantic-ui 的class属性
			}
		}
	}

	/**
	 * markdown格式转换成HTML格式
	 */
	public static String markdownToHtml(String markdown) {
		Parser parser = Parser.builder().build();
		Node document = parser.parse(markdown);
		HtmlRenderer renderer = HtmlRenderer.builder().build();
		return renderer.render(document);
	}

	/**
	 * 增加扩展
	 *
	 * 为什么不能直接 parse：库里 content 有两种来源。
	 * 后台编辑器写的是 Markdown；而通过接口直接写入的、或从别处整篇粘进来的，
	 * 可能是已经渲染好的 HTML。commonmark 解析 HTML 块时遇到空行就认为块结束，
	 * 于是 <pre> 里的空行会把代码块截断，代码里的 "# 2. ..." 被当成标题渲染，
	 * 页面上就是「深色代码块里混着大号标题」。
	 *
	 * 这里的做法是把 HTML 块整体抽出来、用占位注释代替，交由 commonmark 只处理真正的
	 * Markdown 部分，渲染完再把 HTML 原样放回。对纯 Markdown 内容零影响；
	 * 对纯 HTML 内容等于原样透传；混合内容两边都正确。
	 */
	public static String markdownToHtmlExtensions(String markdown) {
		if (markdown == null || markdown.isEmpty()) {
			return "";
		}
		ProtectedHtml protectedHtml = protectHtmlBlocks(markdown);
		Node document = parser.parse(protectedHtml.getText());
		String html = renderer.render(document);
		return protectedHtml.restore(html);
	}

	/**
	 * 需要整体保护的块级标签。
	 *
	 * 只收「内部可能包含空行、且内部文本不应被 Markdown 规则解释」的容器标签。
	 * 不收 p / h1-h6 / hr 这类单行标签——它们本来就能被当成独立的 HTML 块原样输出。
	 */
	private static final Set<String> RAW_BLOCK_TAGS = Set.of(
			"pre", "script", "style", "textarea", "table", "figure", "blockquote",
			"ul", "ol", "dl", "div", "section", "article", "aside", "header", "footer",
			"nav", "main", "form", "details", "iframe", "svg", "video", "audio", "canvas"
	);

	private static final class ProtectedHtml {
		private final String text;
		private final Map<String, String> blocks;

		private ProtectedHtml(String text, Map<String, String> blocks) {
			this.text = text;
			this.blocks = blocks;
		}

		private String getText() {
			return text;
		}

		private String restore(String html) {
			if (blocks.isEmpty()) {
				return html;
			}
			String result = html;
			for (Map.Entry<String, String> entry : blocks.entrySet()) {
				result = result.replace(entry.getKey(), entry.getValue());
			}
			return result;
		}
	}

	/**
	 * 把位于行首的 HTML 块换成占位注释。
	 *
	 * 占位用 HTML 注释而不是普通文本：注释本身就是一个 HTML 块，渲染时会原样输出，
	 * 不会被包进 &lt;p&gt; 里，还原时就不必处理包裹标签。
	 */
	private static ProtectedHtml protectHtmlBlocks(String source) {
		StringBuilder out = new StringBuilder(source.length());
		Map<String, String> blocks = new java.util.LinkedHashMap<>();
		int index = 0;
		// 两个游标各管一件事：scan 只负责往后找标签，pendingStart 标记「还没写出去的文本从哪开始」。
		// 合成一个变量会在跳过标签时把前面的正文丢掉（第一版就是这么错的）。
		int scan = 0;
		int pendingStart = 0;
		while (scan < source.length()) {
			int open = source.indexOf('<', scan);
			if (open < 0) {
				break;
			}
			int nameStart = open + 1;
			if (nameStart >= source.length()) {
				break;
			}
			// 跳过 </tag>、<!-- --> 这类，只处理开标签
			if (source.charAt(nameStart) == '/' || source.charAt(nameStart) == '!') {
				scan = nameStart + 1;
				continue;
			}
			int nameEnd = nameStart;
			while (nameEnd < source.length() && Character.isLetterOrDigit(source.charAt(nameEnd))) {
				nameEnd++;
			}
			String tag = source.substring(nameStart, nameEnd).toLowerCase();
			if (tag.isEmpty() || !RAW_BLOCK_TAGS.contains(tag) || !atLineStart(source, open)) {
				scan = open + 1;
				continue;
			}
			int close = findMatchingClose(source, nameEnd, tag);
			if (close < 0) {
				// 没有闭合标签，说明只是文本里出现了 "<div" 之类，交给默认解析
				scan = open + 1;
				continue;
			}
			int end = source.indexOf('>', close);
			if (end < 0) {
				scan = open + 1;
				continue;
			}
			end += 1;
			String token = "<!--CPHTML-" + index + "-->";
			index++;
			blocks.put(token, source.substring(open, end));
			out.append(source, pendingStart, open);
			// 前后留空行，保证占位符自己成一个块
			out.append('\n').append(token).append('\n');
			pendingStart = end;
			scan = end;
		}
		out.append(source, pendingStart, source.length());
		return new ProtectedHtml(out.toString(), blocks);
	}

	/** 只有行首（允许前导空白）的标签才当成块处理，行内的不动 */
	private static boolean atLineStart(String source, int position) {
		for (int i = position - 1; i >= 0; i--) {
			char c = source.charAt(i);
			if (c == '\n') {
				return true;
			}
			if (!Character.isWhitespace(c)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 从开标签之后开始找配对的闭合标签，按同名标签计深度，
	 * 这样嵌套的 div 不会在第一个 &lt;/div&gt; 处提前结束。
	 *
	 * @return 闭合标签 "&lt;/tag" 的起始下标，找不到返回 -1
	 */
	private static int findMatchingClose(String source, int from, String tag) {
		String openToken = "<" + tag;
		String closeToken = "</" + tag;
		int depth = 0;
		int i = from;
		while (i < source.length()) {
			int nextOpen = indexOfIgnoreCase(source, openToken, i);
			int nextClose = indexOfIgnoreCase(source, closeToken, i);
			if (nextClose < 0) {
				return -1;
			}
			if (nextOpen >= 0 && nextOpen < nextClose) {
				int after = nextOpen + openToken.length();
				char c = after < source.length() ? source.charAt(after) : '>';
				boolean isTagName = c == '>' || c == '/' || Character.isWhitespace(c);
				int gt = source.indexOf('>', nextOpen);
				boolean selfClosing = gt > 0 && gt < nextClose && source.charAt(gt - 1) == '/';
				if (isTagName && !selfClosing) {
					depth++;
				}
				i = nextOpen + openToken.length();
			} else {
				if (depth == 0) {
					return nextClose;
				}
				depth--;
				i = nextClose + closeToken.length();
			}
		}
		return -1;
	}

	private static int indexOfIgnoreCase(String source, String token, int from) {
		int max = source.length() - token.length();
		for (int i = Math.max(from, 0); i <= max; i++) {
			if (source.regionMatches(true, i, token, 0, token.length())) {
				return i;
			}
		}
		return -1;
	}

	public static void main(String[] args) {
		System.out.println(markdownToHtmlExtensions(""));
	}
}
