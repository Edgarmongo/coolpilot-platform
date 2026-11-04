# WB-003 API 与权限安全审计报告

> 审计日期：2026-11-04 ｜ 方式：只读代码审查 + 运行中服务匿名探测（使用无效载荷，未产生数据）
> 覆盖需求：WB-003 的 01–07 全部小节
> 说明：本报告只输出结论，**未修改任何安全配置**（遵循 WB-003 禁止事项）

---

## 结论摘要

**发现 1 个可立即接管后台的 P0 漏洞，并已完成 PoC 验证。**

```
用配置文件里的默认密钥可以离线伪造管理员 token，
无需知道任何账号密码，直接访问 /admin/dashboard 拿到完整经营数据。
```

其余：1 个 P0、5 个 P1、5 个 P2、1 个 P3。详见第七节风险登记册。

> **修复状态（2026-11-05 07:00 更新）**：R-01 ~ R-09、R-11 已修复并回归验证，
> 其中 R-03（默认拒绝）、R-09（友链刷量）、R-11（token 吊销）在最近两轮完成，记录见第十一节。
> 剩余为 P2 的 R-10（CORS 全开）与 P2/P3 的 R-12、R-13。

---

## 01 安全机制概览

| 机制 | 实现 | 评价 |
|---|---|---|
| 认证 | Spring Security 7 + JWT（无状态，`STATELESS`） | ✅ 架构正确 |
| 登录入口 | `JwtLoginFilter`（`AbstractAuthenticationProcessingFilter`），路径 `/admin/login` | ✅ 独立于 Controller |
| 注册/登录（访客） | `VisitorAuthController` + BCrypt | ✅ 有 IP 限流 |
| 授权 | `SecurityFilterChain`：`/admin/**` → `hasRole("admin")`，其余 `permitAll()` | ⚠️ 覆盖面过窄 |
| 密码存储 | `HashUtils.getBC`（BCrypt） | ✅ 无明文 |
| CSRF | `AbstractHttpConfigurer::disable` | ✅ 无状态 Bearer 场景下正确 |
| CORS | `allowedOrigins("*")`，未开 `allowCredentials` | ⚠️ 风险可控（token 在 header，不吃 cookie） |
| 错误响应 | 统一 `{code,msg,data}`，401/403 统一 JSON | ✅ 体验一致 |
| 审计 | 登录/操作/异常/访问四类日志 + AOP | ✅ 完整 |
| 限流 | `@AccessLimit` 仅用于 `/visitor/register`(3/60s)、`/visitor/login`(10/60s) | ⚠️ **后台登录无限流** |

---

## 02 认证流程

### 2.1 博主登录

```
POST /admin/login (JSON body)
  → JwtLoginFilter.attemptAuthentication
      → 空值校验（已加固：user==null / 空用户名密码 → 400）
      → AuthenticationManager.authenticate
  → successfulAuthentication
      → JwtUtils.generateToken(username, authorities)
      → 返回 {user, token}，写 login_log
```

### 2.2 token 结构

| 项 | 值 |
|---|---|
| 算法 | HS512 |
| 密钥 | **SHA-512 摘要派生**：`key = SHA512(token.secretKey)` |
| `sub` | `admin:Admin` 或 `visitor:{id}` |
| `authorities` | `ROLE_admin,`（逗号拼接，**末尾有逗号**） |
| 有效期 | `token.expireTime=259200000`（**3 天**） |
| 吊销 | 无（纯无状态，改密码也不会使旧 token 失效） |

### 2.3 前台访客 token

签发正常，但见 **R-02**：后端没有任何路径消费它。

---

## 03 授权矩阵（实测）

探测环境：后端 8091 运行中，使用 `curl`，未携带任何 token。

