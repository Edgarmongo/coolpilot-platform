# ED-04：前后端 API 设计

> 版本：v1.0 ｜ 日期：2026-11-04
> 原则：**新接口沿用现有约定，不为"规范性"付出前端重写成本**

---

## 一、现状约定（实测，不可随意变更）

| 项 | 现状 | 位置 |
|---|---|---|
| 路径前缀 | 后端无前缀，直接 `/blogs`、`/site`；前端 `/api` 由 nginx（生产）或 `devServer.proxy`（开发）剥掉 | `vue.config.js` / `plugins/axios.js` |
| 后台前缀 | `/admin/**`，CMS 的 `baseURL` 是 `/api/admin/` | `util/request.js` |
| 响应体 | `{code, msg, data}`，`code=200` 表示成功 | `model/vo/Result.java` |
| 分页入参 | `pageNum`（1 起）、`pageSize` | 各 Controller |
| 分页出参 | `{totalPage, list}` | `model/vo/PageResult.java` |
| 错误码 | 业务错误也返回 HTTP 200，错误码在 body `code`（400/401/403/404/500） | `ControllerExceptionHandler` |

### 1.1 三条"不要改"

| 不建议 | 原因 |
|---|---|
| 改成 `/api/v1/**` | 现网 81 个接口全是无版本路径，新旧分属两套规则，调试时还要记哪些带 v1 |
| 改成 `{code:0,message,data,timestamp,traceId}` | 两个前端的 axios 响应拦截器都判断 `res.code !== 200`，改信封等于全量重写 19 个 api 模块 |
| 分页改成 `{records,total,page,size}` | `PageResult` 被前台分页组件、后台 20+ 表格直接消费 |

### 1.2 唯一建议的增量改动

给 `PageResult` **增加一个可选字段 `total`**：

```java
public class PageResult<T> {
    private Integer totalPage;
    private Integer total;   // 新增：精确总数，后台列表需要
    private List<T> list;
}
```

JSON 多一个字段对 Vue 完全透明（旧代码不读它就不受影响），但后台商品/订单列表需要精确总数分页，靠 `totalPage * pageSize` 估算不准。

---

## 二、路径规范（新接口）

```
前台：  /{资源}           例：/product、/order、/demand
后台：  /admin/{资源}     例：/admin/product、/admin/order
```

> **后台接口必须挂在 `/admin/**` 下**。`SecurityConfig` 只保护这个前缀，挂到别处就等于裸奔；若想挂别处就必须改 Security，风险更大。

命名风格与现有一致：**单数表示单个资源，复数表示列表**（`/admin/blog` 单条，`/admin/blogs` 列表）。

---

## 三、前台接口

### 3.1 知识商品

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/products` | 公开 | 列表，参数 `type` `categoryId` `pageNum` `pageSize` |
| GET | `/product/{productNo}` | 公开 | 详情（含当前价格、是否已购） |
| GET | `/product/{productNo}/catalog` | 公开 | 课程目录 / 专栏目录，标记 `freePreview` |
| GET | `/lesson/{lessonId}` | 需权益或试看 | 课时内容；试看不校验权益 |

响应示例：

```json
{
  "code": 200, "msg": "OK",
  "data": {
    "productNo": "P20261104001",
    "title": "企业级 RAG 应用实战",
    "productType": "COURSE",
    "price": 199.00,
    "originalPrice": 299.00,
    "owned": false,
    "cover": "https://.../cover.jpg",
    "lessonCount": 24
  }
}
```

### 3.2 订单

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/order` | 登录 | 下单，body `{productNo, quantity}`；**金额由后端算** |
| GET | `/order/{orderNo}` | 登录 + 归属 | 订单详情 |
| GET | `/orders` | 登录 | 我的订单，`pageNum` `pageSize` `status` |
| POST | `/order/{orderNo}/cancel` | 登录 + 归属 | 仅 `PENDING_PAY` 可取消 |

