# CP-DASHBOARD-02 · 统计数据源映射

> 文档编号：CP-DASHBOARD-02 ｜ 任务：统计数据源映射
> 审计日期：2026-11-21 ｜ 代码基线：`v1.1.3`
> **性质：只读映射。本文所有 SQL 均为设计参考，未执行、未上线。**
> 质量门：满足 G1 —— 每个 P0 指标均可追溯到表、字段、状态、时间归属与验证样例。

---

## 一、61 张表的域归位

按 Flyway 迁移（58 张）+ `sql/` 增量脚本去重后共 **61 张**。归位如下：

### 1.1 被仪表盘消费（42 张）

| 域 | 表 | 张数 |
|---|---|---|
| 交易 D01 | `orders` `order_item` `payment` `refund` `entitlement` `entitlement_log` `product_price` | 7 |
| 内容 D02 | `blog` `blog_tag` `category` `tag` `topic` `topic_article` `moment` `comment` | 8 |
| 用户 D03 | `user` `visitor` `visit_log` `visit_record` `city_visitor` `favorite` `like_record` `message` `feedback` | 9 |
| 商品 D04 | `product` `product_category` `product_resource` `course` `lesson` | 5 |
| 服务 D05 | `demand` `quote` `project` `milestone` `delivery` `service_item` `service_event` | 7 |
| 系统 D06 | `schedule_job` `schedule_job_log` `exception_log` `operation_log` `file` | 5 |
| AI 辅助 | `ai_usage`（仅用于健康度成功率） | 1 |

### 1.2 不参与统计（19 张）

| 表 | 原因 |
|---|---|
| `about` `friend` | 单页内容配置，非运营指标 |
| `nav_menu` `site_setting` `social_account` | 站点配置 |
| `file_version` | 附件历史版本，无统计价值 |
| `product_article` `portfolio` | 作品集 / 商品关联文章，属展示层 |
| `oss_project` `oss_project_article` `oss_project_tag` | 开源观察栏目，独立业务，不纳入经营 |
| `download_record` | 下载留痕，当前无统计需求 |
| `ai_conversation` `ai_message` `ai_setting` | AI 会话明细，隐私敏感，仪表盘不读 |
| `kb_knowledge_base` `kb_document` `kb_chunk` | 知识库存储，非经营指标 |

> **原则**：仪表盘只读"能形成经营判断"的表。配置类、展示类、会话明细类一律不接，避免把运维细节堆进驾驶舱。

---

## 二、交易域字段与状态（D01）

### 2.1 四张核心表

**`orders`**

| 字段 | 类型 | 统计用途 |
|---|---|---|
| `order_no` | varchar | 幂等键、跨表关联 |
| `user_id` | bigint | 付费用户去重 |
| `biz_type` | varchar | 业务类型（当前仅 PRODUCT） |
| `total_amount` | decimal | 原价，**不用于统计** |
| `discount_amount` | decimal | 优惠额 |
| `pay_amount` | decimal | **成交金额唯一来源**（实付） |
| `status` | varchar(20) | 8 状态，见 2.2 |
| `paid_time` | datetime NULL | **现金流口径的时间轴** |
| `create_time` | datetime NOT NULL | 下单时间（订单批次口径） |
| `pay_expire_time` | datetime NULL | 超时关单判定 |
| `cancel_time` | datetime NULL | 取消时间 |

**`order_item`**（快照，商品改名/改价不影响历史）

| 字段 | 统计用途 |
|---|---|
| `order_id` | 关联订单 |
| `product_id` | 商品维度聚合 |
| `product_no` / `product_type` | 品类结构 |
| `title_snapshot` / `cover_snapshot` | 榜单展示（历史值） |
| `price_snapshot` | 成交单价快照 |
| `quantity` | 销量 |
| `amount` | **商品销售额唯一来源** |

**`payment`**

| 字段 | 统计用途 |
|---|---|
| `status` | 5 状态，见 2.3 |
| `amount` | 收款额 |
| `paid_time` | 实际到账时间（与 `orders.paid_time` 同事务） |
| `channel` | 收款渠道 |
| `notify_raw` | 回调原文（排障） |
| `transfer_time` / `payer_name` / `proof_file_id` | 人工转账凭证 |

**`refund`**

