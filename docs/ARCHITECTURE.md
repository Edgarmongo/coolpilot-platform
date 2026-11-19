# CoolSkyAI 项目架构说明

> 更新：2026-11-20 · 对应版本 `v1.1.0` · 数据库 62 表 / Flyway V28
>
> 本文描述**当前实际代码状态**。上游 Vue 2 基线版本已归档到
> [archive/ARCHITECTURE-2026-11-04-baseline.md](archive/ARCHITECTURE-2026-11-04-baseline.md)，
> 仅用于追溯迁移过程，不代表现状。
>
> 功能口径见 [FEATURE-MAP.md](FEATURE-MAP.md)，决策与风险见 [FEATURE-REVIEW.md](FEATURE-REVIEW.md)。

---

## 一、系统拓扑

```
                            浏览器
                              │ 443
                    ┌─────────▼──────────┐
                    │ gateway（nginx）    │  唯一对外端口 80/443
                    └──┬────────┬────────┘
         /             │        │        /cms/            /api/  /image/
    ┌──────────────────▼──┐  ┌──▼───────────┐  ┌──────────▼─────────┐
    │ web                  │  │ admin         │  │ api                 │
    │ Nuxt 3 SSR           │  │ Vue 3 + Vite  │  │ Spring Boot 4.1     │
    │ :3000                │  │ Element Plus  │  │ :8091               │
    └──────────────────────┘  └───────────────┘  └──────┬──────┬───────┘
                                                         │      │
                                                 ┌───────▼──┐ ┌─▼────────┐
                                                 │ MySQL 8  │ │ Redis 7  │
                                                 └──────────┘ └──────────┘
```

| 入口 | 转发到 | 说明 |
|---|---|---|
| `/` | `web:3000` | Nuxt SSR，负责被搜索引擎收录 |
| `/cms/` | `admin:80` | 后台 SPA，登录后使用，无 SEO 诉求 |
| `/api/` | `api:8091` | 网关剥掉 `/api` 前缀后转发 |
| `/image/` | `api:8091` | 上传文件直出，不走 CDN |

**只有 gateway 映射宿主端口**，其余容器在内网。生产服务器上不需要 JDK / Node / 源码，镜像从 GHCR 拉取。

三端的职责边界：

| 端 | 面向 | 认证 | 能做什么 |
|---|---|---|---|
| web | 访客 / 读者 | `visitor:*` JWT（自助注册） | 评论、点赞、收藏、下单、提交需求、AI 问答 |
| admin | 站长 | `admin:*` JWT | 全量增删改查 |
| api | 上述两端 | 见 §4.4 | 承载全部业务规则，不返回页面 |

`web` 不直连数据库，通过 `/api` 取数（SSR 时在服务端发起），因此**业务规则的唯一实现在 api**。

---

## 二、运行时与版本

| 组件 | 当前版本 | 说明 |
|---|---|---|
| JDK | Temurin 21 | `maven.compiler.release=21` |
| Spring Boot | 4.1.x | 内含 Framework 7 + Tomcat 11 |
| Spring Security | 7.x | 编程式 `SecurityFilterChain`，无 XML |
| MyBatis | 3.x | 19 个 XML mapper + 注解混用 |
| MySQL | 8.0 | utf8mb4，库名 `coolpilot` |
| Redis | 7-alpine | 缓存、限流、验证码、token 版本 |
| Node | 22 | 两个前端共用 |
| Nuxt | 3.21 | SSR + i18n + 自带 sitemap / robots |
| Vue | 3.5 | 前台与后台同版本 |
| Vite | 8.x | 后台构建 |
| Element Plus | 2.14 | 后台 UI |
| Docker | Compose v2 | 本地 6 容器，生产 6 容器 |

---

## 三、目录结构

