# ED-02：系统架构与后端模块设计

> 版本：v1.0 ｜ 日期：2026-11-04
> 基线：coolpilot-server 211 个 Java 文件 / 81 个接口 / 19 张表（Spring Boot 4.1.2 + JDK 21）
> 关联文档：`ARCHITECTURE.md`（现状）、`PRD-V1.md`（需求）、`DATA-MODEL.md`（数据）

---

## 一、架构决策

### D-ARCH-1：模块化单体，不拆微服务

| 维度 | 判断 |
|---|---|
| 组织结构 | 只有 1 名开发者，无并行团队，微服务的组织收益为零 |
| 业务规模 | 单机 MySQL + Redis 足以支撑个人平台数年 |
| 事务复杂度 | 支付回调要同时改订单、支付、权益三张表，单体一个本地事务解决；拆开就必须引入 Saga/Outbox |
| 部署成本 | 单体 = 1 个 jar + 2 个静态站点；微服务要容器编排、服务发现、链路追踪 |

**结论**：单进程 + 单库 + 模块边界靠包结构和代码约束表达。

### D-ARCH-2：不重构现有 211 个文件

这是本文档最重要的一条。

现状是**水平分层**：`controller` / `service` / `mapper` / `entity` 平铺在 `com.coolskyai` 下。新业务如果按域垂直分包，会出现两套风格。两种选择：

| 方案 | 代价 | 结论 |
|---|---|---|
| A. 把 211 个文件全量迁到 `modules/content/...` | 改动面 100%，IDEA 重构 + 全量回归，无任何业务收益，且极易在迁移中引入隐性 bug | ❌ 否决 |
| B. 旧代码不动，新业务垂直分包 | 两套风格共存，需明确规则 | ✅ 采用 |

**采用 B，并立规三条：**

1. 遗留包（`com.coolskyai.{controller,service,mapper,entity,model,util,config,...}`）视为**冻结区**，只允许修 bug，不允许新增业务代码
2. 新业务一律落在 `com.coolskyai.modules.{domain}`，内部自带 `controller/service/mapper/entity/dto/vo`
3. 两区之间只能通过 `XxxFacade` 接口调用，不允许直接注入对方的 Mapper

> 将来若真要拆分，`modules/*` 已经是天然的拆分边界，届时把包重命名成独立模块即可。

### D-ARCH-3：不引入 Maven 多模块

单 pom 即可。多模块会增加 `mvn install` 次数、IDE 索引复杂度，而模块边界用 ArchUnit 或包约定就能约束。等你真的需要在 CI 中独立发布某个模块时再拆。

---

## 二、目标包结构

```
com.coolskyai
├── BlogApiApplication.java
│
├── 【遗留核心区 · 冻结】
│   ├── controller/            12 个前台控制器
│   ├── controller/admin/      16 个后台控制器
│   ├── service/ + impl/       19 + 19
│   ├── mapper/                18
│   ├── entity/                18
│   ├── model/dto/ vo/         12 + 20
│   ├── config/                8（Security / Redis / Web ...）
│   ├── aspect/ annotation/    3 + 3
│   ├── task/ interceptor/ handler/
│   └── util/                  40
│
└── 【新业务区 · 增长】
    └── modules/
        ├── content/          专题、教程、SEO 扩展
        ├── product/          商品、课程、资源
        ├── trade/            订单、支付、退款
        ├── entitlement/      权益
        ├── service/          服务商品、服务订单、交付
        ├── demand/           需求、报价、项目、里程碑
        ├── community/        收藏、点赞、反馈、消息
        ├── file/             上传、下载、文件元数据
        ├── notification/     站内消息、邮件
        ├── ai/               知识库、检索（V1.3）
        └── analytics/        经营统计
```

单个模块内部：

```
modules/trade/
├── api/                  对外暴露的 Facade 接口（其他模块只能依赖这里）
│   └── TradeFacade.java
├── controller/           仅本模块的路由
├── service/
│   ├── OrderService.java
│   ├── PaymentService.java
│   └── impl/
├── mapper/
├── entity/
├── dto/                  入参
├── vo/                   出参
├── enums/                本域枚举
└── statemachine/         状态机（仅 trade / service / demand 需要）
```

---

## 三、模块职责与依赖矩阵

### 3.1 职责

