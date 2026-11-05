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

---

## 十、业务通知（邮件）

站内消息不需要任何配置：订单、退款、需求、报价、项目验收的关键节点都会写一条，
用户在前台「我的 → 消息通知」里能看到，未读数显示在顶栏。

邮件是可选增强。不配置时系统**只写站内消息**，启动日志会有一行
`邮件通知未配置（MAIL_PASSWORD 为空），当前仅写站内消息`。

### 开启步骤

1. 准备一个支持 SMTP 的发信邮箱（阿里云、腾讯企业邮、Gmail 应用专用密码都可以），
   在 `.env` 里填：

```ini
MAIL_PASSWORD=发信邮箱的授权码
```

2. 确认 `application-dev.properties` 里的发信账号与服务器和你的邮箱服务商一致
   （默认是阿里云 `smtp.mxhichina.com:465`，SSL）：

```properties
spring.mail.host=smtp.mxhichina.com
spring.mail.port=465
spring.mail.username=admin@example.com   # 改成你的发信地址
spring.mail.password=${MAIL_PASSWORD:}
```

3. 收件地址：

| 收件人 | 取值 |
|---|---|
| 平台所有者 | 后台「页面管理 → 站点设置 → 资料卡 → 邮箱」，未填则回落到发信账号 |
| 前台客户 | 用户注册/资料里填的邮箱，没填就不发 |

4. 邮件里的链接前缀由两个变量决定，上线后必须设成正式域名：

```ini
BLOG_VIEW=https://coolskyai.com
BLOG_CMS=https://coolskyai.com/cms
```

### 验证与排查

- 触发一次「提交需求」：所有者应收到邮件，日志无 WARN；
- 日志出现 `发送模板邮件失败 ... Authentication failed` → 授权码错误或未开 SMTP 服务；
- 出现 `Connection timed out` → 服务器出网被封 465 端口，换 587 + STARTTLS 或找云厂商开白名单；
- 业务接口始终返回成功是**预期行为**：通知失败不回滚业务。

---

## 十一、智能问答（大模型）

问答只依赖 **OpenAI 兼容协议**（`/chat/completions`），本地模型与云端服务用同一套配置切换。
不配置也能用：只返回检索片段，不调用模型、不产生费用。

### 选型对照

