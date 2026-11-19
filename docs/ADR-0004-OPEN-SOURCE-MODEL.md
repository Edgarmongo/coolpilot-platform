# ADR-0004：开源项目商业观察的数据模型

> **状态：已采纳并落地**（v1.1.0，2026-11-20 上线）。本 ADR 是决策记录，不再随实现修改；
> 系统现状以 [FEATURE-MAP.md](FEATURE-MAP.md) 为准，落地偏差见 [TASKS-OSS-INSIGHTS.md](TASKS-OSS-INSIGHTS.md)。

- 背景：要在一个已经跑着 59 张表的站点上加「开源项目商业观察」栏目
- 关联：`CP-WEB-OS-01-现状审计与改造方案.md`、`PRD-OSS-INSIGHTS.md`、`DATA-MODEL-OSS.md`

---

## 1. 约束

| 约束 | 说明 |
|---|---|
| 不能动现有业务 | 文章、商品、订单、支付、技术服务必须零影响（ED-05 §1 第 7 条：遗留区只允许 bug 与小改动） |
| 表结构与代码同源 | Flyway `validate-on-migrate=true`，迁移只能往前走（V28 起） |
| 前后台都要能被非技术操作 | 档案与文章关联必须后台可维护，不能靠改库 |
| 作品集已存在 | `portfolio` 表 + `/projects` 页 + `/portfolios` 接口，已上线有数据 |
| 分类体系已有两套 | `category`（文章栏目）与 `tag`（技术标签）均通用 |
| 单人运营 | 不引入多作者、审核角色、工作流引擎 |

## 2. 候选方案

### 方案 A：复用 `portfolio`，加 `kind` 区分（初版提案）

| 优点 | 缺点 |
|---|---|
| 少一张表；详情页渲染可直接复用 | 需再加 **10 列**，其中 4 列是状态机 |
| 迁移量最小（ALTER 即可） | 与 `portfolio` 的 `enabled` / `status` 语义冲突（两套「是否上线」） |
| — | 现有 `/portfolios`、后台列表、sitemap **都不带 kind 参数**，混表后每处都要加过滤，漏一处就是内容串台 |
| — | 未来 `metric`（Star 历史）、`source`（资料核验）挂上去会继续污染作品集 |

### 方案 B：新建 `oss_project` + 关联表（本 ADR 采纳）

| 优点 | 缺点 |
|---|---|
| 两套状态机各自独立，互不干扰 | 详情页要新写（但形状本来就不同：要评估卡） |
| 现有 `/portfolios` 一行代码不用改，**零回归风险** | 多 3 张表 |
| 查询天然分离：栏目页只查 `oss_project` | 后台多一个菜单 |
| 未来扩展有地方放 | — |

### 方案 C：完全独立 8 张表（规划文档初稿）

`oss_project` / `oss_project_article` / `oss_category` / `oss_tag` / `oss_project_tag_rel` / `oss_project_source` / `oss_project_metric` / `oss_project_review`

| 优点 | 缺点 |
|---|---|
| 领域完全隔离 | `oss_category` / `oss_tag` 与现有 `category` / `tag` **结构几乎相同**（都是 name+slug/color），重复建模 |
| — | 6 张表里有 3 张（source / metric / review）在 MVP 阶段没有任何页面消费 |
| — | 分类与标签分裂成两套，站内搜索、标签云、相关推荐都要写两遍 |

## 3. 决策

**采纳方案 B，并按方案 C 收敛为 3 张表：**

| 表 | 用途 | 与规划文档的差异 |
|---|---|---|
| `oss_project` | 项目档案 | 采纳；`category` 用固定枚举列，不建 `oss_category` 表 |
| `oss_project_article` | 项目 ↔ 文章 | 采纳；主键改为 `(project_id, blog_id)` |
| `oss_project_tag` | 项目 ↔ 现成 `tag` | 采纳关联，**复用现有 `tag` 表**，不建 `oss_tag` |

延后（不在本期）：`oss_project_source`、`oss_project_metric`、`oss_project_review`。

### 3.1 为什么推翻初版的「复用 portfolio」