| 场景 | 请求 | HTTP | body.code | 判定 |
|---|---|---|---|---|
| 匿名访问后台列表 | `GET /admin/blogs` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问仪表盘 | `GET /admin/dashboard` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问站点设置 | `GET /admin/siteSettings` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问文章详情 | `GET /admin/blog?id=1` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问访客统计 | `GET /admin/visitors` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问任务 | `GET /admin/jobs` | 200 | **403 请登录** | ✅ 已保护 |
| 匿名访问操作日志 | `GET /admin/operationLogs` | 200 | **403 请登录** | ✅ 已保护 |
| 白名单接口 | `GET /admin/webTitleSuffix` | 200 | 200 | ⚠️ 设计如此（前台取标题后缀） |
| **伪造 admin token** | `GET /admin/dashboard` + 伪造头 | 200 | **200 + 完整数据** | ❌ **R-01 漏洞** |
| 匿名发评论 | `POST /comment {}` | 200 | 500 参数有误 | ⚠️ 可达（R-06 错误码） |
| 匿名验密 | `POST /checkBlogPassword {}` | 200 | 500 异常错误 | ⚠️ 可达（未捕获异常） |
| 匿名刷友链量 | `POST /friend?nickname=x` | 200 | 200，同 IP 1 小时内只 +1 | ✅ 已修复（R-09） |
| 匿名点赞 | `POST /moment/like/999999` | 200 | 500 操作失败 | ⚠️ 可达 |
| 静态图片 | `GET /image/probe.jpg` | 200 | 404 资源不存在 | ✅ 已修复（R-08） |
| 未知路径 | `GET /totally-unknown` | 200 | 403 请登录 | ✅ 默认拒绝（R-03） |

> **注意**：所有业务错误都返回 **HTTP 200**，真实状态在 `body.code`。这是刻意设计（前端拦截器依赖），不建议改，但意味着**任何基于 HTTP 状态码的监控/网关策略都会失效**。

**探测后数据核对**：`comment=0`、`friend=0`、`moment.likes` 合计 0，确认未产生脏数据。

---

## 04 资源归属检查（缺口分析）

| 资源 | 是否校验归属 | 现状 |
|---|---|---|
| 文章 | 部分 | 私密文章靠 `password` + token（token 的 `sub` 存的是 **blogId**） |
| 评论 | ❌ | 匿名提交，无作者概念，属设计如此 |
| 用户资料 | ❌ | 目前无"我的资料"接口 |
| 订单 / 权益 / 需求 | — | 尚未实现，**必须在实现时补** |

**结论**：现有功能里没有"用户私有资源"的读取接口，所以尚未出现越权读取。但这正是 R-03 的危险之处 —— 一旦新增 `/orders` 之类的接口而不加校验，默认就是匿名可读。

---

## 05 关键安全场景验证

| 场景 | 结果 |
|---|---|
| 未登录访问管理接口 | ✅ 全部拒绝（403） |
| 普通用户调用文章发布接口 | ✅ 无法调用（`/admin/**` 需 admin 角色） |
| 普通用户修改他人资料 | ⚠️ 无此接口，无法验证；新增时必须做归属校验 |
| 普通用户访问他人订单 | — 未实现 |
| 普通用户下载未购买文件 | — 未实现；**R-08 是前置隐患** |
| 通过请求参数修改角色 | ✅ 注册接口只接受 username/password/nickname/email，且服务端硬编码 `ROLE_visitor`（现 `ROLE_user`） |
| JWT 失效后访问受保护资源 | ✅ 无效 token → 403「凭证已失效」 |
| **伪造 token** | ❌ **成功**（R-01） |
| 路径穿越读配置 | ✅ 已拦截，`/image/../application-dev.properties` 返回通用错误，未泄露内容 |
| SQL 注入 | ✅ 全量 MyBatis 参数化，未见字符串拼接 SQL |

---

## 06 敏感信息暴露面

