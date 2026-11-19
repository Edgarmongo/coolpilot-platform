# CP-WEB-OS-01：开源项目商业观察 · 现状审计与改造方案

> **状态：已落地**（v1.1.0，2026-11-20 上线）。本文是审计快照，反映改造前的系统状态；
> 审计结论已用于 [ADR-0004](ADR-0004-OPEN-SOURCE-MODEL.md)，当前状态以 [FEATURE-MAP.md](FEATURE-MAP.md) 为准。

| 项 | 内容 |
| --- | --- |
| 任务 | CP-WEB-OS-01 只读审计 |
| 日期 | 2026-11-20（审计结论已用于 ADR-0004，栏目已上线） |
| 对象 | `Edgarmongo/coolpilot-platform` @ `main`（工作区 `70fbefa`） |
| 方式 | **只读**：读源码 + 读生产库结构 + 打公开接口。未改任何文件、未执行任何写操作 |
| 结论 | 可增量改造；初版 PRD 有 **8 处需修正**，见 §5 |

> **后续更新（2026-11-20）**：本报告 §6 的「复用 portfolio + kind」方案**已被 `ADR-0004` 取代**。
> 重新核对字段后确认：portfolio 可复用 6 字段、需新增 10 字段，其中 4 个是状态机且与
> `enabled` / `status` 语义冲突。最终决定改为新建 `oss_project`，**不动任何现有表**。
> §6 保留作为推理过程，实际执行以 `DATA-MODEL-OSS.md` v2 为准。

---

## 一、审计方法与可复现命令

| # | 目的 | 手段 |
| --- | --- | --- |
| 1 | 仓库基线 | `git log` / `git status` / `find db/migration` |
| 2 | 生产库基线 | `flyway_schema_history`、`information_schema.tables` |
| 3 | 表结构 | `desc` 各相关表 |
| 4 | 后端实现 | 读 `controller` / `service` / `entity` |
| 5 | 前端实现 | 读 `pages` / `views` / `router` / `i18n` |
| 6 | 接口契约 | `curl` 生产环境公开接口（不带凭据） |
| 7 | 鉴权边界 | 读 `SecurityPaths.java` |

> 未做的事：未登录后台、未执行写操作、未改动仓库文件。

---

## 二、基线核验

README 声明与实际情况**完全一致**，可直接作为基线：

| 项 | README 声明 | 实测 | 结论 |
| --- | --- | --- | --- |
| Flyway 最新版本 | V27 | 仓库 27 个文件，最大 V27；生产库 `installed_rank` 最大 27 | ✅ 一致 |
| 迁移数量 | — | 27 条，全部 `success=1` | ✅ |
| 表数量 | 59 张 | `information_schema` 计数 59 | ✅ 一致 |
| 技术栈 | Nuxt 3 / Spring Boot / MySQL 8 / Redis 7 | `nuxt ^3.21.11`；`spring.flyway.*` 配置齐全 | ✅ 一致 |
| 迁移执行方式 | 应用启动自动执行 | `flyway.enabled=true`、`validate-on-migrate=true`、`out-of-order=false` | ✅ |
| 基线版本 | — | `baseline-version=2`、`baseline-on-migrate=true` | 已记录 |

**结论：后续改造必须以 V28 为下一个迁移号，且不能跳号、不能改历史文件。**

---

## 三、审计发现

### 3.1 内容结构

| 表 | 关键列 | 现状 | 可复用性 |
| --- | --- | --- | --- |
| `blog` | `title/slug/content(Markdown)/category_id/first_picture/is_published` | 1 条（已发布） | 直接用 |
| `category` | `id/category_name` | 5 行：文章、知识库、AI工具集、AI测评、Skill资源 | 加行 |
| `tag` | `id/tag_name/color` | 0 行 | 直接用 |
| `blog_tag` | `blog_id/tag_id` | 0 行 | 直接用 |
| `topic` | `slug/title/description/cover/is_published/sort` | 0 行 | 直接用 |
| `topic_article` | `topic_id/blog_id/sort` | 0 行 | 直接用 |