```
coolpilot-platform/
├── coolpilot-server/                    Spring Boot 后端（412 个 Java 文件）
│   └── src/main/
│       ├── java/com/coolskyai/
│       │   ├── modules/                17 个业务模块（见 §4.2）
│       │   ├── controller/             遗留控制器：blog / category / tag / archive /
│       │   │                           comment / friend / moment / about / index / login
│       │   ├── service/ entity/ mapper/  遗留业务实现，只修 bug 不新增能力
│       │   ├── config/ security/ aspect/  全局配置与横切
│       │   └── util/                    markdown / quartz / ip 等工具
│       └── resources/
│           ├── db/migration/            Flyway V1–V28（唯一建表来源）
│           ├── mapper/                  19 个 MyBatis XML
│           ├── templates/               邮件 / 通知模板
│           └── ipdb/                    ip2region 离线库
├── coolpilot-admin/                     后台（Vue 3 + Vite，32 个页面）
├── coolpilot-web/                       前台（Nuxt 3 + TS，34 个页面 + 9 个组件）
│   ├── pages/                           文件路由
│   ├── components/                      AppCard / AppCover / AppChip / AppTag / PostList …
│   ├── composables/                     useApi / useFormat / useAuth
│   ├── i18n/locales/                    zh-CN.json / en.json
│   └── server/
│       ├── api/[...path].ts             /api 代理（SSR 与客户端共用）
│       └── routes/                      sitemap.xml.ts / robots.txt.ts
├── deploy/                              Dockerfile、gateway nginx 模板、systemd 单元
├── scripts/                             启停、重建、进库、备份、续证书
├── docs/                                架构、数据模型、部署、评审、任务卡
├── .github/workflows/                   ci.yml（构建校验）release.yml（镜像发布）
├── docker-compose.yml                   本地开发（含端口映射）
└── docker-compose.prod.yml              生产（拉 GHCR 镜像）
```

---

## 四、coolpilot-server

### 4.1 分层

```
controller  →  service  →  mapper（MyBatis）  →  MySQL
                 ↓
              Redis（缓存、计数、限流）
```

- 控制器只做参数校验与返回包装，业务判断在 service
- 事务边界在 service（`@Transactional`），不跨控制器
- 统一返回 `Result<T>`：`{code, msg, data}`，**业务失败也返回 HTTP 200**（前端拦截器依赖这一约定，不要"顺手规范化"成 4xx）

### 4.2 十七个业务模块

| 模块 | 负责 |
|---|---|
| `auth` | 管理员 / 访客登录、JWT 签发、token 版本失效 |
| `account` | 用户资料、找回密码、账号设置 |
| `nav` | 导航菜单（走 Redis 缓存） |
| `brand` | 站点品牌、页脚、站点设置（复用 `site_setting`） |
| `social` | 社交账号聚合（`/follow`） |
| `planet` | 知识星球介绍页 |
| `portfolio` | 作品集（`/projects`，**不是**服务订单） |
| `product` | 知识商品、目录、课时、资源 |
| `trade` | 订单、支付渠道、退款 |
| `entitlement` | 用户权益、下载 token |
| `service` | 技术服务：需求 → 报价 → 项目 → 里程碑 → 交付 |
| `ai` | 知识库索引、混合检索、问答、模型配置 |
| `topic` | 专题（合集） |
| `community` | 评论、反馈 |
| `file` | 上传、附件、引用检查 |
| `notify` | 邮件 / 消息通知 |
| `opensource` | **开源项目商业观察**（`/open-source`，v1.1.0 新增） |

### 4.3 遗留包边界

`controller/` + `service/` + `entity/` 是上游迁移时保留的扁平包，承载文章、分类、标签、归档、评论、友链、动态、关于、首页聚合。

**约定：只修 bug，不新增业务。** 新能力一律建在 `modules/<域>` 下。开源观察就是按这个约定外挂的：`blog` 表一行没改。

`/projects` 容易误解，这里明确（**URL 与 API 同名不同义，这是本仓库最容易踩的命名坑**）：

| 前台 URL | 调用的 API | 含义 | 表 |
|---|---|---|---|
| `/projects` | `GET /portfolios` | 作品集（我做过什么） | `portfolio` |
| `/project/{no}` | `GET /project/{projectNo}` | 服务项目的交付进度（客户委托） | `project` |
| `/open-source/projects` | `GET /open-source/projects` | 第三方开源项目研究 | `oss_project` |

即：前台路由 `/projects` 对应的是 **portfolio** 接口；API 里的 `/projects` 反而是**服务项目**。
读代码或写页面时先看接口名，别看 URL 名。

### 4.4 鉴权

- 请求头 `Authorization: <token>`，**不带 `Bearer` 前缀**
- 白名单在 `constant/SecurityPaths.java` 的 `PUBLIC` 数组，单段通配（如 `/portfolio/*`）
- 白名单放行的是**路径**，不负责数据可见性；可见性由 service 判断（如 `is_published`、`publish_status='published'`）
- 新增公开接口必须同时改白名单，否则 401

### 4.5 异常与日志

`GlobalExceptionHandler` 把业务异常转成 `Result.error`；三类日志落库（登录 / 操作 / 异常），后台可查。定时任务用 Quartz，任务定义与执行记录在 `schedule_job` / `schedule_job_log`。

---

## 五、coolpilot-admin

