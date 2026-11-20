# 后台仪表盘：数据可行性分析

> 版本：`v2.1` ｜ 日期：2026-11-21 ｜ 代码基线：`v1.1.3`
> **地位：本文不再作为需求基线。** 需求与口径以 `PRD-ADMIN-DASHBOARD.md`（CS-ADMIN-DASHBOARD-PRD-01 V2.0）为准。
> 本文的价值是**逐指标的可行性核查**：哪些能算、哪些算不出、为什么。供 `PRD-ADMIN-DASHBOARD.md` §6.1 的数据映射引用。
> 审计明细：`dashboard/01-CURRENT-STATE.md`、`02-DATA-MAPPING.md`、`03-API-MAPPING.md`。
>
> v2.1 变更：经 CP-DASHBOARD-01 只读审计，修正三处误判（`product.view_count` 实际有写入、`sales_count` 是净销量而非累计、成交时间轴应为 `paid_time`），并据实调整不可算指标数量。

---

## 零、结论摘要

**方向已定**：

| 决策项 | 结论 |
|---|---|
| 管理范围 | **全平台经营管理**：内容 + 用户 + 商品 + 订单 + 服务交付 + 系统健康 |
| 页面风格 | **经营驾驶舱**：数据卡 + 趋势图 + 待办 + 业务榜单 |
| AI 经营分析 | **后续阶段引入**（Phase 5），首期只保证数据准确 |

**可行性核查的核心结论**（详见 §三）：

> 六区结构全部成立，但 **62 个指标里有 5 个当前算不出来、6 个只能给近似值**，原因不是接口没写，是**数据没采**。
> 其中 3 个属于"改几行代码就能修"，另外 2 个需要新建采集链路。

| 分类 | 指标数 | 说明 |
|---|---|---|
| ✅ 现成可算 | 51 | 订单/退款/商品/服务/待办/系统，SQL 直接出数 |
| ⚠️ 条件可算 | 6 | 近似口径或仅累计值（文章排行、分类排行、活跃用户、商品转化率、存储容量、AI 可用性） |
| ❌ 当前不可算 | 5 | 内容 UV、回访用户、访客→注册转化、渠道归因、内容→商品转化 |

**5 个不可算的分布**：

| 指标 | 根因 | 修复成本 |
|---|---|---|
| 内容 UV | 代理头未透传，访客标识坍缩 | 几行代码 |
| 回访用户 | 同上 | 随 UV 一起解决 |
| 访客→注册转化 | 缺 `uuid ↔ user_id` 关联 | 新建映射表 |
| 渠道 / 来源归因 | 表里没有 referer / UTM | 新建采集 |
| 内容→商品转化 | 无归因链路 | 新建采集 |

**必须先修的三项**（§3.4）：代理头未透传、`blog.views` 无时间切片、访客标识不关联用户。**不修这三项，内容与用户两区的图表画出来就是错的。**

> **已排除的误判**：`product.view_count` 与 `sales_count` 都有写入（`ProductService.detail` / `PaymentService.onPaid`），商品转化率**可算**，只是仅有累计口径、无时间切片。详见 02 文档 §6。

---

## 一、定位

后台登录后的默认落地页（`/` → `/dashboard`）。打开后应当立刻回答六个问题：

1. 平台现在赚了多少、账对不对？
2. 用户在看什么、什么内容有价值？
3. 哪些商品/课程/服务在产生收入？
4. 哪些订单、需求、项目等着我处理？
5. 哪个环节出问题了（逾期、失败、异常）？
6. 接下来先做什么？

**它不是** ERP，不是 BI 报表，也不该是"数据库有什么表就摆什么图"。它是个人技术创业者的**每日开工面板**。

与 v1.0 的差别：v1.0 只回答"今天有没有钱进来"（4 张卡 + 5 条订单）。v2.0 扩到六个业务面，但**首屏仍然只放经营与待办**，其余向下滚动。

---

## 二、信息架构

```
┌──────────────────────────────────────────────────────────────┐
│  CoolSkyAI · 管理驾驶舱        [最近30天 ▾] [刷新] 更新于 12:04 │
├──────────────────────────────────────────────────────────────┤
│ 01 经营总览   成交金额 / 净收入 / 退款金额 / 待确认收款          │
│               新增订单 / 客单价                                │
├──────────────────────────────────────────────────────────────┤
│ 02 经营趋势   成交·退款·净收入 折线 + 用户增长 折线（双图并排）   │
├──────────────────────────────────────────────────────────────┤
│ 03 待办事项   待确认收款 / 待处理退款 / 待响应需求 / 待确认报价   │
│               逾期里程碑 / 未处理反馈          （可点击跳转）     │
├──────────────────────────────────────────────────────────────┤
│ 04 内容运营   内容总数/新增/PV/收藏/互动 + 热门文章TOP5 + 搜索词 │
├──────────────────────────────────────────────────────────────┤
│ 05 用户运营   用户总数/新增/付费用户/客单 + 增长趋势 + 付费榜     │
├──────────────────────────────────────────────────────────────┤
│ 06 商品与服务 商品销量TOP5 + 需求漏斗 + 项目交付进度 + 逾期预警  │
├──────────────────────────────────────────────────────────────┤
│ 07 平台健康   后端/DB/Redis/存储/AI/定时任务/支付异常/系统异常   │
└──────────────────────────────────────────────────────────────┘
```

