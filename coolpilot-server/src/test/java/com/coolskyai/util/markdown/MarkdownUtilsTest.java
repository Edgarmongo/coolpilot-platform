package com.coolskyai.util.markdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正文渲染的回归测试。
 *
 * 背景：库里 content 可能是 Markdown（后台编辑器），也可能是已渲染的 HTML
 * （接口直接写入、或整篇粘进来）。commonmark 解析 HTML 块时遇到空行就认为块结束，
 * 于是 &lt;pre&gt; 里的空行会截断代码块，代码里的 "# 2. ..." 被当成标题渲染。
 * 线上出现过一次：代码块里混进大号标题、命令被包进 &lt;p&gt;。
 *
 * 这些用例锁住三件事：HTML 块原样透传、Markdown 照旧转换、两种内容混排都不出错。
 */
class MarkdownUtilsTest {

	private static final Pattern PRE = Pattern.compile("<pre>(.*?)</pre>", Pattern.DOTALL);

	private static List<String> preBlocks(String html) {
		Matcher matcher = PRE.matcher(html);
		java.util.ArrayList<String> list = new java.util.ArrayList<>();
		while (matcher.find()) {
			list.add(matcher.group(1));
		}
		return list;
	}

	@Test
	@DisplayName("HTML 正文里 <pre> 含空行时，代码块不被截断、不被当成标题")
	void htmlPreWithBlankLinesSurvives() {
		String html = "<h2>一、先体检</h2>\n"
				+ "<p>四条命令就够：</p>\n"
				+ "<pre><code class=\"language-bash\"># 1. SSH 生效配置\n"
				+ "sshd -T | grep permitrootlogin\n"
				+ "\n"
				+ "# 2. 安全组件状态\n"
				+ "systemctl is-active fail2ban\n"
				+ "\n"
				+ "# 3. 防火墙\n"
				+ "ufw status</code></pre>\n"
				+ "<p>结果如下。</p>\n";

		String out = MarkdownUtils.markdownToHtmlExtensions(html);

		// 保护块以外的 HTML 必须原样留下（第一版实现把这段吞掉了，下面几条就是当时漏掉的）
		assertThat(out).contains("<h2>一、先体检</h2>");
		assertThat(out).contains("<p>四条命令就够：</p>");
		assertThat(out).contains("<p>结果如下。</p>");

		assertThat(out).doesNotContain("<h1");
		assertThat(out).doesNotContain("<h2>2.");
		assertThat(out).contains("# 2. 安全组件状态");
		assertThat(out).contains("# 3. 防火墙");

		List<String> pres = preBlocks(out);
		assertThat(pres).hasSize(1);
		// 代码块内部不能出现任何块级标签
		assertThat(pres.get(0)).doesNotContain("<p>").doesNotContain("<h1").doesNotContain("<h2");
		assertThat(pres.get(0)).contains("ufw status");
	}

	@Test
	@DisplayName("纯 Markdown 内容照旧转换，不受 HTML 保护逻辑影响")
	void plainMarkdownStillConverts() {
		String md = "## 小标题\n\n这是**加粗**和 `code`。\n\n```bash\nls -la\n```\n";

		String out = MarkdownUtils.markdownToHtmlExtensions(md);

		assertThat(out).contains("<h2");
		assertThat(out).contains("<strong>加粗</strong>");
		assertThat(out).contains("<code>code</code>");
		assertThat(out).contains("<pre><code class=\"language-bash\">");
	}

	@Test
	@DisplayName("Markdown 里嵌一段 HTML 代码块，块外的 Markdown 仍然生效")
	void mixedContentConvertsBothSides() {
		String content = "## 标题\n\n"
				+ "<pre><code>line1\n\nline2</code></pre>\n\n"
				+ "结尾段落 **加粗**。\n";

		String out = MarkdownUtils.markdownToHtmlExtensions(content);

		assertThat(out).contains("<h2");
		assertThat(out).contains("<strong>加粗</strong>");
		assertThat(preBlocks(out)).hasSize(1);
		assertThat(preBlocks(out).get(0)).contains("line1\n\nline2");
	}

	@Test
	@DisplayName("转换过的 HTML 再转一次，结构不退化（幂等）")
	void convertingTwiceKeepsStructure() {
		String html = "<h2>标题</h2>\n<pre><code>a\n\n# b</code></pre>\n";

		String once = MarkdownUtils.markdownToHtmlExtensions(html);
		String twice = MarkdownUtils.markdownToHtmlExtensions(once);

		assertThat(preBlocks(twice)).hasSize(1);
		assertThat(preBlocks(twice).get(0)).isEqualTo(preBlocks(once).get(0));
		assertThat(twice).doesNotContain("<h1");
	}

	@Test
	@DisplayName("嵌套的同名标签按深度配对，不在第一个闭合处提前结束")
	void nestedTagsAreMatchedByDepth() {
		String html = "<div class=\"wrap\">\n<div>内层</div>\n\n文本 # 不是标题\n</div>\n";

		String out = MarkdownUtils.markdownToHtmlExtensions(html);

		// 整段被当成一个 HTML 块原样放回，里面的 "# 不是标题" 不应变成标题
		assertThat(out).doesNotContain("<h1");
		assertThat(out).contains("<div class=\"wrap\">");
		assertThat(out).contains("</div>");
	}

	@Test
	@DisplayName("空内容与 null 不抛异常")
	void emptyInput() {
		assertThat(MarkdownUtils.markdownToHtmlExtensions("")).isEmpty();
		assertThat(MarkdownUtils.markdownToHtmlExtensions(null)).isEmpty();
	}
}
