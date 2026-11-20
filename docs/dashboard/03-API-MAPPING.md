# CP-DASHBOARD-03 · 接口映射与迁移清单

> 文档编号：CP-DASHBOARD-03 ｜ 任务：接口映射
> 审计日期：2026-11-21 ｜ 代码基线：`v1.1.3`
> **状态：Phase 1 已实现**（2026-11-21）。八个接口、驾驶舱页面、四个列表页的 URL 筛选均已落地，
> 接口按本文设计执行，落地时对两处做了调整（见文末「实现偏差」）。

---

## 一、现有接口清单（与仪表盘相关）

| 接口 | 位置 | 现状 | 处置 |
|---|---|---|---|
| `GET /admin/stats/sales` | `TradeAdminController:142` | 前端在用 | **保留路径，内部改造口径** |
| `GET /admin/orders` | `TradeAdminController` | 前端在用（最近订单） | **沿用** |
| `GET /admin/dashboard` | `DashboardAdminController` | **无消费者** | **暂不动**，确认后再议 |
| `GET /health` | `HealthController` | 容器探针在用 | **复用其探测逻辑** |
| `GET /health/live` | 同上 | 存活探针 | 不动 |

### 1.1 `/admin/stats/sales` 的改造点

现状：

```java
BigDecimal paid     = orderMapper.sumPaidAmount(startTime, endTime); // 按 create_time
BigDecimal refunded = refundMapper.sumRefunded(startTime, endTime);  // 按 refund_time
```

必须改两处：

1. `sumPaidAmount` 的时间字段 `create_time` → **`paid_time`**
2. 区间边界 `>=` / `<=` → **`>=` / `<`**（左闭右开）

> **风险提示**：改完会让"全量"数值下降（未支付订单不再被计入成交额）。不过这本来就是对的 —— 现状把 `PENDING_PAY` 之外的成交状态按下单时间算，导致"昨天下的单今天付款"被记在昨天。改动后历史报表不可直接对比，需在发版说明里写清。

**兼容策略**：改造期间保留原方法（`sumPaidAmountByCreateTime`）供对比验证，新接口只调新方法。验证一致后删旧方法。

### 1.2 `/admin/dashboard` 为什么先不动

`DashboardAdminController` 提供 PV / UV / 分类分布 / 城市统计，前端零调用，但：

- 无法确认是否有外部脚本、监控或历史书签在调
- `FEATURE-MAP.md` 仍登记这些接口
- 直接删除会让"接口数"变化，牵连多处文档

**处置**：阶段 1 不做任何改动；阶段 4 用 `grep` + 网关访问日志确认无调用后，再决定下线或保留。PRD §5.1 明确要求"不得因路径相似而直接覆盖旧接口"。

**注意路径冲突**：旧接口是 `GET /admin/dashboard`，新接口是 `GET /admin/dashboard/overview` 等**子路径**，Spring 不会冲突，但同一 Controller 内不要重复映射父路径。

---

## 二、新接口与复用关系

统一前缀 `/admin/dashboard`。**8 个接口是上限，不按指标拆。**

| # | 接口 | 复用 | 需新增 |
|---|---|---|---|
| 1 | `GET /overview` | `OrderMapper.sumPaidAmount`（改造）、`countPaid`（改造）、`RefundMapper.sumRefunded`、`PaymentService.countWaitingConfirm` | 新增订单数、环比 |
| 2 | `GET /trends` | 无 | 全部：4 类时间桶聚合 + 补零 |
| 3 | `GET /todos` | `PaymentService.countWaitingConfirm`、`DemandMapper.countPending`（拆项）、`RefundMapper.countAdmin` | 待办聚合 service、逾期里程碑 SQL |
| 4 | `GET /content` | `BlogMapper.countBlog`、`VisitLogMapper` | 榜单、搜索词、PV/UV 聚合 |
| 5 | `GET /users` | `UserMapper.countUsers` | 付费用户去重、增长趋势 |
| 6 | `GET /products` | `ProductMapper` | 销量/销售额/退款率聚合 |
| 7 | `GET /services` | `DemandMapper.countPending`、`ProjectMapper`、`MilestoneMapper` | 逾期、漏斗、交付时效 |
| 8 | `GET /health` | `HealthController.checkDatabase/checkRedis` | 探测项聚合、存储、任务、异常 |

### 2.1 代码组织建议

