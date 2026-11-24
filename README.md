# 酷天AI · CoolSkyAI Platform

> 一个个人技术品牌站点的完整底座：文章、开源项目研究、知识产品、技术服务，用一套账号打通「看内容 → 买内容 → 交付 → 售后」。
>
> 面向中文用户叫 **酷天AI**，英文站叫 **CoolSkyAI** —— 品牌名按语言区分，配置在 `site_setting.blogName`。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![Nuxt](https://img.shields.io/badge/Nuxt-3-00DC82)](https://nuxt.com/)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)

线上站点：**[酷天AI · coolskyai.com](https://coolskyai.com)** —— 本仓库的完整实例，文章、开源项目商业观察、知识产品与技术服务都在真实运行。

[![站点](https://img.shields.io/badge/%E5%9C%A8%E7%BA%BF%E7%AB%99%E7%82%B9-coolskyai.com-00875a)](https://coolskyai.com)
[![技术文章](https://img.shields.io/badge/%E6%96%87%E7%AB%A0-%E5%BC%80%E6%BA%90%E4%B8%8E%20Agent%20%E5%AE%9E%E8%B7%B5-0f766e)](https://coolskyai.com/archives)
[![开源观察](https://img.shields.io/badge/%E5%BC%80%E6%BA%90%E8%A7%82%E5%AF%9F-%E9%A1%B9%E7%9B%AE%E5%95%86%E4%B8%9A%E4%BB%B7%E5%80%BC%E8%AF%84%E4%BC%B0-1f6feb)](https://coolskyai.com/open-source)

---

## 这是什么

不是博客，是「内容获客 + 知识变现 + 技术服务」的闭环：

- **内容**：文章、动态、分类、标签、专题、归档、友链、评论，Markdown 写作支持截图直粘
- **开源观察**：第三方开源项目的商业价值研究——项目档案 + 七维评估卡（每维带证据等级）+ 关联分析文章，与作品集完全独立
- **知识产品**：文档 / 课程 / 专栏 / 代码 / 服务五类商品，含目录、课时、试看与配套资源
- **交易**：下单 → 扫码支付 → 后台确认 → 发权益 → 下载/学习 → 退款，全链路幂等
- **技术服务**：需求提交 → 报价 → 立项 → 里程碑 → 交付 → 验收，客户可在站内跟踪进度
- **AI 问答**：知识库索引、混合检索（向量 + 关键词）、多轮问答、追问改写、重排；模型与密钥可在后台配置
- **引流**：知识星球介绍页（站外成交）、社交媒体聚合页（`/follow`）、作品集（`/projects`，我做过什么）
- **运营**：导航 / 品牌 / 页脚 / 站点设置全部后台可配；附件管理带引用检查；定时任务可视化管理

支付目前走「扫码转账 + 后台人工确认」，不需要微信支付/支付宝商户资质。将来接官方渠道只需实现一个渠道接口。

> 三个「项目」不要混：前台 `/projects` 是**作品集**（我做过什么，数据取自 `portfolio`），
> `/project/{no}` 是**服务项目进度**（客户委托），`/open-source/projects` 是**第三方开源项目研究**。
>
> 尤其注意：**API 里的 `GET /projects` 返回的是服务项目**，与前台 `/projects` 不是一回事 —— 这是本仓库最容易踩的命名坑。

## 版本

| 版本 | 内容 | 状态 |
|---|---|---|
| `v0.1.0-baseline` | 上游 Vue 2 版本基线（保留在 tag 中，用于对照迁移） | 已归档 |
| `v1.0.0` | 首个正式版：内容 + 商品 + 交易 + 技术服务 + AI 问答 + 知识星球 + 社交引流 + 作品集 + 附件管理 + 找回密码 + 中英双语 | 已上线 |
| `v1.0.1` | 品牌名按语言区分（酷天AI / CoolSkyAI），修英文站标题回落中文的问题 | 已上线 |
| `v1.0.2` | 修后台文章保存：`password` 为 NULL 被误判为加密、列表页未脱敏 | 已上线 |
| `v1.0.3` | 前端按视觉稿重构：白色双行头部、统一卡片与正文排版、文章目录侧栏、列表/时间线双视图 | 已上线 |
| `v1.0.4` | 正文渲染对 HTML / Markdown 双格式都安全，修代码块被二次解析 | 已上线 |
| `v1.1.0` | **开源项目商业观察栏目**：V28 三张表、9 个公开接口、前后台各一套页面 | 已上线 |
| `v1.1.1` | 代码来源整改第一批（19 个文件） | 已上线 |
| `v1.1.2` | 重写支付与权益服务，修超时关单后的凭证错位 | 已上线 |
| `v1.1.3` | `util` 包整体重写（16 个文件 / 1,221 行） | 已上线 |
| `v1.2.0` | 后台经营驾驶舱 + 首页平台化改版 | 已上线 |
| `v1.2.1` | sitemap 翻页取全量（修老文章掉出地图）；开源栏目首批内容 | 已上线 |
| `v1.2.3` | 技术 SEO 与 GEO 基建：软 404、hreflang、结构化数据、llms.txt、RSS、URL 规范化 | 已上线 |
| `v1.2.4` | 自定义错误页，替换 Nuxt 默认 404/500 | 已上线 |
| `v1.2.5` | 英文站 canonical 指向中文原页，撤掉 hreflang | 已上线 |
| `v1.2.6` | 文章页 `og:url` 与 canonical 统一，修两边打架 | 已上线 |
| `v1.3.0` | 九篇文章补摘要、常见问题段与内链；FAQPage 结构化数据；IndexNow 推送 | 已上线 |
| `v1.3.1` | 修文章详情接口不返回 `description`，摘要长期回落正文截断 | 已上线 |
| `v1.3.2` | 推 tag 自动上线：服务器侧拉取 + 健康检查 + 失败回滚 | 已上线 |
| `v1.3.3` | 读接口抖动会把文章误判成 `noindex` | 已上线 |
| `v1.3.4` | 文章正文改由 payload 下发，消除水合期「空窗 + noindex」 | 已上线 |

线上运行版本以服务器 `.env` 的 `TAG` 为准。
`v1.2.2` 未发布：tag 在自动化流程中被重复创建后删除，版本号因此跳过。变更明细见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

## 架构一览

```
                  ┌──────────────────────────────┐
   浏览器 ──► 443 │  gateway（nginx，唯一对外端口）│
                  └───┬──────────┬───────────┬───┘
                      │ /        │ /cms/     │ /api/
                  ┌───▼───┐  ┌───▼────┐  ┌───▼────┐
                  │  web  │  │ admin  │  │  api   │
                  │ Nuxt3 │  │ Vue3   │  │Spring  │
                  │ :3000 │  │ :80    │  │ :8091  │
                  └───────┘  └────────┘  └───┬────┘
                                             │
                                  ┌──────────┴──────────┐
                              ┌───▼───┐            ┌────▼───┐
                              │ MySQL │            │ Redis  │
                              └───────┘            └────────┘
```

生产环境只有 gateway 映射宿主端口（80/443），其余服务全部在内网。镜像从 GHCR 拉取，服务器上不需要 JDK / Node / 源码。

## 后台八个业务域

| 域 | 管什么 |
|---|---|
| 内容管理 | 文章、分类、标签、专题、**开源观察**、动态、评论、友链、反馈 |
| 商品管理 | 知识商品（文档/课程/专栏/代码/服务）、目录、课时、资源 |
| 技术服务 | 服务项目、客户需求、报价、项目、里程碑、交付 |
| 交易管理 | 订单、支付确认、退款、用户权益 |
| 用户管理 | 访客账号、禁用、重置密码 |
| AI 服务 | 模型管理（密钥掩码、热生效）、知识库索引、问答记录、用量统计 |
| 页面管理 | 站点设置（含知识星球）、导航菜单、社交媒体、作品集、关于页 |
| 系统管理 | 管理员、操作日志、附件管理、定时任务 |

## 多语言

前台中英双语：中文在 `/`，英文在 `/en/`（Nuxt i18n `prefix_except_default`）。**不做浏览器语言自动跳转** —— 同一篇文章的中英地址必须稳定可分享、可被分别收录。

| 内容 | 存放位置 | 怎么改 |
|---|---|---|
| 界面文案 | `coolpilot-web/i18n/locales/{zh-CN,en}.json` | 改 JSON 后需重启（词条不热更新） |
| 导航菜单 | `nav_menu.label` / `label_en` | 后台「页面管理 → 导航管理」 |
| 站点名、页脚、品牌标记 | `site_setting.value` / `value_en` | 后台「页面管理 → 站点设置」 |
| 关于页 | `about` 表的 `titleEn` / `contentEn` | 后台「页面管理 → 关于我」 |
| 后台界面 | `coolpilot-admin/src/i18n/ui.js` | 中文原文即 key，英文查表，缺词回落中文 |
| 文章 / 商品正文 | 不翻译 | 英文站展示原文 |

英文值留空时回落中文，可以逐项补翻译。品牌名就是这套机制的一个实例：`blogName` 的中文值 `酷天AI`、英文值 `CoolSkyAI`。

后台语言存 localStorage（不走 URL），因为后台是登录后的工作台，没有 SEO 诉求。

## SEO 与 GEO

技术层已经内建，不需要额外插件：

| 能力 | 实现位置 | 说明 |
|---|---|---|
| 标题 / 描述 / canonical | `app.vue` | 自指 canonical，尾斜杠与 `www` 由中间件 301 归一 |
| hreflang | `app.vue` | 中英互指 + `x-default`，用 `switchLocalePath` 生成 |
| JSON-LD | `app.vue`、`pages/blog/[id].vue` | 全局 `Organization` + `WebSite`；文章页 `BlogPosting` + `BreadcrumbList` |
| 分享卡片 | `app.vue`、`pages/blog/[id].vue`、`public/og-default.png` | 文章无首图时用默认大图，twitter card 为大图模式 |
| 站点地图 | `server/routes/sitemap.xml.ts` | 中英各一条，带 `xhtml:link` hreflang；`/search` 已排除 |
| RSS | `server/routes/rss.xml.ts` | 最近 20 篇，供聚合站与抓取管线发现更新 |
| llms.txt | `server/routes/llms.txt.ts` | 给 AI 引擎的站点索引，内容实时从后端拉 |
| robots | `server/routes/robots.txt.ts` | 显式放行 GPTBot / ClaudeBot / PerplexityBot 等 |
| 错误页 | `error.vue` | 双语、`noindex`、给站内搜索与主要入口 |

两个容易踩的点：

1. **Cloudflare 的 Bot Fight Mode 会绕过 robots.txt 直接拦 AI 爬虫**，
   要在地理位置正确的前提下确认它是关闭的，否则 llms.txt 与 robots 都白写。
2. **`og:image` 必须是绝对地址**，相对路径各家抓取器都不认；
   默认图放在 `public/og-default.png`（1200×630）。

内容侧的规则写在 [docs/CHANGELOG.md](docs/CHANGELOG.md) 的 v1.2.3 条目里：
每节先给结论、关键论断配数字、Q&A 段落用问题式小标题 —— 这几条决定 AI 是否愿意引用。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1、Spring Security + JWT、MyBatis、PageHelper、Quartz |
| 数据 | MySQL 8（Flyway 版本化迁移，62 张表 / V28）、Redis 7 |
| 前台 | Nuxt 3 + Vue 3.5 + TypeScript（SSR，自带 sitemap / robots） |
| 后台 | Vue 3.5 + Vite + Element Plus + Pinia |
| 部署 | Docker Compose、nginx gateway、GHCR 镜像、GitHub Actions |

## 本地开发

```bash
# 1. 准备环境变量（.env 已被 gitignore）
cp .env.example .env
# 改 MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / JWT_SECRET（JWT 用 openssl rand -hex 48）

# 2. 起全部服务（首次构建较慢）
docker compose up -d --build
# 或
./scripts/dev-up.sh
```

| 服务 | 地址 | 说明 |
|---|---|---|
| 前台 | http://localhost:8081 | 访客侧，可注册登录 |
| 后台 | http://localhost:8079/cms/ | 站长侧，种子账号 `Admin`（首字母大写） |
| 接口 | http://localhost:8091 | Spring Boot API |
| MySQL | localhost:13306 | 映射到容器 3306，避免与本机冲突 |
| Redis | localhost:16379 | 同上 |

- 改 Java 代码：`docker compose up -d --build api` 或 `./scripts/rebuild-api.sh`
- 改前端：`coolpilot-web` / `coolpilot-admin` 是 bind mount，自动热更新（i18n 词条除外）
- 进数据库：`./scripts/db-shell.sh`

> 种子密码**仅用于本地**（`V2__init_seed.sql` 里的 bcrypt 值）。生产部署后必须立即通过后台「系统管理 → 账号设置」改掉，见 [docs/DEPLOY.md](docs/DEPLOY.md)。
>
> 用户名是 `Admin`，不是 `admin` —— 登录大小写敏感，小写连续失败会触发锁定。

## 生产部署

一套流程，不手工装环境：

```bash
git tag -a v1.1.0 -m "v1.1.0" && git push origin v1.1.0   # 触发 CI 构建四个镜像
```

服务器上：

```bash
cd /opt/coolpilot-platform
cp .env .env.bak-$(date +%Y%m%d%H%M)            # 出问题可原样退回
sed -i 's/^TAG=.*/TAG=v1.1.0/' .env
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

数据库迁移由 Flyway 在应用启动时自动执行，不需要手工建表。**只要这次包含迁移，发版前先备份库**：

```bash
docker exec coolpilot-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --quick --routines coolpilot' > /opt/backup/pre-$(date +%Y%m%d%H%M).sql
```

迁移不可回退：应用可以退回旧 tag，但已执行到 V28 的表结构会保留。真正的数据回滚要用上面那份 dump。

> **导航菜单走 Redis 缓存**：迁移里插入的菜单项不会自动生效，部署后需要清一次：
> `redis-cli DEL '"navMenuList:zh-CN"' '"navMenuList:en"'`（键名外的双引号不能省）。


**反向代理 / CDN 有一个必踩的坑**：如果域名走 Cloudflare，SSL 模式必须是 **Full（完整）**。选 Flexible 或「自动」都会和 gateway 的 80→443 跳转形成无限重定向（因为源站是自签名证书，自动模式验不过会退回 Flexible）。详见 [docs/DEPLOY.md](docs/DEPLOY.md) 的「Cloudflare 配置」一节。

完整上线步骤、证书、备份恢复、排查清单见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 数据与备份

| 数据 | 位置 | 说明 |
|---|---|---|
| MySQL | 卷 `coolpilot_mysql_data` → `/var/lib/mysql` | 全部业务数据，**必须备份** |
| 上传文件 | 卷 `coolpilot_upload_data` → `/data/upload` | 文章配图、封面、二维码 |
| Redis | 卷 `coolpilot_redis_data` → `/data` | 缓存、限流、验证码、token 版本，**丢可重建** |

宿主机实际路径在 `/var/lib/docker/volumes/coolpilot_*_data/_data`。

定时备份（systemd timer，每天 03:30，保留 14 天）只备 MySQL 与上传目录，Redis 故意不备：

```bash
systemctl list-timers coolpilot-backup.timer
systemctl start coolpilot-backup.service     # 手动触发一次
ls -lh /opt/coolpilot-platform/backups/
```

恢复步骤见 [docs/DEPLOY.md](docs/DEPLOY.md) 第六节。

> `docker compose down -v` 会连数据卷一起删，永远不要带 `-v`。

## 目录结构

```
coolpilot-platform/
├── coolpilot-server/                 # Spring Boot 后端
│   └── src/main/
│       ├── java/com/coolskyai/
│       │   ├── modules/              # 17 个业务模块：account / ai / auth / brand / community /
│       │   │                         #   entitlement / file / nav / notify / opensource /
│       │   │                         #   planet / portfolio / product / service / social / topic / trade
│       │   └── ...                   # 早期遗留包（文章/分类/标签/评论…），只修 bug 不新增业务
│       └── resources/db/migration/   # Flyway 迁移 V1–V28（唯一的建表来源）
├── coolpilot-admin/                  # 管理后台（Vue 3 + Vite + Element Plus）
├── coolpilot-web/                    # 用户前台（Nuxt 3 + TS，SSR）
├── deploy/                           # Dockerfile、gateway nginx 模板、systemd 单元
├── scripts/                          # 启停、重建、进库、备份、续证书
├── docs/                             # 架构、数据模型、接口、部署、任务卡
├── .github/workflows/                # CI（构建校验）与 Release（镜像发布）
├── docker-compose.yml                # 本地开发（含端口映射）
├── docker-compose.prod.yml           # 生产（拉 GHCR 镜像，仅暴露 80/443）
└── README.md
```

## 文档

全部文档的索引与阅读顺序见 **[docs/README.md](docs/README.md)**。最常看的几份：

| 文档 | 内容 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 三端架构、17 个模块、路由、数据流、技术债 |
| [docs/FEATURE-MAP.md](docs/FEATURE-MAP.md) | 全站功能地图：62 表 / 263 接口 / 34 前台页 / 32 后台页 |
| [docs/FEATURE-REVIEW.md](docs/FEATURE-REVIEW.md) | 功能评审总表与风险登记（R1–R7） |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | 59 张存量表 + V28 三张新表的设计与取舍 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 上线手册：gateway 分流、Cloudflare、证书、备份恢复、排查 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | 各版本变更明细 |
| [docs/SEO-CONTENT-PLAN.md](docs/SEO-CONTENT-PLAN.md) | SEO / GEO 内容改造清单（逐篇改写与选题矩阵） |
| [docs/ARTICLE-TEMPLATE.md](docs/ARTICLE-TEMPLATE.md) | **写文章前先看这份**：模板骨架、十二条规则、发布五步、反例清单 |
| [content/](content/) | 文章写作源文件（`*.md` + front matter），配 `scripts/article_lint.py` 校验 |

## CI/CD

- 推 `main` 或提 PR：三端各做一次真实构建（Maven / Vite / Nuxt），编译不过直接红叉
- 打 `v*` tag：构建四个镜像并推 GHCR（`coolpilot-server` / `coolpilot-admin` / `coolpilot-web` / `coolpilot-gateway`）
- 回滚 = 换 tag 再 `up -d`

镜像包是公开的，服务器拉取不需要 `docker login`。

## 许可证

**专有软件，保留所有权利**（见 [LICENSE](LICENSE)）。本项目不是开源项目，
未经书面许可不得复制、分发、公开或商用。

服务端基线派生自 [Naccl/NBlog](https://github.com/Naccl/NBlog)（MIT 协议），
该部分仍受 MIT 约束，原版权声明与许可全文按 MIT 要求保留在
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

**这不是形式条款**：MIT 允许把衍生作品闭源，但要求保留原作者版权声明。
删掉它既构成违约，也会让"全部自研"变成不实陈述。