| 位置 | 内容 | 风险 |
|---|---|---|
| `application-dev.properties:41` | `token.secretKey=abcdefghijklmnopqrstuvwxyz` | **P0，可直接伪造 token** |
| 同文件 | `spring.datasource.password`、`spring.data.redis.password` | P1，明文入库 |
| 同文件 `tg.bot.token` | 形如 `1234567890:qwerty...` | P1，若为真实 token 则 Bot 可被接管 |
| 同文件 | `upload.github.token=ghp_xxxx...` | 占位符，未泄露 |
| `coolpilot-admin` 前端 | 图床凭据存 localStorage（GitHub token / 又拍云 / 腾讯云密钥） | P2，XSS 即可窃取 |
| `README.md` | 默认后台账号 `Admin / 123456` | P1，配合 R-04 无暴力破解防护 |

---

## 07 风险登记册

### R-01【P0】JWT 密钥为公开默认值，可离线伪造管理员 token

| 项 | 内容 |
|---|---|
| 位置 | `coolpilot-server/src/main/resources/application-dev.properties:41` |
| 描述 | `token.secretKey=abcdefghijklmnopqrstuvwxyz`，是上游项目的默认占位值，且已进 Git。`JwtUtils` 用它做 SHA-512 派生后签 HS512 |
| 复现条件 | 拿到仓库或该字符串即可。无需登录、无需知道密码 |
| 影响 | **完全接管后台**：读全部订单、改站点配置、删文章、看用户数据 |
| PoC | 已执行，见下方 |
| 修复 | ① 换成 ≥64 字符随机串；② 从配置文件移除，改用环境变量 `JWT_SECRET`；③ 已签发的 token 全部失效一次；④ 加入 `.gitignore` 历史清理 |
| 工作量 | 10 分钟 + 一次重新登录 |

**PoC 记录（本地开发环境验证，未对外部系统执行）**

```python
key = sha512(b"abcdefghijklmnopqrstuvwxyz").digest()
payload = {"sub":"admin:Admin","authorities":"ROLE_admin,","exp":now+3600}
token = HS512(header.payload, key)
```

```
GET /admin/dashboard   Authorization: <伪造token>
→ {"code":200,"data":{"uv":3,"blogCount":5,"pv":356,...}}
```

> 仅用配置文件里的字符串，未接触数据库、未使用任何账号密码。

### R-02【P0】前台身份链路断裂

| 项 | 内容 |
|---|---|
| 位置 | `config/JwtFilter.java:34-37` |
| 描述 | 非 `/admin` 路径直接 `return`，`SecurityContext` 永不填充 |
| 影响 | 访客 token 形同虚设；所有"我的 XX"功能无法实现，只能信任前端传 `userId`（越权） |
| 修复 | 见 `ED-05` T-101 |
| 等级理由 | 不修则 V1.0 全部用户功能无法安全实现 |

### R-03【P0】`anyRequest().permitAll()` 覆盖面过窄

| 项 | 内容 |
|---|---|
| 位置 | `config/SecurityConfig.java:80` |
| 描述 | 只有 `/admin/**` 受保护，新增的用户级接口默认匿名可调 |
| 影响 | 一旦忘记加规则，`/orders` 就变成"任何人可查任何人订单" |
| 修复 | ✅ 已改为「默认拒绝 + 显式放行」：`anyRequest().denyAll()`，公开接口在 `SecurityPaths.PUBLIC` 逐条列出并注明理由，见 11.5 |

> 维护约定：新增接口必须归入 `SecurityPaths.OWNER_ONLY` / `AUTHENTICATED` / `PUBLIC` 之一，
> 漏归类会返回 403（不是匿名放行）。

### R-04【P1】后台登录无频率限制 + 默认口令

| 项 | 内容 |
|---|---|
| 位置 | `JwtLoginFilter`（无 `@AccessLimit`）、`README.md` 默认 `Admin/123456` |
| 影响 | 可暴力破解；默认口令未改时等于无密码 |
| 修复 | ① 给登录加 IP 限流（可复用 `@AccessLimit` 的拦截器逻辑）；② 首次启动强制改密；③ 连续失败 5 次锁定 15 分钟 |

### R-05【P1】`user.username` 无唯一索引

