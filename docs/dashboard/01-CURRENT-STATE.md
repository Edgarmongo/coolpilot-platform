# CP-DASHBOARD-01 · 现状审计

> 文档编号：CP-DASHBOARD-01 ｜ 任务：现有仪表盘代码审计
> 审计日期：2026-11-21 ｜ 代码基线：`v1.1.3`（`62b364f`）
> **性质：只读审计。本次未修改任何业务代码、数据库结构或线上配置。**
> 上游依据：`CS-ADMIN-DASHBOARD-PRD-01` V2.0

---

## 一、审计范围与结论

### 1.1 审计了什么

| 对象 | 位置 |
|---|---|
| 仪表盘页面 | `coolpilot-admin/src/views/dashboard/index.vue` |
| 销售统计接口 | `TradeAdminController` + `OrderMapper` + `RefundMapper` |
| 遗留看板接口 | `DashboardAdminController` + `DashboardServiceImpl` |
| 交易链路 | `OrderService` / `PaymentService` / `RefundService` |
| 内容与流量 | `BlogController` / `VisitLogAspect` / `RedisSyncScheduleTask` / `VisitorSyncScheduleTask` |
| 用户 | `VisitorAuthController` / `UserServiceImpl` / `JwtLoginFilter` |
| 商品 | `ProductService` / `ProductMapper` |
| 服务交付 | `DemandService` / `ProjectService` / 五个 Mapper |
| 系统 | `HealthController` / `schedule_job` / `schedule_job_log` |
| 前端路由 | `coolpilot-admin/src/router/index.js` |

### 1.2 七项审计问题的结论（PRD §四 指定）

| # | 问题 | 结论 |
|---|---|---|
| 1 | 62 表中哪些真正支撑六大统计域 | **实际 61 张表**，42 张被统计消费，19 张不参与（明细见 02 文档 §一） |
| 2 | 现有销售 SQL 的支付/退款时间问题 | 成交按 `create_time`（下单），退款按 `refund_time`（到账）—— **两条时间轴不同，不能相减** |
| 3 | `visit_log` 能支持哪些 PV/UV | PV 可用；**UV 当前不可用**（代理头未透传，全站坍缩成一个访客） |
| 4 | 用户活跃是否有可靠来源 | **没有**。`login_log` 只记后台管理员；`last_login_time` 是单值快照 |
| 5 | 服务状态枚举 | Demand 11 / Quote 5 / Project 5 / Milestone 5 / Delivery 2，已取得完整流转白名单 |
| 6 | Quartz 能否提供最近执行状态 | **能**。`schedule_job_log` 有 status/error/times/create_time，共注册 3 个任务 |
| 7 | 后台路由能否承接待办跳转 | **部分不能**。5 个跳转目标中，3 个页面的筛选参数会被忽略 |

### 1.3 三个必须纠正的既有认知

审计推翻了上一版设计文档中的三处判断，**以本文为准**：

| 原判断 | 实际 | 影响 |
|---|---|---|
| `product.view_count` 全项目无写入 | **有写入**：`ProductService.detail()` 每次前台详情 +1 | 商品累计转化率可算（但无时间切片） |
| `product.sales_count` 是累计销量 | **是净销量**：支付 +qty、退款 −qty（`GREATEST` 兜底） | 不能当"历史累计销量"，区间统计仍需 `order_item` |
| 待办跳转可用 status 参数筛选 | 只有 `Orders.vue` 读 query，其余 4 页忽略参数 | G2（待办与业务页一致）被阻塞 |

> 第一、二条是我的检索失误：用 `viewCount` 搜 Java 字段名，而写入方法叫 `increaseView`、SQL 字段叫 `view_count`，都没命中。已在 02 文档修正。

---

## 二、现有仪表盘

### 2.1 页面

`coolpilot-admin/src/views/dashboard/index.vue`，220 行，Vue 3 `<script setup>` + Element Plus。

结构：