### 3.3 支付

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/payment` | 登录 + 归属 | 创建支付，body `{orderNo, channel}`，返回支付参数/收款信息 |
| GET | `/payment/{paymentNo}` | 登录 + 归属 | 查询支付状态（前端轮询用） |
| POST | `/payment/{paymentNo}/manual-confirm` | 登录 + 归属 | 人工转账模式下，用户提交付款凭证，订单转 `PENDING_CONFIRM` |
| POST | `/payment/callback/{channel}` | 公开（验签） | 渠道异步通知；`MANUAL` 渠道不开放 |

**人工收款模式**（见第五节）响应示例：

```json
{
  "code": 200, "msg": "OK",
  "data": {
    "paymentNo": "PAY2026110400012",
    "channel": "MANUAL_WECHAT",
    "amount": 199.00,
    "qrCodeUrl": "https://.../wechat-qr.png",
    "expireTime": "2026-11-04T23:35:00",
    "tip": "转账后请填写付款人姓名与转账时间，我们会在 24 小时内确认"
  }
}
```

### 3.4 退款

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/refund` | 登录 + 归属 | 申请退款，body `{orderNo, reason}` |
| GET | `/refund/{refundNo}` | 登录 + 归属 | 退款进度 |

### 3.5 权益

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/entitlements` | 登录 | 我的权益（我的课程 / 已购文档） |
| GET | `/resource/access?type=COURSE&id=1001` | 登录 | 是否可访问，返回 `{accessible, reason}` |

> `reason` 取值：`NOT_PURCHASED` / `EXPIRED` / `REVOKED` —— 前端据此显示"购买""续期""联系客服"，比只返回 403 体验好。

### 3.6 技术服务与需求

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/services` | 公开 | 服务商品列表，参数 `category` |
| GET | `/service/{productNo}` | 公开 | 服务详情（范围、流程、定价方式） |
| POST | `/demand` | 登录 | 提交需求 |
| GET | `/demands` | 登录 | 我的需求 |
| GET | `/demand/{demandNo}` | 登录 + 归属 | 需求详情与进度 |
| POST | `/demand/{demandNo}/cancel` | 登录 + 归属 | 仅未进入实施前可取消 |
| GET | `/quote/{quoteNo}` | 登录 + 归属 | 查看报价 |
| POST | `/quote/{quoteNo}/accept` | 登录 + 归属 | 接受报价 → 生成服务订单 |
| POST | `/quote/{quoteNo}/reject` | 登录 + 归属 | 拒绝报价 |
| GET | `/service-orders` | 登录 | 我的服务订单 |
| POST | `/delivery/{deliveryId}/accept` | 登录 + 归属 | 验收交付物 |
| POST | `/delivery/{deliveryId}/reject` | 登录 + 归属 | 打回，需填原因 |

### 3.7 文件

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/file/token?fileId=&bizType=` | 登录 | 换取一次性下载 token（5 分钟） |
| GET | `/file/download?token=` | 公开（token 校验） | 流式返回文件 |

> 不提供 `/file/{id}` 这种直链。`token` 内含 `fileId + userId + expireAt + HMAC`，服务端解码后校验权益再回源。

### 3.8 社区

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/favorite` | 登录 | 收藏，body `{targetType, targetId}`；重复收藏幂等 |
| DELETE | `/favorite` | 登录 | 取消收藏，参数同上 |
| GET | `/favorites` | 登录 | 我的收藏 |
| POST | `/like` | 登录 | 点赞，唯一键去重 |
| DELETE | `/like` | 登录 | 取消点赞 |
| POST | `/feedback` | 可选登录 | 技术问题反馈 |
| GET | `/messages` | 登录 | 我的消息 |
| PUT | `/message/{id}/read` | 登录 + 归属 | 标记已读 |
| GET | `/messages/unread-count` | 登录 | 未读数（顶栏红点，高频，走缓存） |

---

## 四、后台接口

