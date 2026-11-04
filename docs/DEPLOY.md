# 上线与运维手册

> 目标：把仓库里的镜像部署到一台干净的服务器，对外只暴露 80/443，证书自动可换，备份可恢复。
> 如果你是本地开发，看 `docs/DOCKER.md` 就够了，本文只讲生产。

---

## 一、目标拓扑

```
                 Internet
                    │  80 / 443
          ┌─────────▼──────────┐
          │  gateway (nginx)   │  唯一对外容器
          │  TLS / gzip / 分流  │
          └──┬──────┬──────┬───┘
      /api/**│  /cms│      │/**
             │      │      │
        ┌────▼──┐ ┌─▼────┐ ┌▼─────┐
        │  api  │ │admin │ │ web  │
        │ 8091  │ │ :80  │ │ 8081 │
        └───┬───┘ └──────┘ └──────┘
            │
     ┌──────┴──────┐
   ┌─▼───┐     ┌───▼──┐
   │MySQL│     │Redis │   不映射端口，只走 compose 内网
   └─────┘     └──────┘
```

- **只有 gateway 映射宿主端口**（80/443），其余服务都在内网。
- 镜像来自 GHCR，服务器上不需要 JDK / Node / 源码。
- 数据落在两个卷：`mysql_data`、`upload_data`。

---

## 二、前置条件

| 项 | 要求 |
|---|---|
| 服务器 | 2C4G 起步；Docker 24+ 与 compose 插件 |
| 域名 | A 记录指向服务器 IP；`www` 可选但要和 `SERVER_NAME` 一致 |
| 端口 | 安全组/防火墙只放 22、80、443；**不要**放 8081/8079/8091 |
| 代码 | 仓库已打 tag（`v1.2.0`），Release 已把四个镜像推到 GHCR |

---

## 三、首次部署

### 1. 准备目录

服务器上不需要 clone 源码，只放两个文件 + 两个目录：

```bash
mkdir -p /opt/coolpilot-platform && cd /opt/coolpilot-platform
# 从仓库拷 docker-compose.prod.yml 与 .env.example
scp <repo>/docker-compose.prod.yml .
scp <repo>/.env.example .

mkdir -p certs certbot/www backups
cp .env.example .env && chmod 600 .env
```

### 2. 填 `.env`

至少改这几项：

```ini
MYSQL_ROOT_PASSWORD=<强密码>
REDIS_PASSWORD=<强密码>
JWT_SECRET=<openssl rand -hex 48 生成，禁止用示例值>
REGISTRY=ghcr.io/edgarmongo
TAG=v1.2.0
SITE_URL=https://coolskyai.com
SERVER_NAME=coolskyai.com www.coolskyai.com
```

`JWT_SECRET` 换了之后，所有已签发的 token 立即失效，属于预期行为。

### 3. 登录 GHCR 并启动

```bash
echo "<PAT，带 read:packages 权限>" | docker login ghcr.io -u Edgarmongo --password-stdin

docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

首次启动 Flyway 会自动建表 + 灌种子数据（约 10 秒）。
没有证书时 gateway 会生成**自签名证书**，此时浏览器会告警，先别急，下一步换正式证书。

### 4. 先改默认账号

默认账号 `Admin / 123456` 写在 `V2__init_seed.sql` 里，上线前必须改：

```bash
docker exec -it coolpilot-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" coolpilot
# 生成 bcrypt 比较麻烦，直接走后台「账号设置」改密码更省事：
#   浏览器打开 https://域名/cms/ → 登录 → 系统管理 → 账号设置
```

---

## 四、证书（Let's Encrypt）

前置：域名已解析、80 端口可从公网访问、gateway 已启动。

```bash
cd /opt/coolpilot-platform
CERT_EMAIL=you@example.com ./scripts/renew-cert.sh
```

脚本做四件事：certbot 申请 → 拷到 `./certs/` → `nginx -s reload` → 打印到期时间。
续期同理，certbot 自己会判断是否快到期（`--keep-until-expiring`），所以挂定时任务也不会频繁签发。

**自动续期**（每月 1 号跑一次足够）：

```bash
crontab -e
30 4 1 * * cd /opt/coolpilot-platform && CERT_EMAIL=you@example.com ./scripts/renew-cert.sh >> logs/cert.log 2>&1
```

> 换证书不需要重启容器，`nginx -s reload` 即可。脚本里已经做了。
> 如果你有多个域名，`renew-cert.sh` 只处理 `SERVER_NAME` 的第一个；
> 其余域名用 `-d` 追加，或直接改脚本里的 `-d` 参数。

---

## 五、日常发布与回滚

### 发布

```bash
git tag -a v1.2.1 -m "v1.2.1: ..."
git push origin v1.2.1
```

Release 构建完四个镜像后（开了 `DEPLOY_ENABLED` 会自动部署），服务器上：

```bash
cd /opt/coolpilot-platform
sed -i 's/^TAG=.*/TAG=v1.2.1/' .env
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

`up -d` 只重建镜像变了的服务，MySQL/Redis 不受影响，数据卷不会动。

### 回滚

镜像不可变，回滚就是改回上一个 tag：