```
page-header（标题 + 刷新）
├─ 4 张统计卡（累计销售额 / 净收入 / 退款金额 / 待确认收款）
├─ 最近订单表（5 条：订单号 / 内容 / 金额 / 状态）
└─ 待办 3 入口（待确认收款带角标 / 退款处理 / 权益管理）
```

- **无图表库**。后台未安装 ECharts，`package.json` 无相关依赖。
- **无时间筛选**。接口支持 `startTime/endTime`，前端从不传。
- **无自动刷新**。仅手动点击。
- **无权限细分**。所有 `ROLE_owner` 看到全部数字。

### 2.2 数据加载

```js
const [statRes, orderRes] = await Promise.all([
  getSalesStats(),                        // GET /admin/stats/sales
  getOrderList({pageNum: 1, pageSize: 5}) // GET /admin/orders
])
```

两个请求并发，`try/finally` 控制 loading，**任一失败整页无数据**（无分区容错）。

### 2.3 现有接口实现

`TradeAdminController:142`

```java
@GetMapping("/admin/stats/sales")
public Result sales(@RequestParam(required=false) String startTime,
                    @RequestParam(required=false) String endTime) {
    BigDecimal paid = orderMapper.sumPaidAmount(startTime, endTime);
    BigDecimal refunded = refundMapper.sumRefunded(startTime, endTime);
    ...
    data.put("waitingConfirm", paymentService.countWaitingConfirm());
}
```

**成交 SQL**（`OrderMapper:147`）：

```sql
SELECT IFNULL(SUM(pay_amount), 0) FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND (startTime IS NULL OR create_time >= startTime)
  AND (endTime   IS NULL OR create_time <= endTime)
```

**退款 SQL**（`RefundMapper:91`）：

```sql
SELECT IFNULL(SUM(amount), 0) FROM refund
WHERE status = 'SUCCESS'
  AND (startTime IS NULL OR refund_time >= startTime)
  AND (endTime   IS NULL OR refund_time <= endTime)
```

### 2.4 口径缺陷（审计重点 2）

| 项 | 成交 | 退款 |
|---|---|---|
| 时间字段 | `orders.create_time`（**下单**） | `refund.refund_time`（**到账**） |
| 区间边界 | `>=` / `<=`（**闭区间**） | 同 |
| 状态集合 | 含 `REFUNDED`（已退款仍计入成交额） | 仅 `SUCCESS` |

三个问题：

1. **时间轴不一致**。成交按下单、退款按到账，相减得到的 `netAmount` 在跨期场景下没有业务含义。
2. **闭区间**。`<= endTime` 会让相邻区间重复统计边界那一秒；PRD 要求左闭右开。
3. `REFUNDED` 计入成交额是**有意设计**（"历史上收进来过多少钱"），但必须与"净资金流入"显式区分。

> **PRD 已冻结的正确口径**：成交按**支付确认时间**（`orders.paid_time`）归属，退款按 `refund_time` 归属。详见 02 文档 §四。

### 2.5 遗留看板接口

`DashboardAdminController` → `GET /admin/dashboard`，返回：

| 字段 | 来源 | 是否可信 |
|---|---|---|
| `pv` | `visit_log` 今日条数 | ✅ |
| `uv` | Redis 集合 `identification` 基数 | ❌ 坍缩 |
| `blogCount` / `commentCount` | 表计数 | ✅ |
| `category` / `tag` | 分类标签文章数 | ✅ |
| `visitRecord` | `visit_record` 近 30 天 | ⚠️ UV 列不可信 |
| `cityVisitor` | `city_visitor` | ❌ 容器 IP 归属地 |

前端 `grep admin/dashboard` **零命中**，无消费者。数据仍由定时任务累积。

---

## 三、交易链路（审计重点 2 的深挖）

### 3.1 支付确认是唯一入账口

`PaymentService.onPaid()`，`@Transactional(rollbackFor = Exception.class)`：

