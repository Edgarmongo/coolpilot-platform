# 任务卡：开源项目商业观察

> 配套 `PRD-OSS-INSIGHTS.md` 与 `DATA-MODEL-OSS.md`。
> 治理规则沿用 `ED-05-TASKS.md` §1：不得 `DROP TABLE` / 无 `WHERE` 的 `DELETE` / `UPDATE`；破坏性变更须先出迁移与回滚脚本。

---

## 一、依赖与顺序

```
OSS-01 数据模型
   │
   ├──→ OSS-02 后端接口 ──→ OSS-03 后台管理
   │            │
   │            └──→ OSS-04 栏目首页 ──→ OSS-05 档案详情
   │                        │
   │                        └──→ OSS-06 文章页关联
   │
   └──→ OSS-08 内容生产（可与开发并行，但上线前必须完成）

OSS-07 SEO  ← 依赖 OSS-04 / OSS-05
OSS-09 上线验收 ← 全部
```

**可并行的两条线**：开发线（01→06）与内容线（08）。内容线不需要等代码——文章可以先在本地写完，等页面就绪再发布。

---

## OSS-01 · 数据模型迁移

| 项 | 内容 |
| --- | --- |
| **目标** | `portfolio` 支持双类型与评估卡，新增文章关联表，新增 4 个栏目分类 |
| **前置** | 本文件评审通过 |
| **范围** | 生产库 `coolpilot`；同步补一个 Flyway migration 文件 |
| **改动** | `DATA-MODEL-OSS.md` §3 的 3 组语句 |
| **不做** | 不改现有列类型、不删列、不动 `project` 表 |

**步骤**

```
1. 备份 portfolio / category
2. ALTER TABLE portfolio ADD COLUMN ×7 + idx_portfolio_kind
3. CREATE TABLE portfolio_article
4. INSERT category ×4
5. 校验（见下）
6. 补 Flyway migration 文件并提交
```

**验收**

- [ ] `desc portfolio` 显示 7 个新列，`kind` 默认值为 `own`
- [ ] `portfolio` 现有 1 条记录 `kind='own'`
- [ ] `portfolio_article` 存在且为空
- [ ] `category` 共 9 行，新增 4 行 id 未与现有冲突
- [ ] Flyway 启动无校验失败

**回滚**：`DATA-MODEL-OSS.md` §3 各变更下的回滚语句，逆序执行。

**风险**：Flyway 与手工 DDL 不一致会导致下次启动报 checksum 错。**必须先补 migration 再重启后端。**

---

## OSS-02 · 后端接口

| 项 | 内容 |
| --- | --- |
| **目标** | 前台能按 kind 取档案、取档案详情（含文章）、文章反查项目；后台能带新字段读写 |
| **前置** | OSS-01 |
| **改动** | `Portfolio.java`、`PortfolioSaveDTO.java`、新建 `PortfolioDetailVO`、`PortfolioMapper`、`PortfolioService`、`PortfolioController`、`PortfolioAdminController` |
| **不动** | `project` 模块（服务订单） |

**验收**

```
GET /api/portfolios                  → 返回全部（含 kind 字段），行为与改前一致
GET /api/portfolios?kind=oss         → 只返回 oss
GET /api/portfolios?kind=own         → 只返回 own
GET /api/portfolio/{oss-slug}        → 含 assessment（字符串）+ articles[]
GET /api/portfolio/{own-slug}        → 含 articles[]（空数组）
GET /api/portfolio/by-blog/{blogId}  → 命中返回项目，未关联返回 data=null
POST /api/admin/portfolio            → 可写入 kind/license/verdict/assessmentJson
```

- [ ] `kind` 非法值（如 `foo`）返回 400 而非入库
- [ ] `assessment_json` 非法 JSON 在保存时被拒绝（后端做一次 `JSON.parse` 校验）
- [ ] 匿名访问上述前台接口均为 200
- [ ] 后台接口无 token 为 401/403

**风险**：`assessment_json` 存坏数据会让前端整卡失败。**后端保存时校验，前端解析再加 try/catch，两道防线。**

---

## OSS-03 · 后台管理