**纵向分区，不是九宫格堆首屏**。首屏（01+02+03）在不滚动的情况下可见，04–07 向下浏览。

> 注：原方案是"六区"，实施时把「待办」从「商品与服务」里提出来独立成区 —— 因为待办是**唯一需要每天点进去处理**的东西，混在业务分析里会被忽略。这是对原方案的唯一结构调整。

---

## 三、指标定义与数据支撑核查

### 3.1 核查方法

对每个指标问三个问题：

1. **数据存在吗** —— 有没有字段能承载这个事实？
2. **数据在写吗** —— 有没有代码在维护它（不是建了字段就完事）？
3. **口径一致吗** —— 同一指标在多处出现时，算法是否相同？

三个都"是" → ✅ 可算。缺一个 → ⚠️ 或 ❌。

### 3.2 可用数据源

**交易域**

| 表 | 关键字段 | 可用于 |
|---|---|---|
| `orders` | `pay_amount` / `status` / `create_time` / `paid_time` / `user_id` | 成交、订单数、客单价、趋势 |
| `order_item` | `product_id` / `product_type` / `amount` / `quantity` | 商品销量与销售额（快照表，商品改名不影响历史） |
| `payment` | `status` / `amount` / `channel` / `notify_raw` / `create_time` | 待确认收款、支付失败、渠道分布 |
| `refund` | `amount` / `status` / `apply_time` / `refund_time` / `biz_no` | 退款额、退款率、退款时效 |
| `entitlement` | `user_id` / `product_id` / `status` | 权益存量与发放 |

**内容与用户**

| 表 | 关键字段 | 可用于 |
|---|---|---|
| `blog` | `views` / `create_time` / `published` / `category` | 内容数、新增、累计 PV |
| `visit_log` | `uuid` / `behavior` / `content` / `create_time` / `ip` | 分区 PV、搜索词、文章排行（区间） |
| `visit_record` | `pv` / `uv` / `date` | 日趋势（已由定时任务每日落盘） |
| `visitor` | `uuid` / `pv` / `first` / `lastTime` | 回访判定（不可靠，见 §3.4） |
| `user` | `create_time` / `last_login_time` / `role` / `status` | 用户数、新增 |
| `login_log` | `username` / `status` / `create_time` | **仅后台管理员登录**，平台用户不在内 |
| `favorite` / `like_record` / `comment` | `target_type` / `target_id` | 互动量、收藏量 |

**商品与服务**

| 表 | 关键字段 | 可用于 |
|---|---|---|
| `product` | `product_type` / `status` / `price` / `sales_count` / `view_count` | 商品数、上架数（`view_count` 无效，见 §3.4） |
| `product_category` | `name` / `product_type` | 品类分布 |
| `course` / `lesson` | `lesson_count` / `is_free_preview` | 课程完课结构 |
| `demand` | `status` / `create_time` / `budget_min/max` / `expect_date` | 需求漏斗 |
| `quote` | `status` / `amount` / `valid_until` | 报价转化、即将过期报价 |
| `project` | `status` / `amount` / `end_date` | 项目进度、交付额 |
| `milestone` | `due_date` / `status` / `delivered_time` / `accepted_time` | **逾期预警（最高价值指标）** |
| `delivery` | `status` / `delivered_time` / `accepted_time` | 交付验收时效 |

**系统域**

| 表 / 端点 | 可用于 |
|---|---|
| `GET /health`（现成） | 后端、MySQL（`SELECT 1`）、Redis（`PING`）真实探测 |
| `schedule_job` / `schedule_job_log` | 定时任务最近执行状态与失败 |
| `exception_log` | 系统异常计数与最近报错 |
| `ai_usage` | AI 调用量、失败率、平均耗时、token 消耗 |
| `file_info` | 存储占用（`SUM(size)`） |

### 3.3 逐指标可行性

图例：✅ 现成可算 ｜ ⚠️ 条件可算（需说明口径或小改造）｜ ❌ 当前不可算

#### 01 经营总览

| 指标 | 口径 | 状态 |
|---|---|---|
| 成交金额 | `SUM(pay_amount)`，`status IN (PAID, COMPLETED, REFUNDING, REFUNDED)`，按 **`paid_time`（支付确认时间）** | ✅ 复用 `sumPaidAmount`，需改时间字段 |
| 实际净收入 | 成交金额 − 退款金额 | ✅ 后端计算 |
| 退款金额 | `SUM(amount)`，`refund.status='SUCCESS'`，按 `refund_time` | ✅ 复用 `RefundMapper.sumRefunded` |
| 待确认收款 | `COUNT(payment WHERE status='WAIT_CONFIRM')` | ✅ 复用 `PaymentService.countWaitingConfirm` |
| 新增订单 | `COUNT(orders)` 按 `create_time`，**全状态**（含未支付） | ✅ |
| 客单价 | 成交金额 ÷ 成交订单数 | ✅ |