```java
// 第一道闸：支付单状态机
if (paymentMapper.markSuccess(paymentNo, tradeNo, operatorId, remark, raw) == 0) return false;
// 第二道闸：订单状态机
if (orderMapper.markPaid(order.getOrderNo()) == 0) throw new BadRequestException(...);
entitlementService.grantForOrder(order, items, operatorId);
for (OrderItem item : items) productMapper.addSales(item.getProductId(), qty);
orderMapper.markCompleted(order.getOrderNo());
```

`markSuccess` 写 `payment.status='SUCCESS', paid_time=NOW()`；
`markPaid` 写 `orders.status='PAID', paid_time=NOW()`。

**结论：`orders.paid_time` 与 `payment.paid_time` 在同一事务内写入，两者一致。** 现金流口径可用 `orders.paid_time`，无需 JOIN payment。

### 3.2 一个必须知道的行为：订单不会停在 PAID

`onPaid` 末尾直接调用 `markCompleted`，所以成功支付的订单**立刻变成 `COMPLETED`**。

后果：

- `status='PAID'` 几乎不存在（只在事务中间态）
- 统计"成交订单数"时，`PAID` 与 `COMPLETED` 必须一起算，且实际命中主要是 `COMPLETED`
- 任何只筛 `PAID` 的查询会返回 0

### 3.3 退款链路

`RefundService.finish()`：`APPROVED → SUCCESS`，同时写 `refund_time = NOW()`，并 `addSales(-qty)` 回滚销量。

退款状态全集：`APPLIED / APPROVED / REFUNDING / SUCCESS / REJECTED`。
**`REFUNDING` 在实际代码里没有写入点**（SQL 条件里出现过，但 `finish` 直接从 `APPROVED` 跳 `SUCCESS`）。统计时不必考虑该状态，但枚举里保留。

### 3.4 时间字段可空性

| 字段 | 可空 | 何时写 | 风险 |
|---|---|---|---|
| `orders.create_time` | NOT NULL | 建单 | 无 |
| `orders.paid_time` | NULL | 确认收款 | 若历史数据被直接改状态，可能为 NULL |
| `payment.paid_time` | NULL | `markSuccess` | 同上 |
| `refund.refund_time` | NULL | `finish` | `SUCCESS` 之外全为 NULL |

**待验证项**：线上是否存在 `status IN (成交集合) AND paid_time IS NULL` 的行。审计环境无生产库访问权限，需在实施阶段执行一次校验 SQL（见 02 文档 §四 4.4）。

---

## 四、内容与流量（审计重点 3）

### 4.1 两套浏览量，不要混

| 来源 | 语义 | 时间切片 | 可信度 |
|---|---|---|---|
| `blog.views` | 累计阅读数 | **无**（单值） | ✅ |
| `visit_log` | 访问事件明细 | 有 | ✅ PV / ❌ UV |

`blog.views` 链路：`BlogController.getBlog()` → `updateViewsToRedis()` → Redis Hash `BLOG_VIEWS_MAP` → 每日 01:00 `redisSyncScheduleTask` 刷入 DB。

→ **只能得到"历史累计"，得不到"近 30 天新增"**。

### 4.2 visit_log 的采集粒度

`VisitLogAspect` 只挂在带 `@VisitLogger` 的接口上，共 **12 处**：

`ABOUT / ARCHIVE / INDEX / BLOG / CHECK_PASSWORD / SEARCH / CATEGORY / FRIEND / CLICK_FRIEND / MOMENT / LIKE_MOMENT / TAG`

记录字段：`uuid / uri / method / behavior / content / remark / ip / ip_source / os / browser / times / create_time / user_agent`。

- `behavior` = 页面类型（BLOG/INDEX/CATEGORY/…），可分区统计
- `content` = 具体对象（文章标题、搜索词、分类名），**是字符串不是 ID**
- `uri` = 后端 API 路径（`/blog`、`/blogs`），不是前端页面路径

**含义**：能按"行为类型"切片，能按"标题字符串"聚合，但**不能稳定按 `blog_id` 聚合**（文章改名即断档）。

