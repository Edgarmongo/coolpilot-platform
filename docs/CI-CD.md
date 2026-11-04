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
`V*.sql`，然后断言「业务表 39 张、site_setting ≥ 30 项」。
脚本语法错、顺序错、忘了建表，都会在 PR 阶段红掉。

几点说明：

- **不跑单元测试**。目前只有一个 `@SpringBootTest` 的 `contextLoads`，启动上下文需要 MySQL 和 Redis，CI 里没有。所以先只做 `test-compile`——**测试代码也要能编译**（改包名时最容易漏的就是测试目录）。
- **用 `npm ci` 而不是 `npm install`**。锁文件与 `package.json` 不一致时直接失败，这是刻意的：避免「本地能跑、CI 装了另一套依赖」。
- 只改了 `**.md` 或 `docs/**` 时跳过，不浪费额度。
- 同一分支连续推送会取消上一次未完成的运行（`concurrency`）。

## 二、Release 做什么

### 1. 三个镜像

| 镜像 | 基础 | 说明 |
|---|---|---|
| `ghcr.io/<owner>/coolpilot-server` | maven 构建 → JRE 21 运行 | 后端 jar，多阶段 |
| `ghcr.io/<owner>/coolpilot-admin` | node 构建 → nginx | 静态产物 + `/api` 反代 |
| `ghcr.io/<owner>/coolpilot-web` | node 构建 → node 运行 | Nitro 产物 `.output/server/index.mjs` |

`<owner>` 会自动小写（GHCR 不接受大写用户名）。

镜像 tag 同时打几个：

```
v1.2.0       ← 精确版本，部署用这个
1.2          ← 大版本浮动
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

### 3. 部署逻辑

```bash
cd $DEPLOY_PATH
export TAG=v1.2.0
docker compose -f docker-compose.prod.yml pull     # 拉新镜像
docker compose -f docker-compose.prod.yml up -d    # 只重建变化的容器
docker image prune -f                              # 清悬空镜像
```

`up -d` 只会重建镜像变了的服务，MySQL/Redis 不受影响。**数据卷不会被动**（`mysql_data` / `upload_data`）。

### 4. 反向代理与真实 IP（上线必看）

限流与友链浏览去重依赖客户端 IP。默认取 TCP 对端地址，**不信任** `X-Forwarded-For` ——
因为这个头客户端可以随便写，换一个伪造值就绕过限流与去重。

放在 nginx / Caddy 之后时，必须让代理**覆盖**而不是追加该头：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8091/;
    proxy_set_header Host $host;
    # 用 $remote_addr 覆盖，不要用 $proxy_add_x_forwarded_for（那会保留伪造值）
    proxy_set_header X-Forwarded-For $remote_addr;
}
```

确认代理会覆盖后，再在 `.env` 里打开开关：

```ini
# 对应 application 里的 security.trust-forwarded-for，默认 false
SECURITY_TRUST_FORWARDED_FOR=true
```

没接反代、或反代只是透传该头时保持 `false`，否则等于把限流键交给攻击者控制。

## 四、发布与回滚

### 发布

```bash
git tag -a v1.2.0 -m "v1.2.0: 交易闭环"
git push origin v1.2.0
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