| 模块 | 包 | 职责 | 对应表 |
|---|---|---|---|
| `file` | `modules.file` | 上传、下载鉴权、版本、审计 | `file` `file_version` `download_record` |
| `entitlement` | `modules.entitlement` | 权益发放/撤销/校验（**唯一准入判断方**） | `entitlement` `entitlement_log` |
| `product` | `modules.product` | 商品、价格、课程、课时、可下载资源 | `product` `product_category` `product_price` `course` `lesson` `product_article` `product_resource` |
| `trade` | `modules.trade` | 订单、支付单、退款；状态机；幂等 | `orders` `order_item` `payment` `refund` |
| `demand` | `modules.demand` | 需求、报价、项目、里程碑 | `demand` `quote` `project` `milestone` |
| `service` | `modules.service` | 服务商品扩展、服务订单、交付验收 | `service_item` `service_order` `delivery` |
| `community` | `modules.community` | 收藏、点赞（评论沿用遗留区） | `favorite` `like_record` `feedback` |
| `notification` | `modules.notification` | 站内消息 + 邮件/Telegram 复用遗留 channel | `message` |
| `content` | `modules.content` | 专题、教程（文章/分类/标签沿用遗留区） | `topic` `topic_article`（V1.0 新增） |
| `ai` | `modules.ai` | 知识库、RAG（只读内容与权益） | `kb_*` `ai_*`（V1.3） |
| `analytics` | `modules.analytics` | 经营统计（只读，不建表） | — |

### 3.2 依赖矩阵（✅ 允许，空 = 禁止）

| 调用方 ↓ / 被调用 → | file | entitlement | product | trade | service | demand | content | notify |
|---|---|---|---|---|---|---|---|---|
| product | ✅ | | | | | | ✅ | |
| trade | | ✅ | ✅ | | | | | ✅ |
| service | ✅ | | ✅ | | | ✅ | | ✅ |
| demand | ✅ | | | | | | | ✅ |
| community | ✅ | | | | | | ✅ | |
| ai | | ✅ | | | | | ✅ | |
| analytics | | | | | ✅ | | ✅ | |
| content | ✅ | | | | | | | |
| notification | | | | | | | | |
| file / entitlement | — | — | — | — | — | — | — | — |

**四条硬规则**

1. `entitlement` **不得**依赖 `trade` —— 权益判断不能回查订单，否则退款撤销、赠送、人工授权都无法表达
2. `product` **不得**依赖 `trade` —— 商品不知道自己被谁买了
3. `content` **不得**依赖 `product` —— 文章不知道自己被谁付费了，关联由 `product_article` 单向维护
4. `file` / `entitlement` 是叶子模块，**不得**依赖任何业务模块

**循环依赖的唯一风险点**：`trade → entitlement`（发权益）与 `service → demand`（转项目）。方向已固定，不会成环。如果将来 `entitlement` 需要发消息，走 `notification` 的 Facade，但 `notification` 也不回调 `entitlement`。

### 3.3 跨模块调用方式

只允许依赖对方 `api` 包下的 Facade 接口：

```java
// modules/entitlement/api/EntitlementFacade.java
public interface EntitlementFacade {
    /** 支付成功后发放权益；幂等：已存在则跳过 */
    void grant(GrantCommand cmd);
    /** 退款撤销；按 orderNo 精确撤销 */
    void revokeByOrder(String orderNo, String reason);
    /** 当前用户是否可访问某资源 */
    boolean hasAccess(Long userId, String resourceType, Long resourceId);
}
```

`trade` 注入 `EntitlementFacade`，看不到 `EntitlementMapper`。这样将来 `entitlement` 内部改表、加缓存，`trade` 无需改动。

---

## 四、事务边界

| 场景 | 边界 | 说明 |
|---|---|---|
| 下单 | `trade.service.OrderService#create` | 写 `orders` + `order_item`，一个本地事务 |
| 支付回调 | `trade.service.PaymentService#handleCallback` | 改 `payment` + 改 `orders` + 调 `entitlement.grant`，**必须同一事务** |
| 退款审核 | `trade.service.RefundService#approve` | 改 `refund` + 调 `entitlement.revokeByOrder`，同事务 |
| 服务订单创建 | `service.service.ServiceOrderService#create` | 写 `service_order`，需求状态流转，同事务 |
| 里程碑交付 | `service.service.DeliveryService#deliver` | 写 `delivery` + 改 `milestone`，同事务 |

**为什么支付回调必须同事务**：`payment` 更新成功但 `entitlement.grant` 抛异常，用户付了钱拿不到内容。要么都成功，要么都回滚后由渠道重试。