### 4.3 UV 为什么不可用（关键缺陷）

访客标识算法（`VisitLogAspect`）：

```
uuid = 摘要( 小时戳 + IP + User-Agent )
```

而 `coolpilot-web/server/api/[...path].ts` 转发到后端时只带两个头：

```ts
const headers: Record<string, string> = {}
if (authorization) headers.authorization = authorization
if (contentType) headers['content-type'] = contentType
```

**没有 `user-agent`、`x-real-ip`、`x-forwarded-for`、`referer`。**

后端看到的 IP 是 web 容器地址、UA 是 Nitro 服务端 fetch UA → 所有真实用户在同一小时内算出的 uuid 相同。

线上证据：`visit_record` 的 `pv=364 / uv=4`。这不是访客少，是访客被合并。

连带失效：`visit_log.ip_source`、`visit_log.os/browser`、`city_visitor` 全部记录的是容器信息。

### 4.4 数据保留

**没有任何 `visit_log` 清理任务或保留策略。** 表会无限增长，仅有 `idx_create_time` 索引。

影响：区间 PV 查询随数据量线性变慢；城市分布统计会越跑越贵。PRD §6.3 要求"先 EXPLAIN 再决定汇总表"—— 当前应先确定保留期。

### 4.5 内容侧可用指标

| 指标 | 可行性 |
|---|---|
| 内容总数 / 已发布数 | ✅ `blog` 表计数 |
| 新增文章（区间） | ✅ `blog.create_time` |
| 累计浏览量 | ✅ `SUM(blog.views)` |
| 区间 PV | ✅ `visit_log` 按 `behavior='BLOG'` |
| 区间 UV | ❌ 见 4.3 |
| 分类访问分布 | ⚠️ `behavior='CATEGORY'` 按 content 字符串 |
| 热门文章 TOP N（区间） | ⚠️ 按标题聚合，改名断档 |
| 热门搜索词 | ✅ `behavior='SEARCH'` |
| 收藏 / 点赞 / 评论 | ✅ 三张独立表 |
| 内容→商品转化 | ❌ 无归因链路 |

---

## 五、用户（审计重点 4）

### 5.1 结论：没有可靠的活跃数据源

| 候选来源 | 实际覆盖 | 能否算活跃 |
|---|---|---|
| `login_log` | **只有后台管理员登录**（`JwtLoginFilter`，`OWNER_PREFIX`） | ❌ 平台用户不在内 |
| `user.last_login_time` | 前台登录时 `touchLastLogin()` 覆盖写 | ⚠️ 仅"最后登录"快照 |
| `visit_log.uuid` | 匿名访客，与 `user.id` 无关联字段 | ❌ |
| `orders` | 有成交的用户 | ⚠️ 只能算付费活跃 |

### 5.2 login_log 的覆盖范围（易误判）

`JwtLoginFilter.successfulAuthentication()` 在**管理员登录成功**时写日志；`unsuccessfulAuthentication()` 记失败。

前台访客登录走 `VisitorAuthController.login()`，**不写 login_log**。

→ `login_log` 对"平台用户活跃"完全无用，只能回答"后台被谁登录过"。

### 5.3 last_login_time 的语义

`UserServiceImpl.loginVisitor()` 调用 `userMapper.touchLastLogin(id)`：

```sql
UPDATE user SET last_login_time = NOW() WHERE id = #{id}
```

**这是覆盖写，没有历史。** 因此：

- 能算：「最近 N 天有登录的用户」（`last_login_time >= startTime`）
- 不能算：「区间内登录过几次」、真正的区间活跃（DAU/WAU）
- 会漏：只看不登的用户（浏览、看课、下单未登录）

### 5.4 可用的用户指标

