# deploy/ 部署配置

本目录只放**构建与部署资产**，不放业务代码。开发编排文件 `docker-compose.yml` 仍放在仓库根目录，方便 `docker compose up` 直接可用。

```
deploy/
├── docker/
│   ├── api/Dockerfile       # 后端多阶段构建（maven → temurin-jre21）
│   ├── api/settings.xml     # Maven 阿里云镜像，只作用于镜像内构建
│   └── web/Dockerfile       # 前端共用镜像（blog-view / blog-cms 通过 APP_DIR 区分）
└── mysql/
    ├── 02-seed-base.sql     # 上游脚本缺失的分类/标签种子
    └── 07-seed-product.sql  # 演示商品、课程、课时
```

## 构建上下文

两个 Dockerfile 都假定**构建上下文为仓库根目录**（见 `docker-compose.yml` 的 `context: .`），因此 `COPY` 路径写的是 `blog-api/...`、`deploy/docker/api/settings.xml`。单独构建时必须在根目录执行：

```bash
docker build -f deploy/docker/api/Dockerfile -t nblog-api .
```

## 数据库初始化脚本

`deploy/mysql/*.sql` 与 `blog-api/sql/*.sql` 会在**数据卷为空时**按文件名顺序被 MySQL 镜像执行，具体顺序见 `docs/DOCKER.md` 第六节。

## 生产环境

当前只有开发编排。生产部署（反向代理、HTTPS、备份）尚未落地，规划如下，落地时在本目录新增：

```
deploy/
├── nginx/            # 站点配置与证书说明
├── compose.prod.yml  # 生产编排（不含 dev server）
└── backup/           # 数据库与上传目录备份脚本
```