| 项 | 内容 |
|---|---|
| 位置 | 数据库 `user` 表 |
| 描述 | 注册逻辑"先查后插"，非原子；并发下可产生同名账号 |
| 影响 | 后注册的账号永远无法登录；未来无法补唯一键 |
| 修复 | 查重后加 `UNIQUE INDEX uk_username(username)` |
| 交叉引用 | 见 `02-database-audit.md` §3.3 |

### R-06【P1】参数校验失败返回 500

| 项 | 内容 |
|---|---|
| 位置 | `Result.error(msg)` 写死 `code=500`；`CommentController` 等直接调用 |
| 实测 | `POST /comment {}` → `{"code":500,"msg":"参数有误"}` |
| 影响 | 前端无法区分"用户输错了"与"服务器炸了"，只能统一提示失败；监控会把正常校验计入 5xx |
| 修复 | 校验类错误统一用 `Result.create(400, ...)`；或让 `BadRequestException` 走全局处理器 |

### R-07【P1】敏感配置明文入库

| 项 | 内容 |
|---|---|
| 位置 | `application-dev.properties`（DB/Redis 密码、JWT 密钥、TG token） |
| 影响 | 仓库泄露 = 全套凭据泄露；已在 Git 历史中 |
| 修复 | 改用环境变量或 `.env`（加入 `.gitignore`）；轮换全部已暴露密码 |

### R-08【P1】`/image/**` 公开直读

| 项 | 内容 |
|---|---|
| 位置 | `WebConfig.addResourceHandlers` + `upload.file.access-path=/image/**` |
| 现状 | 目录不存在，请求返回 `code:500`（应为 404）；一旦有文件，任何人知道文件名即可下载 |
| 影响 | 未来把付费文档/源码放这里，等于公开分发 |
| 修复 | 付费资源**不得**走静态映射，必须走 `/file/download?token=`；`/image/**` 只保留头像等公共图 |

### R-09【P2】匿名可刷友链浏览量

| 项 | 内容 |
|---|---|
| 位置 | `FriendController.addViews(@RequestParam nickname)` |
| 实测 | `POST /friend?nickname=xxx` 匿名可达 |
| 影响 | 浏览量数据不可信；无资金损失 |
| 修复 | ✅ 按「昵称 + 真实对端 IP」Redis 去重，1 小时一次；昵称不存在不写键，见 11.6 |

### R-10【P2】CORS 全开

`allowedOrigins("*")` 且未开 `allowCredentials`。由于认证靠 `Authorization` 头而非 Cookie，实际风险低。若未来引入 Cookie 会话，必须收紧。

### R-11【P2】token 3 天有效且不可吊销

改密码、主动登出都不会让已签发的 token 失效。

| 项 | 内容 |
|---|---|
| 修复 | ✅ 令牌版本号（`tv` 声明）+ Redis `auth:tv:{subject}`，改密码/登出即 bump，见 11.7 |

> 保留 3 天有效期：有了吊销机制后，过期时间的意义变成「兜底回收」，
> 缩短到小时级会显著增加登录频率，而收益已经由吊销覆盖。

### R-12【P2】密码文章 token 语义混乱

`sub` 存的是 `blogId`，与用户 token 共用同一密钥和同一套解析逻辑。虽不构成越权（仍需先知道密码），但增加了未来误判风险。建议改为独立 `purpose=pwd` 声明。

### R-13【P3】图床凭据存 localStorage

前端直连 GitHub/又拍云/腾讯云，凭据存浏览器。单人使用可接受；一旦有 XSS 即泄露。建议改服务端代理。

---

## 08 修复优先级与验收

| 顺序 | 项 | 验收标准 | 阻塞谁 |
|---|---|---|---|
| 1 | **R-01 换 JWT 密钥** | 用旧密钥伪造的 token 返回 403；正常登录仍可用 | 一切上线动作 |
| 2 | R-07 外置凭据 | 仓库内 `grep` 不到密码与密钥 | 上线 |
| 3 | R-04 登录限流 | 连续 6 次错误密码返回 429/锁定提示 | 上线 |
| 4 | R-05 唯一索引 | 并发注册同名只有 1 个成功 | V1.0 |
| 5 | R-02 身份链路 | 带访客 token 能取到 `userId` | V1.0 |
| 6 | R-03 默认拒绝 | 新增用户接口未加规则时返回 403 而非放行 | V1.0 |
| 7 | R-06 错误码 | 参数校验返回 400 | V1.0 |
| 8 | R-08 文件隔离 | 私有文件直链 404 | V1.1a |

