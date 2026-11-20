# 任务卡：开源项目商业观察

> 配套 `PRD-OSS-INSIGHTS.md`（v1.2）、`DATA-MODEL-OSS.md`（v2）、`ADR-0004-OPEN-SOURCE-MODEL.md`。
> 治理规则沿用 `ED-05-TASKS.md` §1。编号沿用规划文档的 `OS-DEV-xxx`，便于对齐。

---

## 〇、开工前必须确认

| # | 事项 | 阻塞 |
| --- | --- | --- |
| 1 | ~~导航方案~~ **已定：加第 7 项，sort=25** | 无 |
| 2 | `verdict` 五档中文文案 | OS-DEV-202 渲染 |
| 3 | 是否把 `blog/11` 从「文章」迁「运维实录」 | 无阻塞，可后置 |
| 4 | 首页列表按文章流还是项目列表（`UI` §7） | OS-DEV-108 |
| 5 | 侧栏热门排序依据（建议：研究密度） | OS-DEV-108 |
| 6 | 专题区是否首期建 1 个真实专题 | OS-DEV-201 |

---

## Phase 0 · 审计与设计冻结（✅ 已完成）

| 编号 | 任务 | 交付物 | 状态 |
| --- | --- | --- | --- |
| OS-DEV-001 | 审计现有博客数据模型 | `CP-WEB-OS-01` §3.1 | ✅ |
| OS-DEV-002 | 审计分类标签复用能力 | 同上 | ✅ |
| OS-DEV-003 | 审计 CMS 编辑器 | §3.4（已有 Markdown + 贴图） | ✅ |
| OS-DEV-004 | 审计 Nuxt 页面与 SEO | §3.5、§3.7（sitemap 缺 portfolio） | ✅ |
| OS-DEV-005 | 确定数据模型 | `ADR-0004` + `DATA-MODEL-OSS` v2 | ✅ 3 张表 |
| OS-DEV-006 | 确定 API 契约 | `DATA-MODEL-OSS` §5 | ✅ |
| OS-DEV-007 | 确定 URL 与 SEO 规范 | `PRD` §4.0、§6 | ✅ |

**关键结论**：不改任何现有表；新建 `oss_project` / `oss_project_article` / `oss_project_tag`；文章唯一 URL 为 `/blog/{slug}`。

---

## Phase 1 · 项目档案（P0）

### OS-DEV-101 ✅ · V28 迁移

| 项 | 内容 |
| --- | --- |
| **目标** | 三张表 + 三个栏目分类就绪 |
| **方式** | 写 `V28__open_source_insights.sql`，**不手工执行 DDL** |
| **前置** | 无（导航已定：加第 7 项，sort=25） |

**验收**

- [ ] 本地启动日志 `Migrating to version 28`
- [ ] `show tables like 'oss_%'` → 3 张
- [ ] `desc oss_project` → 23 列，`publish_status` 默认 `draft`
- [ ] `select category_name from category` 含 3 个新栏目
- [ ] `nav_menu` 新增 1 行 `/open-source`，`sort=25`，其余各项 sort 未变
- [ ] 重复启动幂等，不报错

### OS-DEV-102 ✅ · 项目 CRUD

| 项 | 内容 |
| --- | --- |
| **改动** | 新建 `modules/opensource/`（entity/mapper/service/controller/dto/vo/enums） |
| **不动** | `blog` / `portfolio` / `project` 模块 |

**验收**

- [ ] `GET /api/open-source/projects` 只返回 `published`
- [ ] `GET /api/open-source/projects?category=data-search` 过滤正确
- [ ] `GET /api/open-source/projects/{draft-slug}` → 404
- [ ] `slug` 重复返回明确错误（不是 500）
- [ ] `description` 返回 `contentHtml`（Markdown 已转换）
- [ ] 后台 `POST/PUT /admin/oss/project` 可写全部字段

### OS-DEV-103/104 ✅ · 领域与标签

