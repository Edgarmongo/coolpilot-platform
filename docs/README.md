# 文档索引

> 更新：2026-11-20 · 对应线上 `v1.1.0`
>
> 35 份文档分四类（另有 1 份归档快照）。**先看类别，再看内容** —— 历史文档里的事实不是现状，
> 混着读会得出错误结论。这也是这份索引存在的原因。

---

## 一、怎么读

| 想知道 | 看 |
|---|---|
| 系统现在长什么样、代码怎么组织 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 有哪些功能、多少个接口、多少张表 | [FEATURE-MAP.md](FEATURE-MAP.md) |
| 某个功能为什么这样设计、要不要改 | [FEATURE-REVIEW.md](FEATURE-REVIEW.md) + `feature-review/` |
| 怎么发版、出问题怎么回滚 | [DEPLOY.md](DEPLOY.md) |
| 这个版本改了什么 | [CHANGELOG.md](CHANGELOG.md) |
| 某个表为什么这么设计 | [DATA-MODEL.md](DATA-MODEL.md) / [DATA-MODEL-OSS.md](DATA-MODEL-OSS.md) |
| 视觉规范是什么 | [UI-SYSTEM.md](UI-SYSTEM.md) |
| 这份代码归谁、能不能对外 | 根目录 [LICENSE](../LICENSE) / [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) / [ADR-0005](ADR-0005-LICENSE.md) |

---

## 二、现状事实（随发版更新）

这四份描述**当前系统**。读到的数字如果和代码对不上，先修这里，再改代码。

| 文档 | 内容 | 最后核对 |
|---|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 三端拓扑、17 个模块、路由、五条关键数据流、技术债清单 | v1.1.0 |
| [FEATURE-MAP.md](FEATURE-MAP.md) | 9 个业务域，62 表 / 263 接口 / 34 前台页 / 32 后台页 | v1.1.0 |
| [DATA-MODEL.md](DATA-MODEL.md) | 59 张存量表的设计与取舍（§十九 为 V28 新增） | V28 |
| [DATA-MODEL-OSS.md](DATA-MODEL-OSS.md) | 开源观察三张表：字段、索引、查询口径 | V28 |
| [BASELINE.md](BASELINE.md) | 仓库命名、基线定义、分支与提交规范、内容边界 | v1.1.0 |
| [CHANGELOG.md](CHANGELOG.md) | 每个版本的变更明细与发布结果 | v1.1.0 |

---

## 三、操作手册（流程变了才改）

| 文档 | 内容 | 适用 |
|---|---|---|
| [DEPLOY.md](DEPLOY.md) | 首次部署、证书、日常发布与回滚、备份恢复、排查表、上线检查清单 | 运维 |
| [CI-CD.md](CI-CD.md) | CI 做什么、Release 出哪四个镜像、如何启用自动部署 | 改流水线 |
| [DOCKER.md](DOCKER.md) | 本地六容器、配置注入、数据卷、Flyway 初始化、常见问题 | 本地开发 |
| [engineering/README.md](engineering/README.md) | 2026-11-04 三份工程审计的索引（历史） | 追溯 |

---

## 四、评审与决策（只追加，不改写）

**决策记录一旦写下就不再修改**，哪怕结论后来被推翻 —— 追加新的决策说明哪条被取代，这样能看出判断是怎么变的。

| 文档 | 性质 | 状态 |
|---|---|---|
| [FEATURE-REVIEW.md](FEATURE-REVIEW.md) | 全站评审总表：15 维方法、决策图例、R1–R7 风险登记、9 域进度 | DOMAIN-01 已冻结，余 8 域待评审 |
| [feature-review/DOMAIN-01-CONTENT.md](feature-review/DOMAIN-01-CONTENT.md) | 内容与阅读 14 项逐条评审 | 已冻结 |
| [ADR-0001-VECTOR-STORE.md](ADR-0001-VECTOR-STORE.md) | 向量库选型 | 已采纳 |
| [ADR-0002-HYBRID-RETRIEVAL.md](ADR-0002-HYBRID-RETRIEVAL.md) | 混合检索（向量 + 关键词） | 已采纳 |
| [ADR-0003-QUERY-REWRITE-RERANK.md](ADR-0003-QUERY-REWRITE-RERANK.md) | 追问改写与重排 | 已采纳 |
| [ADR-0004-OPEN-SOURCE-MODEL.md](ADR-0004-OPEN-SOURCE-MODEL.md) | 开源观察的数据模型：新建三表而非复用 portfolio | 已落地 |
| [ADR-0005-LICENSE.md](ADR-0005-LICENSE.md) | 授权方式：专有许可 + 保留 NBlog 的 MIT 声明 | 已采纳 |
| [PRD-OSS-INSIGHTS.md](PRD-OSS-INSIGHTS.md) | 开源观察栏目需求（P0 范围） | 已实现 |
| [FEATURE-OSS-INSIGHTS.md](FEATURE-OSS-INSIGHTS.md) | 栏目已实现功能清单 + 8 个待决项 + 未实现对照 | 已上线 |
| [TASKS-OSS-INSIGHTS.md](TASKS-OSS-INSIGHTS.md) | 栏目任务卡、验收对照、与文档的三处偏离、发布记录 | 已上线 |
| [TASKS-CONTENT-RENDER.md](TASKS-CONTENT-RENDER.md) | 正文二次解析问题的任务卡：根因、方案、不做的三件事 | v1.0.4 已修 |
| [CP-WEB-OS-01-现状审计与改造方案.md](CP-WEB-OS-01-现状审计与改造方案.md) | 栏目动工前的只读审计（8 处需修正） | 已落地 |
| [UI-SYSTEM.md](UI-SYSTEM.md) | 视觉系统：令牌、组件、布局约定、栏目页规则 | v2 |
| [UI-OSS-INSIGHTS.md](UI-OSS-INSIGHTS.md) | 栏目视觉方向（六条规范、待决项） | 已实现 |