```
modules/dashboard/
├── controller/DashboardController.java     # 8 个端点，只做参数校验与转发
├── service/DashboardService.java           # 编排
├── service/impl/DashboardServiceImpl.java
├── service/TimeRange.java                  # 区间解析、粒度推断、桶补齐
├── mapper/DashboardMapper.java             # 跨域聚合 SQL
└── dto/                                    # 8 个响应 DTO + TimeRangeParam
```

**为什么新建 `modules/dashboard` 而不是改 `service/impl/DashboardServiceImpl`**：后者是 NBlog 时代的博客看板实现，语义已不符，且正在等 §1.2 的处置结论。新建模块隔离职责，也避免动到它。

**与旧类重名**：旧的是 `DashboardService` / `DashboardServiceImpl`（根包），新的是 `modules.dashboard.service.DashboardService`。包不同不冲突，但 import 时容易混，建议新类命名 `AdminDashboardService`。

### 2.2 时间区间解析（公共组件）

`TimeRange` 负责：

```java
record TimeRange(LocalDateTime start, LocalDateTime end, Granularity granularity) {
    static TimeRange of(String startTime, String endTime, String granularity) { ... }
    List<String> buckets() { ... }   // 生成完整桶序列，用于补零
}
```

校验规则（PRD §5.3）：

| 校验 | 失败 |
|---|---|
| `startTime < endTime` | 400，不静默纠正 |
| `endTime - startTime` 与 `granularity` 匹配（HOUR ≤ 7 天、DAY ≤ 90 天） | 400，提示缩短范围 |
| 日期格式 `yyyy-MM-dd HH:mm:ss` | 400 |
| 时区固定 `Asia/Shanghai` | 不接受客户端传时区（避免歧义） |

> **不让客户端传时区**是刻意的：单站点部署，多时区只会制造"为什么我的今天和你不一样"的问题。响应里返回 `timezone` 字段说明用的是哪个即可。

---

## 三、响应契约要点

### 3.1 通用外壳

沿用现有 `Result`：`{code, msg, data}`。**不引入新外壳**。

### 3.2 每个指标必须带口径

`overview` 响应内嵌 `notes`：

```json
"notes": {
  "salesAmount": "按支付确认时间统计，含已退款订单",
  "refundAmount": "按退款完成时间统计，仅含成功退款",
  "netAmount": "以上两项相减，两项时间归属不同"
}
```

理由：口径说明如果只写在文档里，三个月后没人看。跟着数据返回，前端直接渲染成卡片下方灰字。

### 3.3 不可统计的指标返回 null + reason

```json
"uv": null,
"uvReason": "访客标识未透传真实 IP 与 UA，当前 UV 不可用（见 CP-DASHBOARD-02 §7.1）"
```

**不能用 0 代替**（PRD §4.5 / 质量门 G1）。0 表示"确实是零"，null 表示"算不出来"，两者业务含义完全不同。

### 3.4 健康度四态

`UP` / `WARN` / `DOWN` / `UNKNOWN`。每项独立探测，独立失败原因：

```json
{
  "component": "ai",
  "status": "UNKNOWN",
  "checkedAt": "2026-11-21 12:04:31",
  "latencyMs": null,
  "message": "近 24 小时无调用记录，无法判断可用性",
  "lastSuccessAt": null
}
```

`message` **不得包含**连接串、内部 IP、堆栈、密钥（PRD §5.8）。

---

## 四、待办跳转可达性（G2 的硬约束）

这是本次审计最需要决策的一处。

### 4.1 现状核对

| 待办 | 设计跳转 | 路由存在 | 页面读参数 | 结论 |
|---|---|---|---|---|
| 待确认收款 | `/trade/order?status=PENDING_CONFIRM` | ✅ | ✅ | 可用 |
| 待处理退款 | `/trade/refund?status=APPLIED` | ✅ | ❌ | **假筛选** |
| 待审核需求 | `/service/demand?status=PENDING_REVIEW` | ✅ | ❌ | **假筛选** |
| 待报价需求 | `/service/demand?status=PENDING_QUOTE` | ✅ | ❌ | **假筛选** |
| 待确认报价 | `/service/demand?status=PENDING_CONFIRM` | ✅ | ❌ | **假筛选** |
| 待交付项目 | `/service/project?status=IN_PROGRESS` | ✅ | ❌ | **假筛选** |
| 逾期里程碑 | `/service/project?overdue=1` | ✅ | ❌ | **假筛选** |
| 未处理反馈 | `/community/feedback` | ✅ | — | 可用（无参数） |

验证方法：`grep -E "route\.query|useRoute"` 在这 5 个 `.vue` 文件中的命中情况 —— 仅 `Orders.vue` 命中。