| 字段 | 统计用途 |
|---|---|
| `status` | 5 状态，见 2.3 |
| `amount` | **退款金额唯一来源** |
| `biz_no` | 关联 `orders.order_no` |
| `payment_no` | 关联支付单 |
| `apply_time` | 申请时间（时效分析） |
| `audit_time` | 审批时间 |
| `refund_time` | **退款口径的时间轴**，仅 `SUCCESS` 时有值 |

### 2.2 订单状态映射表（冻结）

| 状态 | 中文 | 计入成交金额 | 计入订单数 | 说明 |
|---|---|---|---|---|
| `PENDING_PAY` | 待支付 | ❌ | 仅"下单数" | |
| `PENDING_CONFIRM` | 待确认 | ❌ | 仅"下单数" | 已提交转账凭证 |
| `PAID` | 已支付 | ✅ | ✅ | **几乎不存在**，见 2.4 |
| `COMPLETED` | 已完成 | ✅ | ✅ | 支付确认后立即进入 |
| `REFUNDING` | 退款中 | ✅（不扣减） | ✅ | 退款另行统计 |
| `REFUNDED` | 已退款 | ✅（不扣减） | ✅ | 退款另行统计 |
| `CANCELLED` | 已取消 | ❌ | ❌ | 用户主动取消 |
| `CLOSED` | 已关闭 | ❌ | ❌ | 超时关单 |

常量定义（实施时写入代码，禁止各处手写）：

```java
/** 计入成交金额与成交订单数的状态集合 */
public static final Set<String> PAID_STATUSES =
        Set.of("PAID", "COMPLETED", "REFUNDING", "REFUNDED");
```

> `REFUNDING` / `REFUNDED` **计入成交额是有意的**：它回答"历史上收进来过多少钱"。真实到账要看净资金流入。

### 2.3 支付与退款状态（冻结）

| 表 | 状态 | 是否计入金额 |
|---|---|---|
| `payment` | `INIT` | ❌ |
| | `WAIT_CONFIRM` | ❌（计入**待办数**） |
| | `SUCCESS` | ✅ 收款额 |
| | `FAIL` | ❌（计入**异常数**） |
| | `CLOSED` | ❌ |
| `refund` | `APPLIED` | ❌（计入**待办数**） |
| | `APPROVED` | ❌（已审批未打款） |
| | `REFUNDING` | ❌ **代码中无写入点**，保留枚举 |
| | `SUCCESS` | ✅ 退款额 |
| | `REJECTED` | ❌ |

### 2.4 一个必须记住的行为

`PaymentService.onPaid()` 在确认收款后**立即**调用 `orderMapper.markCompleted()`。

因此：成功支付的订单最终状态是 `COMPLETED`，`PAID` 只存在于事务中间态。

**任何只筛 `status='PAID'` 的查询都会返回 0。** 必须用 `PAID_STATUSES` 集合。

### 2.5 `paid_time` 的写入保证

`onPaid` 标注 `@Transactional(rollbackFor = Exception.class)`，`markSuccess` 与 `markPaid` 在同一事务内：

```
payment: status='SUCCESS', paid_time=NOW()
orders : status='PAID',    paid_time=NOW()
```

→ 两表 `paid_time` 一致，**现金流口径直接用 `orders.paid_time`，无需 JOIN `payment`**。

**待验证**（实施前执行）：是否存在成交状态但 `paid_time IS NULL` 的历史脏数据。

```sql
SELECT COUNT(*) FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND paid_time IS NULL;
```

预期为 0。若不为 0，说明存在绕过 `onPaid` 直接改状态的路径，需先补数据再上线统计。

---

## 三、两套口径：必须同时存在，不能混

PRD §4.2 要求区分两种分析视角。它们的用途不同，**必须分别标注，禁止相加或互相替代**。

### 3.1 口径 A：经营现金流（默认口径）

**回答**：这个期间实际进出了多少钱。

| 指标 | 时间轴 | 状态过滤 |
|---|---|---|
| 期间成交金额 | `orders.paid_time` | `PAID_STATUSES` |
| 期间退款金额 | `refund.refund_time` | `SUCCESS` |
| 期间净资金流入 | 上两者相减（各自按自身时间轴） | — |

```sql
-- 期间成交金额（资金流入）
SELECT IFNULL(SUM(pay_amount), 0) AS amount
FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND paid_time >= #{startTime}
  AND paid_time <  #{endTime};

-- 期间退款金额（资金流出）
SELECT IFNULL(SUM(amount), 0) AS amount
FROM refund
WHERE status = 'SUCCESS'
  AND refund_time >= #{startTime}
  AND refund_time <  #{endTime};

-- 期间成交订单数
SELECT COUNT(*) FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND paid_time >= #{startTime}
  AND paid_time <  #{endTime};
```