- 路由 hash 模式，`publicPath: /cms/`
- 左侧菜单按域分组：内容管理（文章 / 分类 / 标签 / 专题 / **开源观察** / 动态 / 评论 / 反馈）、商品管理、技术服务、交易管理、用户管理、AI 服务、页面管理、系统管理
- 所有请求经 `src/util/request.js` 统一注入 token 与错误提示
- 界面文案中文原文即 key，英文查表，缺词回落中文（`src/i18n/ui.js`）
- 语言存 localStorage，不走 URL —— 后台是登录后的工作台，没有 SEO 诉求

---

## 六、coolpilot-web

### 6.1 路由

| 路径 | 文件 | 页面 |
|---|---|---|
| `/` | `index.vue` | 首页：首屏 + 头条 + 专题 + 知识产品 + 技术服务 + 关注 |
| `/archives` | `archives.vue` | 文章列表（`?view=timeline` 切时间线） |
| `/blog/{slug}` | `blog/[id].vue` | 文章详情（目录、相关付费内容、所属专题 / 项目） |
| `/category/{name}`、`/tag/{name}` | `category/`、`tag/` | 分类 / 标签 |
| `/topic`、`/topic/{slug}` | `topic/` | 专题列表与详情 |
| `/open-source` | `open-source/index.vue` | 开源观察首页 |
| `/open-source/projects`、`/open-source/projects/{slug}` | `open-source/projects/` | 项目列表 / 档案 |
| `/open-source/category/{code}`、`/open-source/tag/{name}`、`/open-source/search` | `open-source/` | 领域 / 标签 / 栏目搜索 |
| `/knowledge`、`/knowledge/{no}` | `knowledge/` | 知识商品列表与详情 |
| `/service` | `service/index.vue` | 技术服务介绍 |
| `/demand/new`、`/demand/{no}` | `demand/` | 提交需求、需求与报价 |
| `/project/{no}` | `project/[no].vue` | 服务项目进度（里程碑、交付、验收） |
| `/order/{orderNo}` | `order/[orderNo].vue` | 订单详情与支付 |
| `/ask`、`/share/{token}` | `ask.vue`、`share/` | AI 问答、只读分享页 |
| `/projects`、`/projects/{slug}` | `projects/` | 作品集 |
| `/about`、`/friends`、`/moments`、`/follow`、`/planet`、`/search` | 单文件 | 静态与聚合页 |
| `/me`、`/login`、`/register`、`/forgot` | 单文件 | 账号 |

> 路由参数名以文件为准（`[no]` / `[token]` / `[orderNo]`），它们对应的是业务编号而不是主键。

英文站在 `/en` 前缀下（`prefix_except_default`），**不做浏览器语言自动跳转** —— 同一篇文章的中英地址必须稳定可分享、可分别收录。

### 6.2 渲染与 SEO

- 内容页全部 SSR，`sitemap.xml` 与 `robots.txt` 由 `server/routes` 动态生成
- sitemap 收录静态页、文章、作品集、开源项目与**有内容的**领域页；工具页 `/search` 与空聚合页当前仍会被收录（已知问题，见 FEATURE-REVIEW R4）
- 文章页输出 Article / BreadcrumbList 结构化数据，项目档案页输出 BreadcrumbList

### 6.3 数据获取

`composables/useApi.ts` 封装 `call()`：SSR 时走内部地址（`NUXT_API_BASE`），浏览器端走 `/api`。`server/api/[...path].ts` 做代理，避免跨域。

> 本地起 dev 时 `API_BASE` 必须指向**带 `/api` 的地址**（如 `http://localhost:8091` 或 `https://域名/api`），
> 否则代理会把 `/blogs` 拼成 `https://域名/blogs` 而 404。

### 6.4 视觉系统

`assets/css/main.css` 是站点基础层（白底、1px 青边、12px 圆角、1200 容器），`assets/css/ui-tokens.css` 提供 `--c-*` 令牌。

**旧类名（`.card` / `.btn` / `.post-item` / `.article`）全部保留**，只改取值 —— 未逐页重构的页面自动继承新视觉，不会半新半旧。详见 [UI-SYSTEM.md](UI-SYSTEM.md)。

通栏铺色只在 `.main` 内用 `100vw`，横向溢出由 `.main { overflow-x: clip }` 收掉；**不能放 body 上**，否则头部 `sticky` 失效。

### 6.5 正文渲染

后端 `content` 存 **Markdown**，`MarkdownUtils.markdownToHtmlExtensions` 转 HTML，5 类调用点共用（文章、关于、作品集、动态、友链）。

从 v1.0.4 起该方法对**两种格式都安全**：解析前把块级 HTML 元素抽成占位注释，渲染后原样放回。所以「有人往后台贴成品 HTML」不会再导致代码块被二次解析（详见 [TASKS-CONTENT-RENDER.md](TASKS-CONTENT-RENDER.md)）。