> **两个金额的时间轴不同**（成交按下单、退款按退款完成）是**有意保留**的，因为「这个月卖了多少」和「这个月退了多少」本来就是两个问题。但必须**分开显示、分开标注**，禁止把两者相减后再喊"本月净收入"而不说明跨期差异。见 §四 规则 R1。

#### 02 经营趋势

| 指标 | 粒度 | 状态 |
|---|---|---|
| 成交金额趋势 | 时/日/周/月 | ✅ `GROUP BY` 时间表达式 |
| 退款金额趋势 | 日/周/月 | ✅ |
| 净收入趋势 | 日/周/月 | ✅ 两个序列按同一时间桶对齐后相减 |
| 新增用户趋势 | 日/周/月 | ✅ `user.create_time` |
| 新增订单趋势 | 日/周/月 | ✅ |

> 小时粒度只允许跨度 ≤ 7 天，否则全表扫。周/月用 `YEARWEEK` / `DATE_FORMAT`。

#### 03 待办事项

| 待办 | 判定 | 状态 |
|---|---|---|
| 待确认收款 | `payment.status='WAIT_CONFIRM'` | ✅ |
| 待处理退款 | `refund.status='APPLIED'` | ✅ |
| 待审核需求 | `demand.status='PENDING_REVIEW'` | ✅ |
| 待报价需求 | `demand.status='PENDING_QUOTE'` | ✅ |
| 待客户确认报价 | `demand.status='PENDING_CONFIRM'` | ✅ |
| 待交付项目 | `project.status IN ('IN_PROGRESS','PENDING_ACCEPT')` 且存在未验收里程碑 | ✅ |
| 逾期里程碑 | `due_date < CURDATE() AND status NOT IN ('ACCEPTED','REJECTED')` | ✅ ★ |
| 未处理反馈 | `feedback.status` 为待处理态 | ✅ |

> **没有「待审核评论」这一项**。核实过：`CommentUtils` 插入时按站点配置直接 `setPublished(true)`（或 `commentDefaultOpen`），评论**发布即公开，没有审核环节**。`published` 是"公开 / 移入回收站"的开关，不是"待审核"。不要凭想象加待办项 —— 那样点进去会看到空列表。

> **待办数字必须与跳转页面的角标同源**（同一 SQL）。否则仪表盘显示 3、点进去看到 5，会立刻失去信任。见 §四 规则 R6。

#### 04 内容运营

| 指标 | 口径 | 状态 |
|---|---|---|
| 内容总数 | `COUNT(blog)`（含未发布，另给已发布数） | ✅ |
| 新增文章 | `blog.create_time` 落在区间 | ✅ |
| 总 PV（累计） | `SUM(blog.views)` | ✅ Redis 累加 + 定时刷盘，数据真实 |
| 区间 PV | `COUNT(visit_log WHERE behavior='BLOG')` | ✅ |
| **独立访客 UV** | `COUNT(DISTINCT visit_log.uuid)` | ❌ **失真**，见 §3.4-① |
| 收藏量 / 互动量 | `favorite` / `like_record` / `comment` 计数 | ✅ |
| 热门文章 TOP10（区间） | `visit_log` 按 `content`（文章标题）聚合 | ⚠️ 文章改名会断档，且标题非唯一键 |
| 分类访问排行 | `visit_log WHERE behavior='CATEGORY'` 按 `content` | ⚠️ 同上 |
| 热门搜索词 TOP10 | `visit_log WHERE behavior='SEARCH'` 按 `content` | ✅ |
| 内容 → 商品转化 | 无归因链路 | ❌ 需新建采集 |

> **文章浏览量有两个数，别混**：`blog.views` 是**单调累加的累计值**（没有日增量，无法切时间窗）；`visit_log` 能切区间但只能按标题聚合。页面必须写清标签：一个是"累计阅读"，一个是"近 N 天阅读"。

#### 05 用户运营

| 指标 | 口径 | 状态 |
|---|---|---|
| 用户总数 | `COUNT(user WHERE role='ROLE_user')` | ✅ |
| 新增用户 | `user.create_time` 落在区间 | ✅ |
| 付费用户数 | `COUNT(DISTINCT user_id) FROM orders WHERE status IN (成交集合)` | ✅ |
| 付费转化率 | 付费用户 ÷ 注册用户 | ✅（分母是注册用户，**不是访客**） |
| 活跃用户 | — | ❌ 无事件源：`login_log` 只记后台管理员；`last_login_time` 是覆盖写快照 |
| 回访用户 | `visitor.pv > 1` | ❌ 依赖失效的访客标识 |
| **访客 → 注册转化率** | 需要 `visit_log.uuid` ↔ `user.id` 关联 | ❌ **不存在关联字段** |
| **用户来源 / 渠道** | 需要 `referer` / `utm_*` | ❌ **表里没有这些字段** |
| 用户增长趋势 | `GROUP BY DATE(create_time)` | ✅ |