**性质**：左闭右开，与 PRD §3.1 一致。跨期退款**会**减少退款发生期的净流入，**不会**回溯修改原成交期 —— 这是正确的会计处理。

**限制**：不能据此说"这批订单最终退了多少钱"，那要口径 B。

### 3.2 口径 B：订单批次（COHORT）

**回答**：某个月成交的那批订单，到现在累计退了多少。

```sql
SELECT
  DATE_FORMAT(o.paid_time, '%Y-%m') AS cohort_month,
  COUNT(*)                          AS order_count,
  SUM(o.pay_amount)                 AS paid_amount,
  IFNULL(SUM(r.refunded), 0)        AS refunded_amount
FROM orders o
LEFT JOIN (
  SELECT biz_no, SUM(amount) AS refunded
  FROM refund WHERE status = 'SUCCESS' GROUP BY biz_no
) r ON r.biz_no = o.order_no
WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND o.paid_time >= #{startTime} AND o.paid_time < #{endTime}
GROUP BY cohort_month
ORDER BY cohort_month;
```

**用途**：商品退款率、渠道质量、售后成本分析。

**禁止**：把口径 B 的 `refunded_amount` 当作"本期退款金额"放进经营总览 —— 它包含未来才发生的退款。

### 3.3 与其他指标的关系

| 指标 | 口径 |
|---|---|
| 客单价 | 期间成交金额 ÷ 期间成交订单数（同为口径 A） |
| 退款率 | 期间退款金额 ÷ 期间成交金额（A÷A，可跨期，需标注） |
| 商品退款率 | 口径 B（该商品批次累计退款 ÷ 该批次成交额） |

> 退款率的分子分母属于不同批订单，数学上不是"同一批的退款比例"。UI 必须写清"本期退款额 / 本期成交额"，不能写"退款率"三个字了事。

---

## 四、P0 指标映射（经营总览 / 趋势 / 待办）

### 4.1 `overview`

| 指标 | 表.字段 | 状态过滤 | 时间归属 | SQL 参考 |
|---|---|---|---|---|
| `salesAmount` | `orders.pay_amount` | `PAID_STATUSES` | `paid_time` | §3.1 |
| `refundAmount` | `refund.amount` | `SUCCESS` | `refund_time` | §3.1 |
| `netAmount` | 计算值 | — | — | 上两值相减 |
| `pendingPaymentCount` | `payment` | `WAIT_CONFIRM` | **无时间条件**（当前快照） | `SELECT COUNT(*) FROM payment WHERE status='WAIT_CONFIRM'` |
| `newOrderCount` | `orders` | **全部状态** | `create_time` | 见下 |
| `paidOrderCount` | `orders` | `PAID_STATUSES` | `paid_time` | §3.1 |
| `averageOrderValue` | 计算值 | — | — | `salesAmount / paidOrderCount`，分母为 0 时返回 `null` |
| 环比 | — | — | — | 同口径查上一等长区间 |

```sql
-- 新增订单：按下单时间，含未支付（回答"有多少人下单"）
SELECT COUNT(*) FROM orders
WHERE create_time >= #{startTime} AND create_time < #{endTime};
```

> **两个"订单数"不能混**：`newOrderCount` 按下单时间含未支付，`paidOrderCount` 按支付时间只含成交。前者衡量转化入口，后者衡量成交规模。

### 4.2 `trends`

时间桶表达式：

| 粒度 | 表达式 | 跨度限制 |
|---|---|---|
| `HOUR` | `DATE_FORMAT(paid_time,'%Y-%m-%d %H:00')` | ≤ 7 天 |
| `DAY` | `DATE(paid_time)` | ≤ 90 天 |
| `WEEK` | `YEARWEEK(paid_time, 3)` | ≤ 1 年 |
| `MONTH` | `DATE_FORMAT(paid_time,'%Y-%m')` | 不限 |

```sql
SELECT DATE(paid_time) AS bucket, SUM(pay_amount) AS amount, COUNT(*) AS cnt
FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND paid_time >= #{startTime} AND paid_time < #{endTime}
GROUP BY bucket ORDER BY bucket;
```