全部 `/admin/**`，`hasRole("owner")`，写操作加 `@OperationLogger`。

### 4.1 商品

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/products` | 列表，`type` `status` `keyword` `pageNum` |
| GET | `/admin/product/{id}` | 详情 |
| POST | `/admin/product` | 创建（含类型扩展） |
| PUT | `/admin/product/{id}` | 编辑 |
| PUT | `/admin/product/{id}/status` | 上下架，body `{status}` |
| POST | `/admin/product/{id}/price` | 新增限时价格 |
| GET | `/admin/productCategories` | 商品分类列表 |
| POST/PUT/DELETE | `/admin/productCategory` | 分类维护 |
| POST | `/admin/course` | 创建课程扩展 |
| POST/PUT/DELETE | `/admin/lesson` | 课时维护，`POST /admin/lesson/sort` 批量排序 |
| POST | `/admin/product/{id}/resource` | 绑定可下载资源 |
| POST | `/admin/product/{id}/article` | 专栏绑定文章 |

### 4.2 交易

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/orders` | 订单列表，`status` `keyword` `startTime` `endTime` |
| GET | `/admin/order/{orderNo}` | 订单详情（含支付、退款、权益） |
| GET | `/admin/payments` | 支付流水 |
| POST | `/admin/payment/{paymentNo}/confirm` | **人工确认收款**（人工模式核心） |
| POST | `/admin/payment/{paymentNo}/close` | 关闭支付单 |
| GET | `/admin/refunds` | 退款列表 |
| POST | `/admin/refund/{refundNo}/approve` | 同意退款 |
| POST | `/admin/refund/{refundNo}/reject` | 拒绝，需填原因 |

### 4.3 权益

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/entitlements` | 权益列表，`userId` `productId` `status` |
| POST | `/admin/entitlement/grant` | 人工发放，body `{userId, productId, entType, expireTime, reason}` |
| POST | `/admin/entitlement/revoke` | 撤销，必须填原因，写流水 |
| GET | `/admin/entitlement/logs` | 权益变更流水 |

### 4.4 技术服务与需求

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/demands` | 需求列表，`status` `projectType` |
| GET | `/admin/demand/{demandNo}` | 需求详情（含联系方式，仅 OWNER 可见） |
| PUT | `/admin/demand/{demandNo}/status` | 状态流转（审核/拒绝/评估/取消） |
| POST | `/admin/demand/{demandNo}/quote` | 创建报价单（自动递增 version） |
| POST | `/admin/quote/{quoteNo}/send` | 报价发给客户 |
| GET | `/admin/serviceOrders` | 服务订单列表 |
| PUT | `/admin/serviceOrder/{orderNo}/status` | 服务订单状态流转 |
| POST | `/admin/project` | 创建项目（从需求转） |
| POST | `/admin/project/{id}/milestone` | 新增里程碑 |
| POST | `/admin/milestone/{id}/deliver` | 交付里程碑（上传文件 + 说明） |
| GET | `/admin/deliveries` | 交付记录 |

### 4.5 专题（合集）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/topics` | 公开：已发布专题列表，带 `articleCount`（只算已发布文章） |
| GET | `/topic/{slug}` | 公开：专题详情 + 有序文章列表 |
| GET | `/blog/{blogId}/topics` | 公开：文章所属的已发布专题（文章页「本篇属于」） |
| GET | `/admin/topics` | 后台列表，`published` `keyword` 分页 |
| GET | `/admin/topic/{id}` | 后台详情，含 `articleIds` 与全部文章（含草稿） |
| POST | `/admin/topic` | 新建，可同时传 `articleIds` |
| PUT | `/admin/topic/{id}` | 编辑；`articleIds` 传 `null` 表示不改绑定，传 `[]` 表示清空 |
| PUT | `/admin/topic/{id}/status?published=` | 发布 / 下线 |
| PUT | `/admin/topic/{id}/articles` | 整组替换绑定，数组顺序即阅读顺序 |
| DELETE | `/admin/topic/{id}` | 删除专题（文章不受影响） |

