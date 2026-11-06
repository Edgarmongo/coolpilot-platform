# deploy/ 部署配置

本目录只放**构建与部署资产**，不放业务代码。开发编排文件 `docker-compose.yml` 仍放在仓库根目录，方便 `docker compose up` 直接可用。

```
deploy/
├── docker/
│   ├── api/Dockerfile       # 后端多阶段构建（maven → temurin-jre21）
│   ├── api/settings.xml     # Maven 阿里云镜像，只作用于镜像内构建
│   └── web/Dockerfile       # 前端共用镜像（coolpilot-web / coolpilot-admin 通过 APP_DIR 区分）
└── mysql/
    ├── 02-seed-base.sql     # 上游脚本缺失的分类/标签种子
    └── 07-seed-product.sql  # 演示商品、课程、课时
```

## 构建上下文

两个 Dockerfile 都假定**构建上下文为仓库根目录**（见 `docker-compose.yml` 的 `context: .`），因此 `COPY` 路径写的是 `coolpilot-server/...`、`deploy/docker/server/settings.xml`。单独构建时必须在根目录执行：

```bash
docker build -f deploy/docker/server/Dockerfile -t coolpilot-server .
```

## 数据库初始化脚本

`deploy/mysql/*.sql` 与 `coolpilot-server/sql/*.sql` 会在**数据卷为空时**按文件名顺序被 MySQL 镜像执行，具体顺序见 `docs/DOCKER.md` 第六节。

## 生产环境

生产部署已经落地，**操作步骤见 [`docs/DEPLOY.md`](../docs/DEPLOY.md)**，本文件只说明资产放在哪。

```
deploy/
├── docker/
│   ├── server/Dockerfile     # 后端（Maven → JRE21）
│   ├── admin/Dockerfile      # 后台（构建 → nginx 静态）
│   ├── web/Dockerfile        # 前台（Nuxt build → node）
│   └── gateway/              # 反向代理：nginx.conf.template + 自签名兜底 + Dockerfile
├── systemd/                  # 备份 service/timer 模板
└── mysql/                    # 仅开发用的种子脚本（生产由 Flyway 负责）
```

要点：

- 生产编排在仓库根目录的 `docker-compose.prod.yml`（6 个服务：mysql / redis / api / web / admin / gateway）。
- 镜像走 GHCR，由 `.github/workflows/release.yml` 在**打 `v*` tag** 或手动触发时构建推送；服务器只 pull，不在机器上装 JDK/Node。
- 表结构与初始数据由应用启动时的 Flyway 迁移负责，`deploy/mysql/*.sql` 不参与生产初始化。
- 首次启动没有证书时 gateway 会生成自签名证书让服务先跑起来，随后用 `scripts/renew-cert.sh` 换正式证书。