前台在渲染结果上给 `h2/h3` 注入锚点生成目录，不改库、不改接口契约。

---

## 七、数据库

### 7.1 Flyway 规则

- 迁移文件命名 `V{n}__{name}.sql`，**唯一建表来源**，不用 `schema.sql`
- `validate-on-migrate=true`、`out-of-order=false`：**已发布的迁移文件不可修改**，改了应用会拒绝启动
- 幂等写法：`CREATE TABLE IF NOT EXISTS`、`INSERT ... ON DUPLICATE KEY UPDATE`、`INSERT IGNORE`
- 每个迁移应验证两条路径：空库全量执行 + 已有库增量执行

### 7.2 六十二张表按域

业务表 61 张 + `flyway_schema_history`。下表按域归组，表名为库中实际名称。

| 域 | 表 |
|---|---|
| 内容 | `blog` `blog_tag` `category` `tag` `topic` `topic_article` |
| 开源观察 | `oss_project` `oss_project_article` `oss_project_tag` |
| 互动 | `comment` `moment` `friend` `feedback` `message` `favorite` `like_record` |
| 页面配置 | `about` `site_setting` `nav_menu` `social_account` |
| 账号 | `user` `visitor` |
| 统计 | `visit_log` `visit_record` `city_visitor` |
| 日志 | `login_log` `operation_log` `exception_log` `entitlement_log` |
| 任务 | `schedule_job` `schedule_job_log` |
| 文件 | `file` `file_version` |
| 商品 | `product` `product_article` `product_category` `product_price` `product_resource` `course` `lesson` |
| 交易 | `orders` `order_item` `payment` `refund` |
| 权益 | `entitlement` `download_record` |
| 服务 | `service_item` `service_event` `demand` `quote` `project` `milestone` `delivery` |
| AI | `kb_knowledge_base` `kb_document` `kb_chunk` `ai_conversation` `ai_message` `ai_setting` `ai_usage` |
| 作品集 | `portfolio` |

> 字段级说明见 [DATA-MODEL.md](DATA-MODEL.md)（存量表）与 [DATA-MODEL-OSS.md](DATA-MODEL-OSS.md)（V28 三表）。
> 表名的真实来源是 `information_schema`，写文档或写代码前先查库，别按模块名猜。

### 7.3 缓存

| 键 | 内容 | 注意 |
|---|---|---|
| `navMenuList:{lang}` | 导航菜单 | Flyway 改表**不会**让缓存失效，必须手工 DEL |
| `site_setting` 相关 | 品牌、页脚、关于 | 改库不生效，走后台改 |
| 访客 / PV / UV | 计数 | 丢可重建 |

**键名在 Redis 里带双引号**（Spring 序列化结果），手工删除要写成：

```bash
redis-cli DEL '"navMenuList:zh-CN"' '"navMenuList:en"'
```

写成 `DEL navMenuList:zh-CN` 会返回 0 —— 看起来删了，其实没删。

---

## 八、关键数据流

### 8.1 首页

1. 浏览器请求 `/` → gateway → web
2. web 在服务端并发拉取：文章列表、归档统计、专题、商品、服务
3. SSR 出 HTML（含首屏文本，可被收录）
4. 浏览器 hydrate，后续交互走客户端请求

区块无数据时整块隐藏，不做占位。

### 8.2 文章详情

1. `GET /blog?slug=...` 返回文章 + 正文（后端已转成 HTML）
2. 前端渲染时给 `h2/h3` 注入 `sec-n` 锚点并生成目录
3. 并行拉取：相关付费内容、所属专题、所属开源项目、相关阅读
4. 正文 HTML 已在 v1.0.4 做保护，代码块不会被二次解析

### 8.3 AI 问答

1. 前端 `POST /ai/chat/stream`（SSE 流式；另有同步的 `POST /ai/chat`）
2. 后端改写问题 → 向量 + 关键词混合检索 → 重排 → 拼上下文 → 调模型
3. 流式返回，结束写 `ai_message` 与 `ai_usage`
4. 分享页 `/share/{token}` 只读，不暴露会话其余内容

> 流式接口**故意不声明 `produces`**：经 Nitro / nginx 代理时，代理可能带上
> `Accept: application/json`，一旦声明 `text/event-stream` 就直接 406/500。
> `SseEmitter` 会自己设 Content-Type。这个坑在 `AiController` 里有注释，不要"顺手补上"。

**当前索引范围只有 `blog` 和 `product`**，作品集与开源项目不在检索范围（已知缺口，见 FEATURE-REVIEW R3）。