| 指标 | 口径 | 可行性 |
|---|---|---|
| 用户总数 | `COUNT(user WHERE role='ROLE_user')` | ✅ 快照 |
| 新增用户 | `user.create_time` 区间 | ✅ |
| 付费用户（区间） | `DISTINCT user_id FROM orders WHERE paid_time 区间` | ✅ |
| 付费用户（累计） | `DISTINCT user_id` 全部成交 | ✅ |
| 付费转化率 | 付费 ÷ 注册 | ✅（需说明分母） |
| 最近登录用户 | `last_login_time` 降序 | ✅ |
| 活跃用户（区间） | — | ❌ 无事件源 |
| 回访用户 | `visitor.pv > 1` | ❌ 依赖失效的 uuid |
| 用户来源 / 渠道 | 无 referer / UTM 字段 | ❌ 未采集 |

---

## 六、商品（审计重点 2 的延伸）

### 6.1 两个计数字段的真实语义

| 字段 | 写入点 | 语义 | 时间切片 |
|---|---|---|---|
| `view_count` | `ProductService.detail()` → `increaseView()` | 前台详情页访问次数 | ❌ 单调累加 |
| `sales_count` | `PaymentService.onPaid()` +qty；`RefundService.finish()` −qty | **净销量**（退款扣减） | ❌ 单调净额 |

`addSales` 使用 `GREATEST(sales_count + delta, 0)` 保证不减到负数。

### 6.2 由此得到的结论

- **累计转化率可算**：`SUM(sales_count) / SUM(view_count)`
- **区间转化率不可算**：两个字段都无时间切片
- **商品销量排行必须走 `order_item`**：`sales_count` 是净额且无法按区间切
- `view_count` 直接 DB 自增，无 Redis 缓冲，**不过滤爬虫与后台预览**

### 6.3 商品类型

`product.product_type` 取值（来自 `ProductCategory` 与前端）：课程 / 文档 / 代码资源 / 知识产品 / 技术服务。品类由 `product_category` 表管理，`ProductService.pageOnSale(type, categoryId, ...)` 支持按类型过滤。

---

## 七、服务交付（审计重点 5）

### 7.1 状态机全集

**Demand（需求），11 状态**，白名单流转（`DemandService.FLOW`）：

```
PENDING_REVIEW → PENDING_CONTACT / PENDING_QUOTE / REJECTED / CANCELLED
PENDING_CONTACT → EVALUATING / PENDING_QUOTE / CANCELLED / REJECTED
EVALUATING → PENDING_QUOTE / PENDING_CONTACT / CANCELLED
PENDING_QUOTE → PENDING_CONFIRM / CANCELLED / REJECTED
PENDING_CONFIRM → IN_PROGRESS / PENDING_QUOTE / CANCELLED / REJECTED
IN_PROGRESS → PENDING_ACCEPT / DISPUTED
PENDING_ACCEPT → COMPLETED / IN_PROGRESS / DISPUTED
DISPUTED → IN_PROGRESS / COMPLETED / CANCELLED
COMPLETED / REJECTED / CANCELLED → 终态
```

**Quote（报价），5 状态**：`DRAFT → SENT → ACCEPTED / REJECTED / EXPIRED`

**Project（项目），5 状态**：`NOT_STARTED → IN_PROGRESS → PENDING_ACCEPT → COMPLETED / ARCHIVED`
（注意：**没有 CANCELLED**）

**Milestone（里程碑），5 状态**：`PENDING → DELIVERING → DELIVERED → ACCEPTED / REJECTED`（打回可再交付）

**Delivery（交付物），2 状态**：`DELIVERED → ACCEPTED`

### 7.2 已完成的能力

`DemandMapper.countPending()` 已存在：

```sql
SELECT COUNT(*) FROM demand
WHERE status IN ('PENDING_REVIEW','PENDING_QUOTE','PENDING_CONFIRM')
```

`ServiceEvent` 表记录每次状态流转（`biz_type / from_status / to_status / action / operator`），可用于"最近业务事项"与时效分析。

`MilestoneMapper` 已有 `countByProject()` / `countAccepted()`，缺逾期查询（需新增）。

