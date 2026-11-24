# content/

文章的**写作源文件**，一篇一个 `.md`，带 front matter。

```
content/
├── README.md                      ← 你正在看的这份
├── hermes-agent-tutorial.md       ← 示例：教程型（v1.4.0）
└── crawl4ai-403-anti-bot.md       ← 示例：故障排查型（v1.4.0）
```

## 这里是源文件，但不是唯一真相

线上内容存在数据库里，这些 `.md` 是**写作与生成的输入**：

- 新文章：在这里写 → 校验 → 生成迁移 → 发布（流程见 [ARTICLE-TEMPLATE](../docs/ARTICLE-TEMPLATE.md)）
- 线上改了错字：数据库变了，`content/*.md` 不会自动同步。**要有意识地同步**，
  否则下次想基于它改版时，拿到的是旧稿。

## 一篇的最小工作流

```bash
# 1. 写：照 docs/ARTICLE-TEMPLATE.md 的骨架
vim content/my-article.md

# 2. 校验：十二条规定逐条检查，不过就改
python scripts/article_lint.py content/my-article.md

# 3. 生成迁移：自动算字数/阅读时长、处理转义、带幂等判断
python scripts/article_to_sql.py content/my-article.md --version V31

# 4. 本地验证 + 发布（见模板文档第四节与第五节）
```

## front matter 字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `slug` | ✓ | 小写字母/数字/连字符，进 URL，**发布后不要改**（旧链接会 404） |
| `title` | ✓ | 显示宽度 ≤ 42 最佳，必须带数字或工具名 |
| `description` | ✓ | 70–90 字，单独写，不抄正文首段；进 meta 与分享卡片 |
| `category` | ✓ | 分类**名字**（不是 id），如 `开源技术实践`。写错会让迁移失败——这是故意的 |
| `tags` | ✓ | 标签名字数组。不存在的标签会自动创建，已有的直接复用 |

## 为什么分类和标签写名字而不是 id

id 在不同环境会指错（本地 seed 与线上自增序列不一致）。写名字，
生成的 SQL 用 `WHERE category_name = ...` 与 `JOIN tag ON tag_name = ...` 去查，
换个库跑也不会插到别的分类里。