### 8.4 交易

```
下单 → 待支付 → 用户转账 → 提交凭证
   → 后台人工确认到账 → 发权益（entitlement）→ 可下载 / 学习
   → 申请退款 → 后台审批 → 回收权益
```

支付渠道是**人工转账**（微信 / 支付宝 / PayPal），无商户资质依赖。未支付订单由 Quartz 定时关闭。

### 8.5 开源观察

1. 后台录入 `oss_project`（双状态：`research_status` / `publish_status`）+ 评估卡 JSON
2. 公开接口只查 `publish_status='published'`，参数无法绕过
3. 档案页展示七维评估卡（每维带档位、说明、证据等级 F/S/T/M/A）
4. 文章通过 `oss_project_article` 双向关联：档案页列出分析，文章页显示「所属项目」
5. 归档项目的档案返回 404，文章与关联记录全部保留

---

## 九、本地启动

```bash
# 依赖：Docker + JDK 21 + Maven + Node 22
cp .env.example .env      # 改 MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / JWT_SECRET
docker compose up -d --build

# 或分端起
docker compose up -d mysql redis
cd coolpilot-server && mvn spring-boot:run                      # :8091
cd coolpilot-web && API_BASE=http://localhost:8091 npx nuxt dev --port 3000
cd coolpilot-admin && npm run dev                               # :8079/cms/
```

| 服务 | 地址 |
|---|---|
| 前台 | http://localhost:3000（dev）/ 8081（compose） |
| 后台 | http://localhost:8079/cms/ |
| 接口 | http://localhost:8091 |
| MySQL | localhost:13306 |
| Redis | localhost:16379 |

首次启动 Flyway 自动建表并写入种子数据（含管理员账号）。**部署到公网前必须改密码。**

---

## 十、技术债与风险

按影响面排序。已解决项保留记录，避免重复讨论。

| # | 问题 | 位置 | 影响 | 状态 |
|---|---|---|---|---|
| 1 | 支付靠人工确认到账 | `trade` | 订单量上来即成为瓶颈，且依赖人工不出错 | 待解决（P0） |
| 2 | 定时任务单进程 | Quartz | 多副本部署会重复执行关单 / 浏览量落库 | 已记录为部署约束 |
| 3 | AI 检索只覆盖文章与商品 | `KnowledgeIndexService` | 作品集、开源项目搜不到 | 待解决（P1） |
| 4 | 英文站内容未维护 | i18n / 数据 | 英文页面会混入中文文章 | 待决策（补齐或下线） |
| 5 | 无访问统计口径 | 全站 | 不知道哪篇文章、哪个项目被看 | 待解决（P0） |
| 6 | `blog` 表 18 列多模块共用 | 数据库 | 新增内容类型只能外挂表 | 已知限制，按需外挂 |
| 7 | 导航 / 品牌 / 关于走 Redis 长缓存 | `nav` / `brand` | 改库不生效，必须走后台或手工清缓存 | 已知行为 |
| 8 | 后台错误返回 HTTP 200 | 全局 | 不符合 REST 语义，但前端拦截器依赖 | **不要改** |
| 9 | 无 API 级限流（登录除外） | 全局 | 公开接口可被高频调用 | 待评估 |
| 10 | 表注释以 latin1 入库 | 历史迁移 | `SHOW CREATE TABLE` 看到乱码，不影响数据 | 已知，不修 |
| 11 | 单测覆盖薄 | server | 仅有 MarkdownUtils 等少量用例 | 持续补 |
| 12 | ~~正文 HTML 二次解析~~ | `MarkdownUtils` | ~~代码块被截断、出现幽灵标题~~ | **v1.0.4 已修** |
| 13 | ~~Vue 2 / Semantic UI~~ | 前端 | ~~EOL、包体大~~ | **已迁移 Nuxt 3 + Vue 3** |

风险与决策的完整登记见 [FEATURE-REVIEW.md](FEATURE-REVIEW.md)。

---

## 十一、与上游差异

| 项 | 上游 | 当前 |
|---|---|---|
| JDK | 1.8 | 21 |
| 后端 | 单体 controller / service | 17 个模块 + 遗留包 |
| 前台 | Vue 2 SPA（webpack） | Nuxt 3 SSR（Vite） |
| 后台 | Vue 2 SPA | Vue 3 + Element Plus |
| 表 | 19 | 62（Flyway V28） |
| 部署 | 手工 | 六容器 + GHCR 镜像 + 启动时自动迁移 |
| 能力 | 博客 | 内容 + 商品 + 交易 + 服务 + AI + 开源研究 |