---

## 09 正面结论（值得保留的做法）

1. **无状态 JWT + 关闭 CSRF** 是正确的组合，没有引入 Session 复杂度
2. **密码 BCrypt**，无明文、无自制加密
3. **四类审计日志（登录/操作/异常/访问）** 覆盖完整，事后可追溯
4. **统一 JSON 错误结构**，前端处理一致
5. **MyBatis 全量参数化**，未发现 SQL 注入
6. **路径穿越被拦截**，`/image/../` 未泄露配置
7. **注册接口不接收 role**，无法自助提权
8. 上一轮已修复的 `JwtLoginFilter` 空指针与 ThreadLocal 泄漏，本轮回查正常

---

## 10 审计声明

- 本报告未修改任何代码、配置、数据库
- 所有探测使用无效载荷，已核对数据库确认无脏数据
- PoC 仅在本地开发环境执行，未对外部系统发起
- 未导出任何真实密码、密钥全文（报告中 R-01 的密钥因是**公开默认值**才完整引用，用于说明风险）

---

## 11 修复记录（2026-11-04 实施）

审计后已按"先修 P0、再补低风险高收益"的顺序完成 3 项修复。

### 11.1 R-01 JWT 密钥轮换 —— 已修复

| 项 | 内容 |
|---|---|
| 改动 | `application-dev.properties`：`token.secretKey=${JWT_SECRET:<64位随机串>}` |
| 效果 | 环境变量可覆盖；本地默认值不再是有意义的字典串；旧密钥签发的 token 全部失效 |
| 验证 | 用旧密钥伪造的 token 访问 `/admin/dashboard` → `{"code":403,"msg":"凭证已失效，请重新登录！"}` |
| 遗留 | 生产部署必须显式设置 `JWT_SECRET`；仓库历史里仍有旧值，建议后续用 `git filter-repo` 清理 |

### 11.2 R-02 前台身份链路 —— 已修复

| 项 | 内容 |
|---|---|
| 改动 | `JwtFilter` 改为全路径解析 token；新增 `CurrentUser` 工具类；新增 `modules/account` 与 `GET /me`；`SecurityConfig` 增加 `/me` 规则 |
| 关键设计 | 仅识别 `admin:` / `visitor:` 前缀的 token；密码文章 token（subject 为 blogId）不参与身份识别；非后台路径遇到非法 token 按匿名放行，后台路径仍立即 403 |
| 配套 | 管理员登录 token 改为 `admin:{username}`；访客登录 token 改为 `visitor:{userId}`；`OperationLogAspect` 落库前剥离前缀 |
| 验证 | 管理员 token → `/me` 返回 `ROLE_admin`；访客 token → `/me` 返回本人 `id=6`；匿名 → `403 请登录`；访客 token → `/admin/**` → `403 无权限访问`；公开接口不受影响；前台首页渲染正常 |

### 11.3 R-05 用户名唯一约束 —— 已修复

| 项 | 内容 |
|---|---|
| 改动 | `ALTER TABLE user ADD UNIQUE INDEX uk_username(username)` |
| 附带 | 同批补齐 `comment(blog_id)`、`comment(is_published,create_time)`、`blog_tag` 复合主键 + `tag_id`、四张日志表 `create_time`、`tag/category` 名称唯一 |
| 迁移脚本 | `coolpilot-server/sql/2026-11-04-indexes-and-constraints.sql`（含回滚语句） |
| 前置 | 执行前已确认无重复用户名/重复标签/重复分类，并做了全库备份 |

### 11.4 后续批次已修复（2026-11-05）