**验收**

- [ ] `GET /api/open-source/categories` 返回 6 个领域 + 项目数（0 也要返回）
- [ ] `GET /api/open-source/tags` 只统计 `published` 项目的标签
- [ ] 后台可全量覆盖某项目的标签（`PUT /admin/oss/project/{id}/tags`）
- [ ] 标签复用现有 `tag` 表，标签页与文章标签通用

### OS-DEV-105 ✅ · 项目与文章关联

**验收**

- [ ] 后台可勾选文章、设 `relationType`、调顺序
- [ ] 同一项目重复关联同一文章 → 冲突失败（主键约束生效）
- [ ] 文章未发布时不出现在 `projects/{slug}/articles`
- [ ] `GET /api/open-source/blog/{blogId}/project` 可反查

### OS-DEV-106 ✅ · 双状态机

**验收**

- [ ] `research_status` 与 `publish_status` 可独立变更
- [ ] `POST /admin/oss/project/{id}/publish` 后前台立即可见
- [ ] `POST /admin/oss/project/{id}/archive` 后档案 404，文章仍可读
- [ ] 归档不删除任何关联记录

### OS-DEV-107 ✅ · 后台页面

| 项 | 内容 |
| --- | --- |
| **新建** | `views/oss/ProjectList.vue` + 路由 `/oss/projects` |
| **复用** | `PortfolioList.vue` 的 Markdown 编辑器 + Ctrl+V 贴图（不改原文件，复制后裁剪） |

**验收**

- [ ] 列表可按 `publishStatus` / `researchStatus` / `category` / 关键词筛选
- [ ] 编辑表单含基本情况、评估卡七项、标签、关联文章
- [ ] 评估卡七项可逐项填 `level + note + evidence`，未填存 `null`
- [ ] 保存后前台刷新即生效（本期不加缓存）
- [ ] 菜单出现在「内容管理」分组下

### OS-DEV-108 ✅ · 文章流与精选接口

> 视觉稿评审后新增：首页卡片是文章，不是项目。

| 项 | 内容 |
| --- | --- |
| **目标** | 首页「最新项目分析」与「精选推荐」有对应接口 |
| **新增** | `GET /open-source/articles`、`GET /open-source/featured`、`projects?sort=density` |

**验收**

- [ ] `/open-source/articles` 只返回「项目 published + 文章 is_published」的组合
- [ ] 每条含 `relationType` 与所属项目（slug / name / verdict）
- [ ] 支持 `pageNum` / `category` / `tag` / `q`
- [ ] `/open-source/featured` 优先返回 `featured` 项目的 business 文章，无则退回项目
- [ ] `projects?sort=density` 按站内分析篇数排序，star 不参与排序
- [ ] 关联文章被下架后，文章流中立即消失

---

## Phase 2 · 前台（P0）

### OS-DEV-201 ✅ · 栏目首页

**验收**

- [ ] SSR 输出的 HTML 含项目名（`curl` 直接可见）
- [ ] 精选推荐取 `featured` 项目的 business 文章，卡片可点进文章
- [ ] 「最新项目分析」取文章流（`/open-source/articles`），卡片显示类型/日期/阅读时长
- [ ] 领域入口 5 个 + 全部；侧栏热门按研究密度排序
- [ ] 三个区块空数据时显示占位文案
- [ ] 375px 单列不溢出
- [ ] 中英文切换正常（两份 i18n 都加了 key）

### OS-DEV-202 ✅ · 项目档案页

**验收**

- [ ] 用 `contentHtml` 渲染正文（不是 Markdown 原文）
- [ ] 评估卡七项 + 证据标记 `F/S/T/M/A/?` 正确
- [ ] `level=null` 显示「待验证」灰标
- [ ] `assessmentJson` 解析失败 → 显示「评估数据异常」，其余区块正常
- [ ] 结论档中文映射正确
- [ ] 页脚固定声明「结论基于特定版本与环境，不构成交付承诺」
- [ ] `last_verified_at` 展示为「资料核验于 YYYY-MM-DD」