**关键接口约束（初版方案漏了）**

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `GET /blogs` | **只有 `pageNum`** | **不支持按分类筛选** |
| `GET /category` | `categoryName`（中文名）+ `pageNum` | 分类页走这里，按**名称**而非 id |
| `GET /tag` | `tagName` + `pageNum` | 同上 |
| `GET /topic/{slug}` | — | 返回 `{topic, articles}` |

> ⚠️ 影响：栏目首页「最新分析」必须用 `/category?categoryName=开源项目商业观察`。
> 这意味着**分类一旦改名，栏目页会静默变空**。需在验收项里加一条：改名前检查引用。

### 3.2 `/projects` 的命名三层，必须分清

这是本次审计最重要的边界发现。同一个词在三层指三件事：

| 层 | 标识 | 实际含义 | 归属 |
| --- | --- | --- | --- |
| 前台路由 | `/projects`、`/projects/{slug}` | **作品集**（portfolio） | CP-WEB-08 |
| 前台接口 | `/portfolios`、`/portfolio/{slug}` | 作品集数据 | PortfolioController |
| 前台接口 | `/projects` | **我的技术服务项目**（订单进度，需登录） | ServiceController |
| 数据库 | `portfolio` | 作品集 | V24 |
| 数据库 | `project` | **技术服务订单**（`amount/order_no/demand_id`） | V6 |

V24 的注释已经把这件事写死了：

> 「库里已经有一张 `project` 表，是**技术服务项目进度**……所以这里叫 `portfolio`（作品集），不能复用也不能重名。」

**因此新专栏的边界是清晰的：**

| | 现有 `/projects` | 新增 `/open-source` |
| --- | --- | --- |
| 内容 | 我自己的作品 | 第三方开源项目 |
| 目的 | 展示能力 | 分析商业价值 |
| 数据 | `portfolio.kind='own'` | `portfolio.kind='oss'` |
| 表 | `portfolio` | **同一张** |

**结论：两者共用 `portfolio` 表，用 `kind` 隔离，与 V24 的命名避让原则不冲突。**

### 3.3 正文链路：Markdown → HTML（初版方案写错了）

| 环节 | 事实 |
| --- | --- |
| 库中存储 | **Markdown**（`portfolio.content` 实际值以 `## 项目背景` 开头） |
| 后端转换 | `PortfolioService:64` → `MarkdownUtils.markdownToHtmlExtensions()` |
| 接口返回 | `data: { portfolio: {...content 原样 Markdown...}, contentHtml: "<h2>..." }` |
| 前台渲染 | `v-html` 渲染 `contentHtml` |
| 同一链路已用于 | 文章（`BlogServiceImpl:405`）、商品、关于、动态、友链，**共五处** |

**所以：后台写 Markdown，不要写 HTML。** 我在 `PUBLISHING-WORKFLOW.md` 里写的「content 存 HTML」是错的，已修正。

### 3.4 CMS 现状

| 项 | 现状 | 对新功能的影响 |
| --- | --- | --- |
| 菜单机制 | `router/index.js` 路由表 → `layout/index.vue` 的 `menuList` 自动生成，`meta.hidden` 控制隐藏，`meta.bottom` 排最后 | 新增菜单 = 加一条路由，零配置 |
| 权限模型 | **只有登录校验**（`beforeEach` 看 localStorage token），**无角色/菜单级权限** | 新功能不需要配权限；同时也没有细粒度控制能力 |
| 正文编辑器 | `PortfolioList.vue` 已有 Markdown textarea + **Ctrl+V 贴图上传**（bizType=PORTFOLIO） | **不需要新建编辑器** |
| 现有 portfolio 管理页 | `/page/portfolio`，字段：名称/标识/简介/技术栈/仓库/演示/状态/显示/正文/顺序 | 扩展字段即可 |
| 前台多语言 | `i18n/locales/zh-CN.json` + `en.json`，嵌套 key（`projectsPage`、`projectDetail`、`topics`…） | 新页面需**两份**语言文件同步加 key |
| 前端组件 | 仅 3 个：`CommentSection`、`PostList`、`SocialIcon`；卡片样式内联在各页面 | 「复用现有卡片」不成立，需**抽取或复制样式** |

### 3.5 公开接口白名单机制（会踩的坑）

`SecurityPaths.PUBLIC` 已放行：

```
/portfolios, /portfolio/*, /topics, /topic/*, /blogs, /blog, /blog/*/topics, /category, /tag
```