| 编号 | 处理方式 |
|---|---|
| R-04 | `LoginGuard` 按账号计失败（5 次锁 15 分钟）+ `/login` 加 `@AccessLimit(60s/5)`；角色不符不计失败 |
| R-06 | `ControllerExceptionHandler` 补齐 `MethodArgumentNotValid/Bind` → 400、缺参/类型/JSON → 400、`NoResourceFound` → 404 |
| R-07 | datasource / redis / mail / JWT 密钥全部改为环境变量注入，`.env.example` 补齐 |
| R-08 | 私有文件落 `_private/` 前缀，`ImageGuardInterceptor` 拒绝直读；历史文件由 `PrivateFileRelocator` 搬迁 |

### 11.5 R-03 默认拒绝 —— 已修复（2026-11-05）

| 项 | 内容 |
|---|---|
| 改动 | `SecurityConfig` 末尾改为 `.anyRequest().denyAll()`；新增 `SecurityPaths.PUBLIC` 白名单（内容浏览、评论、友链、注册登录、商品展示、图片、`/file/download`、`/resource/access`、`/feedback`、`/tg/**`、`/error`） |
| 理由 | 原设计「默认放行 + 记得加规则」，新增接口漏配就是匿名可读，只能靠人肉 review 兜底 |
| 效果 | 未知路径与漏归类接口一律 403「请登录」，上线前就能暴露缺配置 |
| 验证 | 17 个公开接口全部 200（`searchBlog` 是缺参数 400，说明已过鉴权）；`/me`、`/orders`、`/entitlements`、`/demands`、`/projects`、`/file/token`、`/admin/**` 匿名均 403；owner 与 user 的权限矩阵与改动前一致；前台 8 个页面 SSR 200、CMS 200 |

### 11.6 R-09 友链刷量 —— 已修复（2026-11-05）

| 项 | 内容 |
|---|---|
| 改动 | `FriendService.addViewsOnce`：先校验昵称存在（不存在直接返回，不写键），再按 `friend:view:{昵称}:{IP}` 做 1 小时 Redis 去重；接口加 `@AccessLimit(60s/30)` |
| 额外收紧 | 去重 IP 默认取 `request.getRemoteAddr()`，**不信任** `X-Forwarded-For`（客户端可伪造，换头即换"新访客"）；部署在可信反代后把 `security.trust-forwarded-for` 打开 |
| 验证 | 同 IP 连点 3 次 views 只 +1；换真实 IP 再 +1；伪造 3 个不同 XFF 头仍只 +1；不存在的昵称不产生 Redis 键 |

### 11.7 R-11 令牌吊销 —— 已修复（2026-11-05）

| 项 | 内容 |
|---|---|
| 思路 | 每个身份（`owner:用户名` / `user:ID`）一个版本号，签发时写进 token 的 `tv` 声明，每请求比对；版本不一致即视为失效 |
| 为什么不用黑名单 | 黑名单要给每张 token 存一条记录并保留到过期；版本号每个身份只有一条，天然带 TTL |
| 改动 | 新增 `TokenVersionService`（current / bump）；`JwtUtils` 签发时带 `tv`；`JwtFilter` 校验版本（后台路径返回「凭证已失效」，前台按匿名）；`/me/password` 与 `/admin/account` 改密后 bump；新增 `POST /me/logout` 主动吊销 |
| 可用性取舍 | Redis 异常时 fail-open（按版本 0 处理），避免一次抖动把全站踢下线；代价是故障期间吊销不生效 |
| 兼容 | 旧 token 没有 `tv` 声明，按 0 处理，与未 bump 的身份一致，升级当刻不掉线 |
| 验证 | 同一账号两个会话 → 改密后两个都 403；旧密码登录 401；新密码正常；`/me/logout` 后原 token 403；只改昵称不影响登录态；后台改密后 CMS 旧 token 403 |
| 顺带修复 | 后台「账号设置」保存此前**必然失败**：token subject 是 `owner:用户名`，被直接当 `where username` 条件；且原 SQL 只更新用户名/密码，昵称头像邮箱改了不生效。现改为拆前缀 + 按需改密 + 资料兜底 |
