# 数据模型：开源项目商业观察

> 配套 `PRD-OSS-INSIGHTS.md`。
> 原则：**能复用就不新建**，改动全部向后兼容，每条变更都带回滚。

---

## 一、现状（2026-11-20 实测）

### 1.1 相关表

| 表 | 行数 | 用途 | 是否可复用 |
| --- | --- | --- | --- |
| `blog` | 1 | 文章 | 直接用 |
| `category` | 5 | 分类 | 加 4 行 |
| `tag` / `blog_tag` | 0 / 0 | 标签 | 直接用 |
| `topic` / `topic_article` | 0 / 0 | 专题聚合 | 直接用 |
| `portfolio` | 1 | 项目档案 | **加列复用** |
| `project` | 0 | **服务订单**（有 amount/order_no，不是作品集） | 不用 |

> ⚠️ 注意：`project` 表是交易类业务表，与「项目展示」无关。名字易混，实现时不要动它。

### 1.2 `portfolio` 现有列

```
id bigint · slug varchar(64) · name varchar(100) · summary varchar(255)
cover_url varchar(512) · tech_stack varchar(255) · repo_url varchar(512)
demo_url varchar(512) · status varchar(16) · content longtext
sort int · enabled bit(1) · create_time · update_time
```

已有 `repo_url` / `tech_stack` / `status` / `content` —— **这正是项目档案需要的形状**。

### 1.3 现有数据

| id | slug | name | 说明 |
| --- | --- | --- | --- |
| 1 | `coolpilot-platform` | CoolPilot Platform | 自有产品（kind 应为 `own`） |

---

## 二、为什么复用 `portfolio` 而不新建 `oss_project`

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. 新建 `oss_project` | 语义干净 | 字段与 portfolio 重合 90%；详情页两套模板；sitemap 两处改 | ❌ |
| B. 复用 + `kind` 区分 | 零重复；详情组件共用；迁移 1 条数据 | 需要 `kind` 强隔离 | ✅ |

**唯一的代价**是「自有作品」与「被分析项目」共表。用 `kind` 解决：

- `own` → `/projects`（作品集，现有页面）
- `oss` → `/open-source`（栏目，新增页面）

---

## 三、变更清单

全部为**加列 / 加行 / 加表**，无改类型、无删列，可安全回滚。

### 变更 1：`portfolio` 加 7 列

```sql
ALTER TABLE `portfolio`
  ADD COLUMN `kind` varchar(16) NOT NULL DEFAULT 'own'
    COMMENT 'own=自有作品 / oss=被观察的开源项目' AFTER `slug`,
  ADD COLUMN `license` varchar(64) NULL COMMENT 'SPDX 标识，如 MIT / Apache-2.0' AFTER `status`,
  ADD COLUMN `language` varchar(32) NULL COMMENT '主要语言，如 Python' AFTER `license`,
  ADD COLUMN `stars` int NULL COMMENT 'star 数快照，仅作关注度参考' AFTER `language`,
  ADD COLUMN `stars_at` datetime NULL COMMENT 'star 数采集时间' AFTER `stars`,
  ADD COLUMN `verdict` varchar(32) NULL
    COMMENT '结论档：watch/deploy/extend/commercial/hold' AFTER `stars_at`,
  ADD COLUMN `assessment_json` longtext NULL
    COMMENT '评估卡 JSON，见 §3.1' AFTER `verdict`,
  ADD INDEX `idx_portfolio_kind` (`kind`, `enabled`, `sort`);
```

**为什么 `assessment_json` 用 `longtext` 而不是 `JSON` 类型**

项目用 MyBatis + 自定义类型处理较少。JSON 列需要注册 TypeHandler，多一层出错面。存字符串、由前端 `JSON.parse` 更简单，且评估卡只在详情页渲染，不需要在数据库里做 JSON 查询。

**回滚**

```sql
ALTER TABLE `portfolio` DROP INDEX `idx_portfolio_kind`;
ALTER TABLE `portfolio`
  DROP COLUMN `assessment_json`,
  DROP COLUMN `verdict`,
  DROP COLUMN `stars_at`,
  DROP COLUMN `stars`,
  DROP COLUMN `language`,
  DROP COLUMN `license`,
  DROP COLUMN `kind`;
```

### 3.1 `assessment_json` 结构

七项固定，键名不变，缺项写 `null`：

