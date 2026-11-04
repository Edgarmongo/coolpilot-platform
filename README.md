# CoolSkyAI Platform

> **CoolSkyAI 技术平台** — 个人技术知识与服务平台
> 把文章、课程、文档、代码资源和技术服务放在一个地方，用一套账号打通「看内容 → 买内容 → 交付 → 售后」。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)](https://www.mysql.com/)

---

## 这是什么

一个个人站点的完整技术底座，不只是博客：

- **内容**：文章、动态、分类、标签、归档，含完整的编辑与审核流程
- **用户**：访客可自助注册登录，与站长后台身份彻底分离；收藏、点赞、站内消息、问题反馈
- **知识商品**：文档 / 课程 / 专栏 / 代码 / 技术服务五类商品，含目录、课时、试看与配套资源
- **交易**：下单 → 支付（人工确认收款）→ 发权益 → 下载/学习 → 退款，全链路幂等
- **文件**：私有文件不暴露直链，下载需一次性凭证 + 权益校验

暂不依赖微信支付/支付宝商户资质，先用「扫码转账 + 后台确认」把交易跑通，未来接官方渠道只需实现一个渠道接口。

## 当前进度

| 版本 | 内容 | 状态 |
|---|---|---|
| V1.0 | 访客体系、收藏/点赞、站内消息、问题反馈 | 已完成 |
| V1.1a | 文件服务、知识商品（列表/详情/目录/资源） | 已完成 |
| V1.1b | 订单、支付、权益、退款 | 已完成并实测 |
| V1.2 | 技术服务域（需求 / 报价 / 项目 / 里程碑） | 规划中 |
| V1.3 | AI 能力（知识库问答） | 规划中 |

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1、Spring Security + JWT、MyBatis、PageHelper |
| 数据库 | MySQL 8.0、Redis 7 |
| 前台 | Nuxt 3 + Vue 3.5 + TypeScript（`coolpilot-web`，SSR） |
| 后台 | Vue 3.5 + Vite + Element Plus + Pinia（`coolpilot-admin`） |
| 构建 | Maven、Vite、Nuxt、Docker Compose |

> 前端已于 2026-11-05 从 Vue 2 迁移到 Vue 3 / Nuxt 3，旧的 Vue 2 版本保留在 `v0.1.0-baseline` 标签中，迁移分批进行，进度见 [docs/FRONTEND-MIGRATION.md](docs/FRONTEND-MIGRATION.md)。

## 快速开始

```bash
# 1. 准备环境变量（.env 已被 gitignore，不会提交）
cp .env.example .env
# 修改 .env 中的 MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / JWT_SECRET

# 2. 启动全部服务（首次会构建镜像，Maven 依赖较慢）
docker compose up -d --build
# 或使用脚本
./scripts/dev-up.sh
```

| 服务 | 地址 | 说明 |
|---|---|---|
| 前台 | http://localhost:8081 | 访客侧，支持注册登录 |
| 后台 | http://localhost:8079/cms/ | 站长侧，账号 `admin`，密码见 `deploy/mysql/02-seed-base.sql` |
| 接口 | http://localhost:8091 | Spring Boot API（生产环境不对外暴露） |
| MySQL | localhost:13306 | 容器映射，避免与本机冲突 |
| Redis | localhost:16379 | 同上 |

改 Java 代码后重建后端：`docker compose up -d --build api`（前端源码是 bind mount，自动热更新）。

## 目录结构

```
coolpilot-platform/
├── coolpilot-server/    # Java 后端（Spring Boot 4.1 / JDK 21）
│   ├── src/main/java/com/coolskyai/
│   │   ├── controller/  # 内容、评论、友链、站点设置等接口
│   │   ├── modules/     # 业务模块：account / community / file / product / trade / entitlement
│   │   └── ...
│   ├── schema.sql       # 全量建表
│   └── sql/             # 增量迁移脚本（按日期命名）
├── coolpilot-admin/     # 管理后台（Vue 3 + Vite + Element Plus）
├── coolpilot-web/       # 用户前台（Nuxt 3 + TS，SSR）
├── deploy/              # 镜像与编排资产（Dockerfile、nginx、MySQL 种子）
├── scripts/             # 工程脚本（启停、重建、进库、备份）
├── docs/                # 架构、PRD、数据模型、接口、任务卡
├── .github/workflows/   # CI（构建校验）与 Release（镜像发布）
├── docker-compose.yml        # 本地开发
├── docker-compose.prod.yml   # 生产（拉 GHCR 镜像）
└── README.md
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 三端架构、模块边界、技术选型理由 |
| [docs/BASELINE.md](docs/BASELINE.md) | 上游导入、基线定义、提交与分支规范 |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | 39 张表的设计与取舍 |
| [docs/ED-04-API-DESIGN.md](docs/ED-04-API-DESIGN.md) | 接口约定、错误码、幂等设计 |
| [docs/ED-05-TASKS.md](docs/ED-05-TASKS.md) | 任务卡与验收标准 |
| [docs/V1.1B-TRADE.md](docs/V1.1B-TRADE.md) | 交易与权益闭环的实现与实测 |
| [docs/DOCKER.md](docs/DOCKER.md) | 容器编排、初始化顺序、常见问题 |
| [docs/CI-CD.md](docs/CI-CD.md) | 流水线、镜像发布、部署与回滚 |

## CI/CD

推送 `main` 或提 PR 时，GitHub Actions 会对三端各做一次真实构建（Maven / Vite / Nuxt），编译不过直接红叉。

打版本标签即触发镜像发布：

```bash
git tag -a v1.2.0 -m "v1.2.0"
git push origin v1.2.0
```

三个生产镜像推到 GHCR（`coolpilot-server` / `coolpilot-admin` / `coolpilot-web`），服务器只需 `docker compose -f docker-compose.prod.yml pull && up -d`。回滚就是换个 tag。详见 [docs/CI-CD.md](docs/CI-CD.md)。

## 许可证

具体授权方式尚未确定，正式对外分发前需补齐。