```bash
sed -i 's/^TAG=.*/TAG=v1.2.0/' .env
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

> 如果新版本包含 Flyway 迁移，回滚**代码**不会回滚**表结构**。
> 迁移请遵循「只加不减」：加列、加表可以，删列/改类型不要做，否则回滚会炸。

---

## 六、备份与恢复

### 手动备份

```bash
cd /opt/coolpilot-platform
./scripts/backup.sh                 # 输出到 ./backups
KEEP_DAYS=30 ./scripts/backup.sh    # 保留 30 天
```

产出两个文件：`db-<时间>.sql.gz`（mysqldump，含存储过程/触发器）与 `upload-<时间>.tar.gz`（上传目录）。

### 定时备份（systemd timer）

```bash
sudo cp deploy/systemd/coolpilot-backup.service /etc/systemd/system/
sudo cp deploy/systemd/coolpilot-backup.timer   /etc/systemd/system/
# 改 service 里的 WorkingDirectory / ExecStart 为实际路径
sudo systemctl daemon-reload
sudo systemctl enable --now coolpilot-backup.timer
systemctl list-timers | grep coolpilot        # 确认下次执行时间
journalctl -u coolpilot-backup -n 50          # 看执行日志
```

### 恢复

```bash
cd /opt/coolpilot-platform

# 1) 停应用，避免恢复过程中继续写库
docker compose -f docker-compose.prod.yml stop api web admin gateway

# 2) 恢复数据库（会覆盖现有数据）
gunzip < backups/db-20261105-033000.sql.gz \
  | docker exec -i coolpilot-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" coolpilot

# 3) 恢复上传目录
MSYS_NO_PATHCONV=1 docker run --rm \
  -v coolpilot_upload_data:/data \
  -v "$PWD/backups:/backup" alpine \
  sh -c "rm -rf /data/* && tar xzf /backup/upload-20261105-033000.tar.gz -C /data"

# 4) 启动并验证
docker compose -f docker-compose.prod.yml up -d
curl -fsS https://coolskyai.com/api/health
```

> 备份文件含全站用户数据与联系方式，**不要**放进仓库、不要发到公开渠道。
> 建议每周把 `backups/` 同步到另一台机器或对象存储（`rclone` / `ossutil` 均可）。

---

## 七、健康检查与监控

| 端点 | 含义 | 用途 |
|---|---|---|
| `GET /health` | 检查 DB + Redis，全通则 200，否则 503 | 监控/负载均衡 |
| `GET /health/live` | 只证明进程能响应 | 容器 healthcheck（不因数据库抖动而重启） |
| `GET /healthz`（gateway） | nginx 自身存活 | 入口探活 |

```bash
curl -fsS https://coolskyai.com/api/health
# {"time":"...","uptimeSeconds":123,"db":"UP","redis":"UP","status":"UP"}
```

**知识库索引**：文章/商品/课时有增删改后，需要到后台 `智能检索 → 知识库索引` 点一次「重建索引」。
全量替换，几秒完成；没重建只是搜不到新内容，不会报错。

最小监控建议：

- 外部探活：每 1 分钟请求 `/api/health`，连续 3 次失败告警（UptimeRobot / 阿里云监控都行）。
- 磁盘：`df -h`，上传目录和备份会持续增长；`docker system df` 看悬空镜像。
- 日志：`docker compose -f docker-compose.prod.yml logs -f --tail=200 api`，日志已限制 10MB×3 轮转。

---

## 八、排查表

| 现象 | 常见原因 | 处理 |
|---|---|---|
| 502 Bad Gateway | api 容器没起来 / 正在启动 | `docker ps`、`docker logs coolpilot-server --tail=100` |
| 502 只在 /cms/ | admin 容器挂了 | `docker logs coolpilot-admin` |
| 浏览器证书告警 | 还是自签名证书 | 跑 `scripts/renew-cert.sh`，确认 80 端口通 |
| 上传 413 | 文件超过 100MB | 调 gateway 与 admin nginx 的 `client_max_body_size`，后端也有上限 |
| 首页空白但接口正常 | web 容器 SSR 报错 | `docker logs coolpilot-web`，注意 `NUXT_API_BASE` 是否指向 `http://api:8091` |
| 登录后立刻掉线 | `JWT_SECRET` 变更 / 多实例密钥不一致 | 确认 `.env` 只有一个值并 `up -d` 重建 |
| 图片 404 | `BLOG_API`/`SITE_URL` 与实际域名不一致 | 库里存的是绝对地址，改完要刷缓存 + 重新上传 |
| 接口 403 但已登录 | 新接口没归入 `SecurityPaths` | 见 `docs/ARCHITECTURE.md` 4.4，加进 AUTHENTICATED/PUBLIC |
| 时间差 8 小时 | 容器 TZ 未生效 | compose 已设 `TZ=Asia/Shanghai`，检查是否被覆盖 |

---

## 九、上线安全检查清单

- [ ] `JWT_SECRET` 已换成 64 位随机值，且**不在**任何聊天记录/仓库里
- [ ] MySQL/Redis 密码已换，`.env` 权限 600
- [ ] 默认账号 `Admin / 123456` 已改
- [ ] 防火墙只开 22/80/443，`docker ps` 里没有 `0.0.0.0:8091` 之类的映射
- [ ] `https://域名/api/health` 返回 200 且 `db`、`redis` 都是 `UP`
- [ ] `/cms/` 可以正常登录，浏览器地址栏是锁标
- [ ] 备份定时任务已启用，`systemctl list-timers` 有记录，且**实际恢复过一次**
- [ ] `security.trust-forwarded-for` 与入口方式匹配（用自带 gateway → true；裸奔 → false）
- [ ] 已确认 `BLOG_API` / `BLOG_VIEW` / `SITE_URL` 三处都是 https 域名
