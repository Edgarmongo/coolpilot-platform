# ED-05：开发阶段与任务拆分

> 版本：v1.0 ｜ 日期：2026-11-04
> 用途：可直接作为开发卡逐项执行与验收
> 前置：`ARCHITECTURE.md`（现状）、`PRD-V1.md`（需求）、`DATA-MODEL.md`（数据）、`ED-02`（模块）、`ED-04`（接口）

---

## 一、执行治理规则

任何任务执行时必须遵守，无例外：

| # | 规则 |
|---|---|
| 1 | 不得执行 `DROP TABLE` / `TRUNCATE` / 无 `WHERE` 的 `DELETE` / `UPDATE` |
| 2 | 不得清库、不得重置 `coolpilot` 数据库；验证用临时库，用完立即 `DROP DATABASE` |
| 3 | 不得修改 `application-dev.properties` 中的数据库连接与密码，除非任务明确要求 |
| 4 | 不得替换 JWT secret、不得改默认后台账号密码 |
| 5 | 不得把支付、退款相关接口直接暴露到公网前不做验签 |
| 6 | 每张卡完成必须给出：改动文件清单、验证方式、验证结果；无法验证的项要显式说明 |
| 7 | 遗留区（`com.coolskyai.{controller,service,mapper,entity,...}`）只允许改 bug 与必要的小改动，新增业务一律进 `modules/**` |
| 8 | 任何破坏性数据库变更（改列类型、删列、改唯一键）必须先出迁移脚本 + 回滚脚本，经确认后执行 |

---

## 二、阶段 0：工程审计（已完成 5 / 7）

| 编号 | 任务 | 状态 | 结论 |
|---|---|---|---|
| AUDIT-01 | 后端工程审计 | ✅ 完成 | 211 个 Java 文件、18 包，分层清晰；见 `ARCHITECTURE.md` §4.1 |
| AUDIT-02 | 数据库审计 | ✅ 完成 | 19 张表，InnoDB/utf8mb4，**无物理外键**，索引偏少（多数表只有主键） |
| AUDIT-03 | API 审计 | ✅ 完成 | 81 个接口（前台 19 / 后台 62），清单见 `ARCHITECTURE.md` §4.3 |
| AUDIT-04 | 权限审计 | ⚠️ 部分 | 见下方「关键发现」 |
| AUDIT-05 | 前端审计 | ✅ 完成 | 前台 28 vue / 10 页面；后台 38 vue / 25 页面；路由全静态 |
| AUDIT-06 | 部署审计 | ❌ 待补 | 仓库内**无** Dockerfile、无 nginx 配置、无 CI；只有一份 `deploy.sh` |
| AUDIT-07 | 测试审计 | ❌ 待补 | `src/test` 仅 1 个空壳 `BlogApiApplicationTests`，**有效单测 0** |

### 2.1 AUDIT-04 的三项关键发现

**发现 1（阻塞级）：前台身份链路是断的**

`JwtFilter` 对非 `/admin` 路径直接 `return`，所以：

- 访客 token 签发后**无人消费**
- 新做任何"我的收藏/订单/消息"接口，后端都拿不到当前用户

→ 已立为 **T-101**，排在所有业务之前。

**发现 2（高危）：`anyRequest().permitAll()` 覆盖面过大**

当前只有 `/admin/**` 受保护。一旦新增 `/favorite`、`/orders` 这类用户接口而不加规则，它们默认是**匿名可调**的。

→ 每加一组用户接口，必须同步在 `SecurityConfig` 加 `hasAnyRole("owner","user")`。

**发现 3（中危）：文件访问无鉴权**

`upload.file.access-path=/image/**` 映射到本地磁盘，任何人知道文件名即可直读。付费资源若沿用此机制，等于白送。

→ 付费文件必须走 `/file/download?token=`，见 T-201。

### 2.2 AUDIT-06 / 07 的补充任务

| 编号 | 任务 | 交付物 |
|---|---|---|
| AUDIT-06 | 部署审计与补齐 | `deploy/nginx.conf`、`Dockerfile`、`.env.example` |
| AUDIT-07 | 建立测试基线 | 覆盖登录鉴权、文章 CRUD、缓存读写的集成测试，CI 可跑 |

