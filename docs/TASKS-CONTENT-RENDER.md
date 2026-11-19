# 任务卡：正文渲染不重复解析（HTML / Markdown 双格式）

> 关联：`docs/CHANGELOG.md` v1.0.4、`coolpilot-server/.../util/markdown/MarkdownUtils.java`
> 触发事件：2026-11-20 线上文章《44,256 次失败登录之后》代码块里混进大号标题

---

## 一、问题

文章详情页出现「深色代码块里套着 h1 标题、命令被包进段落」的排版错乱。

### 复现条件

1. 文章的 `content` 存的是**已渲染好的 HTML**（例如通过接口直接写入、或整篇从别处粘进编辑器）
2. 该 HTML 里存在 `<pre>`，且代码内容里有**空行**
3. 代码里出现形如 `# 1. xxx` 的行

### 现象

`<pre>` 在空行处被截断，后半段内容脱离了代码块，`# 1. xxx` 被当成一级标题渲染，
`sshd -T ...` 被包成 `<p>`，而这些标签又落在 `<pre>` 的闭合标签之前，
于是整块深色背景一直延续，视觉上就是「代码块里混着标题」。

---

## 二、根因

`BlogServiceImpl.getBlogByIdAndIsPublished()` 对 `content` **无条件**执行
`MarkdownUtils.markdownToHtmlExtensions()`。

commonmark 解析 HTML 块时按空行判断块边界（`<pre>` 这类本应延续到闭合标签，
但实现上空行会中断 HTML 块）。HTML 内容被当成 Markdown 再解析一遍，
空行就成了块边界，后面的内容自然被当成 Markdown 语法处理。

同类调用点还有 5 处：`AboutServiceImpl`、`PortfolioService`、`MomentServiceImpl`、
`FriendServiceImpl`、`BlogServiceImpl#getBlogInfoListByIsPublished`（列表摘要）。
只要正文/摘要里出现 HTML + 空行 + Markdown 特征字符，都会中招。

---

## 三、方案

**不改数据格式约定**（后台编辑器仍写 Markdown），改为让渲染**对两种格式都安全**：

1. 渲染前先把「块级 HTML 元素」整体抽出来，用 `<!--CPHTML-n-->` 占位注释替换；
2. commonmark 只解析剩下的 Markdown；
3. 渲染完把占位符换回原始 HTML。

效果：

| 内容形态 | 行为 |
| --- | --- |
| 纯 Markdown | 与改动前完全一致（没有可保护的 HTML 块） |
| 纯 HTML | 等价于原样透传，不再被二次解析 |
| 混合（Markdown 里嵌一段 HTML） | HTML 块保真，块外的 Markdown 照常转换 |

### 保护哪些标签

`pre / script / style / textarea / table / figure / blockquote / ul / ol / dl / div /
section / article / aside / header / footer / nav / main / form / details /
iframe / svg / video / audio / canvas`

不收 `p` / `h1`-`h6` / `hr`：它们是单行块，本身就作为独立 HTML 块原样输出，额外保护没有收益。

### 两个实现细节（都是踩过的坑）

1. **必须有「扫描游标」和「待输出起点」两个变量。**
   第一版合成一个 `cursor`，跳过不保护的标签时把前面的正文一起丢了，
   结果整篇文章只剩占位符和几段 `p>`。单元测试当时没覆盖到，是靠真实文章回归才发现的。
2. **占位符用 HTML 注释而不是普通文本。**
   注释本身就是 HTML 块，渲染后原样输出；用普通文本会被包进 `<p>`，
   还原时还得处理包裹标签，反而更容易出错。

---

## 四、测试

`coolpilot-server/src/test/java/com/coolskyai/util/markdown/MarkdownUtilsTest.java`（6 个用例）：

| 用例 | 锁住什么 |
| --- | --- |
| `htmlPreWithBlankLinesSurvives` | 代码块不被截断；块外 h2/p 原样保留 |
| `plainMarkdownStillConverts` | 纯 Markdown 行为不变 |
| `mixedContentConvertsBothSides` | 混排时两边都正确 |
| `convertingTwiceKeepsStructure` | 转换结果再转一次不退化（幂等） |
| `nestedTagsAreMatchedByDepth` | 嵌套同名标签按深度配对 |
| `emptyInput` | null / 空串不抛异常 |

**验证过测试是有效的**：把修复临时 stash 掉再跑，2 个失败 + 1 个 NPE，恢复后全绿。
另外用线上那篇文章的**原始 HTML**（9733 字符）单独跑过一次：23 个代码块全部干净、
11 个 h2 / 3 个 h3 / 2 张表格完整，输出与输入几乎逐字相同。

---

## 五、不做的事

- **不自动把 HTML 转成 Markdown 存库。** 转换有损（样式、特殊结构会丢），
  而且会把「作者写了什么」这件事变得不可追溯。
- **不在保存时拦截 HTML。** 编辑器允许粘贴，拦下来只会让人换个方式绕过去；
  渲染层能正确处理才是根本。
- **不动 CMS 契约。** 正文仍以 Markdown 为主，HTML 作为兼容输入。

---

## 六、验收

- [x] 单测覆盖两类内容与混排场景
- [x] 用真实故障文章回归，结构完全正确
- [x] 纯 Markdown 文章渲染结果与改动前一致
- [x] 发布 v1.0.4 后线上文章页正常