### OS-DEV-203 ✅ · 文章详情增量

**验收**

- [ ] 底部出现「所属项目」卡（名称 + 档位 + 链接）
- [ ] 未关联时不显示、不留空白
- [ ] 不增加首屏请求阻塞（与文章详情并发或延后）

### OS-DEV-204/205 ✅ · 分类与标签页

**验收**

- [ ] `/open-source/category/data-search` 可直接访问、可被收录
- [ ] 空领域显示「该领域暂无已发布项目」
- [ ] 标签页复用现有 tag 数据，计数只算 published 项目

### OS-DEV-209 ✅ · 全部项目列表页

> 视觉稿评审补充：首页「查看更多 →」需要落点。

**验收**

- [ ] `/open-source/projects` 分页展示全部 published 项目
- [ ] 支持领域与标签筛选，筛选条件反映在 URL 上（可分享、可收录）
- [ ] 分页有 `rel=prev/next`，超过 10 页可跳页
- [ ] 无数据时显示空态而非空白页

### OS-DEV-206 ✅ · 栏目搜索

**验收**

- [ ] `/open-source/search?q=crawl` 返回 `{projects, articles}`
- [ ] 无结果给明确提示，不白屏

### OS-DEV-207/208 ✅ · 内链与移动端

**验收**

- [ ] 档案 ↔ 文章双向可回
- [ ] 同标签相关项目 ≤ 3 条
- [ ] 375 / 768 / 1440 三档无溢出、无遮挡

---

## Phase 3 · SEO（P0）

| 编号 | 任务 | 验收 |
| --- | --- | --- |
| OS-DEV-301 | 元信息 | 每个项目/列表页有独立 title + description |
| OS-DEV-302 | Canonical | 全部指向自身，无跨页 canonical |
| OS-DEV-303 | Sitemap | 含 `/open-source`、领域页、published 项目页；draft 不含 |
| OS-DEV-304 | OG | 分享卡片标题/描述/封面正确 |
| OS-DEV-305 | 结构化数据 | 项目页 `TechArticle` + `BreadcrumbList`，Rich Results 无报错 |
| OS-DEV-306 | 面包屑 | 首页 / 开源观察 / 项目名 |
| OS-DEV-307 | 内链 | 每篇商业分析 ≥ 2 条技术文章链接 |
| OS-DEV-308 | 访问统计 | 项目页、列表页、搜索各埋一个事件 |

> 审计发现：现有 sitemap **从未收录 portfolio 详情**。本次一并补，顺手解决旧问题。

**不做**：`Review` / `AggregateRating` 结构化数据。

---

## Phase 4 · 运营（P1）

| 编号 | 任务 | 说明 |
| --- | --- | --- |
| OS-DEV-401/402 | 栏目与文章访问统计 | 复用现有 `visit_log` |
| OS-DEV-403 | 搜索词统计 | 记录 `q`，用于选题 |
| OS-DEV-404 | 来源追踪 | UTM 参数落库 |
| OS-DEV-405/406 | 知识星球 / 技术服务入口 | 文章底部转化位，与主题相关 |
| OS-DEV-407 | 分享 | 复用现有分享能力 |
| OS-DEV-408 | 订阅入口 | 复用现有 |

---

## Phase 5 · AI 辅助（P2，不做）

`OS-DEV-501~506`（资料采集、GitHub 同步、大纲生成、分析草稿、证据检查、更新提醒）全部延后。

理由：MVP 阶段内容是手工的，先验证「有没有人看」，再谈自动化。
硬约束：AI 只能辅助，**商业判断、许可证解释、市场规模、客户需求必须人工核验**。

---

## 首批内容计划

### 能做的（有实测）