> 这两项**不是可选项**。V1.1b 涉及资金，没有测试就等于拿真钱做实验。

---

## 三、V1.0：内容 + 社区（9 张卡）

### T-101【已完成】前台身份上下文改造

> 状态：**2026-11-04 已完成并回归验证**，实现记录见 `engineering/03-security-audit.md` §11.2
> 实际改动：`JwtFilter` 全路径解析、新增 `util/CurrentUser`、新增 `modules/account` + `GET /me`、`SecurityConfig` 补 `/me` 规则、登录 token 统一加 `admin:`/`visitor:` 前缀、`OperationLogAspect` 剥离前缀

| 项 | 内容 |
|---|---|
| 目标 | 让后端能识别前台登录用户，为所有"我的 XX"接口提供当前用户 |
| 现状 | `JwtFilter` 仅处理 `/admin/**`；非 admin 路径 `SecurityContext` 为空；三个类各自手工解析 header |
| 改动文件 | `config/JwtFilter.java`、`config/SecurityConfig.java`、新增 `util/CurrentUser.java` |
| 禁止 | 不得改动 `BlogController` 的密码文章 token 语义（`subject` 存的是 blogId） |
| 实现要点 | ① 所有路径都尝试解析 token；② 未带 token 放行；③ 非法 token 在 `/admin/**` 返回 403、其他路径按匿名放行；④ `CurrentUser` 从 `SecurityContext` 取，不新建 ThreadLocal |
| 验收 | ① 带访客 token 调 `/favorite` 能拿到 `userId`；② 不带 token 调同一接口返回 403「请登录」；③ 未登录浏览文章页不受影响；④ 密码文章校验逻辑回归通过 |
| 测试 | 携带/不携带/伪造/过期 token 四种情况各 1 例；OWNER token 访问用户接口应通过（`hasAnyRole`） |

### T-102 数据库 V1.0 迁移

> 状态：**索引与唯一约束部分已完成**（`coolpilot-server/sql/2026-11-04-indexes-and-constraints.sql`）；
> 4 张新表（`favorite`/`like_record`/`message`/`feedback`）与 6 表加列尚未执行，待 T-104 起随功能落地。

| 项 | 内容 |
|---|---|
| 目标 | 落地 4 张新表 + 6 张表加列 + 角色改名 |
| 现状 | 19 张表，DDL 已在 `DATA-MODEL.md` 并实测通过 |
| 改动 | 新建 `favorite` `like_record` `message` `feedback`；`blog` 加 4 列、`category` 加 3 列、`comment` 加 `user_id`、`moment` 加 2 列、`user` 加 3 列、`friend` 加 `sort` |
| 禁止 | 不得改列类型、不得删列、不得加物理外键 |
| 验收 | 迁移脚本可在空库与现有库各跑一次；重复执行不报错（`ADD COLUMN IF NOT EXISTS` 或先查 `information_schema`） |
| 测试 | 迁移后 19+4=23 张表齐全；`SELECT COUNT(*)` 各表数据量与迁移前一致 |

### T-103 角色迁移 ✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 目标 | `ROLE_admin → ROLE_owner`、`ROLE_visitor → ROLE_user` |
| 状态 | **已完成并回归**，HEAD 提交「refactor(security): 角色语义迁移」 |
| 落地方式 | ① 新增 `Role`（`ROLE_owner`/`ROLE_user` + `normalize`）与 `SecurityPaths`（`OWNER_ONLY` / `AUTHENTICATED`）；② `SecurityConfig` 改为 `hasAuthority(Role.OWNER)` / `hasAnyAuthority(Role.OWNER, Role.USER)`；③ token 前缀改 `owner:` / `user:`，`JwtFilter` 归一化旧 token 的 `ROLE_admin`/`ROLE_visitor`，升级不踢人；④ `CurrentUser` 提供 `isOwner()` / `ownerUsername()` / `userId()`，控制器不再手工拆前缀；⑤ Flyway `V5__role_rename.sql` 收敛库内数据并给 `role` 加默认值 `ROLE_user` |
| 关键约束 | **先兼容后收口**：`Role.normalize` / `JwtConstants.LEGACY_*` 保留旧值读取分支，新代码只允许引用常量 |
| 验收 | OWNER 能登录后台；USER 只能访问前台；旧 token（`admin:` + `ROLE_admin`）仍可正常使用且不 500 |
| 测试 | 已实测：owner token → `/admin/blogs` 200、`/me` 200；user token → `/admin/blogs` 403、`/me` 200；匿名 → 403 请登录；伪造旧 `admin:`/`visitor:` token 均按新角色放行 |
| 后续 | 旧 token 全部过期（3 天）后可删除 `LEGACY_*` 兼容分支，并同步删除 `Role` 里的历史值映射 |