---

## 五、历史（不要当现状读）

这些文档描述的是**当时的系统**。它们的价值在于解释「为什么现在是这样」，不在于告诉你现在是什么样。

| 文档 | 记录的是 | 说明 |
|---|---|---|
| [PRD-V1.md](PRD-V1.md) | 2026-11-04 的 V1.0–V1.2 需求 | 已基本落地 |
| [ED-02-MODULE-DESIGN.md](ED-02-MODULE-DESIGN.md) | 模块划分设计 | 已实施，数量有演进 |
| [ED-04-API-DESIGN.md](ED-04-API-DESIGN.md) | 接口约定：错误码、幂等、鉴权 | **约定仍有效**，清单以代码为准 |
| [ED-05-TASKS.md](ED-05-TASKS.md) | 开发阶段与任务拆分 | 多数已交付 |
| [CP-WEB-01-现状核查与设计方案.md](CP-WEB-01-现状核查与设计方案.md) | 信息架构改造的现状核查 | 已实施 |
| [CP-WEB-02-功能范围与代码审计对照表.md](CP-WEB-02-功能范围与代码审计对照表.md) | 功能范围对照 | 口径已被 FEATURE-MAP 取代 |
| [FRONTEND-MIGRATION.md](FRONTEND-MIGRATION.md) | Vue 2 → Vue 3 / Nuxt 3 迁移方案 | 已完成 |
| [V1.1B-TRADE.md](V1.1B-TRADE.md) | 交易与权益的状态机、幂等规则 | **规则仍有效** |
| [engineering/](engineering/) | 2026-11-04 三份审计（19 表 / 81 接口时代） | 结论已用于迁移 |
| [archive/ARCHITECTURE-2026-11-04-baseline.md](archive/ARCHITECTURE-2026-11-04-baseline.md) | 上游 Vue 2 架构快照 | 仅供追溯 |

---

## 六、发版时该更新什么

文档过时不是因为没人写，是因为没有触发条件。下面这张表就是触发条件。

| 发版内容 | 必须更新 |
|---|---|
| 任何版本 | `CHANGELOG.md`（版本号、变更、发布结果） |
| 加了表 / 改了表结构 | `DATA-MODEL.md` 或 `DATA-MODEL-OSS.md`、`ARCHITECTURE.md` §7.2 的表数 |
| 加了接口 / 改了鉴权 | `ARCHITECTURE.md` §4.4、`FEATURE-MAP.md` 的接口数 |
| 加了页面 / 改了路由 | `ARCHITECTURE.md` §6.1、`FEATURE-MAP.md` 的页面数 |
| 加了模块 | `ARCHITECTURE.md` §4.2 |
| 改了视觉规范 | `UI-SYSTEM.md` |
| 改了发布流程 | `DEPLOY.md` / `CI-CD.md` |
| 做了产品级取舍 | 新增 `ADR-000N-*.md`，并在 `FEATURE-REVIEW.md` 登记 |

**约定：数字只写在一处。** 表数、接口数、页面数的事实源是代码；文档里写数字时，
要么写具体值（并在发版时核对），要么写"见 FEATURE-MAP"，不写"大约"。

---

## 七、这次整理改了什么（2026-11-20）

| 文档 | 改动 |
|---|---|
| `ARCHITECTURE.md` | **重写**。原文描述 Vue 2 上游基线（19 表 / Semantic UI / `application-dev.properties`），与现状已无对应关系；旧版归档到 `archive/` |
| `README.md` | 版本表补到 v1.1.0、表数 62 / V28、后台域加开源观察、发版段补备份与清缓存、修正管理员用户名 `Admin` |
| `BASELINE.md` | 表数、tag 列表 |
| `DATA-MODEL.md` | 表数 59 → 62，新增 §十九 开源观察域 |
| `DEPLOY.md` | 版本示例统一 v1.1.0，发布段补「迁移前备库」「迁移后查日志」「清导航缓存」 |
| `CHANGELOG.md` | 标注当前线上版本，v1.1.0 补发布结果 |
| `FEATURE-MAP.md` / `FEATURE-REVIEW.md` | 提交号与状态对齐 v1.1.0，后台页数 33 → 32 |
| `UI-SYSTEM.md` | 新增 §九 栏目页与评估卡规则 |
| 6 份栏目文档 | 头部加状态标注，明确「设计记录 vs 现状」 |
| 10 份历史文档 | 头部加「文档性质」标注，避免被当现状读 |
| `docs/README.md` | **新增**：本文，四类划分 + 发版更新触发条件 |
| `LICENSE` / `THIRD-PARTY-NOTICES.md` | **新增**：专有许可 + NBlog 的 MIT 声明与来源边界 |
| `docs/ADR-0005-LICENSE.md` | **新增**：授权方式的决策记录 |