### 4.2 为什么这必须解决

仪表盘显示"待处理退款 2 条"，点进去看到 47 条全部退款 —— 管理员会开始怀疑仪表盘的所有数字。**一处不可信，整页不可信。**

这正是 PRD 质量门 G2 的要求："待办数量必须与对应业务模块使用同一套状态定义和筛选条件"。

### 4.3 两个方案

**方案 A（推荐）：4 个页面补 query 初始化**

每个页面 `onMounted` 时读 `route.query`，赋给现有筛选状态变量：

```js
import { useRoute } from 'vue-router'
const route = useRoute()
const query = reactive({
  status: route.query.status || '',
  overdue: route.query.overdue === '1'
})
```

改动量：`Refunds.vue`、`DemandList.vue`、`ProjectList.vue`、`Entitlements.vue` 各 5–10 行。无后端改动。

附带收益：浏览器前进/后退能保留筛选，刷新页面不丢状态。

**方案 B：待办跳转不带参数，到列表页，UI 注明"需手动筛选"**

零改动，但违背 G2，且体验差。

### 4.4 建议

选 **A**，并入 `CP-DASHBOARD-07`（业务待办 API）同一批交付 —— 因为待办接口的数字只有和落地页筛选对齐才有意义，拆成两批发会先上线一个"看起来对但点进去不对"的版本。

---

## 五、旧接口治理

### 5.1 `/admin/stats/sales`

| 阶段 | 动作 |
|---|---|
| 阶段 1 | 改造时间字段与区间边界；`overview` 内部复用改造后的方法 |
| 阶段 1 末 | 前端切到 `overview`，`/admin/stats/sales` 保留一版做回滚兜底 |
| 阶段 2 | 确认无调用后下线（需同步 `coolpilot-admin/src/api/trade.js`） |

**注意**：`api/trade.js` 的 `getSalesStats()` 目前被 `dashboard/index.vue` 引用。页面重写后该引用消失，但函数本身可留。

### 5.2 `/admin/dashboard`（旧看板）

**阶段 1–3 完全不动。** 阶段 4 执行：

1. `grep -rn "admin/dashboard"` 全仓（含 `coolpilot-web`、`coolpilot-admin`、`docs`、部署脚本）
2. 查网关访问日志近 30 天是否有请求
3. 无消费者 → 标记 `@Deprecated`，保留一个版本后删除
4. 有消费者 → 保留，并在文档标注"遗留，勿新增依赖"

### 5.3 不改的东西（质量门 G3）

明确**不做**以下改动，避免重构伤及业务：

- 不改订单状态机（8 状态与流转）
- 不改支付确认流程（`onPaid` 四步）
- 不改退款审批流程（`APPLIED→APPROVED→SUCCESS`）
- 不改权益发放（`grantForOrder`）
- 不改服务项目状态机（`FLOW` 白名单）
- 不删除 `visit_log` 历史数据
- 不直接删旧接口

---

## 六、鉴权与安全

| 项 | 现状 | 要求 |
|---|---|---|
| 认证 | Spring Security + JWT，`/admin/**` 需 `ROLE_owner` | 沿用，不新增角色 |
| 参数校验 | 部分接口无校验 | 8 个新接口全部校验时间参数 |
| 大范围查询 | 无限制 | `granularity=HOUR` 限 7 天，`DAY` 限 90 天 |
| 敏感信息 | `HealthController` 已注意不暴露连接串 | `health` 接口保持同等克制 |
| SQL 注入 | Mapper 全部用 `#{}` 参数绑定 | 新增 SQL 同样禁止字符串拼接 |
| 前端直连库 | 不存在 | 保持 |

**额外约束**：`health` 接口虽然只返回"通/不通"，但它暴露了基础设施拓扑（有 Redis、有 AI 服务）。当前 `/admin/**` 已鉴权，风险可接受；**不要**把它加到 PUBLIC 白名单。

---

## 七、测试要点（CP-DASHBOARD-14 的输入）

### 7.1 口径一致性（与 02 文档 §八 的验证 SQL 一一对应）

| 用例 | 期望 |
|---|---|
| 跨月订单（10/31 下单、11/1 付款） | 计入 **11 月**成交额（按 `paid_time`） |
| 跨月退款（11 月申请、12 月到账） | 计入 **12 月**退款额，不回溯 11 月 |
| 区间边界（`endTime` 恰好等于某订单 `paid_time`） | **不计入**（左闭右开） |
| 只有 `PENDING_PAY` 的月份 | 成交额 0，新增订单数 > 0 |
| 全额退款订单 | 成交额仍计入，退款额另计，净流入为 0 |