| 部署 | base-url | 模型示例 | 说明 |
|---|---|---|---|
| 本地 Ollama | `http://host.docker.internal:11434/v1` | `qwen2.5:7b` | 不需要 Key；2C4G 建议 3B/4B 量化模型 |
| 本地 vLLM / LM Studio | `http://host.docker.internal:8000/v1` | 自选 | 需要 GPU 才有速度优势 |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-flash` | 便宜、中文好，需 Key；只有对话接口，没有 embeddings |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | 需 Key |

### 配置

```ini
AI_LLM_BASE_URL=http://host.docker.internal:11434/v1
AI_LLM_API_KEY=            # 本地模型留空；云端填对应 Key
AI_LLM_MODEL=qwen2.5:7b
AI_LLM_TIMEOUT=60          # 秒，本地小机器可以放宽到 120
AI_LLM_MAX_TOKENS=800
AI_LLM_TEMPERATURE=0.3
# 厂商专有参数（JSON，原样并入请求体，不覆盖标准字段）
# DeepSeek V4 默认开思考模式，只要正文：{"thinking":{"type":"disabled"}}
AI_LLM_EXTRA_BODY=
```

> **DeepSeek 注意**：它的 OpenAI 兼容层只有对话接口，**没有 embeddings**。
> 用 DeepSeek 生成时，向量侧请继续用本地 bge-m3（或通义 `text-embedding-v4` 等），
> 两边各配各的：`AI_LLM_*` 指向 DeepSeek，`AI_EMBEDDING_*` 指向向量服务。
> 模型名以接口返回为准：`curl -s $AI_LLM_BASE_URL/models -H "Authorization: Bearer $KEY"`。
> 2026-11 实测可用的是 `deepseek-flash`（V4.1 Flash）与 `deepseek-v4-pro`；
> 旧的 `deepseek-chat` / `deepseek-reasoner` 已退役，写成 `deepseek-v4.1-flash:free` 这类
> 带后缀的名字会被拒（`The supported API model names are ...`）。
>
> V4.1 默认开启思考模式，直接调用会先返回一段 `reasoning_content`：耗时翻倍、
> 流式还容易出现「等好几秒没输出」。只要正文就配：
>
> ```ini
> AI_LLM_EXTRA_BODY='{"thinking":{"type":"disabled"}}'
> ```
>
> 实测同一句话：开思考 78 tokens / 44 个推理 token，关掉 11 tokens。

改完 `docker compose -f docker-compose.prod.yml up -d api` 生效。

### 向量模型（检索侧）

检索与生成是两套配置，可以只配其一：

```ini
# hashing（默认，离线字面召回）或 openai（任意 OpenAI 兼容 /embeddings）
AI_EMBEDDING_PROVIDER=openai
AI_EMBEDDING_BASE_URL=http://host.docker.internal:11434/v1
AI_EMBEDDING_API_KEY=        # 本地留空，云端填 Key
AI_EMBEDDING_MODEL=bge-m3
AI_EMBEDDING_DIM=1024        # 必须与模型一致：bge-m3=1024，text-embedding-3-small=1536
AI_EMBEDDING_BATCH=16        # 重建索引时每批条数
# 混合检索权重：向量占比，其余给关键词
AI_SEARCH_HYBRID_ALPHA=0.65
```

### 追问改写与重排（可选）

```ini
# 会话里的「那退款呢」先补全成独立问题再检索；失败自动回退到原话
AI_CHAT_REWRITE=true
AI_CHAT_REWRITE_HISTORY=3      # 带进 prompt 的历史轮数（用改写后的句子）
AI_CHAT_REWRITE_ANSWER_ROUNDS=2 # 带进 prompt 的回答轮数（序号指代常指向更早那条）
AI_CHAT_REWRITE_ANSWER=300      # 单条回答节选长度（≈列表前三条）
AI_CHAT_REWRITE_ANSWER_BUDGET=600  # 回答节选总预算，超出按轮数均分
AI_CHAT_REWRITE_MAX_ROUNDS=5    # 首次没解开时最多回溯几轮（序号指代不依赖它）
AI_CHAT_REWRITE_ESCALATE=true   # 关掉就固定轮数，不再回溯
AI_CHAT_REWRITE_CACHE=3600      # 改写结果缓存秒数（键含模型名）