### 7.3 逾期里程碑的判定

`milestone` 表字段：`due_date`（`LocalDate`，可空）、`status`、`delivered_time`、`accepted_time`。

逾期 = `due_date IS NOT NULL AND due_date < CURDATE() AND status NOT IN ('ACCEPTED','REJECTED')`

**注意**：`due_date` 可空，空值不计入逾期（不宜用 `COALESCE` 兜底成创建日，会制造假逾期）。

### 7.4 缺失指标

`demand.expect_date`（期望交付日）存在但无业务使用，可作"需求紧急度"排序依据，**当前不参与统计**。

---

## 八、系统健康（审计重点 6）

### 8.1 已具备真实探测

`HealthController`：

| 端点 | 探测 | 返回 |
|---|---|---|
| `GET /health` | `jdbcTemplate.queryForObject("SELECT 1")` + Redis `PING` | 200 UP / 503 DOWN，body 含 `db` / `redis` / `uptimeSeconds` |
| `GET /health/live` | 不查依赖，仅证明进程存活 | 200 |

**这是现成可复用的真实探测**，无需引入 Actuator。

### 8.2 定时任务

`schedule_job` 共注册 **3 个**：

| bean | 方法 | cron | 作用 |
|---|---|---|---|
| `redisSyncScheduleTask` | `syncBlogViewsToDatabase` | `0 0 1 * * ?` | 每日 01:00 刷博客浏览量 |
| `visitorSyncScheduleTask` | `syncVisitInfoToDatabase` | `0 0 0 * * ?` | 每日 00:00 记 PV/UV、清访客标识 |
| `orderScheduleTask` | `closeExpiredOrders` | `0 */10 * * * ?` | 每 10 分钟关超时订单 |

`schedule_job_log` 字段：`job_id / bean_name / method_name / params / status(tinyint) / error(text) / times(ms) / create_time`。

→ **能提供**：最近执行时间、成功/失败、耗时、错误信息。
→ **不能提供**：任务是否"按时"执行（没有 next_fire_time 记录），只能靠 cron 反推。

### 8.3 可复用的异常与日志表

| 表 | 字段 | 用途 |
|---|---|---|
| `exception_log` | `ip / method / uri / description / create_time` | 异常趋势 |
| `operation_log` | 操作人、操作内容、时间 | 审计（非健康） |
| `payment` | `status='FAIL'`、`notify_raw` | 支付回调异常 |
| `ai_usage` | `success` / `duration_ms` / `scene` / `create_time` | AI 服务间接可用性 |
| `file` | `size` | 存储占用 |

### 8.4 缺口

- **无 Actuator**，无 JVM / 连接池指标
- **无 AI 主动探测端点**，只能用 `ai_usage` 成功率间接判断（易误报）
- **磁盘容量需读 `File.getUsableSpace()`**，无现成接口
- Quartz 的 `QRTZ_*` 表由框架管理，不建议直接查

---

## 九、前端路由与待办可达性（审计重点 7）

### 9.1 后台路由全清单（相关部分）

| 路由 | 组件 | 存在 |
|---|---|---|
| `/trade/order` | `trade/Orders.vue` | ✅ |
| `/trade/refund` | `trade/Refunds.vue` | ✅ |
| `/trade/entitlement` | `trade/Entitlements.vue` | ✅ |
| `/service/demand` | `service/DemandList.vue` | ✅ |
| `/service/project` | `service/ProjectList.vue` | ✅ |
| `/product/list` | `product/ProductList.vue` | ✅ |
| `/user/list` | `user/UserList.vue` | ✅ |
| `/blog/list` | `blog/BlogList.vue` | ✅ |

### 9.2 关键缺陷：三个跳转是"假筛选"