> 用户来源这块**不要做成"图表先上、数据后补"**。没有 referer 和 UTM，任何"来源分布饼图"都是编的。这属于**采集能力建设**，不是前端工作。见 §七 阶段二前置。

#### 06 商品运营

| 指标 | 口径 | 状态 |
|---|---|---|
| 商品总数 / 上架数 | `product` 全量 / `status='ON_SALE'` | ✅ |
| 商品销量 | `SUM(order_item.quantity)` JOIN 成交订单 | ✅ |
| 商品销售额 | `SUM(order_item.amount)` JOIN 成交订单 | ✅ |
| 商品退款率 | 该商品订单中产生成功退款的订单数 ÷ 该商品成交订单数 | ✅（`refund.biz_no` ↔ `orders.order_no`） |
| 热门商品 TOP10 | 按销售额或销量 | ✅ |
| 品类收入结构 | `order_item.product_type` 分组 | ✅ |
| **商品转化率** | `sales_count / view_count` | ⚠️ 两字段都在维护，但**只有累计值**、无时间切片；`view_count` 不过滤爬虫与 SSR 重复请求 |

> `sales_count` 与 `view_count` **都有写入**（`PaymentService.onPaid` 加、`RefundService.finish` 减；`ProductService.detail` 加）。但 `sales_count` 语义是**净销量**（退款扣减），且两者都无时间切片。所以商品销量、销售额排行**一律走 `order_item` 聚合**（R7），只把 `sales_count` 当作"当前净销量"展示。

#### 07 服务交付

| 指标 | 口径 | 状态 |
|---|---|---|
| 新增服务需求 | `demand.create_time` 区间 | ✅ |
| 待评审 / 待报价 / 待确认 | `status` 分别为 `PENDING_REVIEW` / `PENDING_QUOTE` / `PENDING_CONFIRM` | ✅ 已有 `DemandMapper.countPending()` |
| 报价转化率 | `quote` 中 `ACCEPTED` ÷ (`ACCEPTED`+`REJECTED`+`EXPIRED`) | ✅ |
| 进行中项目 | `project.status='IN_PROGRESS'` | ✅ |
| 待交付项目 | `status IN ('IN_PROGRESS','PENDING_ACCEPT')` | ✅ |
| **逾期里程碑** | `due_date < CURDATE() AND status NOT IN ('ACCEPTED','REJECTED')` | ✅ **建议放首屏** |
| 交付额（已验收） | `SUM(milestone.amount) WHERE status='ACCEPTED'` | ✅ |
| 平均交付周期 | `AVG(accepted_time - delivered_time)` | ✅ |
| 已完成项目 | `status='COMPLETED'` | ✅ |

> 服务交付是**六个区里数据最完整、最值得做的一块** —— 状态机是完整的（`demand → quote → project → milestone → delivery`），每一跳都有时间戳和操作人。这里的图表几乎零成本。

#### 08 平台健康

| 检查项 | 探测方式 | 状态 |
|---|---|---|
| Java 后端 | 进程存活（本接口自身可返回即证明） | ✅ |
| MySQL | `SELECT 1` | ✅ 复用 `HealthController` |
| Redis | `PING` | ✅ 复用 `HealthController` |
| 文件存储 | `SELECT SUM(size) FROM file_info` + 磁盘可用空间 | ⚠️ 需读 `File.getUsableSpace()` |
| AI 服务 | 无主动探测接口；用 `ai_usage` 近 24h 成功率间接判断 | ⚠️ 间接指标，标注清楚 |
| 定时任务 | `schedule_job` 最后执行时间 + `schedule_job_log` 最近失败 | ✅ |
| 支付回调异常 | `payment WHERE status='FAIL'` 近 N 天 | ✅ |
| 系统异常 | `exception_log` 近 24h 计数 | ✅ |

> **健康度绝不能"页面能打开就全绿"**。每一项必须有独立的探测动作和独立失败原因。AI 那一项如果只是读历史成功率，UI 上必须写"近 24 小时调用成功率"，不能写"AI 服务正常"。

### 3.4 三个数据缺陷（阻塞项）

这三条不修，内容与用户两区的图表不可信。

#### ① 代理头未透传 → 全站 UV 坍缩成一个

`coolpilot-web/server/api/[...path].ts` 转发请求时只带了两个头：

```ts
const headers: Record<string, string> = {}
if (authorization) headers.authorization = authorization
if (contentType) headers['content-type'] = contentType
// ↑ 没有 user-agent，没有 x-real-ip，没有 referer
```

而访客标识的算法是 `小时戳 + IP + User-Agent` 的 SHA 摘要。后端拿到的 IP 是 web 容器地址、UA 是 Nitro 服务端的 fetch UA —— **所有真实用户在同一小时内算出同一个 uuid**。

证据：线上 `visit_record.uv` 长期是个位数，而 PV 三百多。这不是"访客少"，是"访客被合并了"。同理，`visit_log.ip_source`、`city_visitor` 的城市分布也全部失真（记的是容器 IP 的归属地）。

**修法**（本次唯一必须做的采集修复）：