```json
{
  "techMaturity":   { "level": "high",   "note": "0.9.5 实测 7 场景全通过", "evidence": "V" },
  "marketDemand":   { "level": "mid",    "note": "搜索量与社区活跃度", "evidence": "D" },
  "commercialSpace":{ "level": "mid",    "note": "", "evidence": "?" },
  "deployCost":     { "level": "low",    "note": "单机 1.5G 内存，无 GPU", "evidence": "V" },
  "competition":    { "level": "high",   "note": "Firecrawl / Scrapy / Playwright", "evidence": "D" },
  "devValue":       { "level": null,     "note": "", "evidence": "?" },
  "insightFit":     { "level": "high",   "note": "已有 7 个 REST 端点可对接", "evidence": "V" }
}
```

| 字段 | 取值 |
| --- | --- |
| `level` | `high` / `mid` / `low` / `null`（未评） |
| `evidence` | `V` 实测 · `S` 源码 · `D` 文档 · `?` 待验证 |
| `note` | ≤ 40 字，写条件而不是形容词 |

> **不设总分字段。** 一个数字无法表达「在什么条件下值得做什么」，评分表见 `COMMERCIAL-REVIEW-STANDARD.md`。

### 变更 2：新表 `portfolio_article`

```sql
CREATE TABLE IF NOT EXISTS `portfolio_article` (
  `portfolio_id` bigint NOT NULL,
  `blog_id`      bigint NOT NULL,
  `sort`         int    NOT NULL DEFAULT 0,
  PRIMARY KEY (`portfolio_id`, `blog_id`),
  KEY `idx_pa_blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='项目档案与分析文章的关联';
```

无物理外键（沿用项目约定）。`idx_pa_blog` 用于「某篇文章属于哪个项目」的反查。

**回滚**：`DROP TABLE IF EXISTS portfolio_article;`

### 变更 3：`category` 加 4 行

```sql
INSERT INTO `category` (`category_name`) VALUES
  ('开源项目商业观察'),
  ('开源技术研究'),
  ('AI 应用实践'),
  ('商业实践与案例');
```

现有 5 个（文章 / 知识库 / AI工具集 / AI测评 / Skill资源）保持不动。

**可选（需确认）**：把 `blog/11`（SSH 加固实录）从「文章」迁到新增的「运维实录」，避免「文章」继续当兜底桶。这一步涉及现有内容，**默认不做**，等确认。

**回滚**

```sql
DELETE FROM `category`
WHERE `category_name` IN ('开源项目商业观察','开源技术研究','AI 应用实践','商业实践与案例')
  AND `id` NOT IN (SELECT DISTINCT `category_id` FROM `blog`);
```

---

## 四、接口契约

### 4.1 前台

| # | 方法 | 路径 | 变更 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | GET | `/portfolios` | **加参数** `kind` | 不传 = 全部（保持原行为） |
| 2 | GET | `/portfolio/{slug}` | **加返回字段** | 附 `articles[]`、`assessment`（原文串） |
| 3 | GET | `/portfolio/{slug}/articles` | **新增** | 单独取文章列表，供分页 |
| 4 | GET | `/portfolio/by-blog/{blogId}` | **新增** | 文章页反查所属项目 |
| 5 | GET | `/topics` | 不变 | 栏目首页复用 |

**`/portfolios` 参数**

```
kind   own | oss   可选，不传返回全部
tag    标签名      可选，第二期实现
```

**`/portfolio/{slug}` 响应增量**

```json
{
  "code": 200,
  "data": {
    "slug": "crawl4ai",
    "name": "Crawl4AI",
    "kind": "oss",
    "license": "MIT",
    "language": "Python",
    "verdict": "deploy",
    "assessment": "{\"techMaturity\":{...}}",
    "techStack": "Python, Playwright, FastAPI",
    "repoUrl": "https://github.com/unclecode/crawl4ai",
    "content": "...",
    "articles": [
      { "id": 12, "title": "Crawl4AI 商业价值分析", "slug": "crawl4ai-business", "categoryName": "开源项目商业观察" }
    ]
  }
}
```

`assessment` 保持字符串，前端解析。解析失败时整卡降级为「评估数据异常」，不白屏。

### 4.2 后台

| # | 方法 | 路径 | 变更 |
| --- | --- | --- | --- |
| 1 | GET | `/admin/portfolios` | 加 `kind` / `keyword` 筛选 |
| 2 | POST | `/admin/portfolio` | DTO 加 7 个字段 |
| 3 | PUT | `/admin/portfolio` | 同上 |
| 4 | PUT | `/admin/portfolio/{id}/articles` | **新增**，全量覆盖关联 |

---

## 五、代码改动点

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Entity | `modules/portfolio/entity/Portfolio.java` | +7 字段（`assessmentJson` 映射 `assessment_json`） |
| DTO | `modules/portfolio/dto/PortfolioSaveDTO.java` | +7 字段，`kind` 校验枚举 |
| VO | 新建 `PortfolioDetailVO` | 含 `articles`、`assessment` |
| Mapper | `modules/portfolio/mapper/PortfolioMapper.java` | `listByKind`、`listArticles`、`findByBlogId` |
| Service | `modules/portfolio/service/PortfolioService.java` | 装配 articles、按 kind 过滤 |
| Controller | `PortfolioController` | 加参数与 2 个新接口 |
| Controller | `PortfolioAdminController` | 筛选、关联维护 |
| 前端 | `pages/projects/index.vue` | 请求加 `kind=own`（**必须改，否则作品集会混入 oss**） |
| 前端 | `pages/projects/[slug].vue` | 复用为共享详情组件 |
| 前端 | 新建 `pages/open-source/index.vue` | 栏目首页 |
| 前端 | 新建 `pages/open-source/[slug].vue` | 档案详情 |
| 前端 | `server/routes/sitemap.xml.ts` | 加 `/open-source` 与档案 URL |
| 后台 | `views/page/PortfolioList.vue`（现有） | 加 kind 筛选 + 新字段表单 |

> 后台 portfolio 管理页已存在（`src/views/page/` 下，路由 `/portfolio`），是扩展而非新建。

---

## 六、缓存

现有缓存键（`homeBlogInfoList` / `newBlogList` / `archiveBlogMap` / `categoryNameList` / `tagCloudList`）与本功能无关。

若给 `/portfolios` 加缓存，规则：

| 键 | 失效时机 |
| --- | --- |
| `portfolioList:{kind}` | 后台新增/编辑/删除档案 |
| `portfolioDetail:{slug}` | 同上 + 关联文章变更 |

**第一期不加缓存**——档案数量少、查询简单，加缓存只会引入「改了不生效」的排查成本。

---

## 七、迁移步骤（生产）

按顺序执行，每步可独立回滚：

```
1. 备份
   mysqldump --single-transaction coolpilot portfolio category > backup_before_oss.sql