### 4.6 导航菜单（V15）

顶部导航原本写死在 `coolpilot-web/layouts/default.vue`，V15 起改为可配置。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/nav` | 公开：前台菜单，只返回 `visible = 1`，按 `sort` 升序；走 Redis `navMenuList` 缓存 |
| GET | `/admin/navs` | 后台全量列表（含隐藏项） |
| POST | `/admin/nav` | 新增；`path` 以 `/` 开头为站内，`http(s)://` 开头为外链 |
| PUT | `/admin/nav/{id}` | 编辑文字 / 链接 / 排序 / 显隐 / 打开方式 |
| PUT | `/admin/nav/{id}/move?direction=up|down` | 与相邻项交换位置（先把 sort 规整为 10/20/30） |
| PUT | `/admin/nav/{id}/visible?visible=` | 单独切换显隐 |
| DELETE | `/admin/nav/{id}` | 删除 |

任何写操作都会清 `navMenuList` 缓存。前台在接口失败时回落到内置默认菜单（`FALLBACK_NAV`），
返回空数组则不回落——那是站长主动把菜单全隐藏了。

### 4.7 文件与统计

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/file/upload` | 上传，返回 `{fileId, url, size, sha256}` |
| POST | `/admin/file/{id}/version` | 新增版本（代码资源更新） |
| GET | `/admin/files` | 文件列表 |
| GET | `/admin/stats/sales` | 销售额/订单量/退款率，按日聚合 |
| GET | `/admin/stats/conversion` | 商品浏览→下单→支付漏斗 |

---

## 五、支付渠道设计（结合你的实际条件）

你目前有 **PayPal 账户 + 微信收款码 + 支付宝收款码**。这三者都**不是**商户 API 接入，因此设计上必须把"渠道"和"收款方式"分开。

### 5.1 渠道枚举

| channel | 类型 | 说明 |
|---|---|---|
| `MANUAL_WECHAT` | 人工 | 展示微信收款码，用户转账后提交凭证，你后台确认 |
| `MANUAL_ALIPAY` | 人工 | 同上 |
| `PAYPAL` | 人工/半自动 | 展示 PayPal.me 链接或邮箱；可后续接 IPN/Webhook 自动确认 |
| `WECHAT_PAY` | 自动 | 预留，需商户号 |
| `ALIPAY` | 自动 | 预留，需商户资质 |

> 说明：个人主体通常难以直接开通微信支付商户号、支付宝当面付，一般需要个体工商户或企业资质，具体以各渠道当前政策为准。**不要为了"自动到账"卡在资质上，先用人工确认把交易跑通。**

### 5.2 人工模式流程

```
用户点购买 → 后端创建订单(PENDING_PAY) + 支付单(INIT, channel=MANUAL_WECHAT)
          → 返回收款码 + 金额 + 订单号后 6 位作为转账备注
          → 用户转账，页面填「付款人姓名 + 转账时间 + 截图」
          → 支付单转 WAIT_CONFIRM，订单转 PENDING_CONFIRM
          → OWNER 后台看到待确认列表，核对到账后点「确认收款」
          → 触发与自动回调完全相同的发权益逻辑
