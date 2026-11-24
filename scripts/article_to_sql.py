#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把一篇 Markdown 文章转成 Flyway 迁移 SQL。

为什么要有这一步：内容写进迁移而不是后台粘贴，才能 review、回滚、复算。
手写 INSERT 有两个必踩的坑——引号转义和字数/阅读时长，脚本都替你做了。

用法：
    python scripts/article_to_sql.py content/my-article.md --version V31
    python scripts/article_to_sql.py content/*.md --version V31 --outdir <目录>

生成的 SQL 是幂等的（NOT EXISTS），重复执行不会插出第二份。
分类与标签按名字查，不写死 id —— 写死 id 在不同环境会指错。
"""
import argparse
import math
import re
import sys
from pathlib import Path

WORDS_PER_MINUTE = 350   # 与后台编辑器一致


def parse_front_matter(text):
    if not text.startswith('---'):
        raise SystemExit('缺少 front matter（文件开头要有 --- 包住的元信息）')
    end = text.find('\n---', 3)
    if end < 0:
        raise SystemExit('front matter 没有闭合的 ---')
    head, body = text[3:end], text[end + 4:]
    meta = {}
    for line in head.splitlines():
        if not line.strip() or line.strip().startswith('#'):
            continue
        key, _, value = line.partition(':')
        key, value = key.strip(), value.strip()
        if value.startswith('[') and value.endswith(']'):
            meta[key] = [v.strip().strip('"\'') for v in value[1:-1].split(',') if v.strip()]
        else:
            meta[key] = value.strip('"\'')
    return meta, body.lstrip('\n')


def q(s):
    return "'" + s.replace('\\', '\\\\').replace("'", "''") + "'"


def build(meta, body, author):
    slug = meta.get('slug') or ''
    title = meta.get('title') or ''
    desc = meta.get('description') or ''
    category = meta.get('category') or ''
    tags = meta.get('tags') or []
    if isinstance(tags, str):
        tags = [tags]
    missing = [k for k, v in (('slug', slug), ('title', title), ('description', desc), ('category', category)) if not v]
    if missing:
        raise SystemExit('front matter 缺字段：%s' % ', '.join(missing))

    words = len(re.sub(r'\s', '', re.sub(r'```[\s\S]*?```', '', body)))
    read_time = max(1, math.ceil(words / WORDS_PER_MINUTE))

    out = []
    out.append("""-- %s
--
-- 由 scripts/article_to_sql.py 生成，请勿手改结构与转义。
-- 正文是 Markdown，读取时由 MarkdownUtils 转 HTML，所以这里不需要写 <h2>/<p>。
-- 幂等：slug 已存在则跳过；标签按名字查，不存在就先建。

INSERT INTO blog (title, slug, first_picture, content, description,
                  is_published, is_recommend, is_appreciation, is_comment_enabled,
                  create_time, update_time, views, words, read_time,
                  category_id, is_top, password, user_id)
SELECT %s, %s, '', %s, %s,
       b'1', b'0', b'0', b'1',
       NOW(), NOW(), 0, %d, %d,
       (SELECT id FROM category WHERE category_name = %s), b'0', '', %d
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM blog WHERE slug = %s);
""" % (slug, q(title), q(slug), q(body), q(desc), words, read_time, q(category), author, q(slug)))

    if tags:
        out.append('-- 标签：先确保词典里有这个名字，再建立关联')
        for tag in tags:
            out.append("""INSERT INTO tag (tag_name)
SELECT %s FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM tag WHERE tag_name = %s);

INSERT INTO blog_tag (blog_id, tag_id)
SELECT b.id, t.id
FROM blog b JOIN tag t ON t.tag_name = %s
WHERE b.slug = %s
  AND NOT EXISTS (SELECT 1 FROM blog_tag bt WHERE bt.blog_id = b.id AND bt.tag_id = t.id);
""" % (q(tag), q(tag), q(tag), q(slug)))
    return '\n'.join(out), words, read_time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('files', nargs='+', help='content/*.md')
    ap.add_argument('--version', default='V31', help='迁移版本号，只用于文件命名与日志')
    ap.add_argument('--outdir', default='coolpilot-server/src/main/resources/db/migration')
    ap.add_argument('--out', help='单文件时直接指定输出路径')
    ap.add_argument('--author', type=int, default=1, help='user_id，默认 1（站长）')
    args = ap.parse_args()

    for path in args.files:
        text = Path(path).read_text(encoding='utf-8')
        meta, body = parse_front_matter(text)
        sql, words, read_time = build(meta, body, args.author)
        if args.out:
            target = Path(args.out)
        else:
            target = Path(args.outdir) / ('%s__%s.sql' % (args.version, meta['slug'].replace('-', '_')))
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(sql, encoding='utf-8', newline='\n')
        print('写出 %s（%d 字 / %d 分钟）' % (target, words, read_time))
    return 0


if __name__ == '__main__':
    sys.exit(main())