2. 加列（变更 1）
   执行 ALTER ... ADD COLUMN（7 条 + 索引）

3. 建表（变更 2）
   CREATE TABLE IF NOT EXISTS portfolio_article ...

4. 加分类（变更 3）
   INSERT INTO category ...

5. 校验
   - desc portfolio 确认 7 列存在
   - select count(*) from portfolio_article 返回 0
   - select id, category_name from category 确认 9 行

6. 把现有作品标记为 own
   UPDATE portfolio SET kind='own' WHERE kind='own';   -- 默认值已是 own，仅作确认
```

> ⚠️ 执行前确认 `flyway_schema_history` 状态。项目用 Flyway 管理迁移，**手写 DDL 后要同步补一个 migration 文件**，否则下次自动迁移会与实体不一致。

---

## 八、索引与查询

栏目首页三条查询：

```sql
-- 精选档案（kind=oss + 启用 + 按 sort）
SELECT id, slug, name, summary, cover_url, tech_stack, license, language, verdict
FROM portfolio WHERE kind='oss' AND enabled=1 ORDER BY sort DESC, update_time DESC LIMIT 6;

-- 某档案的文章（按 sort）
SELECT b.id, b.title, b.slug, b.read_time, c.category_name
FROM portfolio_article pa
JOIN blog b ON b.id = pa.blog_id
LEFT JOIN category c ON c.id = b.category_id
WHERE pa.portfolio_id = ? AND b.is_published = b'1'
ORDER BY pa.sort;

-- 文章反查项目
SELECT p.slug, p.name, p.verdict
FROM portfolio_article pa JOIN portfolio p ON p.id = pa.portfolio_id
WHERE pa.blog_id = ? AND p.kind='oss' LIMIT 1;
```

三条都走主键或新建索引，无需额外优化。

---

## 九、遗留问题

| # | 问题 | 处理 |
| --- | --- | --- |
| 1 | 空分类是否会在导航/首页显示 | 需在 OSS-02 验证；若有展示则加「仅显示有文章的栏目」 |
| 2 | 一篇文章是否可归属多个项目 | 当前设计允许（join 表多对多），但业务上应唯一，先不强制 |
| 3 | star 数如何更新 | 第一期手工填；第二期可加定时任务，但要遵守平台 API 条款 |
| 4 | `status` 与 `verdict` 语义重叠 | `status` 保留为「项目状态」（ACTIVE/ARCHIVED），`verdict` 是「我们的结论」，不合并 |
