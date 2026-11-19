# 数据模型：开源项目商业观察（v2）

> 配套 `PRD-OSS-INSIGHTS.md`、`ADR-0004-OPEN-SOURCE-MODEL.md`、`CP-WEB-OS-01-现状审计与改造方案.md`。
> **v2 变更**：由「复用 portfolio + kind」改为「新建 oss_project」。理由见 ADR-0004。
> 执行方式：只写 `V28__open_source_insights.sql`，随部署自动执行，**不手工改生产库**。

---

## 一、基线（2026-11-20 实测）

| 项 | 值 |
|---|---|
| Flyway | 27 条迁移，生产 `installed_rank=27`，全部 success |
| 表数量 | 59 |
| 下一个迁移号 | **V28**，不可跳号、不可改历史文件 |
| 已有可复用 | `blog`（文章）、`category`（栏目）、`tag` + `blog_tag`（标签）、`topic`（专题） |
| 已有但**不复用** | `portfolio`（作品集，见 ADR）、`project`（技术服务订单） |

---

## 二、三张新表

```
oss_project ──┬── oss_project_article ──→ blog
              └── oss_project_tag ──────→ tag（现成）
```

### 2.1 `oss_project` 项目档案

```sql
CREATE TABLE IF NOT EXISTS `oss_project`
(
    `id`               bigint       NOT NULL AUTO_INCREMENT,
    `slug`             varchar(180) NOT NULL COMMENT '前台地址 /open-source/projects/{slug}',
    `name`             varchar(150) NOT NULL COMMENT '项目名',
    `summary`          varchar(500)          DEFAULT NULL COMMENT '列表页一句话简介',
    `description`      longtext              DEFAULT NULL COMMENT '详细介绍，Markdown',
    `repo_url`         varchar(500)          DEFAULT NULL COMMENT '代码仓库',
    `official_url`     varchar(500)          DEFAULT NULL COMMENT '官方网站',
    `docs_url`         varchar(500)          DEFAULT NULL COMMENT '官方文档',
    `license`          varchar(80)           DEFAULT NULL COMMENT 'SPDX 标识，如 MIT',
    `language`         varchar(80)           DEFAULT NULL COMMENT '主要语言',
    `category`         varchar(32)  NOT NULL DEFAULT 'ai-project' COMMENT '领域枚举码，见 §2.4',
    `cover_url`        varchar(512)          DEFAULT NULL COMMENT '封面图',
    `stars`            int                   DEFAULT NULL COMMENT 'star 数快照，仅作关注度参考',
    `stars_at`         datetime              DEFAULT NULL COMMENT 'star 采集时间',
    `verdict`          varchar(32)           DEFAULT NULL COMMENT '结论档，见 §2.5',
    `assessment_json`  longtext              DEFAULT NULL COMMENT '评估卡 JSON，见 §2.6',
    `research_status`  varchar(20)  NOT NULL DEFAULT 'todo' COMMENT 'todo/researching/done',
    `publish_status`   varchar(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
    `featured`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '首页精选',
    `last_verified_at` datetime              DEFAULT NULL COMMENT '资料最近核验时间',
    `sort`             int          NOT NULL DEFAULT 0 COMMENT '升序',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oss_slug` (`slug`),
    KEY `idx_oss_pub` (`publish_status`, `featured`, `sort`),
    KEY `idx_oss_cat` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='开源项目档案';