**什么时候才需要 Outbox / MQ**：当权益发放变成跨进程（例如独立的权益服务），或需要触发耗时任务（发邮件、生成课程副本）。V1 阶段同库同事务，**不要提前上 MQ**。

---

## 五、鉴权落地

### 5.1 现状

- `SecurityConfig`：`/admin/**` → `hasRole("admin")`，其余 `permitAll()`
- 身份由 `JwtFilter` 写入 `SecurityContext`，token 前缀 `admin:` / `visitor:`
- 鉴权失败返回统一 JSON（`MyAuthenticationEntryPoint` / `MyAccessDeniedHandler`）

### 5.2 新接口的三条规则

| 规则 | 做法 |
|---|---|
| 后台接口必须挂在 `/admin/**` 下 | 否则要么绕过 `hasRole` 保护，要么得改 Security 规则。**新后台接口路径统一 `/admin/product/**`、`/admin/order/**`…** |
| 前台"我的"接口必须校验归属 | 不能只靠 `userId` 从请求参数取；必须从 `SecurityContext` 拿当前用户，再按 `user_id` 过滤 |
| 付费资源必须走 `entitlement` | 不查订单状态、不查前端传的标记 |

### 5.3 【阻塞项】前台身份链路当前是断的

**这是做任何"我的 XX"功能之前必须先修的前置问题。**

```java
// config/JwtFilter.java 第 34-37 行
// 后台管理路径外的请求直接跳过
if (!request.getRequestURI().startsWith(request.getContextPath() + "/admin")) {
    filterChain.doFilter(request, servletResponse);
    return;
}
```

后果：

1. `/visitor/login` 签发 `visitor:` token，但**后端没有任何地方消费它** —— 前台 token 目前是装饰性的
2. 非 `/admin` 路径下 `SecurityContextHolder` 为空，`Authentication` 为 null
3. 少数控制器（`BlogController` / `CommentController` / `OperationLogAspect`）自己手工 `getTokenBody(header)`，属于各自为政，且用途混杂（密码文章 token 里存的居然是 `blogId`）

**改造方案（新增 T-101，先于所有业务卡）**

```
JwtFilter 改为：所有路径都尝试解析 token
  ├─ 命中有效 token → 写入 SecurityContext（authorities = ROLE_owner / ROLE_user）
  ├─ 未带 token → 放行（前台匿名浏览不受影响）
  └─ token 非法/过期
       ├─ /admin/** → 返回 403（保持现状）
       └─ 其他路径 → 放行，按匿名处理（避免旧的密码文章 token 被误判）
```

配套：

| 项 | 做法 |
|---|---|
| 当前用户 | 新增 `CurrentUser` 工具类：`getUserId()` / `getRole()` / `isOwner()`，从 `SecurityContext` 取，取不到返回 null |
| 前台接口鉴权 | `SecurityConfig` 增加 `.requestMatchers("/favorite/**","/orders/**","/messages/**","/demands/**","/entitlements/**").hasAnyRole("owner","user")` —— **注意必须 `hasAnyRole`**，因为 OWNER 的角色是 `ROLE_owner`，不包含 `ROLE_user` |
| 兼容 | `subject` 仍以 `admin:` / `visitor:` 前缀区分，解析时拆前缀取真实用户名/ID；不能破坏 `BlogController` 现有的密码文章校验逻辑 |

> 不做这一步，收藏、订单、消息全部只能"写死用户"或"信任前端传 userId"，后者是越权漏洞。

### 5.4 角色迁移

`ROLE_admin → ROLE_owner`、`ROLE_visitor → ROLE_user`。影响面：

| 位置 | 改动 |
|---|---|
| `SecurityConfig` | `hasRole("admin")` → `hasRole("owner")` |
| `JwtConstants` | 前缀 `admin:` 可保留（语义是"博主 token"），不必改 |
| `VisitorAuthController` | 写入 `ROLE_user` |
| `coolpilot-admin` | 路由守卫里 `role !== 'ROLE_admin'` 的判断 |
| `coolpilot-web` | 顶栏登录态判断 |

> 建议：**先加兼容**（`hasAnyRole("admin","owner")`），灰度跑一轮后再收口成 `hasRole("owner")`。这样数据迁移和代码发布可以解耦。

---

## 六、横切约定

