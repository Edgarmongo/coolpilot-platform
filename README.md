# 酷天AI · CoolSkyAI Platform

> 一个个人技术品牌站点的完整底座：文章、知识产品、技术服务、付费社群引流，用一套账号打通「看内容 → 买内容 → 交付 → 售后」。
>
> 面向中文用户叫 **酷天AI**，英文站叫 **CoolSkyAI** —— 品牌名按语言区分，配置在 `site_setting.blogName`。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![Nuxt](https://img.shields.io/badge/Nuxt-3-00DC82)](https://nuxt.com/)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)

线上实例：<https://coolskyai.com>

---

## 这是什么

不是博客，是「内容获客 + 知识变现 + 技术服务」的闭环：

- **内容**：文章、动态、分类、标签、专题、归档、友链、评论，Markdown 写作支持截图直粘
- **知识产品**：文档 / 课程 / 专栏 / 代码 / 服务五类商品，含目录、课时、试看与配套资源
- **交易**：下单 → 扫码支付 → 后台确认 → 发权益 → 下载/学习 → 退款，全链路幂等
- **技术服务**：需求提交 → 报价 → 立项 → 里程碑 → 交付 → 验收，客户可在站内跟踪进度
- **AI 服务**：知识库索引、混合检索（向量 + 关键词）、多轮问答、追问改写、重排；模型与密钥可在后台配置
- **引流**：知识星球介绍页（站外成交）、社交媒体聚合页（`/follow`）、开源项目展示（`/projects`）
- **运营**：导航 / 品牌 / 页脚 / 站点设置全部后台可配；附件管理带引用检查；定时任务可视化管理

支付目前走「扫码转账 + 后台人工确认」，不需要微信支付/支付宝商户资质。将来接官方渠道只需实现一个渠道接口。

## 版本

| 版本 | 内容 | 状态 |
|---|---|---|
| `v0.1.0-baseline` | 上游 Vue 2 版本基线（保留在 tag 中，用于对照迁移） | 已归档 |
| `v1.0.0` | 首个正式版：内容 + 商品 + 交易 + 技术服务 + AI 问答 + 知识星球 + 社交引流 + 项目展示 + 附件管理 + 找回密码 + 中英双语 | 已上线 |
| `v1.0.1` | 品牌名按语言区分（酷天AI / CoolSkyAI），修英文站标题回落中文的问题 | 已上线 |

变更明细见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

## 架构一览

```
                  ┌──────────────────────────────┐
   浏览器 ──► 443 │  gateway（nginx，唯一对外端口）│
                  └───┬──────────┬───────────┬───┘
                      │ /        │ /cms/     │ /api/
                  ┌───▼───┐  ┌───▼────┐  ┌───▼────┐
                  │  web  │  │ admin  │  │  api   │
                  │ Nuxt3 │  │ Vue3   │  │Spring  │
                  │ :8081 │  │ :80    │  │ :8091  │
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
| 内容管理 | 文章、分类、标签、专题、动态、评论、友链、反馈 |
| 商品管理 | 知识商品（文档/课程/专栏/代码/服务）、目录、课时、资源 |
| 技术服务 | 服务项目、客户需求、报价、项目、里程碑、交付 |
| 交易管理 | 订单、支付确认、退款、用户权益 |
| 用户管理 | 访客账号、禁用、重置密码 |
| AI 服务 | 模型管理（密钥掩码、热生效）、知识库索引、问答记录、用量统计 |
| 页面管理 | 站点设置（含知识星球）、导航菜单、社交媒体、项目展示、关于页 |
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

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1、Spring Security + JWT、MyBatis、PageHelper、Quartz |
| 数据 | MySQL 8（Flyway 版本化迁移，59 张表 / V27）、Redis 7 |
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
| 后台 | http://localhost:8079/cms/ | 站长侧，初始账号 `admin` / `123456` |
| 接口 | http://localhost:8091 | Spring Boot API |
| MySQL | localhost:13306 | 映射到容器 3306，避免与本机冲突 |
| Redis | localhost:16379 | 同上 |

- 改 Java 代码：`docker compose up -d --build api` 或 `./scripts/rebuild-api.sh`
- 改前端：`coolpilot-web` / `coolpilot-admin` 是 bind mount，自动热更新（i18n 词条除外）
- 进数据库：`./scripts/db-shell.sh`

> 初始密码 `123456` **仅用于本地**。生产部署后必须立即修改，见 [docs/DEPLOY.md](docs/DEPLOY.md)。

## 生产部署

一套流程，不手工装环境：

```bash
git tag -a v1.0.2 -m "v1.0.2" && git push origin v1.0.2   # 触发 CI 构建四个镜像
```

服务器上：

```bash
cd /opt/coolpilot-platform
sed -i 's/^TAG=.*/TAG=v1.0.2/' .env
docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d
```

数据库迁移由 Flyway 在应用启动时自动执行，不需要手工建表。

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
│       │   ├── modules/              # 业务模块：nav / brand / social / portfolio / planet /
│       │   │                         #            account / product / trade / service / ai / file …
│       │   └── ...                   # 早期遗留包，只修 bug 不新增业务
│       └── resources/db/migration/   # Flyway 迁移 V1–V27（唯一的建表来源）
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

| 文档 | 内容 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 三端架构、模块边界、技术选型理由 |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | 59 张表的设计与取舍 |
| [docs/ED-04-API-DESIGN.md](docs/ED-04-API-DESIGN.md) | 接口约定、错误码、幂等设计 |
| [docs/ED-05-TASKS.md](docs/ED-05-TASKS.md) | 任务卡与验收标准 |
| [docs/DEPLOY.md](docs/DEPLOY.md) | 上线手册：gateway 分流、Cloudflare、证书、备份恢复、排查 |
| [docs/CI-CD.md](docs/CI-CD.md) | 流水线、镜像发布、发布与回滚 |
| [docs/DOCKER.md](docs/DOCKER.md) | 容器编排、Flyway 迁移、常见问题 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | 各版本变更明细 |
| [docs/BASELINE.md](docs/BASELINE.md) | 上游导入、基线定义、提交规范 |
| [docs/CP-WEB-01](docs/CP-WEB-01-现状核查与设计方案.md) / [02](docs/CP-WEB-02-功能范围与代码审计对照表.md) | 信息架构改造的现状核查与范围基线 |
| [docs/ADR-0001](docs/ADR-0001-VECTOR-STORE.md) · [0002](docs/ADR-0002-HYBRID-RETRIEVAL.md) · [0003](docs/ADR-0003-QUERY-REWRITE-RERANK.md) | 检索与问答的技术决策记录 |

## CI/CD

- 推 `main` 或提 PR：三端各做一次真实构建（Maven / Vite / Nuxt），编译不过直接红叉
- 打 `v*` tag：构建四个镜像并推 GHCR（`coolpilot-server` / `coolpilot-admin` / `coolpilot-web` / `coolpilot-gateway`）
- 回滚 = 换 tag 再 `up -d`

镜像包是公开的，服务器拉取不需要 `docker login`。

## 许可证

具体授权方式尚未确定，正式对外分发前需补齐。
