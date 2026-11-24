#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文章合规校验：把 docs/ARTICLE-TEMPLATE.md 的规则变成可执行的检查。

用法：
    python scripts/article_lint.py content/my-article.md
    python scripts/article_lint.py content/*.md        # 批量

退出码：0 全过（可能有警告）；1 有错误。CI 里可以直接用。
"""
import re
import sys
from pathlib import Path

# 标题/摘要按「显示宽度」算：中文 1，ASCII 0.5。搜索引擎截断是按视觉宽度来的
CJK_MIN = 0x2E80


def width(text):
    return sum(1.0 if ord(ch) >= CJK_MIN else 0.5 for ch in text)


def parse_front_matter(text):
    """只认 key: value 与 tags: [a, b] 两种形态，够用且不引第三方依赖。"""
    if not text.startswith('---'):
        return {}, text
    end = text.find('\n---', 3)
    if end < 0:
        return {}, text
    head = text[3:end].strip('\n')
    body = text[end + 4:]
    meta = {}
    for line in head.splitlines():
        if not line.strip() or line.strip().startswith('#'):
            continue
        key, _, value = line.partition(':')
        key = key.strip()
        value = value.strip()
        if value.startswith('[') and value.endswith(']'):
            meta[key] = [v.strip() for v in value[1:-1].split(',') if v.strip()]
        else:
            meta[key] = value.strip('"\'')
    return meta, body


class Checker:
    def __init__(self):
        self.fails = []
        self.warns = []
        self.passes = []

    def ok(self, rule, msg):
        self.passes.append('%s：%s' % (rule, msg))

    def fail(self, rule, msg):
        self.fails.append('%s：%s' % (rule, msg))

    def warn(self, rule, msg):
        self.warns.append('%s：%s' % (rule, msg))


def check_file(path):
    c = Checker()
    text = Path(path).read_text(encoding='utf-8')
    meta, body = parse_front_matter(text)

    # ---------- front matter ----------
    for key in ('slug', 'title', 'description', 'category', 'tags'):
        if not meta.get(key):
            c.fail('front-matter', '缺少字段 `%s`' % key)
    slug = meta.get('slug', '')
    if slug and not re.fullmatch(r'[a-z0-9]+(-[a-z0-9]+)*', slug):
        c.fail('slug', '`%s` 只能是小写字母、数字、连字符' % slug)
    if isinstance(meta.get('tags'), str) and meta.get('tags'):
        c.fail('front-matter', '`tags` 要写成 [a, b] 的数组形式')

    # ---------- 标题 ----------
    title = meta.get('title', '')
    tw = width(title)
    if tw > 60:
        c.fail('标题', '显示宽度 %.0f 超过 60，搜索结果会被截断' % tw)
    elif tw > 42:
        c.warn('标题', '显示宽度 %.0f 偏长（建议 ≤ 42），移动端可能显示不全' % tw)
    if title and not re.search(r'\d', title):
        c.warn('标题', '没有数字。带具体数字的标题点击率更高，也更容易被引用')

    # ---------- 摘要 ----------
    desc = meta.get('description', '')
    if desc:
        dw = width(desc)
        if dw < 50:
            c.fail('摘要', '显示宽度 %.0f 太短（建议 60–90），没说清价值' % dw)
        elif dw > 95:
            c.fail('摘要', '显示宽度 %.0f 太长（建议 60–90），会被截断' % dw)
        elif not 60 <= dw <= 90:
            c.warn('摘要', '显示宽度 %.0f，建议落在 60–90' % dw)
        if re.search(r'[。．.！!？?]\s*$', desc) is None:
            c.warn('摘要', '结尾没有句号，分享卡片里会显得像半句话')
        if desc.strip() == title.strip():
            c.fail('摘要', '和标题完全一样，等于浪费展示位')

    # ---------- 正文规模 ----------
    plain = re.sub(r'```[\s\S]*?```', '', body)          # 代码块不计入字数
    words = len(re.sub(r'\s', '', plain))
    if words < 1000:
        c.fail('篇幅', '正文仅 %d 字（去空白、不含代码），低于 1000 基本覆盖不住长尾' % words)
    elif words < 1500:
        c.warn('篇幅', '正文 %d 字，建议 ≥ 1500' % words)
    else:
        c.ok('篇幅', '%d 字' % words)

    # ---------- 结构 ----------
    h2 = [l[3:].strip() for l in body.splitlines() if l.startswith('## ')]
    h2 = [re.sub(r'[*`]', '', x) for x in h2]
    if not h2:
        c.fail('结构', '没有任何 H2，文章没有骨架')
    else:
        if h2[0] != '先说结论':
            c.warn('结构', '第一节是「%s」，建议改成「先说结论」（结论前置是 GEO 的第一条）' % h2[0])
        if not 5 <= len(h2) <= 12:
            c.warn('结构', 'H2 有 %d 个，建议 5–12 个' % len(h2))
        else:
            c.ok('结构', '%d 个 H2' % len(h2))

    if not re.search(r'^\|.+\|\s*$', body, re.M):
        c.warn('结构', '没有表格。对比类信息用表格，最容易被 AI 引擎整块引用')
    if body.count('```') < 2:
        c.warn('结构', '没有代码块，技术文章缺可复制的命令')

    # ---------- 常见问题（FAQPage 的来源） ----------
    m = re.search(r'^## 常见问题\s*$', body, re.M)
    if not m:
        c.fail('问答', '缺少「## 常见问题」段，前端不会输出 FAQPage')
    else:
        tail = body[m.end():]
        nxt = re.search(r'^## ', tail, re.M)
        block = tail[:nxt.start()] if nxt else tail
        # 文末「延伸阅读」不算答案的一部分
        block_no_more = re.split(r'^\*\*延伸阅读\*\*', block, flags=re.M)[0]
        qs = re.findall(r'^### (.+)$', block_no_more, re.M)
        if not qs:
            c.fail('问答', '「常见问题」段里没有任何 H3 问句')
        elif not 3 <= len(qs) <= 4:
            c.warn('问答', '有 %d 组问答，建议 3–4 组' % len(qs))
        else:
            c.ok('问答', '%d 组 Q&A' % len(qs))
        for q in qs:
            if not q.rstrip().endswith(('？', '?')):
                c.warn('问答', '「%s」不是问句形式，AI 检索时匹配不到' % q[:20])
        # 每条问题后面必须紧跟一个普通段落（前端只认 H3 + 第一个 <p>）
        segments = re.split(r'^### .+$', block_no_more, flags=re.M)[1:]
        for i, seg in enumerate(segments):
            first = next((l for l in seg.splitlines() if l.strip()), '')
            if not first or first.startswith(('|', '```', '-', '*', '>')):
                c.fail('问答', '第 %d 个问题后面不是普通段落，FAQPage 会解析失败' % (i + 1))
            if '](' in '\n'.join(seg.splitlines()[1:]):
                c.fail('问答', '第 %d 个答案里含链接，会污染 FAQPage 的答案文本' % (i + 1))

    # ---------- 内链 ----------
    links = re.findall(r'\[([^\]]+)\]\((/blog/[a-z0-9-]+)\)', body)
    if len(links) < 3:
        c.fail('内链', '只有 %d 条指向站内文章，至少 3 条' % len(links))
    else:
        c.ok('内链', '%d 条' % len(links))
    for anchor, _ in links:
        if anchor.strip() in ('点击这里', '这里', '详情', '原文', '链接', '本文'):
            c.fail('内链', '锚文本「%s」没有信息量，应换成目标页关键词' % anchor)
    if '**延伸阅读**' not in body:
        c.warn('内链', '文末没有「**延伸阅读**」行，收尾缺一个内链枢纽')

    # ---------- 占位符 ----------
    for bad in ('TODO', 'XXX', '待补', '此处填写'):
        if bad in body:
            c.fail('收尾', '正文里还有占位符「%s」' % bad)

    return c, words


def main(paths):
    if not paths:
        print('用法：python scripts/article_lint.py content/<slug>.md [更多文件...]')
        return 2
    bad = 0
    skipped = 0
    for p in paths:
        # content/README.md 这类说明文件不是文章，不该被模版规则卡住
        if Path(p).name.lower() == 'readme.md':
            print('\n=== %s ===\n  跳过（说明文件，不是文章）' % p)
            skipped += 1
            continue
        c, words = check_file(p)
        print('\n=== %s ===' % p)
        for line in c.passes:
            print('  ok   %s' % line)
        for line in c.warns:
            print('  warn %s' % line)
        for line in c.fails:
            print('  FAIL %s' % line)
        if c.fails:
            bad += 1
            print('  -> 未通过（%d 个错误，%d 个警告）' % (len(c.fails), len(c.warns)))
        else:
            print('  -> 通过（%d 个警告）' % len(c.warns))
    print('\n合计：%d 个文件，%d 个未通过，%d 个跳过' % (len(paths), bad, skipped))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