### 7.2 空值与异常

| 用例 | 期望 |
|---|---|
| 无数据区间 | 所有金额 `0`，趋势全桶补零，不报错 |
| 分母为 0（客单价） | 返回 `null`，不返回 `NaN` 或 `Infinity` |
| UV 不可用 | 返回 `null` + `uvReason` |
| 单模块失败 | 其余模块正常渲染（前端分区容错） |
| `startTime >= endTime` | 400 |
| 非法日期字符串 | 400，不静默纠正 |

### 7.3 一致性对账（G2）

| 对账项 | 对照页面 |
|---|---|
| 待确认收款数 | `/trade/order?status=PENDING_CONFIRM` 列表总数 |
| 待处理退款数 | `/trade/refund` 筛选 `APPLIED` 后总数 |
| 待报价需求数 | `/service/demand` 筛选后总数 |
| 逾期里程碑数 | `/service/project` 逾期标记数 |
| 商品销售额合计 | 订单明细逐笔相加 |

---

## 八、实施顺序与接口依赖

```
阶段 1（可独立上线）
  ├─ TimeRange 组件
  ├─ overview  ← 依赖 sumPaidAmount 改造
  ├─ trends
  ├─ todos     ← 同时改 4 个列表页 query
  └─ health    ← 抽 HealthProbeService
阶段 2
  ├─ content   ← 依赖代理头透传（UV 才能出数）
  └─ users
阶段 3
  ├─ products  ← 依赖 order_item 聚合
  └─ services
阶段 4
  └─ 旧接口治理 + AI 项补全
```

**关键路径**：`overview` 的口径改造是唯一会触碰现有业务 SQL 的动作，其余全部是新增只读查询。先做它，后面才不会被"数字对不上"反复打断。

---

## 九、结论

| 判断 | 结论 |
|---|---|
| 8 个接口是否都需新建 Controller | 是，新建 `modules/dashboard`，与旧 `DashboardServiceImpl` 隔离 |
| 有多少可复用 | `overview` 3 个方法、`todos` 2 个方法、`health` 2 个探测方法 |
| 最大改造风险 | `sumPaidAmount` 换时间字段 → 历史数字变化，需发版说明 |
| 最大落地风险 | 4 个列表页不读 query → 待办跳转假筛选，必须与 todos 同批交付 |
| 旧接口 | 一个不动、一个改造、一个待废弃 |

---

## 十、实现偏差（落地时与设计不同的两处）

| 原设计 | 实际实现 | 原因 |
|---|---|---|
| `todos` 只返回 count | 另返回 `earliest`（最早发生时间） | 设计稿有待办区的「最早 xx」，不返回就只能空着。改 `demand/refund/feedback` 的状态聚合 SQL 顺带 `MIN()` 带出，零额外查询 |
| `dashboard/health` 复用 `HealthController` 的探测方法 | 服务内自行探测 | `HealthController` 的方法私有且返回 `ResponseEntity`，抽公共会改动既有健康检查；改为在本服务内做 `SELECT 1` 与 `PING`，语义一致且不动存量代码 |

**另有一处设计缺陷在实现时才暴露**：`DashboardPeriod` 原本对「不传区间」一律取最近 30 天，
但小时粒度限 7 天 —— 于是 `GET /trends?granularity=HOUR`（不传时间）会直接 400。
已改为**默认跨度跟随粒度**（小时 24h / 日 30d / 周 12 周 / 月 365d），接口不再自己给自己下套。

**落地过程中发现并修正的真实缺陷**（都不是设计问题，是代码/数据问题）：

| # | 问题 | 影响 |
|---|---|---|
| 1 | `blog` 表列名是 `is_published`，不是 `published` | 已发布内容数查询直接报 SQL 错 |
| 2 | `schedule_job_log.status` 是 `true→1` 表示成功 | 初版按 `0=成功` 判断，会把正常任务报成 DOWN |
| 3 | `ProjectMapper.COLUMNS` 同时被带/不带 `<script>` 的查询复用 | 里面写 `&lt;` 会让不带 script 的查询输出非法 SQL，改用 `NOT (>= )` 等价写法 |
| 4 | 前端模板 `users?.payRate === null` 在 users 为 null 时判断失效 | 随后访问 `users.payRate` 抛错，整页白屏 |
| 5 | `c.value` 已含 `¥` 又叠加了 `prefix` | 卡片显示成「¥ ¥199.00」 |