**注意 `/portfolio/*` 是单段通配**：

| 新接口 | 是否命中现有白名单 | 结论 |
| --- | --- | --- |
| `/portfolio/crawl4ai` | ✅ 命中 `/portfolio/*` | 免配置 |
| `/portfolio/crawl4ai/articles` | ❌ 两段，不命中 | **必须补** |
| `/blog/12/portfolio` | ❌ 现有只有 `/blog/*/topics` 和 `/blog/*/products` | **必须补** |

设计原则（`SecurityPaths` 自身的注释）：「默认拒绝 + 显式放行。新增接口如果忘了归类，会得到 403 而不是静默匿名可达。」

**所以新接口必须显式加白名单，这是验收项。**

### 3.6 现有数据

```
portfolio: 3 条
  1 coolpilot-platform  ACTIVE  enabled=1  ← 前台唯一可见
  2 coolpilot-business  WIP     enabled=0
  3 ai-service          WIP     enabled=0
```

初版方案写「现有 1 条」是**错的**——那只是 `/portfolios` 过滤 `enabled=1` 后的结果。迁移时 3 条都会拿到默认值 `kind='own'`，无需 UPDATE。

### 3.7 导航现状

`nav_menu` 共 11 行，`visible=1` 的**恰好 6 项**（V26 `nav_final_six` 刻意收敛）：

```
首页 / 技术文章 / 技术专题 / 项目 / 知识产品 / 技术服务
```

**新栏目要不要占第 7 格，是一个产品决策，不是技术决策**，见 §8。

---

## 四、复用矩阵

| 能力 | 处理 | 依据 |
| --- | --- | --- |
| 文章存储与渲染 | **直接复用** | blog + MarkdownUtils |
| 分类 / 标签页 | **直接复用** | `/category/[name]`、`/tag/[name]` |
| 专题聚合 | **直接复用** | topic + topic_article |
| 项目档案表 | **扩展**（+7 列） | portfolio 字段重合 90% |
| 档案详情页渲染 | **改造复用** | 现有 `/projects/[slug]` 的 `contentHtml` + `v-html` |
| 后台档案管理 | **扩展** | PortfolioList.vue 已有 Markdown + 贴图 |
| 后台菜单 | **加路由即可** | 自动生成，无权限配置 |
| 富文本编辑器 | **无需新建** | 已是 Markdown textarea |
| 文件上传 | **直接复用** | `/file/upload`，bizType 可加 OSS_PROJECT |
| 文章↔项目关联 | **新建表** | V24 当年刻意没建，条件已变，见下 |
| 栏目首页 | **新建页面** | 无同类页面 |
| 卡片组件 | **新建** | 现有组件只有 3 个，样式内联 |

**关于 `portfolio_article`：V24 当年的决定是**

> 「『相关技术文章』首期在正文里手写链接，等确实需要自动关联时再加 `portfolio_article` 关联表（参照 `topic_article`），不为展示列表先加表。」

现在**条件已满足**：新栏目要求「档案页自动列出本项目的 N 篇分析」且要按顺序展示，手写链接无法维护。所以这次建表是触发条件达成，不是推翻原决定。

---

## 五、与初版方案的差异（必须修正的 8 处）

| # | 初版写法 | 审计事实 | 处理 |
| --- | --- | --- | --- |
| 1 | `content` 存 HTML | 存 **Markdown**，后端转 `contentHtml` | 已修正发布规范 |
| 2 | `/portfolio/{slug}` 返回扁平对象含 `articles[]` | 实际 `{portfolio:{...}, contentHtml:"..."}` | 契约改为在同层加 `articles` |
| 3 | portfolio 现有 1 条 | **3 条**（2 条 `enabled=0`） | 迁移说明更正 |
| 4 | 栏目页用 `/blogs?categoryId=` | `/blogs` 只支持 `pageNum`，分类走 `/category?categoryName=` | 接口改 |
| 5 | 复用 `components/` 现有卡片 | 只有 3 个组件，卡片样式内联 | 工作量上调，需抽组件 |
| 6 | 新接口 `…/articles`、`by-blog` 直接可用 | `/portfolio/*` 是单段通配，两段路径不通 | **必须补 PUBLIC 白名单** |
| 7 | 手写 `ALTER TABLE` 执行 | `validate-on-migrate=true`、`out-of-order=false` | **改为只写 V28 迁移文件**，启动自动执行 |
| 8 | 未提导航 | 导航是 V26 定稿的 6 项 | 升级为待决事项，见 §8 |