```ts
// 透传真实客户端信息
const ua = getHeader(event, 'user-agent')
if (ua) headers['user-agent'] = ua
const realIp = getHeader(event, 'x-real-ip')
if (realIp) headers['x-real-ip'] = realIp
const fwd = getHeader(event, 'x-forwarded-for')
if (fwd) headers['x-forwarded-for'] = fwd
const referer = getHeader(event, 'referer')
if (referer) headers.referer = referer
```

修完后 UV 才成立。**注意**：修完不会追平历史数据，历史 UV 一律视为无效，趋势图要标注起始日期。

另外，`小时戳` 这个设计本身也让 UV 偏低（同一 IP 同一 UA 在同一小时内只算一个）。若要做准，建议改成**天戳**或改用 Cookie 持久标识 —— 顺带这也是与上游 NBlog 逻辑脱钩的一处。

#### ② `product` 计数字段可用，但只有累计口径（已修正）

> **v2.1 更正**：v2.0 曾判断这两个字段"空转、永远是 0"，这是检索失误 —— 用 `viewCount` 搜 Java 字段名，而写入方法叫 `increaseView`、SQL 字段叫 `view_count`，都没命中。实际两者都在维护。

| 字段 | 写入点 | 语义 |
|---|---|---|
| `view_count` | `ProductService.detail()` → `increaseView()` | 前台详情页访问次数 |
| `sales_count` | `PaymentService.onPaid()` +qty / `RefundService.finish()` −qty | **净销量**（退款扣减，`GREATEST` 兜底不为负） |

**真实限制不是"没有数据"，而是"没有时间维度"**：

- 两个字段都是单调累加，**无法按时间区间切片**
- `view_count` 直接 DB 自增，不过滤爬虫、后台预览、SSR 重复请求

**因此**：

- 商品**累计**转化率可算：`SUM(sales_count) / SUM(view_count)`
- 商品**区间**转化率不可算 → UI 必须标注"累计"
- 商品销量/销售额排行**仍必须走 `order_item`**：`sales_count` 是净额且无法按区间切

无需新增埋点。若要区间转化率，得另建 `product_view_daily`，当前判定为不做。

#### ③ `blog.views` 没有时间切片

`views` 是 Redis 累加后整体刷盘的**单值**，只有"历史累计"，没有"某天新增"。

所以"近 30 天热门文章"只能靠 `visit_log` 按标题聚合，而标题不是稳定键（改名即断）。

**修法（低成本）**：新增 `blog_view_daily(blog_id, stat_date, pv, uv)` 汇总表，由 `RedisSyncScheduleTask` 刷盘时顺手按天写入。表很小（文章数 × 天数），比查 `visit_log` 快得多，且能稳定按 `blog_id` 聚合。

不改也能用 `visit_log`，但要接受"标题改名断档"和"每次查询扫日志表"两个代价。

#### ④ 访客标识不关联用户

`visit_log.uuid` 是匿名标识，`user.id` 是账号，两者之间**没有任何字段连通**。所以：

- "访客 → 注册 → 付费"这条漏斗断在第二环
- 无法回答"付费用户是从哪篇文章进来的"

**修法**：访客登录/注册成功时，把 `uuid` 回写一张 `visitor_user_link(uuid, user_id, bind_time)` 映射表。**这是新建采集链路，属于阶段二的前置工作，不能靠前端补。**

---

## 四、统计口径冻结规则

以下 12 条为**基线约束**，后续任何统计接口/sql 都必须满足。写入代码注释，评审时逐条对照。

| # | 规则 |
|---|---|
| R1 | **成交按 `paid_time`（支付确认时间），退款按 `refund_time`（退款完成时间）**。两者都是资金实际发生的时间，因此可以相减得到净资金流入。**不得**把退款改为按下单时间归属 —— 那会让跨期退款挤进错误的期间。若需「某批订单累计退了多少」，用订单批次口径（按 `paid_time` 分组建批，用 `refund.biz_no` 关联），两种口径必须分开标注、禁止混用。 |
| R2 | 订单"成交"状态集合固定为 `PAID / COMPLETED / REFUNDING / REFUNDED`，提取为常量 `PAID_STATUSES`，禁止各处手写字符串。 |
| R3 | 退款"已发生"只认 `SUCCESS`。`APPLIED / APPROVED / REFUNDING` 一律不计入金额，只在待办里计数。 |
| R4 | 用户统计三分：**注册用户**（`role='ROLE_user'`）、**活跃用户**（区间内有登录）、**付费用户**（有成交订单）。三个词不能混用，指标卡必须标注是哪一个。 |
| R5 | PV 与 UV 定义：PV = 访问事件数；UV = 去重标识数。**UV 的数据源不可用时，显示"—"并注明原因，不允许用 PV 冒充 UV。** |
| R6 | 待办数量必须与跳转页面的筛选结果同源（同一 SQL 或同一 service 方法）。 |
| R7 | 商品统计一律走 `order_item` 快照（`amount`/`quantity`/`title_snapshot`），不读 `product.sales_count`、不 JOIN 商品现价。 |
| R8 | 无数据一律返回 `0` / 空数组，前端显示空状态。**禁止用示意数字、mock 数据、随机数填充。** |
| R9 | 无法统计的指标，返回 `null` + `reason` 字段，UI 显示"暂不可统计"。不允许省略该指标造成"看起来是 0"。 |
| R10 | 所有区间查询一律「左闭右开」：`字段 >= startTime AND 字段 < endTime`。成交用 `paid_time`、退款用 `refund_time`、下单用 `create_time`、日志用 `create_time` —— **按指标选字段，不按表选**。禁止 `<= endTime`，会让相邻区间重复统计边界那一秒。 |
| R11 | 首期一律 MySQL 实时聚合，**不引入 Redis 缓存、汇总表、离线任务**。单表行数超过 10 万或接口 P95 > 500ms 时再议。 |
| R12 | 时间参数统一 `yyyy-MM-dd HH:mm:ss`，服务端校验 `startTime < endTime`，非法直接 400，不静默纠正。 |

