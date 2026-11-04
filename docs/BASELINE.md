# 项目基线与提交规范

> 生效日期：2026-11-05
> 适用范围：coolpilot-platform 全仓库

---

## 一、仓库命名

| 项目属性 | 命名 |
|---|---|
| 项目名称 | CoolPilot Platform |
| GitHub 仓库 | `coolpilot-platform` |
| 中文名称 | CoolPilot 技术平台 |
| 项目标识 | `coolpilot-platform` |
| 基础项目 | NBlog（二次开发） |
| 产品定位 | 个人技术知识与服务平台 |
| 核心品牌 | CoolPilot |

仓库地址：`git@github.com:Edgarmongo/coolpilot-platform.git`（Private）

## 二、基线的定义

**基线 = 上游 NBlog `b8e537c` + 本项目全部工程化改造 + V1.0 / V1.1a / V1.1b 业务模块。**

用一个提交固定下来并打 tag：

```
commit  chore: 初始化 coolpilot-platform 基线
tag     v0.1.0-baseline
分支    main
```

基线包含：

| 层 | 内容 |
|---|---|
| 工程化 | JDK 21、Spring Boot 4.1、Maven 阿里云镜像、Docker Compose 五服务编排、`.env` 外置密钥 |
| 安全修复 | JWT 密钥 64 位强随机、`SecurityFilterChain` 重写、后台越权漏洞修复、Tomcat 11 参数校验适配 |
| 数据 | 39 张表；增量脚本 `blog-api/sql/` 按日期命名，初始化顺序见 `docs/DOCKER.md` |
| 业务 | `modules/account` `community` `file` `product` `trade` `entitlement` |
| 前端 | 前台门户改版；后台新增「交易管理」 |
| 文档 | `docs/` 下架构、PRD、数据模型、接口、任务卡、Docker、交易闭环 |

### 为什么只有一个基线提交

上游提交历史完整保留（`git log` 里能看到 NBlog 的全部历史），但我们自己的改造是在**仓库初始化之前**完成的，所有改动都堆在同一个工作区里，无法在不伪造历史的前提下拆分。因此：

- 上游历史 → 原样保留，作为「代码来源」的审计线索；
- 我们的全部工作 → 一个 `chore: 初始化基线` 提交；
- **此后每一个功能都必须独立成提交**，这是规范的重点。

不采用「按 V1.0 / V1.1a / V1.1b 拆成三个提交」的做法，原因是三者存在代码依赖（`FileService` 依赖 `EntitlementService`），拆开后中间提交无法编译，属于伪历史。

## 三、上游代码导入流程（如需重建仓库）

```bash
# 1. 克隆上游到指定提交
git clone https://github.com/Naccl/NBlog.git coolpilot-platform
cd coolpilot-platform
git checkout b8e537c

# 2. 保留上游历史，另起主干
git branch -m master main

# 3. 指向自己的仓库（origin 换成自己的，上游留作 upstream）
git remote rename origin upstream
git remote add origin git@github.com:Edgarmongo/coolpilot-platform.git

# 4. 应用改造与业务代码后，形成基线提交
git add -A
git commit -m "chore: 初始化 coolpilot-platform 基线"
git tag -a v0.1.0-baseline -m "基线：NBlog b8e537c + 工程化改造 + V1.1b"
git push -u origin main --tags
```

## 四、分支模型

| 分支 | 用途 | 规则 |
|---|---|---|
| `main` | 唯一长期分支 | 受保护，只接受 PR；始终可构建、可启动 |
| `feature/<短横线描述>` | 新功能 | 例：`feature/cms-product-manage` |
| `fix/<短横线描述>` | 缺陷修复 | 例：`fix/order-expire-close` |
| `chore/<短横线描述>` | 工程化、依赖、配置 | 例：`chore/upgrade-vue3` |
| `tag vX.Y.Z` | 可交付节点 | `v0.1.0-baseline`、`v1.2.0` |