| 关注点 | 约定 |
|---|---|
| 响应 | 沿用 `Result{code,msg,data}`，**不改结构**（两个前端依赖它） |
| 分页 | 参数 `pageNum`（从 1 开始）、`pageSize`（默认 10）；返回 `PageResult{totalPage,list}`，**新增可选 `total` 字段**（向后兼容，后台列表需要精确总数） |
| 异常 | 业务异常抛 `BadRequestException` / `NotFoundException`，由 `ControllerExceptionHandler` 统一转 JSON |
| 校验 | `spring-boot-starter-validation`，DTO 上加 `@NotBlank` / `@Size`，Controller 加 `@Valid` |
| 限流 | 复用 `@AccessLimit`（注册/登录已在用），支付回调不限制 |
| 审计 | 后台写操作加 `@OperationLogger`；订单/权益状态变更写 `entitlement_log` 与操作日志 |
| 缓存 | 商品、权益走 Redis；键名沿用现有 `<domain>:<id>` 风格 |
| 日志 | 支付回调必须打印 `biz_no` / `trade_no` / 验签结果 / 处理耗时 |

---

## 七、状态机实现规范

`trade`、`service`、`demand` 三个模块的交易类状态**禁止**在 Controller 或 Service 里散落 `if (status.equals(...))`。

统一写法：

```java
public final class OrderStateMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
        PENDING_PAY, Set.of(PAID, CANCELLED, CLOSED),
        PAID,        Set.of(COMPLETED, REFUNDING),
        REFUNDING,   Set.of(REFUNDED, PAID),   // 退款被拒可回到 PAID
        ...
    );

    public static void assertCanTransit(OrderStatus from, OrderStatus to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BadRequestException("订单状态不允许从 " + from + " 变更为 " + to);
        }
    }
}
```

好处：状态流转图就是代码，改规则只改这张 Map，且非法流转会被立刻拒绝而不是产生脏数据。

---

## 八、NFR 落地检查表

| 维度 | 要求 | 现有状态 | 待办 |
|---|---|---|---|
| 密码 | BCrypt | ✅ 已用 `HashUtils.getBC` | — |
| 鉴权 | JWT + 资源级校验 | ⚠️ 接口级已有，资源级缺 | V1.0 补归属校验 |
| 支付安全 | 验签 + 幂等 + 金额校验 | ❌ 无支付 | V1.1 |
| 文件安全 | 不暴露永久地址 | ❌ 当前直链 | V1.1a 重建 |
| 审计 | 操作/登录/异常/访问四类 | ✅ 已完整 | 新增权益流水 |
| 性能 | 索引 + 缓存 | ⚠️ 内容域已做，交易域待建 | V1.1 |
| 测试 | 单测 + 集成测试 | ❌ 仅空壳 | **阶段 0 必须补** |
| 部署 | Docker / Nginx / 健康检查 | ❌ 仓库无配置 | 阶段 0 补 |
| 可观测 | 错误日志 + 业务日志 | ⚠️ 有 logback，无业务埋点 | V1.1 支付链路 |

> **阶段 0 的测试与部署两项是硬门槛**：没有测试就动交易代码，等于在无护栏的高速上换轮胎。

---

## 九、迁移路线（绞杀者模式）

```
V1.0  新增 modules/community, modules/content, modules/file(骨架)
      遗留区：只加 SEO 字段相关代码
      ↓
V1.1a 新增 modules/product + modules/file 完整实现
      ↓
V1.1b 新增 modules/trade + modules/entitlement
      遗留区完全不动
      ↓
V1.2  新增 modules/service + modules/demand
      ↓
V1.3  新增 modules/ai
```

每个阶段结束时，`modules/` 增加一层，遗留区保持冻结。**任何时候都不出现"为了整齐而搬文件"的提交。**

---

## 十、风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 两套包风格导致新人困惑 | 维护成本 | 包根目录放 `README.md` 说明冻结区/增长区；新代码评审时检查落点 |
| 遗留 `UserServiceImpl` 同时实现 `UserDetailsService` | 角色改名会牵动登录链路 | 先兼容双角色，再收口 |
| `PageResult` 只有 `totalPage` | 后台无法显示精确总数 | 加可选 `total` 字段，前端按需读取 |
| 支付回调与超时关单并发 | 订单状态互相覆盖 | 状态机 + `UPDATE ... WHERE status=?` 条件更新（乐观并发） |
| 权益缓存与数据库不一致 | 用户明明买了却提示无权限 | 缓存只做加速，**判定仍以数据库 + 唯一索引为准**；发放/撤销时删缓存而非改缓存 |