| 待办 → 目标 | 页面是否存在 | 是否消费参数 |
|---|---|---|
| 待确认收款 → `/trade/order?status=PENDING_CONFIRM` | ✅ | ✅ `Orders.vue` 读 query |
| 待处理退款 → `/trade/refund?status=APPLIED` | ✅ | ❌ **不读 query** |
| 待响应需求 → `/service/demand?status=PENDING_QUOTE` | ✅ | ❌ **不读 query** |
| 逾期里程碑 → `/service/project?overdue=1` | ✅ | ❌ **不读 query** |
| 权益核查 → `/trade/entitlement` | ✅ | 无需参数 |

`Refunds.vue`、`Entitlements.vue`、`DemandList.vue`、`ProjectList.vue` 均无 `useRoute()` / `route.query`。

**后果**：从仪表盘点"待处理退款 2 条"进去，看到的是全部退款列表，用户要自己再筛一遍。G2 质量门（待办数量与业务页一致）无法通过。

**处置选项**（实施阶段决定，不在本次审计范围）：

- A. 这 4 个页面补 query 初始化（改动小，各页约 5–10 行）
- B. 待办跳转不带筛选，仅到列表，并在 UI 上注明"需手动筛选"
- 推荐 A —— 否则待办区的数量与落点永远对不上账。

### 9.3 其他

- 无 `useRoute` 的页面也意味着**不支持浏览器前进/后退保留筛选**，这是同一处改造可顺带解决的。
- 后台无 403/权限页面路由，统一 404 兜底。

---

## 十、数量核对（与 PRD 的偏差）

| 项 | PRD 记载 | 实测 | 说明 |
|---|---|---|---|
| 数据库表 | 62 | **61** | 迁移 58 + `sql/` 增量去重后 61；`schema.sql` 的 19 张全被迁移包含 |
| 接口总数 | 263 | **263** | 一致（`@*Mapping` 计数） |
| 后台接口 | 76 | **156** | 29 个后台控制器文件的映射数；PRD 的 76 偏低 |
| 前台页面 | 34 | 未逐页复核 | `coolpilot-web/pages` 下 25 个 `.vue`（部分为目录） |
| 后台页面 | 33 | **32** | `FEATURE-MAP` 已修正为 32 |
| 容器 | 6 | **6** | gateway / web / admin / server / mysql / redis |

> 数字有偏差不构成风险，但**文档里的数字只写一处**（`FEATURE-MAP.md`）。本次审计的实测值以本文为准。

---

## 十一、审计未覆盖的部分（诚实声明）

以下内容本次**没有验证**，实施前必须补：

1. **生产库真实数据分布** —— 无生产访问权限。`paid_time IS NULL` 的成交订单是否存在、`visit_log` 实际行数、`blog.views` 与 `visit_log` 的差异幅度，都需在实施阶段用只读 SQL 校验。
2. **接口实测响应** —— 未启动服务调用接口验证返回结构（审计期间本地服务已停）。
3. **性能基线** —— 未做 EXPLAIN，未测 P95。PRD §9.4 的 1 秒目标尚无数据支撑。
4. **前台页面逐页核对** —— 只看了 `pages/` 目录结构，未逐页确认实际调用的接口。
5. **`city_visitor` 写入任务** —— `VisitorSyncScheduleTask` 末尾有城市同步逻辑，未逐行核对。

---

## 十二、结论

**可以做，但必须先修三件事，否则图表是错的。**

| 优先级 | 事项 | 阻塞什么 |
|---|---|---|
| P0 | 代理头透传（UA / X-Real-IP / X-Forwarded-For / Referer） | 内容 UV、回访用户、所有访客维度指标 |
| P0 | 成交金额改用 `paid_time` 归属，区间改左闭右开 | 经营总览、经营趋势、所有金额口径 |
| P1 | 4 个列表页补 query 初始化 | 待办跳转、G2 一致性门 |

**可以立即开工的部分**：`overview` / `trends`（修正口径后）、`todos`（不含退款跳转筛选）、`services`（数据最完整）、`health`（探测现成）。

**依赖修复才能做的部分**：`content` 的 UV 与排行、`users` 的活跃指标。