| 项目 | 可写文章 | 证据位置 |
| --- | --- | --- |
| **Crawl4AI** | ① 商业价值分析 ② Markdown 质量边界 ③ 深度采集边界 ④ Docker 部署 | 研究仓 `docs/experiments/crawl4ai/` + `commercial-value-round1.md` |

档案评估卡可一次填满三项实测：技术成熟度 `T`、部署成本 `T`、Insight 适配度 `T`，其余标 `?`。

### 不能做的（零实测）

规划文档首批列了 Scrapy、Swirl Search、Milvus、Hermes Agent。**手上全部零实测。**

按 `ARTICLE-STANDARD.md`，只有三条路：

1. 不写（最诚实）
2. 写成「选题预告」，明说研究中、无实测
3. 先补研究，每个项目 2–3 天

**建议**：首批只发 Crawl4AI。一个做透的档案 + 4 篇文章，比 5 个半成品档案更能建立可信度。

### 与栏目映射

| 栏目 | 篇目 |
| --- | --- |
| 开源项目商业观察 | 商业价值分析（新写） |
| 开源技术研究 | 02 架构 · 03 AsyncWebCrawler · 04 配置 · 05 结果 · 06 Markdown · 07 提取 · 08 深爬 · 09 缓存 |
| 开源技术实践 | 10 Docker · 11 可视化封装 · 12 接入 Insight |

---

## 排期（4 周）

| 周 | 内容 | 交付 |
| --- | --- | --- |
| 第 1 周 | 决策确认 + OS-DEV-101/102 | V28 跑通、接口可用 |
| 第 2 周 | 103–107 | 后台能录数据、能关联文章 |
| 第 3 周 | 201–208 | 前台页面可浏览 |
| 第 4 周 | 301–308 + 首批内容 | SEO 就绪、上线验收 |

**上线最小集**：101 + 102 + 201 + 202 + 303 + 首批内容。
后台（107）可第二周补——第一期用 SQL 录数据也能跑，但长期必须有，否则运营成本会把人劝退。

---

## 落地记录（2026-11-20）

本期一次做完 Phase 1 + Phase 2 的前台与后台，验收项逐条对照如下。

| 项 | 结果 | 说明 |
| --- | --- | --- |
| V28 迁移 | ✅ | 3 张表、23 列、3 个分类、导航 sort=25；本地实测 `now at version v28` |
| 公开接口 | ✅ | 9 个，全部只返回 published；不存在的 slug 返回 `code=404` |
| 后台接口 | ✅ | 6 个；标签与关联文章随保存全量覆盖，未单开两个接口 |
| 栏目首页 | ✅ | 首屏/领域/精选/研究流/研究方法；无数据区块显示「本期暂无，正在研究」 |
| 项目档案 | ✅ | 评估卡七维 + 证据等级 + 关联分析 + 相关项目；评估 JSON 解析失败只影响评估卡 |
| 列表/领域/标签/搜索 | ✅ | 均可直接访问；空领域显示空态 |
| 文章详情 | ✅ | 底部「所属项目」卡，未关联不渲染 |
| sitemap | ✅ | 栏目、项目页、有内容的领域页；draft 不进 |
| 后台页面 | ✅ | 内容管理 → 开源观察；评估卡逐项填、标签多选、关联文章逐行配置 |

**实现中与文档的三处偏离（有意）**

1. `PUT /admin/oss/project/{id}/tags` 与 `/articles` 两个接口没有单独实现。
   后台是整表保存，多两个接口会出现「正文存了、标签没存」的中间态；
   改为保存接口内全量覆盖，语义只有一个。
2. 评估卡不在后端解析。后端把 `assessmentJson` 原样透传，页面 try/catch 解析，
   失败时把「评估卡」区域显示成数据异常。少一次可能改变结构的往返。
3. 栏目首页的「最新项目研究」在没有关联文章时退回展示项目卡（带「待研究」角标），
   与视觉稿的状态一致；有文章后自动切换成文章流。

**部署时必须做的一步**