初版判断的依据是「字段重合 90%」。审计后重新数了一遍，**重合的是 6 个字段，不重合的是 10 个**：

| 已有可复用 | 新增需求 | 性质 |
|---|---|---|
| `slug` `name` `summary` `cover_url` `repo_url` | `official_url` `docs_url` `license` `language` | 资料字段 |
| `content`（Markdown） | `stars` `stars_at` `assessment_json` `verdict` | 评估字段 |
| `sort` `create_time` | `category` `featured` `last_verified_at` | 分类与运营字段 |
| `enabled` | `publish_status`（draft/published/archived） | **状态机冲突** |
| `status`（ACTIVE/WIP/ARCHIVED） | `research_status`（todo/researching/done） | **状态机冲突** |

两套状态机塞进一张表，是最容易在半年后出事的地方：后台点「下架」到底改 `enabled` 还是 `publish_status`？

### 3.2 为什么不用 `oss_category` 表

栏目内分类固定 6 个值（AI 开源项目 / AI Agent / 数据采集 / 开发者工具 / 企业软件 / 开源商业模式），
单人运营、一年内不会变。建表 + 后台管理页 + 关联查询，成本大于收益。

用 `oss_project.category` 存枚举码，值域写在常量类里，前端按 i18n 映射显示。
真到了需要运营自定义分类时，再加表并把枚举值迁过去。

### 3.3 为什么复用 `tag` 而不建 `oss_tag`

标签是**共享词汇**：`Python`、`RAG`、`Crawl4AI` 这类词对文章和项目是同一个概念。
分裂成两套会导致「标签云里 Crawl4AI 只显示文章数，不显示项目数」。

结构上现有 `tag` 只有 `id / tag_name / color`，完全够用。

### 3.4 为什么关联表主键用 `(project_id, blog_id)` 而不是三列唯一

规划文档建议 `UNIQUE(project_id, article_id, relation_type)`，允许同一篇文章以不同角色关联同一项目两次。
实际不会发生——一篇文章对一个项目只扮演一个角色。三列唯一反而会让后台能重复提交而没报错，
改成两列主键后，重复关联直接冲突失败，是更好的约束。

`relation_type` 存在关联表上（商业分析 / 技术研究 / 实践 / 对比），**不改 `blog` 表**——
`blog` 是遗留区表，18 列、被多模块共用，为栏目加列属于侵入式改动。

## 4. 后果

**变好的**

- 现有作品集、文章、商品、订单、支付：**一行不用改**
- 栏目可以独立演进（加指标表、加核验记录）而不影响别的模块
- 后台两个入口各自干净：`/page/portfolio` 管作品，新增的管开源档案

**变差的**

- 详情页要新写一套模板（不能直接套 `/projects/[slug]`）
- 多 3 张表、多一组 CRUD
- 两张「项目」表并存（`portfolio` 与 `oss_project`），新人容易混——靠命名与注释兜住

**必须守住的**

1. 文章只有**一个 URL**：`/blog/{slug}`。栏目页只做聚合，不建 `/open-source/{文章slug}`，否则同一内容两个地址，SEO 上被判重复。
2. 公开接口只读 `publish_status='published'`，草稿任何参数都取不到。
3. 关联表删除不级联删文章。

## 5. 落地范围

本期（V28）只建：

```sql
oss_project
oss_project_article
oss_project_tag
```

外加：4 条栏目分类（`category` 表）、1 条导航（待决策）。

不做：`oss_category`、`oss_tag`、`oss_project_source`、`oss_project_metric`、`oss_project_review`。

---

## 补充：与规划文档的四处偏离（均已在上文说明）

| 规划文档 | 本 ADR | 位置 |
|---|---|---|
| 8 张表 | 3 张 | §3 |
| `oss_category` 独立表 | `oss_project.category` 枚举列 | §3.2 |
| `UNIQUE(project_id, article_id, relation_type)` | `PRIMARY KEY(project_id, blog_id)` | §3.4 |
| `/open-source/{文章slug}` 文章详情 | 复用 `/blog/{slug}` | §4 |
