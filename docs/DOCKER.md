# Docker 开发环境

> 目标：一条命令拉起 MySQL、Redis、后端、前台、后台，不再依赖本机装 Java/MySQL/Redis/Node。
> 适用：本地开发。生产部署请看后续 `deploy/` 方案。

---

## 一、前置要求

| 项 | 要求 |
|---|---|
| Docker Desktop | 已安装并启动（Linux 容器模式） |
| Docker Compose | v2+（`docker compose` 子命令） |
| 可用内存 | ≥ 4 GB（MySQL + Redis + 3 个应用容器） |
| 磁盘 | 首次构建约需 1.5 GB（含 Maven 依赖与两份 node_modules） |

验证：

```bash
docker --version
docker compose version
docker info --format '{{.OSType}}'
```

---

## 二、快速开始

```bash
cd coolpilot-platform

# 1. 准备环境变量（首次）
cp .env.example .env
#    编辑 .env，至少改掉 MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / JWT_SECRET
#    JWT_SECRET 生成：openssl rand -hex 48

# 2. 构建并启动
docker compose up -d --build

# 3. 查看状态
docker compose ps
docker compose logs -f api
```

启动完成后：

| 入口 | 地址 | 说明 |
|---|---|---|
| 前台 | http://localhost:8081 | 门户首页 `/home` |
| 后台 | http://localhost:8079/cms/ | 默认账号 `Admin` / `123456` |
| 后端 API | http://localhost:8091/site | 无页面，仅 JSON |
| MySQL | `localhost:13306` | 账号 root，密码取自 `.env` |
| Redis | `localhost:16379` | 密码取自 `.env` |

> MySQL / Redis 刻意映射到 **13306 / 16379**，避免与本机已安装的 3306 / 6379 冲突。
> 容器之间通过服务名互访（`mysql:3306`、`redis:6379`、`api:8091`），不经过宿主端口。

---

## 三、服务拓扑

```
                    宿主机
   ┌──────────────────┴───────────────────┐
   │  :8081 view        :8079 cms          │
   │      │ /api 代理         │ /api 代理   │
   │      └────────┬──────────┘            │
   │               ▼                        │
   │           :8091 api                    │
   │          ┌────┴────┐                   │
   │          ▼         ▼                   │
   │      mysql:3306  redis:6379            │
   └────────────────────────────────────────┘
```

| 容器 | 镜像 | 说明 |
|---|---|---|
| `coolpilot-mysql` | mysql:8.0 | 首次启动执行初始化 SQL |
| `coolpilot-redis` | redis:7-alpine | 开启密码，关闭 AOF |
| `ncoolpilot-server` | 本地构建（Maven + JRE21） | 多阶段构建，产出可执行 jar |
| `ncoolpilot-web` | 本地构建（node:22-alpine） | 依赖在镜像内，源码 bind mount |
| `ncoolpilot-admin` | 同上，`APP_DIR=coolpilot-admin` | 与 view 共用 Dockerfile |

---

## 四、配置注入

后端配置文件（`application-dev.properties`）里的本机地址**没有被修改**，全部由 compose 通过环境变量覆盖，Spring Boot 的环境变量优先级高于配置文件：