**补零规则**：SQL 只返回有数据的桶，**服务端必须按粒度生成完整桶序列并填 0**。否则前端折线图会把两个有数据的日子连成直线，掩盖中间的空档。

**净流入序列**：成交桶与退款桶按同一时间键 LEFT JOIN 后相减，任一侧缺失填 0。

### 4.3 `todos`

| 待办 | 表 | 条件 | 数量来源 |
|---|---|---|---|
| 待确认收款 | `payment` | `status='WAIT_CONFIRM'` | `PaymentService.countWaitingConfirm()` ✅ 现成 |
| 待处理退款 | `refund` | `status='APPLIED'` | 新增 |
| 待审核需求 | `demand` | `status='PENDING_REVIEW'` | 新增 |
| 待报价需求 | `demand` | `status='PENDING_QUOTE'` | `DemandMapper.countPending()` 含此项 |
| 待确认报价 | `demand` | `status='PENDING_CONFIRM'` | 同上 |
| 待交付项目 | `project` | `status IN ('IN_PROGRESS','PENDING_ACCEPT')` | 新增 |
| 逾期里程碑 | `milestone` | 见下 | 新增 |
| 未处理反馈 | `feedback` | `status` 待处理态 | 新增 |

```sql
-- 逾期里程碑
SELECT COUNT(*) FROM milestone
WHERE due_date IS NOT NULL
  AND due_date < CURDATE()
  AND status NOT IN ('ACCEPTED','REJECTED');
```

> **`due_date` 为 NULL 的里程碑不计入逾期**。不要用 `COALESCE(due_date, 创建日)` 兜底 —— 会制造假逾期，让预警失去可信度。

**一致性要求（G2）**：每个待办的数字必须与其跳转页面使用**同一 SQL 条件**。当前 `Orders.vue` 支持 `?status=`，其余页面不支持（见 03 文档 §四）。

---

## 五、P1 / P2 指标映射

### 5.1 内容（D02）

| 指标 | 来源 | 条件 |
|---|---|---|
| 内容总数 | `blog` | 全部 |
| 已发布数 | `blog` | `published = 1` |
| 区间新增 | `blog` | `create_time` 区间 |
| 累计浏览 | `blog` | `SUM(views)` |
| 区间 PV | `visit_log` | `behavior='BLOG'` + `create_time` 区间 |
| 区间 UV | `visit_log` | `COUNT(DISTINCT uuid)` —— **当前不可用** |
| 分类访问 | `visit_log` | `behavior='CATEGORY'`，`content` 为分类名 |
| 搜索词 | `visit_log` | `behavior='SEARCH'`，`content` 为关键词 |
| 收藏 / 点赞 | `favorite` / `like_record` | `create_time` 区间，`target_type` 区分对象 |
| 评论 | `comment` | `create_time` 区间 |

**热门文章榜单的现实限制**：

```sql
SELECT content AS title, COUNT(*) AS pv, COUNT(DISTINCT uuid) AS uv
FROM visit_log
WHERE behavior = 'BLOG'
  AND create_time >= #{startTime} AND create_time < #{endTime}
GROUP BY content
ORDER BY pv DESC
LIMIT #{limit};
```

- `content` 是**标题字符串**，不是 `blog_id`。文章改名 → 同一篇分裂成两行。
- `uv` 列因 §7.1 的缺陷不可用。
- 若需稳定榜单，方案见 §7.2。

### 5.2 用户（D03）

| 指标 | 来源 | 条件 |
|---|---|---|
| 用户总数 | `user` | `role='ROLE_user'`（快照，非期间量） |
| 新增用户 | `user` | `create_time` 区间 |
| 付费用户（区间） | `orders` | `DISTINCT user_id`，`paid_time` 区间 + `PAID_STATUSES` |
| 付费用户（累计） | `orders` | `DISTINCT user_id`，全部成交 |
| 付费转化率 | 计算 | 付费 ÷ 注册，**分母必须标注** |
| 最近登录 | `user` | `last_login_time` 降序 |
| 活跃用户 | — | ❌ 无事件源（见 01 文档 §5） |
| 回访用户 | `visitor` | `pv > 1` —— ❌ 依赖失效 uuid |
| 渠道来源 | — | ❌ 无 referer / UTM 字段 |