# 重排：none / llm / http
#   llm  —— 用同一个对话模型做列表排序，不需要额外部署（本项目当前用这个）
#   http —— 接 bge-reranker 服务（Infinity / TEI / Jina / Cohere）
AI_RERANK_PROVIDER=llm
AI_RERANK_BASE_URL=            # provider=http 时填，例 http://host.docker.internal:8080
AI_RERANK_API_KEY=
AI_RERANK_MODEL=bge-reranker-v2-m3
AI_RERANK_CANDIDATES=20        # 第一轮多取多少候选交给重排挑
# 严格度：一次决定截断下限 / 相对下限 / 兜底分数线
#   strict（1 / 0.7 / 0.6）只留能直接回答的，宁可空着
#   balanced（2 / 0.5 / 0.5）默认
#   loose（3 / 0.35 / 0.35）宁多勿漏
AI_RERANK_STRICTNESS=balanced
AI_RERANK_MIN_KEEP=            # 留空=按严格度推导，填数字覆盖
AI_RERANK_SCORE_MODE=auto      # 仅 http：auto / logit / probability，logits 自动过 sigmoid
AI_RERANK_RELATIVE_FLOOR=      # 留空=按严格度推导；0=关闭，填数覆盖
AI_RERANK_MIN_SCORE=0          # 仅 http：绝对下限（归一化后 0~1），0=不启用
AI_RERANK_CACHE=900            # 重排结果缓存秒数
```

`AI_RERANK_STRICTNESS` 就是「不用标定」的答案：交叉编码器的分数量纲各不相同，
但「与最高分的比例」语义一致，相对下限天然跨模型；再把它和截断下限、兜底分数线
绑成三档语义，部署方只需回答一个问题 —— 你更怕漏还是更怕噪音。
真要业务硬卡某条线时才动 `AI_RERANK_MIN_SCORE`。

改之前先看数据：后台「知识库索引 › 检索质量」（`GET /admin/ai/quality?days=7`）会给
0 引用占比、平均引用数、改写率与引用分布，并据此建议 strict / balanced / loose。
建议只是建议 —— 系统不会自己切档。

重排可以选择「只保留相关项」，因此结果条数可能少于上限：模型判断无关的会被丢掉，
但至少保留 `AI_RERANK_MIN_KEEP` 条；全都不相关且首位原始分低于 0.5 时返回 0 条，
问答会直接回「站内没有找到」而不是拿无关片段硬答。

用 `http` 时服务需暴露 `POST /rerank`，请求 `{"model","query","documents","top_n"}`，
响应 `{"results":[{"index","relevance_score"}]}`（TEI 的 `score` 与顶层数组也兼容）。
启动日志出现「重排已启用：provider=http」即为接上；连续失败会熔断 60 秒，不会拖慢每个请求。
取舍与实测对比见 `docs/ADR-0003-QUERY-REWRITE-RERANK.md`。

**换 provider 或 dim 后必须重建索引**（后台「知识库索引 → 重建」，或 `POST /admin/ai/reindex`），
否则旧向量维度对不上会被跳过，检索结果为空 —— 后台 stats 里的 `ignoredChunks` 就是提示。
这一节的取舍与阈值说明见 `docs/ADR-0002-HYBRID-RETRIEVAL.md`。

> 本地模型跑在宿主机时，容器内要用 `host.docker.internal` 访问；
> 仓库的 compose 已经加了 `extra_hosts: host.docker.internal:host-gateway`（Linux 也适用）。
> 如果 Ollama 跑在另一台机器上，直接写那台机器的内网 IP。

### 验证

后台「智能检索 → 知识库索引」顶部有模型卡片，点**测试连通**：
返回「连通正常 + 耗时」即通；报错信息会直接显示原因。

常见错误：

| 现象 | 原因 |
|---|---|
| `401 Unauthorized` | Key 错或没生效（改完要重建容器） |
| `404 Not Found` | base-url 少了 `/v1`（Ollama 也要带） |
| `Connection timed out` | 容器到宿主机不通，检查 `host.docker.internal` 或换成内网 IP |
| 答案里没有引用编号 | 命中的片段太少，或模型没遵守提示；可以换更强的模型 |

### 流式输出

问答默认走 SSE：答案边生成边显示，长回答不用干等十几秒。

如果自己在外层再加一层反代，必须关掉缓冲，否则会退化成「一次性返回」：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8091/;
    proxy_buffering off;
    proxy_read_timeout 300s;
}
```

仓库自带的 gateway 已经配好了，用 `docker compose -f docker-compose.prod.yml` 部署无需额外操作。
后端也会返回 `X-Accel-Buffering: no` 作为兜底。

### 分享链接

用户可以对单条回答生成 `/share/{token}` 公开页，不需要额外配置；
链接不带过期时间，撤销由用户自己点「取消分享」，删除对话时会一并失效。

### 成本与限制

- 每次提问 = 1 次模型调用，上下文最多 6 个片段（约 2k 字符）
- `/ai/chat` 限流 60 秒 12 次，防止被当成免费 API 刷
- 检索目前用的是本地哈希向量（字面召回），换成语义 embedding 后答案质量会明显提升；
  换 embedding 需要重建索引（后台点一次「重建索引」）