| 环境变量 | 覆盖的配置项 |
|---|---|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` → 指向 `mysql:3306` |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 |
| `SPRING_DATA_REDIS_HOST` / `PORT` / `PASSWORD` | Redis 连接 |
| `JWT_SECRET` | `token.secretKey` |
| `UPLOAD_FILE_PATH` | `upload.file.path` → `/data/upload/` |
| `BLOG_API` | `blog.api` → 浏览器侧访问地址 |
| `API_TARGET`（前端） | `vue.config.js` 的 devServer 代理目标 |

这意味着**本机直接跑和容器里跑可以共存同一份配置文件**，不用来回改。

---

## 五、数据持久化

| 卷 | 挂载点 | 内容 |
|---|---|---|
| `mysql_data` | `/var/lib/mysql` | 数据库全部数据 |
| `upload_data` | `/data/upload` | 上传文件 |
| `view_node_modules` | `/app/node_modules` | 前台依赖（避免被源码挂载覆盖） |
| `cms_node_modules` | `/app/node_modules` | 后台依赖 |

```bash
docker compose down          # 停止，数据保留
docker compose down -v       # 停止并删除数据卷（数据库清空，慎用）
```

---

## 六、数据库初始化顺序

MySQL 官方镜像**只在数据卷为空时**执行 `/docker-entrypoint-initdb.d` 下的脚本，按文件名排序：

| 顺序 | 文件 | 作用 |
|---|---|---|
| 01 | `coolpilot-server/schema.sql` | 建表 + 站点配置 |
| 02 | `deploy/mysql/02-seed-base.sql` | 分类、标签种子（上游脚本不含） |
| 03 | `coolpilot-server/demo-data.sql` | 5 篇演示文章 |
| 04 | `coolpilot-server/sql/2026-11-04-indexes-and-constraints.sql` | 索引与唯一约束 |
| 05 | `coolpilot-server/sql/2026-11-04-v1-0-community.sql` | 社区模块 4 张表 |
| 06 | `coolpilot-server/sql/2026-11-05-v1-1a-file-product.sql` | 文件服务 + 商品域 10 张表 |
| 07 | `deploy/mysql/07-seed-product.sql` | 演示商品、课程、课时 |
| 08 | `coolpilot-server/sql/2026-11-05-v1-1b-order-payment-entitlement.sql` | 交易域 + 权益域 6 张表，以及收款配置项 |

> **修改初始化脚本后不会自动重跑**。需要重建：
> `docker compose down -v && docker compose up -d`
> 这会清空数据，确认没有要保留的内容再执行。

---

## 七、开发工作流

### 前端（热更新）

`./coolpilot-web`、`./coolpilot-admin` 以 bind mount 挂到容器 `/app`，改 `src` 下任何文件都会触发热更新。

如果热更新不生效（Windows 常见），已通过 `CHOKIDAR_USEPOLLING=true` 兜底；仍无反应时：

```bash
docker compose restart view
```

### 后端（需重建）

后端是编译产物运行，改 Java 代码后需要重新构建镜像：

```bash
docker compose up -d --build api
```

耗时主要在 Maven 层，依赖已用 BuildKit 缓存（`/root/.m2`），改代码通常 1–2 分钟。

### 依赖变更

改了 `package.json` 或 `pom.xml` 后必须重建对应镜像：

```bash
docker compose build --no-cache view   # 前端依赖
docker compose up -d --build api       # 后端依赖
```

---

## 八、常用命令

```bash
docker compose ps                      # 状态
docker compose logs -f --tail=100 api  # 后端日志
docker compose restart view            # 重启单个服务
docker compose exec api sh             # 进后端容器
docker compose exec mysql mysql -uroot -p coolpilot   # 进数据库
docker compose exec redis redis-cli -a "$REDIS_PASSWORD"
docker compose build --progress=plain  # 排查构建问题时看详细输出
```

---

## 九、与本地运行方式的关系

两种方式**不要同时启动**，会争抢 8081 / 8079 / 8091。

| 方式 | 启动 | 适用 |
|---|---|---|
| Docker | `docker compose up -d --build` | 默认方式，环境一致 |
| 本地 | 见 `docs/ARCHITECTURE.md` 启动手册 | 临时调试、需要 IDE 断点时 |

从本地切到 Docker 前，先停掉占用端口的进程：

```powershell
# 停本机后端与前端 dev server
Get-CimInstance Win32_Process |
  Where-Object { $_.CommandLine -like '*coolpilot-server-0.0.1.jar*' -or $_.CommandLine -like '*vue-cli-service*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

---

## 十、常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `port is already allocated` | 8081/8079/8091 被本机进程占用 | 按上节命令停止本地进程，或改 compose 的宿主端口 |
| 首次 `up` 卡很久 | 拉取 maven/temurin/node 基础镜像 | `docker pull` 单独执行可看到进度 |
| 后台打开是 404 | 后台挂在 `/cms/` 子路径 | 用 `http://localhost:8079/cms/` |
| 登录后接口 403 | JWT_SECRET 变更导致旧 token 失效 | 重新登录 |
| 改了初始化 SQL 没生效 | 数据卷已存在，脚本不再执行 | `down -v` 后重建 |
| 前端热更新延迟 | Windows 文件事件不穿透 | 已开轮询；仍慢可 `restart view` |
| 数据库连不上 | 首次启动 MySQL 初始化需 20–40 秒 | 等 healthcheck 变 `healthy`，`api` 会自动等待 |

---

## 十一、后续扩展

| 场景 | 建议 |
|---|---|
| 生产部署 | 单独 `docker-compose.prod.yml`：前端改 Nginx 静态站点，API 用同一镜像、只暴露内网 |
| 文件存储 | 现在挂 `upload_data`；后续可换 MinIO 容器并切换 `storage_type` |
| AI 服务 | 增加 `ai` 服务（Python FastAPI），通过 `http://ai:8000` 互访 |
| 数据库迁移 | 引入 Flyway，把 `sql/*.sql` 收编为版本化脚本，替代 initdb 顺序约定 |