- 不直接在 `main` 上提交；本地临时改动也走分支。
- 合并前必须满足：能 `docker compose up -d --build` 启动、相关接口自测通过、文档同步更新。

## 五、提交规范

采用 **Conventional Commits**，类型 + 范围 + 中文描述：

```
<type>(<scope>): <中文简述>

<为什么这么改 / 关键取舍，可多行>

<关联文档或任务卡，如 docs/ED-05-TASKS.md T-205>
```

**type**

| type | 含义 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 重构（不改外部行为） |
| `perf` | 性能优化 |
| `docs` | 仅文档 |
| `chore` | 构建、依赖、配置、脚本 |
| `test` | 测试 |
| `style` | 格式（不影响逻辑） |

**scope**（与目录/模块对齐）

`api` `view` `cms` `trade` `entitlement` `product` `file` `community` `account` `db` `docker` `docs`

**示例**

```
feat(trade): 人工确认收款后自动发放权益

- 支付单条件更新 INIT|WAIT_CONFIRM → SUCCESS，影响行数为 1 才继续
- 发放 ACCESS；商品有资源时追加 DOWNLOAD
- 重复确认返回 confirmed=false，避免重复发权益与重复加销量

关联：docs/V1.1B-TRADE.md、ED-05 T-205
```

**硬性要求**

1. 一个提交只做一件事；混入无关格式化会被要求拆开。
2. 每个提交都必须能编译（前端能 build，后端能 package）。
3. 数据库变更与使用它的代码**同一个提交**，脚本放 `blog-api/sql/YYYY-MM-DD-描述.sql`，只用 `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` 之类的幂等写法。
4. 提交信息不得包含密钥、内网地址、真实用户数据。
5. 不提交 `.env`、`_backup_*.sql`、`target/`、`node_modules/`、`dist/`。

## 六、远程与上游同步

| remote | 地址 | 用途 |
|---|---|---|
| `origin` | `git@github.com:Edgarmongo/coolpilot-platform.git` | 主仓库 |
| `upstream` | `https://github.com/Naccl/NBlog.git` | 仅用于取上游修复 |

同步上游的原则：

- **只拣安全修复与明确缺陷**，不整体 merge（上游是 1.8 / Boot 2.2，我们是 21 / Boot 4，整体合并必然冲突）。
- 操作方式：`git fetch upstream` → 查看差异 → 手工移植 → 在自己的分支上提交，提交信息注明 `port upstream <short-hash>`。
- 上游 README、`pic/` 品牌素材不同步。

## 七、仓库内容边界

**纳入版本控制**

- 三端源码、`deploy/` 构建资产、`scripts/` 工程脚本、`docs/` 文档
- `blog-api/sql/` 增量迁移脚本与种子数据
- `.env.example`（只有占位值）

**禁止纳入**

- `.env`（真实密钥）、`blog-api/sql/_backup_*.sql`（含真实数据）
- `target/`、`node_modules/`、`dist/`、`blog-api/blog/`（日志）
- IDE 配置（`.idea`、`.vscode`）

## 八、已知事项

| 项 | 说明 |
|---|---|
| 本地目录名 | 工作区目录当前仍是 `NBlog`（IDE 占用导致无法重命名）。关闭 IDE 后执行 `mv NBlog coolpilot-platform` 即可，仓库内外路径不受影响 |
| 容器与前缀命名 | `docker-compose.yml` 的 `name: nblog` 与容器名 `nblog-*` 暂未改。改动会导致数据卷名变化（`nblog_mysql_data` → `coolpilot-platform_mysql_data`），等同于清库，需单独安排迁移，不在本次范围 |
| `package-lock.json` | 上游 `.gitignore` 忽略了 lock 文件。本次基线已取消该忽略并提交前端 lock，保证依赖可复现；后续升级依赖时请一并提交 lock 变更 |
| 本项目许可证 | 未定，见 `docs/LICENSE-REVIEW.md` |
