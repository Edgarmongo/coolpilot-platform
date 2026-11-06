# CI/CD

两条流水线，都在 `.github/workflows/` 下：

| 文件 | 触发 | 作用 |
|---|---|---|
| `ci.yml` | push 到 main、PR、手动 | 三端各做一次真实构建，编译不过就红叉 |
| `release.yml` | 推送 `v*` 标签、手动 | 构建三个生产镜像推到 GHCR，可选 SSH 部署 |

## 一、CI 做什么

四个 job 并行跑，互不阻塞：

| Job | 环境 | 命令 | 产物 |
|---|---|---|---|
| `server` | Temurin JDK 21 + Maven 缓存 | `mvn -DskipTests package` | `server-jar` |
| `migrations` | MySQL 8 service + Flyway CLI 镜像 | `flyway migrate` + 表数断言 | — |
| `admin` | Node 22 + npm 缓存 | `npm ci` → `npm run build` | `admin-dist` |
| `web` | Node 22 + npm 缓存 | `npm ci` → `npm run build` | `web-output` |

`migrations` 这个 job 值得单独说：**迁移脚本是表结构的唯一来源**，写错一条就等于
发布一个起不来的版本。CI 里起一个空 MySQL，用 `flyway/flyway:11` 真跑一遍
`V*.sql`，然后断言「业务表 ≥ 53 张、site_setting ≥ 30 项」。
用下限而不是等值：后续版本还会加表，等值断言每加一张表就要改 CI。
（当前实际 59 张 / V27。）
脚本语法错、顺序错、忘了建表，都会在 PR 阶段红掉。

几点说明：

- **不跑单元测试**。目前只有一个 `@SpringBootTest` 的 `contextLoads`，启动上下文需要 MySQL 和 Redis，CI 里没有。所以先只做 `test-compile`——**测试代码也要能编译**（改包名时最容易漏的就是测试目录）。
- **用 `npm ci` 而不是 `npm install`**。锁文件与 `package.json` 不一致时直接失败，这是刻意的：避免「本地能跑、CI 装了另一套依赖」。
- 只改了 `**.md` 或 `docs/**` 时跳过，不浪费额度。
- 同一分支连续推送会取消上一次未完成的运行（`concurrency`）。

## 二、Release 做什么

### 1. 四个镜像

| 镜像 | 基础 | 说明 |
|---|---|---|
| `ghcr.io/<owner>/coolpilot-server` | maven 构建 → JRE 21 运行 | 后端 jar，多阶段 |
| `ghcr.io/<owner>/coolpilot-admin` | node 构建 → nginx | 静态产物 + `/api` 反代 |
| `ghcr.io/<owner>/coolpilot-web` | node 构建 → node 运行 | Nitro 产物 `.output/server/index.mjs` |
| `ghcr.io/<owner>/coolpilot-gateway` | nginx + openssl | **生产唯一对外入口**：80→443、按路径分发、证书挂载 |

`<owner>` 会自动小写（GHCR 不接受大写用户名）。

镜像 tag 同时打几个：

```
v1.0.1       ← 精确版本，部署用这个
1.0          ← 大版本浮动
sha-1a5960f  ← 按提交定位，排查用
latest       ← 只在 tag 时更新
```

### 2. 前端镜像为什么不是同一个 Dockerfile

`deploy/docker/web/Dockerfile` 是**开发镜像**：跑 `vite dev` / `nuxt dev`，靠 dev server 的 proxy 转发 `/api`，源码 bind mount 热更新。

生产不能用它——dev server 带着 HMR、源码映射、按需编译，内存高、启动慢、暴露面大。所以另有两个：

- `deploy/docker/admin/Dockerfile` → nginx 托管 `dist`，用 `nginx.conf` 把 `/api/**` 反代并去掉前缀（与 vite proxy 的 rewrite 行为一致，前端代码不用改）
- `deploy/docker/web/Dockerfile.prod` → 只用 `.output`，`node .output/server/index.mjs`，Nitro 的 `/api/**` 代理逻辑是编译进产物的，运行时靠 `NUXT_API_BASE` 指向后端

## 三、启用部署（可选）

默认**不部署**，只构建镜像。要开启：

### 1. 仓库设置

Settings → Secrets and variables → Actions

**Variables**

| 名称 | 值 |
|---|---|
| `DEPLOY_ENABLED` | `true` |

**Secrets**

| 名称 | 说明 |
|---|---|
| `DEPLOY_HOST` | 服务器 IP 或域名 |
| `DEPLOY_USER` | SSH 用户（需在 docker 组） |
| `DEPLOY_SSH_KEY` | SSH 私钥全文（对应公钥已加入服务器 `authorized_keys`） |
| `DEPLOY_PATH` | 服务器上的项目目录，如 `/opt/coolpilot-platform` |

### 2. 服务器一次性准备