```sql
-- 区间付费用户（注意用 paid_time 不是 create_time）
SELECT COUNT(DISTINCT o.user_id)
FROM orders o
WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND o.paid_time >= #{startTime} AND o.paid_time < #{endTime};

-- 用户增长趋势
SELECT DATE(create_time) AS bucket, COUNT(*) AS cnt
FROM user
WHERE role = 'ROLE_user'
  AND create_time >= #{startTime} AND create_time < #{endTime}
GROUP BY bucket ORDER BY bucket;
```

### 5.3 商品（D04）

**必须用 `order_item`，不用 `product.sales_count`。**

| 指标 | 来源 | 说明 |
|---|---|---|
| 商品总数 | `product` | 全部 |
| 在售数 | `product` | `status='ON_SALE'` |
| 商品销量（区间） | `order_item.quantity` | JOIN `orders` 限定 `paid_time` 与状态 |
| 商品销售额（区间） | `order_item.amount` | 同上 |
| 商品退款额 | `refund.amount` | 经 `biz_no` 关联订单，再关联明细 |
| 商品退款率 | 口径 B | 批次累计退款 ÷ 批次成交额 |
| 累计转化率 | `sales_count / view_count` | ⚠️ 仅累计，无时间切片 |

```sql
SELECT oi.product_id,
       oi.title_snapshot AS title,
       SUM(oi.quantity)  AS qty,
       SUM(oi.amount)    AS revenue
FROM order_item oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND o.paid_time >= #{startTime} AND o.paid_time < #{endTime}
GROUP BY oi.product_id, oi.title_snapshot
ORDER BY revenue DESC
LIMIT #{limit};
```

> `title_snapshot` 参与 GROUP BY 是为了在商品被删除后仍能显示名称；同一 `product_id` 改过名会产生两行，属可接受的历史真实性。

### 5.4 服务（D05）

| 指标 | 表 | 条件 |
|---|---|---|
| 新增需求 | `demand` | `create_time` 区间 |
| 待审核 / 待报价 / 待确认 | `demand` | 对应 status |
| 进行中项目 | `project` | `status='IN_PROGRESS'` |
| 待交付项目 | `project` | `status IN ('IN_PROGRESS','PENDING_ACCEPT')` |
| 逾期里程碑 | `milestone` | §4.3 |
| 已完成项目 | `project` | `status='COMPLETED'` |
| 报价转化率 | `quote` | `ACCEPTED ÷ (ACCEPTED+REJECTED+EXPIRED)` |
| 已验收交付额 | `milestone` | `SUM(amount) WHERE status='ACCEPTED'` |
| 平均交付周期 | `milestone` | `AVG(TIMESTAMPDIFF(HOUR, delivered_time, accepted_time))` |

> `project` **没有 CANCELLED 状态**。统计"未关闭项目"时不要写 `status NOT IN ('COMPLETED','CANCELLED')`，会因枚举不存在而产生误导。

### 5.5 系统健康（D06）

| 检查项 | 来源 | 判定 |
|---|---|---|
| 后端 / MySQL / Redis | `GET /health` | 直接读 `status` / `db` / `redis` |
| 文件存储 | `file` | `SUM(size)` + `File.getUsableSpace()` |
| 定时任务 | `schedule_job_log` | 最近一条 `status`；无记录 → `UNKNOWN` |
| 支付异常 | `payment` | `status='FAIL'` 近 N 天计数 |
| 系统异常 | `exception_log` | `create_time` 近 24h 计数 + 最近 3 条 |
| AI 服务 | `ai_usage` | 近 24h `success` 率；**样本为 0 时返回 `UNKNOWN`** |

---

## 六、缺什么（P0 前置）

| # | 缺口 | 影响指标 | 补法 |
|---|---|---|---|
| 1 | 代理不透传 UA / IP | 内容 UV、回访、城市分布 | 改 `[...path].ts` 透传四个头 |
| 2 | 访客标识 = 小时戳+IP+UA | UV 精度 | 改天戳或 Cookie |
| 3 | `visit_log.content` 存标题 | 文章榜单稳定性 | 增 `blog_view_daily(blog_id, stat_date, pv, uv)` |
| 4 | `uuid` 不关联 `user_id` | 访客→注册→付费漏斗 | 增 `visitor_user_link(uuid, user_id, bind_time)` |
| 5 | 无 referer / UTM 字段 | 渠道归因 | 采集改造（阶段 2 评估） |
| 6 | `visit_log` 无保留期 | 长期性能 | 定保留期 + 归档任务 |
| 7 | 4 个列表页不读 query | 待办跳转一致性 | 各页补 5–10 行 |