```

> `description` 是 **Markdown**，与 `blog`、`portfolio` 一致：后端用
> `MarkdownUtils.markdownToHtmlExtensions()` 转 HTML，前台 `v-html`。

### 2.2 `oss_project_article` 项目 ↔ 文章

```sql
CREATE TABLE IF NOT EXISTS `oss_project_article`
(
    `project_id`    bigint      NOT NULL COMMENT 'oss_project.id',
    `blog_id`       bigint      NOT NULL COMMENT 'blog.id',
    `relation_type` varchar(24) NOT NULL DEFAULT 'research'
        COMMENT 'business=商业分析 research=技术研究 practice=实践 compare=对比',
    `sort`          int         NOT NULL DEFAULT 0 COMMENT '升序',
    PRIMARY KEY (`project_id`, `blog_id`),
    KEY `idx_opa_blog` (`blog_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='项目与文章关联';
```

**主键用两列而不是三列**（与规划文档的差异）：一篇文章对一个项目只扮演一个角色，
三列唯一会让后台能重复提交而不报错。两列主键下重复关联直接冲突失败。

**不改 `blog` 表加 `article_type`**：`blog` 是遗留区表（18 列、多模块共用），
为栏目加列属侵入式改动。文章类型放在关联表的 `relation_type` 上，按项目维度表达。

### 2.3 `oss_project_tag` 项目 ↔ 标签（复用 `tag`）

```sql
CREATE TABLE IF NOT EXISTS `oss_project_tag`
(
    `project_id` bigint NOT NULL,
    `tag_id`     bigint NOT NULL,
    PRIMARY KEY (`project_id`, `tag_id`),
    KEY `idx_opt_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='项目标签关联，tag 复用 blog 的标签表';
```

### 2.4 项目领域：枚举，不建表

| 码 | 名称 |
|---|---|
| `ai-project` | AI 开源项目 |
| `agent` | AI Agent 与自动化 |
| `data-search` | 数据采集与搜索 |
| `devtool` | 开发者工具 |
| `enterprise` | 企业级开源软件 |

> **2026-11-20 调整（由视觉稿发现）**：去掉了初稿的 `business-model`（开源商业模式）。
> 它不是「项目属于哪个技术领域」，而是「从什么角度分析」——属于内容视角，不是分类维度。
> 硬把两者放进一个下拉框，后台录入时会犹豫该选哪个。

值域写在 `modules/opensource/enums/OssCategory.java`，前端按 i18n 映射显示中文。
5 个值、单人运营、一年不变，建表 + 管理页 + 关联查询的成本大于收益。

### 2.5 结论档：五值

| 值 | 中文 |
|---|---|
| `watch` | 值得持续关注 |
| `deploy` | 值得部署使用 |
| `extend` | 值得二次开发 |
| `commercial` | 值得商业化探索 |
| `hold` | 暂不建议投入 |

判定条件见研究仓 `COMMERCIAL-REVIEW-STANDARD.md` §4。

### 2.6 `assessment_json` 结构

```json
{
  "techMaturity":    { "level": "high", "note": "0.9.5 实测 7 场景通过", "evidence": "T" },
  "marketDemand":    { "level": "mid",  "note": "有付费替代品", "evidence": "M" },
  "commercialSpace": { "level": "mid",  "note": "", "evidence": "?" },
  "deployCost":      { "level": "low",  "note": "单机 1.5G 内存", "evidence": "T" },
  "competition":     { "level": "high", "note": "Firecrawl / Scrapy", "evidence": "F" },
  "devValue":        { "level": null,   "note": "", "evidence": "?" },
  "insightFit":      { "level": "high", "note": "7 个 REST 端点", "evidence": "T" }
}
```

| 字段 | 取值 |
|---|---|
| `level` | `high` / `mid` / `low` / `null`（未评） |
| `evidence` | `F` 官方事实 · `S` 源码 · `T` 实测 · `M` 市场 · `A` 推断 · `?` 待验证 |
| `note` | ≤ 40 字，写条件不写形容词 |

**不设总分字段。** 一个数字无法表达「在什么条件下值得做什么」。

### 2.7 状态机

```
research_status:  todo ──→ researching ──→ done
publish_status:   draft ──→ published ──→ archived
                          ↑↓（可来回）
```

两条轴互相独立：研究完成但没写完 → `research_status=done` + `publish_status=draft`；
先发布简版再补研究 → `published` + `researching`。
**公开接口只认 `publish_status='published'`**，与 `research_status` 无关。

---

## 三、栏目分类（`category` 表加 3 行）

文章仍按「栏目」归入现有 `category` 表。本期只建实际会用到的 3 个：

```sql
INSERT INTO `category` (`category_name`)
SELECT '开源项目商业观察' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `category_name` = '开源项目商业观察');
-- 同法：'开源技术研究'、'开源技术实践'
```

> 计划中的「商业实践与案例」等有内容后再加（V29），避免前台出现空栏目。

**两套分类不要混**

| 体系 | 承载 | 回答 | 例子 |
|---|---|---|---|
| 文章栏目 | `category` 表 | 这篇属于哪个内容中心 | 开源项目商业观察 |
| 项目领域 | `oss_project.category` 枚举 | 这个项目属于哪个技术领域 | data-search |

---

## 四、V28 迁移文件

```sql
-- V28：开源项目商业观察（CP-WEB-OS）
-- 新建三张表 + 三个栏目分类；不改任何现有表结构

-- 1) 建表：见 §2.1 / §2.2 / §2.3 三段 CREATE TABLE
-- 2) 栏目分类：见 §3
-- 3) 导航：待 PRD §4.1 决策后追加，未定前不写
```

**幂等**：全部使用 `CREATE TABLE IF NOT EXISTS` 与 `INSERT ... WHERE NOT EXISTS`（与 V24 写法一致）。

**回滚**：另存 `docs/rollback/V28-down.sql`

```sql
DROP TABLE IF EXISTS `oss_project_tag`;
DROP TABLE IF EXISTS `oss_project_article`;
DROP TABLE IF EXISTS `oss_project`;
DELETE FROM `category` WHERE `category_name` IN
  ('开源项目商业观察','开源技术研究','开源技术实践')
  AND `id` NOT IN (SELECT DISTINCT `category_id` FROM `blog`);