### 其余 V1.0 卡（表格式）

> **进度（2026-11-04）**：T-104 收藏、T-105 点赞、T-107 消息、T-110 反馈 **已完成并回归验证**；
> 新增表 `favorite` `like_record` `message` `feedback`（脚本 `sql/2026-11-04-v1-0-community.sql`）。
> 前端新增 `views/me/Me.vue`、`api/community.js`，文章页加入收藏/点赞按钮。
> T-106 个人中心已完成"收藏 + 消息"两个面板，账户设置与我的评论待补。

| 编号 | 任务 | 核心内容 | 验收标准 |
|---|---|---|---|
| T-104 | 收藏功能 | `favorite` 表 + `POST/DELETE /favorite` + 文章页收藏按钮 + 个人中心列表 | 重复收藏不报错；未登录点击跳登录；列表分页正确 |
| T-105 | 点赞改造 | `like_record` 表；动态点赞从 `moment.likes` 累加改为明细 + 计数；文章点赞同理 | 同一用户重复点赞只算 1；取消后计数 -1；并发点赞不超卖 |
| T-106 | 个人中心 | 前台 `/me` 一套页面：概览、收藏、评论、设置 | 刷新后登录态保持；无权限访问他人数据返回 403 |
| T-107 | 站内消息 | `message` 表 + 列表/已读/未读数；订单与评论事件写消息 | 未读数走缓存；标记已读幂等 |
| T-108 | 专题管理 | `topic` `topic_article` 两张表（V1.0 追加）+ 后台增删改 + 前台专题页 | 专题内文章可排序；删除专题不删文章 |
| T-109 | SEO 字段与路由 | `blog` 的 `slug/seo_*` + 后台编辑项 + 前台 `/blog/:slug` 兼容 `/blog/:id` | 旧链接 200；slug 重复时提示；sitemap.xml 可生成 |
| T-110 | 技术问题反馈 | `feedback` 表 + 前台提交 + 后台处理 | 未登录也能提交；后台可回复 |

**V1.0 完成定义（DoD）**

1. 用户能注册登录，并在个人中心看到自己的收藏、评论、消息
2. 所有用户级接口在未登录时返回 403，且不能通过改参数读他人数据
3. 后台能管理专题、处理反馈
4. 文章支持 slug，旧 id 链接继续可用
5. 新增单测覆盖 T-101 的鉴权矩阵

---

## 四、V1.1：知识付费（8 张卡）

### T-201【已完成】文件服务重建

> 状态：**2026-11-05 已完成并回归验证**
> 实现：`modules/file`（FileInfo/FileInfoMapper/FileService/FileController）；
> 上传经 `/admin/file/upload`，下载走「换 token → 流式返回」，token 为 HMAC-SHA256 签名的 `fileId:userId:expire`；
> 私有文件校验「公开 / 所有者 / 商品权益」，V1.1b 接入权益表后放开第三项。
> 验证：上传→换 token→下载内容一致；篡改 token 400；`.sh` 等可执行扩展名被拒；下载写入 `download_record`。
> 未做：`file_version` 写入接口、MinIO 实现（当前仅 LOCAL）。