```bash
# 1) 安装 Docker + compose 插件（略）

# 2) 建目录，只放两个文件，不需要源码
mkdir -p /opt/coolpilot-platform && cd /opt/coolpilot-platform
# 把仓库里的 docker-compose.prod.yml 拷过来
# 把 .env.example 拷成 .env 并改真实值

# 3) 登录 GHCR（包是私有的，不登录无法 pull）
#    PAT 需要 read:packages 权限
echo "<YOUR_PAT>" | docker login ghcr.io -u Edgarmongo --password-stdin

# 4) 首次启动
REGISTRY=ghcr.io/edgarmongo TAG=latest docker compose -f docker-compose.prod.yml up -d
```

`.env` 里至少要改这几项：

```ini
MYSQL_ROOT_PASSWORD=<强密码>
REDIS_PASSWORD=<强密码>
JWT_SECRET=<64 位随机，绝不能用示例值>
REGISTRY=ghcr.io/edgarmongo
TAG=latest
SITE_URL=https://coolskyai.com
```

> `JWT_SECRET` 必须有 64 字符以上。之前的审计里发现过默认密钥可离线伪造管理员 token 的问题，生产环境换掉是硬要求。
>
> 完整的首次上线、证书、备份恢复与排查清单见 [DEPLOY.md](DEPLOY.md)。

### 3. 部署逻辑

```bash
cd $DEPLOY_PATH
export TAG=v1.0.1
docker compose -f docker-compose.prod.yml pull     # 拉新镜像
docker compose -f docker-compose.prod.yml up -d    # 只重建变化的容器
docker image prune -f                              # 清悬空镜像
```

`up -d` 只会重建镜像变了的服务，MySQL/Redis 不受影响。**数据卷不会被动**（`mysql_data` / `upload_data`）。

### 4. 统一入口与真实 IP（上线必看）

生产**只用 gateway 一个容器对外**，其余服务不映射端口：

```
80  → 301 到 443（保留 /.well-known/acme-challenge 给证书校验）
443 → /api/**   api:8091（去掉 /api 前缀）
      /image/** api:8091（图片直链，库里存的是绝对地址）
      /cms/**   admin:80
      /**       web:8081（Nuxt SSR，含 sitemap.xml / robots.txt）
```

gateway 的 `server_name` 取 `.env` 的 `SERVER_NAME`（多个域名用空格分隔）。
证书放在 `./certs/fullchain.pem` 与 `./certs/privkey.pem`；**首次启动没有证书时会自动生成自签名**，
保证容器能起来，之后用 `./scripts/renew-cert.sh` 换成 Let's Encrypt 正式证书，完整步骤见 `docs/DEPLOY.md`。

限流与友链浏览去重依赖客户端 IP。后端默认取 TCP 对端地址、**不信任** `X-Forwarded-For`；
gateway 会用 `$remote_addr` **覆盖**该头（不是追加），所以生产 compose 里
`SECURITY_TRUST_FORWARDED_FOR` 默认是 `true`。

如果你不用自带 gateway，而是自己在外层再放一层 nginx/Caddy，那外层也必须覆盖该头：

```nginx
proxy_set_header X-Forwarded-For $remote_addr;   # 不要用 $proxy_add_x_forwarded_for
```

否则等于把限流键交给攻击者控制。

## 四、发布与回滚

### 发布

```bash
git tag -a v1.0.1 -m "v1.0.1: 品牌名按语言区分"
git push origin v1.0.1
```

推 tag 触发 release，镜像好了自动部署（如果开了 `DEPLOY_ENABLED`）。

### 回滚

镜像不可变，回滚就是换 tag：

```bash
# 服务器上
cd /opt/coolpilot-platform
TAG=v1.1.0 docker compose -f docker-compose.prod.yml up -d
```

或者改 `.env` 的 `TAG=` 后重新 `up -d`。**不需要重新构建**，旧镜像还在。

## 五、本地等价命令

CI 跑的东西，本地都能复现：

```bash
# 后端
cd coolpilot-server && mvn -B -ntp -DskipTests package

# 后台
cd coolpilot-admin && npm ci && npm run build

# 前台
cd coolpilot-web && npm ci && npm run build

# 生产镜像（不推送，只验证能不能构建）
docker build -f deploy/docker/admin/Dockerfile -t coolpilot-admin:test .
docker build -f deploy/docker/web/Dockerfile.prod -t coolpilot-web:test .
```

## 六、已知限制

- **CI 不跑测试**：唯一一个 `@SpringBootTest` 需要真实 MySQL/Redis。要跑起来得加 `services: mysql/redis` 或 Testcontainers，属于独立一件事。目前靠人工回归 + 构建校验兜底。
- **没有前端 lint/typecheck**：两端都没配 ESLint，Nuxt 的 `typescript.strict` 也是 `false`。加 lint 会先炸出一堆历史问题，要单独一轮清理。
- **数据库迁移在应用启动时执行**：Flyway 已接入，表结构变更写成 `db/migration/V3__xxx.sql`，随镜像一起发布、随容器启动自动执行。这意味着升级顺序天然正确（先迁移后对外提供服务），不需要再手工跑 SQL。但**回滚镜像不会回滚数据库**，涉及破坏性变更（删列、改类型）时要单独准备逆向脚本。
- **部署不健康检查**：`up -d` 返回成功不代表服务真的起来了。需要的话可以在 SSH 脚本末尾加 `curl -f http://127.0.0.1:8081/` 之类的探活。