```

> 回滚后要一并删除 `flyway_schema_history` 里 V28 的记录，否则再次部署不会重跑。
> 这一步本身有风险，非必要不执行。

---

## 五、API 契约

网关把 `/api/*` 转发到服务根路径，所以控制器写 `/open-source/...`，对外是 `/api/open-source/...`。

### 5.1 前台（需加白名单 `/open-source/**`）

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | GET | `/open-source/projects` | 项目分页列表，支持 `category` / `tag` / `q` / `featured` / `pageNum` |
| 2 | GET | `/open-source/projects/{slug}` | 档案详情 |
| 3 | GET | `/open-source/projects/{slug}/articles` | 关联文章（含 `relationType`） |
| 4 | GET | `/open-source/categories` | 领域枚举 + 各自项目数 |
| 5 | GET | `/open-source/tags` | 项目标签 + 计数 |
| 6 | GET | `/open-source/featured` | 精选（≤ 6） |
| 7 | GET | `/open-source/search?q=` | 栏目内搜索，返回 `{projects:[], articles:[]}` |
| 8 | GET | `/open-source/blog/{blogId}/project` | 文章反查所属项目 |

**`/open-source/projects/{slug}` 响应**

```json
{
  "code": 200,
  "data": {
    "project": {
      "slug": "crawl4ai", "name": "Crawl4AI", "category": "data-search",
      "license": "MIT", "language": "Python", "verdict": "deploy",
      "stars": 42000, "starsAt": "2026-11-19", "lastVerifiedAt": "2026-11-19",
      "assessmentJson": "{...}", "tags": ["Python", "Web Crawling"]
    },
    "contentHtml": "<h2>...</h2>",
    "articles": [
      { "id": 12, "title": "Crawl4AI 商业价值分析", "slug": "crawl4ai-business",
        "relationType": "business", "readTime": 8 }
    ]
  }
}
```

`contentHtml` 沿用 `PortfolioService:64` 的做法：从 `description`（Markdown）转换。

### 5.2 后台

| # | 方法 | 路径 | 用途 |
|---|---|---|---|
| 1 | GET | `/admin/oss/projects` | 分页，支持 `publishStatus` / `researchStatus` / `category` / `keyword` |
| 2 | POST | `/admin/oss/project` | 新建 |
| 3 | PUT | `/admin/oss/project` | 更新 |
| 4 | PUT | `/admin/oss/project/{id}/articles` | 全量覆盖关联（含 order 与 relationType） |
| 5 | PUT | `/admin/oss/project/{id}/tags` | 全量覆盖标签 |
| 6 | POST | `/admin/oss/project/{id}/publish` | 发布（draft → published） |
| 7 | POST | `/admin/oss/project/{id}/archive` | 归档（不删文章） |

### 5.3 业务规则（验收时要逐条测）

1. 公开接口只返回 `publish_status='published'`，任何参数都不能绕过
2. 未发布项目的关联文章不通过 `/projects/{slug}/articles` 泄漏
3. 归档项目：档案详情 404，但历史文章仍可通过 `/blog/{slug}` 打开
4. `slug` 唯一，重复时返回明确错误而非 500
5. 删除项目前必须检查关联；默认只允许归档
6. 关联文章必须存在且已发布，否则拒绝

---

## 六、白名单

`SecurityPaths.PUBLIC` 追加：

```java
"/open-source/**",
```

一条通配覆盖全部 8 个前台接口。后台接口在 `/admin/oss/**`，不在白名单内（默认拒绝）。

> 与 v1 的 `/portfolio/*` 单段通配不同，这次用 `**` 是有意的：
> 栏目下全是公开内容，逐个列举容易漏（漏一个就是 403）。
> 代价是以后不能在 `/open-source/` 下放任何需要鉴权的接口——真要放，改用别的前缀。

---

## 七、代码改动点

**新建模块**（遵守 ED-05 §1 第 7 条：新增业务进 `modules/**`）

```
modules/opensource/
├── controller/  OpenSourcePublicController.java, OpenSourceAdminController.java
├── service/     OssProjectService.java
├── mapper/      OssProjectMapper.java
├── entity/      OssProject.java, OssProjectArticle.java, OssProjectTag.java
├── dto/         OssProjectSaveDTO.java
├── vo/          OssProjectDetailVO.java, OssProjectCardVO.java
└── enums/       OssCategory.java, OssVerdict.java, OssStatus.java
```

| 层 | 改动 |
|---|---|
| 常量 | `SecurityPaths.PUBLIC` 加 `/open-source/**` |
| 前端 | 新建 `pages/open-source/index.vue`、`pages/open-source/projects/[slug].vue` |
| 前端 | 新建 `pages/open-source/category/[code].vue`、`pages/open-source/tag/[name].vue` |
| 前端 | 新建 `components/OssProjectCard.vue`、`components/OssAssessment.vue` |
| 前端 | `pages/blog/[id].vue` 加「所属项目」区块 |
| 前端 | `i18n/locales/zh-CN.json` + `en.json` 同步加 key |
| 前端 | `server/routes/sitemap.xml.ts` 加栏目与项目页 |
| 后台 | 新建 `views/oss/ProjectList.vue`（含评估卡表单）+ 路由 |
| 不做 | 不改 `blog` / `portfolio` / `project` 模块任何代码 |

---

## 八、核心查询

```sql
-- 列表（栏目首页 / 分类页共用）
SELECT id, slug, name, summary, cover_url, category, license, language, verdict, stars
FROM oss_project
WHERE publish_status = 'published'
  AND (#{category} IS NULL OR category = #{category})
ORDER BY featured DESC, sort ASC, update_time DESC
LIMIT #{offset}, #{size};

-- 详情
SELECT * FROM oss_project WHERE slug = #{slug} AND publish_status = 'published';

-- 关联文章
SELECT b.id, b.title, b.slug, b.read_time, b.update_time, pa.relation_type
FROM oss_project_article pa
JOIN blog b ON b.id = pa.blog_id AND b.is_published = b'1'
WHERE pa.project_id = #{id}
ORDER BY pa.sort ASC, b.create_time DESC;

-- 文章反查项目
SELECT p.slug, p.name, p.verdict
FROM oss_project_article pa
JOIN oss_project p ON p.id = pa.project_id
WHERE pa.blog_id = #{blogId} AND p.publish_status = 'published'
LIMIT 1;

-- 标签
SELECT t.id, t.tag_name, t.color, COUNT(*) AS n
FROM oss_project_tag opt
JOIN tag t ON t.id = opt.tag_id
JOIN oss_project p ON p.id = opt.project_id AND p.publish_status = 'published'
GROUP BY t.id ORDER BY n DESC;
```

栏目规模在百级，走索引即可，不需要额外优化。

---

## 九、迁移与验证

```
1. 写 V28__open_source_insights.sql
2. 本地起服务，启动日志出现 Migrating to version 28
3. 本地校验：
   - show tables like 'oss_%'  → 3 张
   - desc oss_project          → 23 列
   - select count(*) from category where category_name like '开源%'  → 3
4. 提交，随版本部署
5. 生产只读校验：
   - select max(installed_rank) from flyway_schema_history  → 28
   - select publish_status, count(*) from oss_project group by publish_status
```

---

## 十、比 v1 好在哪（一句话）

v1 要在 `portfolio` 上加 10 列、并给现有 3 处查询都补 `kind` 过滤，漏一处就内容串台；
v2 **不动任何现有表**，风险面从「改 1 张表 + 3 处查询」降到「新增 3 张表」。

代价是详情页要新写一套模板——但本来就要加评估卡，形状早就不同了。