| 项 | 内容 |
|---|---|
| 目标 | 统一文件元数据，私有文件必须授权下载 |
| 现状 | 无 `file` 表；`upload.file.path` 仍是 `/Users/naccl/Desktop/upload/`（Mac 路径，本机不可用）；`/image/**` 直读 |
| 改动 | 新建 `file` `file_version` `download_record`；新增 `modules/file`；改 `UploadUtils` 与三个 channel；新增 `/file/token`、`/file/download` |
| 禁止 | 不得删除现有 `LocalChannel` 等类（QQ 头像转存仍在用）；不得把 `is_public=0` 的文件继续映射到 `/image/**` |
| 实现要点 | ① 存储抽象：`storage_type + bucket + object_key`；② token = `base64(HMAC-SHA256(fileId\|userId\|exp))`，5 分钟；③ 下载先校验 token，再校验 `entitlement`，最后写 `download_record` 并流式返回 |
| 验收 | ① 未登录无法下载私有文件；② 无权益用户下载返回 410；③ 有权益用户下载成功且记录入库；④ 直链 `/image/xxx` 无法访问私有文件 |
| 测试 | token 过期、token 篡改、跨用户使用他人 token、重复下载，各 1 例 |

### T-204【已完成】下单

> 状态：**2026-11-05 已完成并回归验证**
> 实现：`modules/trade` 的 `OrderService`；`orders`、`order_item` 两表；接口 `POST /order`、`GET /order/{orderNo}`、`GET /orders`、`POST /order/{orderNo}/cancel`。
> 金额只按商品当前售价计算，请求体只有 `productNo`；数字商品 `quantity` 固定 1；
> 已持有权益拒绝重复购买；`findPending` 复用未过期待支付订单 + Redis SETNX 10s 防连点；
> 状态流转全用带原状态条件的 UPDATE；超时关单由 `POST /admin/order/closeExpired` 手动触发。

| 项 | 内容 |
|---|---|
| 目标 | 服务端可信地创建订单 |
| 改动 | 新建 `orders` `order_item`；`modules/trade`；`POST /order` |
| 关键 | **金额一律后端计算**，前端只传 `productNo`；写订单项快照（标题、封面、单价） |
| 验收 | ① 前端传任意金额被忽略；② 商品下架不能下单；③ 已有有效权益时拒绝重复购买；④ 30 分钟未支付自动关闭 |
| 测试 | 改价后老订单金额不变；连点两次只生成一单（Redis 去重） |

### T-205【已完成】支付（人工确认 + 渠道抽象）

> 状态：**2026-11-05 已完成并回归验证**
> 实现：`modules/trade` 的 `PaymentService`；`payment` 表；`POST /payment`、`GET /payment/{paymentNo}`、`POST /payment/{paymentNo}/manual-confirm`、`POST /admin/payment/{paymentNo}/confirm`。
> 收款码读 `site_setting`（`type=5`：`wechatQr`/`alipayQr`/`paypalUrl`/`payeeName`），后台可改；
> 转账备注提示订单后 6 位；`onPaid()` 是唯一发权益出口，条件更新影响行数为 1 才继续；
> 实测重复确认返回「此前已确认，未重复发放」，权益与销量均不重复。
> 未做：`PaymentChannel` 接口与官方渠道实现（当前三个渠道均为人工确认，接入时复用 `onPaid` 即可）。

| 项 | 内容 |
|---|---|
| 目标 | 在无商户资质前提下，跑通真实收款 |
| 现状 | 无支付能力；你持有微信收款码、支付宝收款码、PayPal 账户 |
| 改动 | 新建 `payment` 表；`modules/trade` 的 `PaymentService`；`PaymentChannel` 接口 + 三个实现（`MANUAL_WECHAT` / `MANUAL_ALIPAY` / `PAYPAL`）；`POST /payment`、`POST /payment/{no}/manual-confirm`、`POST /admin/payment/{no}/confirm` |
| 关键 | ① 收款码存 `site_setting`，后台可换；② 转账备注要求带订单尾号；③ **后台确认与未来渠道回调走同一个 `onPaid()` 方法**；④ 确认幂等：`UPDATE ... WHERE status='WAIT_CONFIRM'`，影响行数 1 才发权益 |
| 验收 | ① 下单后能看到收款码与应付金额；② 用户提交凭证后订单变 `PENDING_CONFIRM`；③ 后台确认后订单 `PAID` 且权益立即生效；④ **同一点击两次「确认收款」只发放一次权益** |
| 测试 | 重复确认、确认后取消、金额与订单不符的人工确认（应告警） |

### 其余 V1.1 卡