---

## 五、接口设计

### 5.1 总原则

按**业务域聚合**，一个区域一个接口，不按指标拆接口。统一前缀 `/admin/dashboard`，统一参数，统一响应外壳（沿用现有 `Result`）。

**不为符合命名机械新建** —— 能复用底层 service/mapper 的，直接调。

### 5.2 接口清单

| 接口 | 职责 | 复用基础 |
|---|---|---|
| `GET /admin/dashboard/overview` | 经营总览 6 指标 | `OrderMapper.sumPaidAmount/countPaid`、`RefundMapper.sumRefunded`、`PaymentService.countWaitingConfirm` |
| `GET /admin/dashboard/trends` | 成交/退款/净收入/用户/订单 趋势 | 新增聚合 SQL |
| `GET /admin/dashboard/todos` | 8 类待办计数 + 各自跳转 | `DemandMapper.countPending`、`RefundMapper.countAdmin`、`MilestoneMapper` |
| `GET /admin/dashboard/content` | 内容规模 + 排行 + 搜索词 | `BlogMapper`、`VisitLogMapper` |
| `GET /admin/dashboard/users` | 用户规模 + 增长 + 付费 | `UserMapper`、`OrderMapper` |
| `GET /admin/dashboard/products` | 商品销量/收入/退款率排行 | `OrderItemMapper` + `RefundMapper` |
| `GET /admin/dashboard/services` | 需求漏斗 + 项目进度 + 逾期 | `DemandMapper`、`ProjectMapper`、`MilestoneMapper` |
| `GET /admin/dashboard/health` | 8 项健康探测 | `HealthController` 的 checkDatabase/checkRedis、`ScheduleJobLogMapper`、`ExceptionLogMapper`、`AiUsageMapper` |

> **不做**「每个指标一个接口」。8 个接口是上限，能合并的合并（例如 `trends` 一次返回 5 条序列，而不是 5 个接口）。

### 5.3 统一查询参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `startTime` | `yyyy-MM-dd HH:mm:ss` | 可选，缺省 = 不设下限 |
| `endTime` | `yyyy-MM-dd HH:mm:ss` | 可选，左闭右开 |
| `granularity` | `hour` / `day` / `week` / `month` | 仅 `trends` 使用，默认 `day` |
| `comparePeriod` | `boolean` | 是否返回上一等长区间用于环比，默认 `false` |
| `limit` | `int` | 榜单条数，默认 5（后台页面只放 TOP5），上限 20 |

**预设区间由前端换算成时间戳后传参**，不在后端维护"最近30天"的语义 —— 避免时区与"今天算不算"的歧义。

### 5.4 响应示例

`GET /admin/dashboard/overview?startTime=2026-10-22 00:00:00&endTime=2026-11-21 00:00:00`

```json
{
  "code": 200,
  "msg": "OK",
  "data": {
    "range": {"startTime": "2026-10-22 00:00:00", "endTime": "2026-11-21 00:00:00"},
    "paidAmount": 12680.00,
    "refundAmount": 1400.00,
    "netAmount": 11280.00,
    "waitingConfirm": 3,
    "newOrders": 24,
    "paidOrders": 18,
    "avgOrderValue": 704.44,
    "compare": {
      "paidAmountDelta": 0.23,
      "newOrdersDelta": -0.08
    },
    "notes": {
      "paidAmount": "按订单创建时间统计，含已退款订单",
      "refundAmount": "按退款完成时间统计，仅含已成功退款"
    }
  }
}
```

`notes` 字段是刻意的：**口径说明跟着数据走**，前端直接渲染成卡片下方的灰字，避免"这个数到底算的什么"要靠翻文档。

`GET /admin/dashboard/todos`

```json
{
  "code": 200, "msg": "OK",
  "data": {
    "total": 12,
    "items": [
      {"key": "payment_confirm", "label": "待确认收款", "count": 3, "path": "/trade/order?status=PENDING_CONFIRM", "level": "warn"},
      {"key": "refund_applied",  "label": "待处理退款", "count": 2, "path": "/trade/refund?status=APPLIED",           "level": "danger"},
      {"key": "demand_quote",    "label": "待报价需求", "count": 4, "path": "/service/demand?status=PENDING_QUOTE",  "level": "info"},
      {"key": "milestone_overdue","label": "逾期里程碑","count": 1, "path": "/service/project?overdue=1",           "level": "danger"}
    ]
  }
}
```