| 项 | 内容 |
| --- | --- |
| **目标** | 运营可在后台维护开源项目档案与评估卡，无需碰数据库 |
| **前置** | OSS-02 |
| **改动** | `coolpilot-admin/src/views/page/PortfolioList.vue`（现有）、新增/编辑表单组件 |
| **复用** | 现有路由 `/portfolio`、现有列表分页 |

**验收**

- [ ] 列表可按 `kind` 筛选，列显示「类型 / 名称 / 结论档 / 星数 / 更新时间」
- [ ] 新建时可选 `kind`，选 `oss` 时显示 license / language / stars / verdict / 评估卡
- [ ] 评估卡七项可逐项填 level + note + evidence；未填保存为 `null`
- [ ] 可在一个页面内勾选关联文章并排序（走 `PUT /admin/portfolio/{id}/articles`）
- [ ] 保存后前台刷新即生效（无缓存问题）

**不做**：不做批量导入、不做 GitHub API 自动填充（第二期评估）。

---

## OSS-04 · 栏目首页

| 项 | 内容 |
| --- | --- |
| **目标** | `/open-source` 可访问，呈现精选档案、最新分析、进行中专题 |
| **前置** | OSS-02 |
| **新建** | `coolpilot-web/pages/open-source/index.vue` |
| **复用** | `components/` 里现有卡片样式、`useApi`、`useFormat` |

**验收**

- [ ] SSR 输出 HTML 中含档案名称（`curl` 直接可见，非客户端渲染）
- [ ] 精选档案 ≤ 6 条，显示 name / verdict / license / techStack / 篇数
- [ ] 最新分析取 `categoryId` 对应文章，显示标题 + 栏目 + 阅读时长
- [ ] 专题区取 `/topics`，显示标题 + 篇数 + 更新时间
- [ ] 三个区块各自空数据时显示占位文案，页面不报错
- [ ] 375px 宽度下卡片单列不溢出
- [ ] `kind=oss` 无数据时不显示空白卡片框

---

## OSS-05 · 项目档案详情

| 项 | 内容 |
| --- | --- |
| **目标** | `/open-source/{slug}` 展示评估卡、结论、正文、关联文章、相关项目 |
| **前置** | OSS-02 |
| **新建** | `coolpilot-web/pages/open-source/[slug].vue` |
| **复用** | `pages/projects/[slug].vue` 的正文渲染逻辑（抽成共享组件，两处引用） |

**验收**

- [ ] 评估卡七项按 `assessment_json` 渲染；`level` 为 null 显示「待验证」灰标
- [ ] 每项显示证据标记 `[V]/[S]/[D]/[?]`
- [ ] 结论档位映射为中文：`watch→值得持续关注`、`deploy→值得部署使用`、`extend→值得二次开发`、`commercial→值得商业化探索`、`hold→暂不建议投入`
- [ ] `assessment_json` 解析失败时显示「评估数据异常」，其余区块正常
- [ ] 关联文章列表可点击，跳转正确
- [ ] 正文为空时不渲染空 `<article>`
- [ ] 档案页固定声明：「结论基于特定版本与环境，不构成交付承诺」
- [ ] `/projects` 仍只显示 `kind=own`（回归检查）

---

## OSS-06 · 文章页关联

| 项 | 内容 |
| --- | --- |
| **目标** | 文章底部显示所属项目卡，形成双向内链 |
| **前置** | OSS-02、OSS-05 |
| **改动** | `coolpilot-web/pages/blog/[id].vue` |

**验收**

- [ ] 文章关联了项目时，底部出现项目卡（名称 + 结论档 + 链接）
- [ ] 未关联时不显示，不留空白
- [ ] 反向：档案页 → 文章 → 档案，一圈可回
- [ ] 不增加首屏请求数（与文章详情并发或延后加载）

---

## OSS-07 · SEO

| 项 | 内容 |
| --- | --- |
| **目标** | 新页面可被收录，结构化数据正确 |
| **前置** | OSS-04、OSS-05 |
| **改动** | `server/routes/sitemap.xml.ts`、两个新页面的 `useHead` |

**验收**

- [ ] sitemap 含 `/open-source` 及所有 `kind=oss && enabled` 的档案 URL
- [ ] sitemap 中 own 类档案指向 `/projects/{slug}`，oss 类指向 `/open-source/{slug}`
- [ ] 每个新页面有独立 `title` / `description` / `canonical`
- [ ] 档案页输出 `TechArticle` + `BreadcrumbList`，用 Google Rich Results 测试无误
- [ ] `robots.txt` 未误屏蔽 `/open-source`