> **进度（2026-11-05）**：T-202 商品模型与后台、T-203 前台商品页 **已完成**；
> 新增 10 张表（`sql/2026-11-05-v1-1a-file-product.sql`），`modules/product` 提供商品/课时/资源/分类接口，
> 前台新增 `/knowledge` 列表与 `/knowledge/:no` 详情。
>
> T-206 权益、T-207 交付下载、T-208 退款 **已于 2026-11-05 随 V1.1b 完成**：
> 新增 6 张表（`sql/2026-11-05-v1-1b-order-payment-entitlement.sql`）与 `modules/entitlement`；
> `/file/token` 已接入权益判断；退款三段式（APPLIED→APPROVED→SUCCESS）在确认打款后才撤销权益。
> 详细设计、接口清单与实测记录见 `docs/V1.1B-TRADE.md`。
>
> 已知缺口：coolpilot-admin 尚无商品管理页（V1.1a 遗留），商品上下架与资源绑定目前只能用接口操作。

| 编号 | 任务 | 核心内容 | 验收标准 |
|---|---|---|---|
| T-202 | 商品模型与后台 | `product` 系列 7 张表；`modules/product`；后台商品 CRUD、上下架、限时价 | 4 类商品都能创建；下架后前台不可见、详情页返回 404 |
| T-203 | 前台商品页 | 列表、详情、目录、试看标记、已购标识 | 已购用户看到"已拥有"而非"购买" |
| T-206 | 权益管理 | `entitlement` `entitlement_log`；发放/撤销/校验 Facade | 退款撤销按 `order_no` 精确撤销，不影响用户其他订单的权益 |
| T-207 | 数字资源交付 | 商品 ↔ 文件绑定；授权下载；代码资源版本更新 | 更新版本后已购用户可下新版，历史下载记录保留 |
| T-208 | 退款 | `refund` 表；用户申请 + 后台审核 + 权益撤销 | 下载次数超限的商品拒绝自动退款；拒绝退款后订单回到 `PAID` |

**V1.1 完成定义（DoD）**

1. 一笔真实收款走完全流程：下单 → 付款 → 后台确认 → 权益生效 → 可下载/可学习
2. 重复确认不重复发权益（幂等回归）
3. 退款后立即失去访问权
4. 未购买用户无法通过任何 URL 直连付费内容
5. 后台能看到销售额、订单量、退款率

---

## 五、V1.2：技术服务与需求（5 张卡）✅ 已完成（2026-11-05）

> **实施记录**：T-301 ~ T-305 一次性落地，迁移脚本 `V6__service_demand.sql`。
> 与计划的差异：**没有建 `service_order`**——固定价服务的支付复用 `orders` + `payment` + `refund`
> （再建一张订单表只会让对账分叉），`project.order_no` 用于关联已支付订单；
> 另加 `service_event` 记录状态流水，满足 DoD 第 3 条（原计划的 `operation_log` 记不了用户侧动作）。
> 代码位置：`modules/service`（entity / mapper / service / controller），后台页 `views/service/*`，
> 前台页 `/service`、`/demand/new`、`/demand/[no]`、`/project/[no]`。

### T-301 服务商品

| 项 | 内容 |
|---|---|
| 目标 | 把技术能力包装成可售服务，支持三种定价方式 |
| 改动 | `service_item` 表；`product` 的 `product_type=SERVICE`；`price_mode` 三值；`/services`、`/service/{no}`、后台 `/admin/product`（复用） |
| 关键 | `FIXED` 可直接下单付款；`STARTING` 下单后进入评估；`CUSTOM` **只能提交需求**，不允许直接付款 |
| 验收 | 三种定价方式在前台的按钮文案与行为不同；`CUSTOM` 服务无法通过接口直接创建订单 |
| 测试 | 构造 `CUSTOM` 商品直接调 `POST /order` 应被拒绝 |