`GET /admin/dashboard/health`

```json
{
  "code": 200, "msg": "OK",
  "data": {
    "checkedAt": "2026-11-21 12:04:31",
    "items": [
      {"key": "backend",   "label": "后端服务", "status": "UP",   "detail": "uptime 3d 4h"},
      {"key": "mysql",     "label": "MySQL",    "status": "UP",   "detail": "SELECT 1 · 8ms"},
      {"key": "redis",     "label": "Redis",    "status": "UP",   "detail": "PING · 1ms"},
      {"key": "storage",   "label": "文件存储", "status": "WARN", "detail": "已用 3.2GB · 磁盘剩余 8%"},
      {"key": "ai",        "label": "AI 服务",  "status": "UNKNOWN", "detail": "近24h调用 0 次，无法判断"},
      {"key": "job",       "label": "定时任务", "status": "UP",   "detail": "最近执行 12:00 成功"},
      {"key": "payment",   "label": "支付异常", "status": "UP",   "detail": "近7天失败 0 笔"},
      {"key": "exception", "label": "系统异常", "status": "DOWN", "detail": "近24h 5 条"}
    ]
  }
}
```

`status` 四态：`UP` / `WARN` / `DOWN` / `UNKNOWN`。**必须有 `UNKNOWN`** —— 数据不足时说"不知道"比说"正常"诚实。

### 5.5 与现有接口的关系

| 现有接口 | 处置 |
|---|---|
| `GET /admin/stats/sales` | **保留**。作为 `overview` 的下层复用，暂不下线（`coolpilot-admin/src/api/trade.js` 仍引用）。新页面切到 `overview` 后再决定是否废弃。 |
| `GET /admin/dashboard`（`DashboardAdminController`） | **保留但不接前端**。其中 PV/UV/分类分布将在阶段二被 `content` 接口取代；`cityVisitor` 因 §3.4-① 数据失真，**不接入**。先确认无其他消费者再删。 |
| `GET /health` | 复用其探测逻辑（抽成 `HealthProbeService`，两处共用）。 |

---

## 六、页面设计

### 6.1 布局

沿用现有后台的 `page-card` / `page-header` 结构与 CSS 变量，**不引入图表库之外的任何新依赖**。图表用 ECharts（项目已用于旧仪表盘，按需引入 `LineChart` / `BarChart` 减少包体）。

```
顶部：标题 + 区间选择器（今日/昨日/近7天/近30天/本月/上月/自定义）+ 刷新 + "更新于 HH:mm"
01 六张指标卡（3×2 或 6×1，窄屏降级 2×3）
02 两张折线图并排（左：金额三序列；右：用户/订单）
03 待办：4–6 个可点击条目，带角标与颜色分级
04 内容：左 5 指标 + 右 TOP5 表 + 搜索词标签云
05 用户：指标 4 个 + 增长折线 + 付费用户 TOP5
06 商品服务：商品 TOP5 表 + 需求漏斗条 + 项目进度条 + 逾期预警条
07 健康：8 个状态点横排，异常项展开详情
```

### 6.2 交互约定

- **区间切换立即重算全部 7 个区域**，一次并发 8 个请求，任一失败只让对应区域显示错误，不影响其他区。
- **不做自动轮询**。顶部显示"更新于"，手动刷新。理由：数据量小、变化慢，轮询只增加噪声。
- **每一张指标卡必须带口径副标题**（"按创建时间" / "仅含成功退款" / "累计值"）。
- **空数据不是错误**：显示 `0` 或"暂无数据"，并给一句引导（如"还没有成交，去[商品管理]上架第一个"）。
- 窄屏（< 1200px）单列纵向堆叠，指标卡两列。

### 6.3 视觉

- 沿用现有 token：主色 `#00875A`，卡片白底 `border-radius:10px`，`border-left:3px` 作为区域色标。
- 金额涨用**红**、跌用**绿**（国内习惯，与 `regional_conventions` 一致）。
- 健康状态色：`UP #16a34a` / `WARN #d97706` / `DOWN #dc2626` / `UNKNOWN #909399`。

---

## 七、分期实施

### 阶段 0：数据采集修复（前置，必做）

| 任务 | 文件 | 影响 |
|---|---|---|
| 代理透传 UA / X-Real-IP / X-Forwarded-For / Referer | `coolpilot-web/server/api/[...path].ts` | UV 从"失效"变"可用" |
| 访客标识从小时戳改天戳（或改 Cookie） | `VisitLogAspect.hourMillis()` | UV 精度 |
| 新增 `blog_view_daily` 汇总表 | `V29__dashboard.sql` + 刷盘任务 | 文章区间排行可用 `blog_id` 聚合 |
| 4 个列表页补 query 初始化 | `Refunds` / `DemandList` / `ProjectList` / `Entitlements` | 待办跳转不再"假筛选" |