```

### 5.3 关键设计

| 要点 | 做法 |
|---|---|
| 收款码不写死在代码 | 存 `site_setting`，后台可换 |
| 人工确认也要幂等 | 复用 `payment` 的 `UPDATE ... WHERE status='WAIT_CONFIRM'`，影响行数为 0 则已处理 |
| 转账备注必须带订单尾号 | 否则无法对账；下单时明确提示 |
| 确认入口只有一个 | 后台「确认收款」和未来「渠道回调」都调用同一个 `PaymentService.onPaid(...)`，保证逻辑不分叉 |
| PayPal 先人工 | 展示 `paypal.me/xxx` 链接；后续可加 `POST /payment/callback/paypal` 走 IPN 验签 |

### 5.4 未来接官方渠道的改造成本

只需新增一个 `PaymentChannel` 实现类：

```java
public interface PaymentChannel {
    String code();                                  // WECHAT_PAY
    PrepayResult prepay(Payment payment);           // 下单，返回唤起参数
    boolean verify(NotifyRequest req);              // 验签
    String extractTradeNo(NotifyRequest req);       // 渠道交易号
    BigDecimal extractAmount(NotifyRequest req);
}
```

`PaymentService` 不关心具体渠道。人工渠道的 `verify` 恒返回 `true`（由后台操作触发），`prepay` 返回收款码。

---

## 六、错误码与语义

沿用现有约定：HTTP 恒 200，业务码在 body。

| code | 含义 | 典型场景 |
|---|---|---|
| 200 | 成功 | — |
| 400 | 参数/业务校验失败 | 商品已下架、状态不允许、报价过期 |
| 401 | 未认证 | token 缺失/过期 |
| 403 | 无权限 | 访问他人订单、访客访问 `/admin/**` |
| 404 | 资源不存在 | 商品/订单不存在 |
| 500 | 服务端异常 | 未捕获异常 |

**新增两个业务码**（不改结构，只加取值）：

| code | 含义 | 前端动作 |
|---|---|---|
| 410 | 无权益 | 展示购买按钮，而非报错弹窗 |
| 429 | 超出访问频率 | 提示稍后再试 |

---

## 七、幂等与并发约定

| 场景 | 幂等键 | 实现 |
|---|---|---|
| 支付回调 | `payment(channel, trade_no)` 唯一索引 | 先条件更新，影响行数为 1 才发放权益 |
| 人工确认 | `payment_no` + 状态条件更新 | 同上 |
| 下单 | Redis `SETNX order:{userId}:{productNo}` 30s | 防连点重复下单 |
| 取消订单 | 状态机 + 条件更新 | 只有 `PENDING_PAY` 能取消 |
| 申请退款 | 同一订单同时只允许一条 `APPLIED/APPROVED` | 查询校验 + 唯一约束（`biz_no, status` 部分唯一） |
| 收藏/点赞 | `uk_user_target` 唯一索引 | `INSERT IGNORE` 或捕获重复键 |

**超时关单与支付回调并发**：用条件更新防覆盖。

```sql
-- 关单任务
UPDATE orders SET status='CLOSED'
 WHERE order_no=? AND status='PENDING_PAY';
-- 若此时回调已把状态改为 PAID，影响行数为 0，关单自动放弃
```

---

## 八、与现有 81 个接口的关系

| 类型 | 处理 |
|---|---|
| 内容域 16 个公开接口 | **完全不动**，前台继续用 |
| 后台 63 个接口 | **完全不动**，CMS 继续用 |
| 新增接口 | 按本文档路径，全部落在新模块 |
| 唯一需要改的老接口 | `GET /site`：追加返回"精选商品"块（新增字段，向后兼容） |

> `/site` 是前台首屏唯一的数据入口，首页要展示"精选服务/课程"就靠它，不必新增请求。加字段即可，老前端忽略新字段不受影响。

---

## 九、接口交付清单（供任务卡引用）

| 批次 | 接口数 | 说明 |
|---|---|---|
| V1.0 | 18 | 收藏 2、点赞 2、消息 4、反馈 1、我的中心 6、专题 3 |
| V1.1a | 22 | 商品 10、课程课时 5、文件 4、资源 3 |
| V1.1b | 20 | 订单 5、支付 5、退款 3、权益 7 |
| V1.2 | 24 | 服务商品 4、需求 5、报价 4、服务订单 4、项目里程碑 5、交付 2 |

> 合计约 84 个新接口，与现有 81 个规模相当。这也是为什么必须按模块分批交付，而不是一次性铺开。