| 编号 | 任务 | 核心内容 | 验收标准 |
|---|---|---|---|
| T-302 | 需求提交与审核 | `demand` 表；前台提交（含附件）；后台审核/拒绝 | 联系方式仅 OWNER 可见；用户只能看自己的需求 |
| T-303 | 报价 | `quote` 表，版本化；发送、接受、拒绝、过期 | 同一需求多版本报价，历史版本保留；接受后生成服务订单 |
| T-304 | 项目与里程碑 | `project` `milestone`；从需求转项目 | 一个需求可拆多期；里程碑可排序、可设金额 |
| T-305 | 交付与验收 | `delivery` 表；交付文件 + 说明；用户验收/打回 | 打回必须填原因；验收后里程碑状态变 `ACCEPTED` |

**V1.2 完成定义（DoD）**

1. 用户提交需求 → 后台审核 → 报价 → 确认 → 生成项目与里程碑
2. 交付文件走 T-201 的授权下载
3. 每个状态变更都有记录（谁、何时、从什么到什么）
4. 用户全程只能看到自己的需求与项目

---

## 六、依赖关系与建议顺序

```
T-101 身份链路 ─┬─→ T-102 数据迁移 ─┬─→ T-104 收藏
                │                   ├─→ T-105 点赞
                │                   ├─→ T-106 个人中心
                │                   └─→ T-107 消息
                └─→ T-103 角色迁移 ─┘

V1.0 完成 ──→ T-201 文件服务 ──→ T-202 商品 ──→ T-203 前台商品页
                                        │
                                        └─→ T-204 下单 ──→ T-205 支付 ──→ T-206 权益
                                                                  │
                                                                  └─→ T-207 下载 ──→ T-208 退款

V1.1a 完成 ──→ T-301 服务商品 ──→ T-302 需求 ──→ T-303 报价 ──→ T-304 项目 ──→ T-305 交付
```

**三条不可颠倒的顺序**

1. **T-101 必须最先** —— 没有身份上下文，所有用户接口都是空中楼阁
2. **T-201 必须早于 T-207** —— 先有授权下载，才谈得上交付
3. **T-205 必须早于 T-206 的撤销逻辑** —— 先能收到钱，再谈退钱

---

## 七、任务卡模板（后续新卡统一使用）

```markdown
## T-XXX 任务名

| 项 | 内容 |
|---|---|
| 目标 | 一句话说明交付什么 |
| 现状 | 改之前是什么样，涉及哪些文件 |
| 改动范围 | 精确到文件/目录 |
| 禁止范围 | 明确不能碰的东西 |
| 实现要点 | 关键设计决策与坑 |
| 验收标准 | 可逐条勾选的清单 |
| 测试要求 | 必须覆盖的异常场景 |
```

---

## 八、排期建议（按依赖，不按时间估算）

| 批次 | 任务 | 可交付标志 |
|---|---|---|
| 批次 1 | T-101、T-102、T-103 | 登录态可用、表结构就绪 |
| 批次 2 | T-104、T-105、T-107、T-110 | 社区互动闭环 |
| 批次 3 | T-106、T-108、T-109 | 个人中心与内容组织成型 |
| 批次 4 | AUDIT-06、AUDIT-07 | 部署与测试基线补齐 |
| 批次 5 | T-201、T-202、T-203 | 能展示商品 |
| 批次 6 | T-204、T-205、T-206 | **能收到第一笔钱** |
| 批次 7 | T-207、T-208 | 交付与售后退款闭环 |
| 批次 8 | T-301 ~ T-305 | 技术服务全流程 |
| 批次 9 | AI（V1.3） | 知识库问答 |

> 批次 6 是整个项目最重要的里程碑：钱进来了，才验证得了商业假设；在那之前所有工作都是投入。

---

## 九、V1.3 进展（知识库检索）

### 第一阶段：索引 + 检索 + 引用 ✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 目标 | 让站内内容可被「按意思」检索到，并给出可点击的来源 |
| 决策 | 向量存 MySQL + 进程内余弦检索，不引入独立向量库；见 `docs/ADR-0001-VECTOR-STORE.md` |
| 表 | `kb_knowledge_base` / `kb_document` / `kb_chunk`（V7） |
| 索引 | 已发布文章（跳过密码文章）、上架商品文案、课时正文（付费，带 product_id） |
| 接口 | `GET /ai/search`（公开）、`POST /admin/ai/reindex`、`GET /admin/ai/stats` |
| 前端 | 前台搜索页「智能检索」区；后台 `智能检索 → 知识库索引`（统计/重建/自测） |
| 验收 | 匿名搜不到付费课时；持有权益可搜到；召回延迟 < 20ms；越权召回 0 条 |
| 未做 | 对话式问答（等 LLM Key）、引用高亮、增量索引、多知识库 |

