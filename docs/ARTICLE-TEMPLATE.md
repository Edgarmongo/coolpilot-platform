# 文章模板（SEO / GEO 固定规则）

> 适用：`coolpilot-platform` 站点上的所有技术文章
> 配套脚本：`scripts/article_lint.py`（校验）、`scripts/article_to_sql.py`（生成迁移）
> 背景与数据：见 [SEO-CONTENT-PLAN.md](SEO-CONTENT-PLAN.md)

规则不是凭感觉定的。这十二条全部来自 v1.3.0 / v1.4.0 两次实测：
9 篇改造 + 2 篇新写，以及 GSC 报 `Excluded by noindex` 那次排查。
**新文章只要照这个模板写，就不需要再回头做一遍结构改造。**

---

## 一、模板骨架（直接复制）

保存为 `content/<slug>.md`，用 `---` 包住 front matter，下面是正文：

```markdown
---
slug: hermes-agent-tutorial
title: Hermes Agent 入门：从构建 4.65GB 镜像到跑通第一个自动研究任务
description: 官方没有镜像，从构建容器到跑通第一个自动研究任务的完整五步。含主进程必须是 gateway、配置优先于环境变量这两处最容易卡住的地方。
category: 开源技术实践
tags: [Hermes Agent, Docker, AI Agent]
---

## 先说结论

第一句就是结论，不要铺垫。第二段给出三条「为什么会这样」，每条带一个具体数字或源码位置。

---

## 一、它到底是什么

| 维度 | 常见误解 | 实际情况 |
| --- | --- | --- |
| 例 | 例 | 例 |

---

## 二、具体步骤

```bash
# 命令要能直接复制执行
```

---

## 常见问题

### 第一个真实的人会输入的问题？

两到四句直答，第一句就是答案。**不要在答案里塞链接。**

### 第二个问题？

同上。

### 第三个问题？

同上。

**延伸阅读**：[目标页关键词作锚文本](/blog/slug-a)、[另一篇](/blog/slug-b)、[第三篇](/blog/slug-c)
```

---

## 二、十二条硬规则

### 结构类

| # | 规则 | 为什么 |
|---|---|---|
| 1 | **第一节必须是「## 先说结论」** | 搜索摘要与 AI 引用只取前一两句，结论埋在第三段等于没写 |
| 2 | **每个 H2 的第一句就是该节结论**，后面才展开 | 同上；爬虫按段落切块，首句权重最高 |
| 3 | **H2 控制在 5–12 个**，H3 只用于「问答」和「并列子项」 | 层级过深会让目录侧栏失去导航价值 |
| 4 | **必须有一个表格** | 表格式对比最容易被 AI 引擎整块抽走引用 |

### 内容类

| # | 规则 | 为什么 |
|---|---|---|
| 5 | **每个论断后面挂一个可验证的数**（0.29s / 4.65GB / 44,256 次 / ¥0.0014） | 「挺快的」「效果不错」没有引用价值 |
| 6 | **区分【实测】与【文档】**，没验证过的不要写成已验证 | 一次造假被读者发现，整站可信度归零 |
| 7 | **写清边界：什么情况下这个方法不适用** | 只有优点没有边界的文章，AI 引擎不敢引用 |
| 8 | **正文 ≥ 1500 字**（去空白、**不含代码块**——代码按行算，会把没内容的文章撑大） | 低于这个量基本覆盖不住长尾，也撑不起「权威」信号 |

### GEO 类（这是和普通博客最大的区别）

| # | 规则 | 为什么 |
|---|---|---|
| 9 | **必须有「## 常见问题」，3–4 组 `###` 问句 + 单段直答** | AI 引擎检索时匹配问句形式；前端会据此自动输出 `FAQPage` |
| 10 | **问句按真实搜索语句写**：「改了 sshd 配置为什么没生效？」而不是「配置生效问题说明」 | 问句越长尾，越接近用户的真实输入 |
| 11 | **FAQ 的答案里不要放链接**，链接统一放文末「延伸阅读」 | 解析 `FAQPage` 时只取 H3 后的第一个 `<p>`，链接会污染答案文本 |

### 内链类

| # | 规则 | 为什么 |
|---|---|---|
| 12 | **至少 3 条正文内链，锚文本用目标页的关键词**，不要写「点击这里」 | 内链是当前最大的免费增量；锚文本本身就是给搜索引擎的主题信号 |

---

## 三、摘要（description）单独写，不要抄首段