**验收**：连续两天线上 `visit_record.uv > 1` 且与 PV 比例合理；文章榜单能按 `blog_id` 稳定聚合；从待办点进去看到的是筛选后的列表。

> `product.view_count` / `sales_count` **无需修复**（v2.1 已确认两者都在维护），商品转化率直接读累计值。

### 阶段 1：骨架 + 经营（优先）

- 新页面布局（纵向七区）
- 区间选择器 + 并发加载 + 分区容错
- `overview` / `trends` / `todos` 三个接口
- 最近订单（沿用现有 `GET /admin/orders`）
- 健康度区（后端/DB/Redis/任务/异常，**先做能真探测的这 5 项**）

**产出可上线**：这一步做完，仪表盘已经是"可用的经营面板"，不依赖任何采集修复。

### 阶段 2：内容与用户（依赖阶段 0）

- `content` / `users` 接口
- PV/UV 趋势、热门文章 TOP5、搜索词
- 用户增长、付费用户、付费转化率
- 访客→注册→付费漏斗（**需先建 `visitor_user_link`**）

**约束**：UV 只展示修复日期之后的数据，并在图上标注分界。渠道来源**不做**，除非先加 referer/UTM 采集。

### 阶段 3：商品与服务

- `products` / `services` 接口
- 商品销量/收入 TOP5、品类结构、退款率
- 需求漏斗（提交→报价→确认→签约→交付）、逾期里程碑预警
- 商品转化率（**仅累计口径**：`sales_count / view_count`）

### 阶段 4：健康度补全 + AI 分析

- AI 服务探测（轻量 ping 或读 `ai_usage` 成功率）
- 存储容量、支付异常趋势
- 经营异常识别（规则型：退款率突增、逾期增加、转化下滑）
- AI 经营分析（**必须放在最后**，且只允许读已校验的聚合结果，不允许让模型自己写 SQL）

---

## 八、首期明确不做

| 不做 | 原因 |
|---|---|
| 独立 BI / 数据仓库 | 数据量级不到万行，MySQL 聚合足够 |
| 为仪表盘复制业务表 | 违反单一数据源，必然对不上账 |
| 实时推送 / WebSocket | 个人站无此需求，手动刷新够用 |
| AI 自动生成经营结论 | 数据还没准，结论必然错 |
| 渠道归因 / UTM 分析 | 采集链路不存在，做了就是编数据 |
| 删除旧 `/admin/dashboard` | 先确认无消费者，且它承载历史统计逻辑 |
| Redis 缓存统计结果 | 违反 R11，过早优化 |

---

## 九、风险与待定

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| 1 | 修复代理头后历史 UV 与新增 UV 不可比 | 趋势图在修复日出现断层 | 图上标注分界点，历史段灰显 |
| 2 | `visit_log` 的 `content` 存标题，文章改名断档 | 文章排行历史数据分裂 | 阶段 0 建 `blog_view_daily` 后不再依赖标题聚合 |
| 3 | 线上交易量为 0，所有交易图表是空态 | 无法验证口径正确性 | 用 `demo-data.sql` 在本地造 30 天数据验证，**不写入生产** |
| 4 | `product` 计数字段无时间切片 | 只能算累计转化率，给不出区间转化率 | UI 标注"累计"；区间排行走 `order_item` |
| 5 | 无权限细分，所有 `ROLE_owner` 看到全部 | 当前单账号，无实际风险 | 多账号时再引入（不在本期） |
| 6 | 健康度里 AI 项依赖间接指标 | 可能误报"正常" | 用 `UNKNOWN` 态，不猜 |

**待确认（需产品决策，不在本文档范围内自行决定）**：

1. 阶段 0 的「访客标识改天戳」会改变 UV 口径 —— 是否接受 UV 数值显著下降（更真实）？
2. 是否允许在仪表盘首屏放「逾期里程碑」这类可能长期为 0 的红字预警？还是收进待办区？
3. 阶段 2 的 `visitor_user_link` 是否值得做 —— 它需要改注册/登录链路，收益是"付费用户来源"这一条链路。

---

## 十、附录：v1.1.3 现状快照

保留 v1.0 文档的事实，供对照"改了什么"。

| 项 | 现状 |
|---|---|
| 页面文件 | `coolpilot-admin/src/views/dashboard/index.vue`（220 行，单文件，无图表） |
| 数据来源 | `GET /admin/stats/sales` + `GET /admin/orders?pageNum=1&pageSize=5` |
| 区域 | 4 张统计卡 + 最近 5 条订单 + 3 个待办入口 |
| 区间筛选 | 无（接口支持 `startTime/endTime`，前端从不传） |
| 自动刷新 | 无 |
| 孤儿接口 | `GET /admin/dashboard`（PV/UV/分类/城市），前端零调用 |
| 线上数据 | 商品 0 · 订单 0 · 权益 0 · 退款 0，四卡全 `¥0.00` |

**从 v1.0 到 v2.0 的实质变化**：从"4 个交易数字"扩到"6 个业务面 + 健康度"，并且把"能不能算"这件事查清楚了 —— 而不是先画图再发现数据是空的。