### 第二阶段（待定）：问答

- 需要先定 LLM 提供方（DeepSeek / 通义 / 本地模型）
- 流程：检索 TopN → 拼上下文 → 生成答案 → 标注引用来源
- 表：`ai_conversation` / `ai_message`（已在 DATA-MODEL 规划，未落库）

---

## 十、专题（合集）✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 目标 | 给系列文章一个组织单位，形成「读一篇 → 顺着读完整系列」的路径，同时作为 SEO/分享入口 |
| 表 | `topic` / `topic_article`（V8） |
| 后端 | `modules/topic`：公开列表/详情/文章所属专题；后台增删改、发布开关、整组绑定文章 |
| 后台 | `内容管理 → 专题管理`：筛选、发布开关、抽屉内搜文章并调序 |
| 前台 | `/topic`、`/topic/{slug}`；文章页「本篇属于专题」；导航加入口；专题进 sitemap |
| 验收 | 未发布文章不出现在公开专题页且计数一致；未发布专题前台 404 语义；顺序调整即时生效 |
| 未做 | 专题封面可视化编辑（当前填 URL）、专题级权限（付费专栏建议直接挂商品而不是给专题加价） |

---

## 十一、业务通知（站内消息 + 邮件）✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 发现的问题 | `message` 表、`/messages` 接口、前台消息标签都在，但**没有任何代码写消息**，页面永远是空的 |
| 目标 | 关键业务节点主动告知：钱的状态（支付/退款）和活的状态（需求/报价/验收） |
| 实现 | `modules/notify/NotifyService`：`toUser` / `toOwner`，站内消息 + 邮件一并处理 |
| 接入点 | 支付凭证提交、确认收款、退款申请/同意/拒绝/打款、需求提交/审核、报价发送/接受/拒绝、里程碑交付/验收/打回/项目完成（12 处） |
| 关键取舍 | 邮件在**事务提交后**发；邮件失败只 WARN 不抛；无 SMTP 凭据时只写站内消息 |
| 前台 | `/me?tab=` 直达对应标签；消息列表带类型标签与「查看 ›」 |
| 验收 | 交易与服务两条链路实测：所有者 5 条、客户 6 条消息，链接与类型正确；伪造授权码时邮件报 Authentication failed 但接口仍 200 |
| 配置 | `MAIL_PASSWORD`（发信）、`BLOG_VIEW` / `BLOG_CMS`（邮件链接前缀）；说明见 `docs/DEPLOY.md` 第十节 |
| 未做 | 通知偏好开关（用户自选邮件/仅站内）、摘要式合并发送、短信/微信模板消息 |

---

## 十二、智能问答（V1.3 第二阶段）✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 目标 | 在检索之上生成回答，并给出可核对的来源；本地与云端模型都能接 |
| 表 | `ai_conversation` / `ai_message`（V9），引用以 JSON 快照存 |
| 模型 | 只依赖 OpenAI 兼容 `/chat/completions`：Ollama / vLLM / DeepSeek / 通义 一套配置切换 |
| 后端 | `LlmClient` 接口 + `OpenAiCompatibleLlmClient`；`RagChatService` 负责检索、拼上下文、降级、落库 |
| 接口 | `POST /ai/chat`（公开，60s/12 次限流）、`GET /ai/conversations`、`GET/DELETE /ai/conversation/{id}`；`GET /admin/ai/llm`、`POST /admin/ai/llm/test` |
| 前台 | `/ask` 问答页（历史会话、引用卡片、示例问题）；搜索页加「让 AI 直接回答」入口；导航加「问答」 |
| 后台 | 知识库索引页顶部新增「问答模型」卡片：配置状态、模型名、最低相似度、连通性自测 |
| 降级 | 未配置 / 调用失败 → `degraded=true` + 原因 + 检索片段，接口不报错 |
| 验证 | 用本地假模型跑通：请求体正确、答案与引用返回、会话列表/详情/删除、越权访问被拒；未配置时降级返回 6 条片段 |
| 流式 | `POST /ai/chat/stream`（SSE）：`meta` → `delta`* → `done`；前端 fetch 解析，失败自动退回一次性接口 |
| 中止 | 前端 AbortController + 后端取消标记：断连即停止读上游，半截答案仍落库；`AsyncRequestNotUsableException` 单独处理，日志不再刷堆栈 |
| 引用联动 | 答案内 `[n]` 点击滚动到来源卡片并高亮（不跳走）；只对有效编号生成标记，输出先转义再替换 |
| 收藏 | 复用 `favorite`，`AI_ANSWER` + 消息 id，收藏前校验归属；「我的收藏」可跳回对应会话 |
| 分享 | 每条回答可生成 `/share/{token}` 公开页（问题+答案+来源，无用户信息），可撤销；另支持复制成纯文本 |
| 未做 | 真正的指代消解（现为「本轮+上一轮」补救）、分享页的评论/点赞 |