---

## 七、数据缺陷的量化影响

### 7.1 UV 坍缩

**机制**：`uuid = f(小时戳, IP, UA)`，而后端收到的 IP/UA 对所有用户相同 → 同一小时全站 1 个 uuid。

**影响**：

- `visit_record.uv` 恒为个位数（线上 4）
- `COUNT(DISTINCT uuid)` 无法反映真实访客
- 所有"人均""转化率""回访率"全部失真

**修复后**：UV 会显著上升。历史数据不可回补，趋势图必须在修复日标注分界。

### 7.2 标题聚合断档

`visit_log.content` 存标题而非 ID。文章改名后，同一篇内容在榜单里出现两行。

**短期方案**：接受，榜单标注"按标题聚合"。
**长期方案**：`blog_view_daily` 表按 `blog_id` + 日期落库，由 `RedisSyncScheduleTask` 刷盘时写入。

```sql
CREATE TABLE blog_view_daily (
  blog_id    BIGINT   NOT NULL,
  stat_date  DATE     NOT NULL,
  pv         INT      NOT NULL DEFAULT 0,
  uv         INT      NOT NULL DEFAULT 0,
  PRIMARY KEY (blog_id, stat_date),
  KEY idx_date (stat_date)
);
```

> 这是**唯一建议新增的汇总表**，理由不是性能，是"标题不可作为维度"。其余指标首期全部实时聚合（PRD §6.3）。

### 7.3 商品转化率只有累计值

`view_count` / `sales_count` 都是单调累加，无时间切片。

→ 只能给"历史累计转化率"，不能给"近 30 天转化率"。UI 必须标注"累计"。

---

## 八、验证 SQL（人工复核用）

上线前逐条执行，结果与业务页面核对。

```sql
-- 1. 成交金额（区间）与订单管理页筛选结果比对
SELECT COUNT(*) AS cnt, IFNULL(SUM(pay_amount),0) AS amt
FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND paid_time >= '2026-11-01' AND paid_time < '2026-12-01';

-- 2. 待确认收款 = 支付管理页待确认数量
SELECT COUNT(*) FROM payment WHERE status = 'WAIT_CONFIRM';

-- 3. 待处理退款 = 退款管理页申请中数量
SELECT COUNT(*) FROM refund WHERE status = 'APPLIED';

-- 4. 逾期里程碑 = 项目页逾期标记数量
SELECT COUNT(*) FROM milestone
WHERE due_date IS NOT NULL AND due_date < CURDATE()
  AND status NOT IN ('ACCEPTED','REJECTED');

-- 5. 商品销售额合计应等于同期订单实付合计（允许差额 = 运费/优惠差异）
SELECT IFNULL(SUM(oi.amount),0) FROM order_item oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status IN ('PAID','COMPLETED','REFUNDING','REFUNDED')
  AND o.paid_time >= '2026-11-01' AND o.paid_time < '2026-12-01';

-- 6. 脏数据检查：成交但无支付时间
SELECT COUNT(*) FROM orders
WHERE status IN ('PAID','COMPLETED','REFUNDING','REFUNDED') AND paid_time IS NULL;

-- 7. 脏数据检查：退款成功但无退款时间
SELECT COUNT(*) FROM refund WHERE status='SUCCESS' AND refund_time IS NULL;
```

第 5 条若出现显著差额，说明 `order_item.amount` 与 `orders.pay_amount` 的折扣分摊逻辑需要单独说明（当前设计未见分摊字段）。

第 6、7 条**应为 0**。若不为 0，必须先修数据，否则所有区间统计都会漏掉这些行。

---

## 九、结论

| 分类 | 结论 |
|---|---|
| 交易域 | 数据完整，口径需从 `create_time` 改为 `paid_time`，区间改左闭右开 |
| 服务域 | **数据最完整**，五级状态机齐全，零成本可做 |
| 商品域 | 销量/销售额可用（走 `order_item`），转化率仅累计口径 |
| 内容域 | PV 可用，**UV 与文章 ID 维度不可用** |
| 用户域 | 注册/付费可用，**活跃与渠道不可用** |
| 系统域 | 探测现成，任务日志完整，AI 项只能间接判断 |

**P0 指标（overview / trends / todos）全部有据可依，前提是先修两项**：代理头透传、成交时间轴。