**不做**：不加 `Review` / `AggregateRating`（无真实用户评分，属滥用）。

---

## OSS-08 · 首批内容

| 项 | 内容 |
| --- | --- |
| **目标** | 上线时栏目至少有 1 个完整档案 + 3 篇文章 |
| **前置** | 无（可与开发并行） |
| **产出** | 项目档案 1–2 条 + 商业分析 1 篇 + 技术文章 2 篇 |

见下方「首批内容计划」。

**验收**

- [ ] 档案评估卡七项无空缺（未评项显式标 `?`）
- [ ] 每篇文章能追到研究仓的 `docs/experiments/`
- [ ] 通过 `ARTICLE-REVIEW-CHECKLIST.md` 三条红线

---

## OSS-09 · 上线验收

- [ ] 匿名访问 `/open-source`、`/open-source/{slug}` 均 200
- [ ] sitemap 中随机抽 3 条 URL 均 200
- [ ] 移动端 375 / 768 / 1440 三档检查
- [ ] 后台建一条测试档案 → 前台可见 → 删除测试档案 → 前台消失
- [ ] `/projects` 作品集无 oss 混入
- [ ] 首页导航出现「开源商业观察」入口（导航配置在 `nav_menu` 表）
- [ ] 记录改动文件清单与验证结果（ED-05 §1 第 6 条）

---

## 首批内容计划

### 能立刻做的（有实测证据）

| 项目 | 状态 | 可写的文章 | 证据位置 |
| --- | --- | --- | --- |
| **Crawl4AI** | 已实测 | ① 商业价值分析 ② Markdown 质量边界 ③ 深度采集边界 ④ Docker 部署 | 研究仓 `docs/experiments/crawl4ai/` + `commercial-value-round1.md` |
| **Hermes Agent** | 仅调研 | ① 项目定位与插件生态（**只能写调研，不能写实测**） | 插件页核实记录 |

Crawl4AI 的档案可以一次做完整：技术成熟度 `[V]`、部署成本 `[V]`、Insight 适配度 `[V]`，其余标 `?`。

### 不能立刻做的（必须说明）

原计划里的 Scrapy、Swirl Search、MCP 生态、Milvus —— **手上零实测**。

按 `ARTICLE-STANDARD.md`，这些只能写成三种东西：

1. **不写**（最诚实）
2. 写成「选题预告」，明说「研究中，尚无实测」
3. 先做研究：每个项目按 10 维度补齐，至少 2–3 天

**建议**：首批只发 Crawl4AI 一个项目。一个做透的档案 + 4 篇文章，比 6 个半成品档案更能建立可信度。

### 与技术研究栏目的映射

Crawl4AI 12 篇专题在四个栏目里的归属：

| 栏目 | 篇目 |
| --- | --- |
| 开源项目商业观察 | 商业价值分析（新写，基于 round1 商业分析） |
| 开源技术研究 | 02 架构 · 03 AsyncWebCrawler · 04 配置 · 05 结果 · 06 Markdown · 07 提取 · 08 深爬 · 09 缓存 |
| AI 应用实践 | 10 Docker · 11 可视化封装 · 12 接入 Insight |
| 商业实践与案例 | 后续：CoolPilot Insight 研发实录 |

> 一篇商业分析 + 八篇技术研究 + 三篇实践 = 一个项目的完整内容组。
> 这正是「一次研究产出多篇内容」的可行形态，而不是把一份研究拆成六份重复内容。

---

## 排期建议

| 批次 | 任务 | 说明 |
| --- | --- | --- |
| 第 1 批 | OSS-01 → OSS-02 | 数据与接口，无 UI 变化，风险最低 |
| 第 2 批 | OSS-04 + OSS-05（并行） | 页面骨架，先用假数据调样式 |
| 第 3 批 | OSS-03 + OSS-06 | 后台与关联，依赖前两批 |
| 第 4 批 | OSS-07 + OSS-08 + OSS-09 | SEO 与首批内容，一起上线 |

**上线最小集**：01 + 02 + 04 + 05 + 07 + 08。后台（03）可以第二周补——第一期用 SQL 录数据也能跑，但长期必须有后台，否则运营成本会把人劝退。