---

## 十三、上线前运营补丁（V1.3 之后）✅ 已完成（2026-11-05）

上线的功能都齐了，缺的是「出问题能看见、能处理」的三件事。

| 项 | 内容 |
|---|---|
| 超时关单 | 之前 `closeExpired()` 只有后台一个按钮，没人点就永远不关；现登记为 `schedule_job`（`orderScheduleTask.closeExpiredOrders`，每 10 分钟），只关「过了支付截止时间且未付款」的订单 |
| 用户管理 | `GET /admin/users`（关键词 / 状态筛选，带订单数与累计支付）、`PUT /admin/user/{id}/status`；禁用即递增 token 版本，已登录设备立刻失效；所有者账号不可禁用（SQL 带 `role` 条件） |
| 登录信息 | `user.status` / `user.last_login_time`（V13）；被禁用账号密码正确也拒绝登录；登录成功才刷新最后登录时间 |
| 问答记录 | `GET /admin/ai/conversations`（按标题搜索，带用户名）、`GET /admin/ai/conversation/{id}`（问答成对 + 引用条数）；只读，不提供删除 —— 记录是排查依据 |
| 后台页面 | 新增「用户管理 › 用户列表」「智能检索 › 问答记录」两页；引入问答记录抽屉，引用为 0 的回答用黄色标签提示 |
| 验收 | 定时任务立即执行把手动改过期的订单关掉并写 `schedule_job_log`；禁用后前台登录被拒、旧 token 立即失效；启用后恢复；禁用所有者返回 400；问答记录列表/详情/不存在三态正常 |

---

## 十四、检索升级：真实向量 + 混合检索 ✅ 已完成（2026-11-05）

| 项 | 内容 |
|---|---|
| 目标 | 补上 V1.3 的短板：哈希向量只会字面召回，换个说法就搜不到 |
| 向量 | 新增 `OpenAiCompatibleEmbeddingClient`（任意 OpenAI 兼容 `/embeddings`），与哈希实现按 `ai.embedding.provider` 二选一；本地实测 bge-m3（1024 维） |
| 索引 | 重建改为批量向量化（`ai.embedding.batch-size`，默认 16），11 块耗时 1.3s；切换模型后旧块按 `dim`/`model` 跳过，stats 显示 `ignoredChunks` |
| 检索 | 混合打分：`alpha×余弦 + (1-alpha)×字面覆盖率 + 标题加成`；查询词按「二字优先、单字兜底」切分，正文与标题覆盖率取大者作为字面证据 |
| 阈值 | `semantic-floor=0.5`（无字面证据时向量必须更相似）、`min-score=0.25`；语义模型余弦普遍偏高，沿用 0.05 会把无关问题检出 8 条 |
| 降级 | embedding 调用失败自动退化为纯字面检索，不让搜索整页报错 |
| 决策 | `docs/ADR-0002-HYBRID-RETRIEVAL.md` |
| 验证 | 骨架/组件/RAG 三类查询首选正确；「今天天气怎么样」仅 1 条低分；站内没有的「退款」返回 0 条；问答用 qwen2.5:7b 返回带引用回答，`degraded=false` |
| 未做 | 查询改写/指代消解、rerank 模型、增量索引 |