---

## 六、修订后的数据方案

**执行方式变更：只写迁移文件 `V28__open_source_insights.sql`，绝不手工在生产库执行 DDL。**
理由：Flyway 开启 `validate-on-migrate`，手工 DDL 会让 schema 与历史迁移漂移，下次启动校验失败直接起不来（V27 的注释专门解释过这个坑）。

```sql
-- V28：开源项目商业观察（CP-WEB-OS）
-- 复用 portfolio：kind 区分自有作品与被观察的开源项目

ALTER TABLE `portfolio`
    ADD COLUMN `kind` varchar(16) NOT NULL DEFAULT 'own'
        COMMENT 'own=自有作品 / oss=被观察的开源项目' AFTER `slug`,
    ADD COLUMN `license` varchar(64) DEFAULT NULL COMMENT 'SPDX 标识' AFTER `status`,
    ADD COLUMN `language` varchar(32) DEFAULT NULL COMMENT '主要语言' AFTER `license`,
    ADD COLUMN `stars` int DEFAULT NULL COMMENT 'star 数快照' AFTER `language`,
    ADD COLUMN `stars_at` datetime DEFAULT NULL COMMENT 'star 采集时间' AFTER `stars`,
    ADD COLUMN `verdict` varchar(32) DEFAULT NULL
        COMMENT 'watch/deploy/extend/commercial/hold' AFTER `stars_at`,
    ADD COLUMN `assessment_json` longtext DEFAULT NULL COMMENT '评估卡 JSON' AFTER `verdict`,
    ADD KEY `idx_kind_enabled_sort` (`kind`, `enabled`, `sort`);

CREATE TABLE IF NOT EXISTS `portfolio_article`
(
    `portfolio_id` bigint NOT NULL COMMENT 'portfolio.id',
    `blog_id`      bigint NOT NULL COMMENT 'blog.id',
    `sort`         int    NOT NULL DEFAULT 0 COMMENT '升序',
    PRIMARY KEY (`portfolio_id`, `blog_id`),
    KEY `idx_pa_blog` (`blog_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='项目档案与分析文章的关联';

-- 栏目分类（用 NOT EXISTS 保证幂等，与 V24 写初始数据的方式一致）
INSERT INTO `category` (`category_name`)
SELECT '开源项目商业观察' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `category_name` = '开源项目商业观察');
-- 同法插入：开源技术研究 / AI 应用实践 / 商业实践与案例

-- 导航第 7 项（sort 45，落在「项目」40 与「知识产品」50 之间）
INSERT INTO `nav_menu` (`label`, `label_en`, `path`, `sort`, `visible`)
SELECT '开源观察', 'Insights', '/open-source', 45, b'1' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `nav_menu` WHERE `path` = '/open-source');
```

**注意**

- `kind` 有默认值，3 条存量数据自动变 `own`，无需 UPDATE
- 导航插入与 §8 的决策绑定，**决策未定前不要写进 V28**
- 回滚：Flyway 社区版无自动回滚，写一份 `docs/rollback/V28-down.sql` 备查

---

## 七、修订后的 API 契约

### 7.1 前台（全部需加 PUBLIC 白名单）

| # | 方法 | 路径 | 变更 | 白名单 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/portfolios?kind=oss` | 加参数，不传=全部 | ✅ 已有（`/portfolios`） |
| 2 | GET | `/portfolio/{slug}` | data 层加 `articles[]` | ✅ 已有（`/portfolio/*`） |
| 3 | GET | `/portfolio/{slug}/articles` | 新增（可选，列表量大时用） | ❌ **需加 `/portfolio/*/articles`** |
| 4 | GET | `/blog/{blogId}/portfolio` | 新增，文章反查项目 | ❌ **需加 `/blog/*/portfolio`** |
| 5 | GET | `/category?categoryName=…` | 不变，栏目页复用它 | ✅ 已有 |