导航菜单走 Redis 缓存，Flyway 插的菜单不会自动生效，部署后需要：

```bash
redis-cli DEL '"navMenuList:zh-CN"' '"navMenuList:en"'
```

键名外的双引号属于 key 序列化结果，删的时候不能省，否则 DEL 返回 0。

## 发布记录（v1.1.0，2026-11-20）

| 项 | 结果 |
| --- | --- |
| 代码 | `021ab69` 推送 GitHub；tag `v1.1.0` |
| CI | 约 5 分钟出齐 4 个镜像（server / admin / web / gateway） |
| 数据库 | api 启动时自动迁移：`Successfully applied 1 migration ... now at version v28`（0.109s） |
| 备份 | 部署前 `mysqldump` 全库 8.3MB 至 `/opt/backup/`，`.env` 备份为 `.env.bak-v1.0.4-*` |
| 导航缓存 | 已执行 `DEL '"navMenuList:zh-CN"' '"navMenuList:en"'`，返回 2；导航第 7 项生效 |
| 生产档案 | 录入 Crawl4AI（`oss_project.id=1`），关联分析文章 `blog_id=11`（调研关系） |
| 线上验证 | `/open-source`、`/open-source/projects`、`/open-source/projects/crawl4ai`、领域页、标签页、文章页全部 200；文章页「所属项目」卡渲染正常 |
| sitemap | 已含 `/open-source`、`/open-source/projects`、`/open-source/projects/crawl4ai`、`/open-source/category/data-search` |

容器状态：api / web / admin / gateway 全部 healthy，mysql（13 天）与 redis 未重启，数据卷未动。

**回滚**：`.env` 改回 `TAG=v1.0.4` 后 `pull` + `up -d`；数据库已由 Flyway 迁移到 V28，
回滚应用不会自动回滚表结构，V28 建的三张表保持存在但不被旧代码使用（无破坏性影响）。

## 内容上线记录（2026-11-21，v1.2.1）

栏目上线后一直没有真实项目分析——线上唯一关联的是一篇《SSH 加固实录》，属运维实践，
不是 Crawl4AI 分析。本次把研究仓里已完成的 5 篇稿件发布进栏目：

| 文章 | 分类 | 关系 | 说明 |
| --- | --- | --- | --- |
| Crawl4AI 商业价值分析 | 开源项目商业观察 | business | 唯一推荐位；区分「已核验」与「原稿观点」 |
| 从跑起来到能改造：研究路线 | 开源技术研究 | research | 8 阶段 / 30 项验收 |
| Crawl4AI 操作手册（v0.9.5 实测） | 开源技术实践 | practice | 逐条标注【实测】/【文档】 |
| Crawl4AI + Hermes Agent：采集与执行的组合 | 开源项目商业观察 | business | 能力边界 + 五个商业方向 |
| 用 24 篇文章研究透 Crawl4AI 与 Hermes | 开源技术研究 | research | 24 篇路线总纲 |

**处理细节**

- 正文以 **Markdown 原文**入库（`blog.content`），后端用 commonmark 渲染（含 GFM table 扩展），
  不做 HTML 预转换，后续在后台编辑不会变成一坨标签
- 剥掉正文首个 H1：标题由页面单独渲染，留着会重复
- 「SSH 加固实录」与 Crawl4AI 的关联解除（文章本身保留在博客，照常可读）
- 文章流按 `create_time` 倒序，因此按系列顺序错开发布时间，旗舰篇最新

**发布时踩到的两个缓存/接口问题**

1. 文章列表走 Redis 分页缓存（`homeBlogInfoList` / `newBlogList` / `archiveBlogMap`），
   直接改库后前台看不到，必须 DEL 这三个键
2. sitemap 只取 `/blogs?pageNum=1`，而后端每页固定 5 条且忽略 `pageSize`，
   文章超过 5 篇后老文章会静默掉出站点地图 —— 已改为按 `totalPage` 翻页取全量（v1.2.1）