这是最容易被糊弄过去的一条，也是线上真实踩过的坑。

| 写法 | 结果 |
|---|---|
| ❌ 不填，让系统截正文首段 150 字 | 搜索结果里是一句话被拦腰截断（v1.3.1 之前一直是这样） |
| ❌ 抄正文第一段 | 和标题重复，白占 78 个字的展示位 |
| ✅ **70–90 字，含工具名 + 一个具体数字 + 一句价值** | 搜索结果、分享卡片、AI 引用都用它 |

判断标准：**把摘要单独拿出来读，它应该能独立说清「这篇讲了什么、为什么值得点」。**

长度按显示宽度算：中文 1、ASCII 0.5，合计 ≤ 90。

---

## 四、发布流程（五步，缺一步就会出问题）

```bash
# 1. 校验：不合规会直接列出哪一条没过
python scripts/article_lint.py content/<slug>.md

# 2. 生成迁移（自动算字数/阅读时长、处理转义、带幂等判断）
python scripts/article_to_sql.py content/<slug>.md \
  --version V31 --out coolpilot-server/src/main/resources/db/migration/V31__<slug>.sql

# 3. 本地验证：在临时库跑一遍，确认能插进去
#    （见下方「本地验证」）

# 4. 提交并发版：推 tag 即自动上线
git add . && git commit -m "feat(content): <标题>"
git tag -a v1.5.0 -m "v1.5.0 ..." && git push origin main refs/tags/v1.5.0

# 5. 上线后两件事（顺序不能反）
docker exec ... redis-cli DEL "homeBlogInfoList" "archiveBlogMap"   # 清列表缓存
./scripts/indexnow.sh https://coolskyai.com/blog/<slug>              # 推给 AI 引擎
```

**第 5 步的清缓存不能省。** 内容迁移直接写 `blog` 表，绕过了服务层的
`deleteBlogRedisCache()`，不清缓存的话首页列表与 sitemap 都还是旧条数——
V30 上线时就卡在这里，页面能打开但列表里找不到新文章。

验证 sitemap 是否更新：

```bash
curl -s https://coolskyai.com/sitemap.xml | grep -c "<loc>"
# 应为：11 个静态页 + 文章数 + 专题 + 商品 + 作品 + 开源项目/领域
```

---

## 五、本地验证（不碰生产）

```bash
# 建一个临时库，把生产数据导进去
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" coolpilot-mysql mysql -uroot \
  -e "DROP DATABASE IF EXISTS seo_test; CREATE DATABASE seo_test DEFAULT CHARACTER SET utf8mb4"
docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" coolpilot-mysql \
  mysqldump -uroot --no-create-info coolpilot blog tag blog_tag category \
  | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" coolpilot-mysql mysql -uroot seo_test

# 跑迁移，再跑一次确认幂等（不应该插出第二份）
docker exec -i ... mysql -uroot seo_test < V31__<slug>.sql
docker exec -i ... mysql -uroot seo_test < V31__<slug>.sql
```

---

## 六、常见反例

| 反例 | 为什么不行 |
|---|---|
| 开头写「最近在研究 XXX，顺便记录一下」 | 前两句是元叙述，摘要和结构数据都拿不到信息 |
| 中间章节用「接下来我们看看…」过渡 | 过渡句占掉了本该放结论的位置 |
| 代码块只贴片段、不给上下文 | AI 引擎抽走片段后无法执行，引用价值为零 |
| FAQ 写成「Q：… A：…」 | 前端按 `<h3>` + 紧随的 `<p>` 配对，这种写法解析不出来 |
| 内链堆在文末「相关文章」列表 | 锚文本离正文太远，主题信号弱；至少 2 条要在正文里 |
| 标题里没有工具名/数字 | 长尾词匹配不到，且「为什么 X 不生效」这类标题太文学 |

---

## 七、和代码侧的关系

写作侧只需遵守本文；下面这些是代码侧已经做好的，**作者不用管**：

- 摘要进 `meta description` / `og:description`（v1.3.1 修好，之前接口根本没返回这个字段）
- 「常见问题」自动转 `FAQPage` 结构化数据（`pages/blog/[id].vue`）
- 正文随 payload 下发，客户端不再二次请求（v1.3.4，否则会把自己标成 noindex）
- 正文里的 `[锚文本](/blog/slug)` 直接渲染成内链，不需要写 HTML
- 封面由 `AppCover` 按 seed 生成，`first_picture` 可以留空