> 第 4 条刻意做成 `/blog/{id}/portfolio` 而不是 `/portfolio/by-blog/{id}`，为了与已有的 `/blog/{blogId}/topics` 保持同一形状。

**`/portfolio/{slug}` 修订后响应**

```json
{
  "code": 200,
  "data": {
    "portfolio": {
      "slug": "crawl4ai", "name": "Crawl4AI", "kind": "oss",
      "license": "MIT", "language": "Python", "verdict": "deploy",
      "assessmentJson": "{...}", "status": "ACTIVE", "techStack": "Python, Playwright",
      "content": "## 项目背景 ...（Markdown 原文）"
    },
    "contentHtml": "<h2>项目背景</h2> ...",
    "articles": [
      { "id": 12, "title": "Crawl4AI 商业价值分析", "slug": "crawl4ai-business", "sort": 0 }
    ]
  }
}
```

`articles` 的组装方式与 `TopicService:57` 一致（`data.put("articles", ...)`），可照抄。

### 7.2 后台

| # | 方法 | 路径 | 变更 |
| --- | --- | --- | --- |
| 1 | GET | `/admin/portfolios?kind=&keyword=` | 加筛选（现有为 `listAll()` 无参） |
| 2 | POST / PUT | `/admin/portfolio` | DTO 加 7 字段，`kind` 白名单校验 |
| 3 | PUT | `/admin/portfolio/{id}/articles` | 新增，全量覆盖 |

---

## 八、待决事项：导航第 7 格

导航是 V26 刻意收敛的 6 项。新增栏目有三种放法：

| 方案 | 做法 | 优点 | 代价 |
| --- | --- | --- | --- |
| **A 加第 7 项**（推荐） | `nav_menu` 插 `/open-source`，sort 45 | 栏目有一级入口，能吃到流量；改动最小 | 破坏 `nav_final_six` 的整洁度；移动端需确认折叠 |
| B 复用「项目」格 | `/projects` 页内加两个 tab，`/open-source` 仍独立存在供 SEO | 导航仍是 6 项 | 两类内容语义不同，混在一起读者会困惑；需要改现有页面 |
| C 挂在「技术专题」下 | `/topic` 页顶部加「开源观察」入口 | 不动导航 | 曝光最低，栏目基本拿不到首页流量 |

**我的判断：选 A。** 理由：这个栏目的价值在于吸引「创业者/独立开发者」这类新受众，藏在二级页面等于没做。6 项变 7 项在桌面端宽度足够，移动端本来就走折叠。

但这属于产品决策，**未确认前不写进 V28**。

---

## 九、修订后的任务卡

| 编号 | 任务 | 关键变更 |
| --- | --- | --- |
| OSS-01 | 数据模型 | 改为**只写 V28 迁移文件** + 本地起服务验证；不执行生产 DDL |
| OSS-02 | 后端接口 | 加 `articles` 装配（照抄 TopicService）+ **补 2 条 PUBLIC 白名单** + `kind` 校验 |
| OSS-03 | 后台管理 | 扩展 `PortfolioList.vue`：kind 筛选、评估卡表单、关联文章选择 |
| OSS-04 | 栏目首页 | 文章区改用 `/category?categoryName=`；卡片组件需**新建** |
| OSS-05 | 档案详情 | 保留 `{portfolio, contentHtml}` 结构，只加 `articles` 区块 |
| OSS-06 | 文章页关联 | 用 `/blog/{id}/portfolio`，与 topics 同形 |
| OSS-07 | SEO | sitemap 目前**不含 portfolio 详情**，需补 |
| OSS-08 | 首批内容 | 不变：只发有实测的 Crawl4AI |
| OSS-09 | 上线验收 | 增加：分类改名断链检查、白名单 403 检查、`enabled=0` 不泄漏 |

---

## 十、放行条件

开始写代码前需确认三件事：

- [ ] **导航方案 A / B / C**（§8）
- [ ] 是否把现有 `blog/11` 从「文章」分类迁到「运维实录」
- [ ] `verdict` 五档中文文案（我的建议：值得持续关注 / 值得部署使用 / 值得二次开发 / 值得商业化探索 / 暂不建议投入）

确认后按 OSS-01 → 02 → 04/05 → 03/06 → 07 的顺序推进。
