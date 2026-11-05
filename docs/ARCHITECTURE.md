# CoolSkyAI 项目架构说明

> 快照时间：2026-11-04
> 基线：三端项目，已完成 JDK 21 + Spring Boot 4.1.2 升级、前台门户化改造、访客登录体系新增。
> 本文档描述的是**当前实际代码状态**，与上游原始版本已有较大差异。

---

## 一、总体架构

```
                          浏览器
        ┌───────────────────┴───────────────────┐
        │                                       │
   :8081 coolpilot-web                        :8079/cms coolpilot-admin
   前台（访客可见）                        后台（仅博主）
   Vue 2 SPA                              Vue 2 SPA
        │                                       │
        │  axios baseURL: /api/                  │  axios baseURL: /api/admin/
        └───────────────────┬───────────────────┘
                            │ 开发期：vue.config.js devServer.proxy
                            │ 生产期：nginx 反向代理
                            ▼
                 :8091 coolpilot-server  Spring Boot 4.1.2
                 无状态 REST 服务，不提供任何页面
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
      MySQL 8.0 :3306              Redis :6379
      coolpilot 库，19 张表             缓存 + PV/UV 计数 + 访问者标识
```

**职责边界**

| 端 | 面向对象 | 认证方式 | 是否可写 |
|---|---|---|---|
| coolpilot-web | 访客 / 读者 | `visitor:*` JWT（访客自助注册） | 仅评论、友链申请、点赞、注册登录 |
| coolpilot-admin | 博主本人 | `admin:*` JWT | 全量增删改查 |
| coolpilot-server | 上述两端 | 见鉴权矩阵 | — |

---

## 二、运行时环境

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | Temurin **21.0.5 LTS** | `maven.compiler.release=21`，字节码 major version 65 |
| Maven | 3.9.9 | — |
| Node / npm | 22.22.2 / 10.9.7 | 两个前端共用 |
| MySQL | 8.0.40 | 库名 `coolpilot`，字符集 utf8mb4 |
| Redis | 5.0.14.1 | 用于缓存与计数，非持久化关键路径 |
| Spring Boot | **4.1.2** | 内含 Spring Framework 7 + Tomcat 11 |
| Spring Security | 7.x（随 Boot 4） | 已迁移为 `SecurityFilterChain` 编程式配置 |

---

## 三、目录结构

```
coolpilot-platform/
├── coolpilot-server/                 Spring Boot 后端（211 个 Java 文件）
│   ├── src/main/java/top/naccl/
│   ├── src/main/resources/
│   │   ├── mapper/           18 个 MyBatis XML
│   │   ├── ipdb/             ip2region 离线 IP 库（.db + .xdb）
│   │   ├── templates/        owner.html / guest.html（邮件通知模板）
│   │   ├── application.properties      仅一行：spring.profiles.active=dev
│   │   ├── application-dev.properties   全部实际配置
│   │   └── logback-spring.xml
│   ├── schema.sql             建库建表 + 初始数据
│   ├── demo-data.sql         演示文章（5 篇，本地预览用）
│   └── pom.xml
├── coolpilot-admin/                 Vue 2 后台（38 个 .vue，25 个业务页）
│   ├── src/{api,components,layout,router,store,util,views,icons}
│   └── vue.config.js         publicPath '/cms/' + devServer.proxy
├── coolpilot-web/                Vue 2 前台（28 个 .vue，10 个页面，17 个组件）
│   ├── src/{api,assets,components,plugins,router,store,util,views}
│   │   └── assets/css/       portal.css / portal-content.css / base.css / typo.css
│   └── vue.config.js         devServer.proxy
├── docs/                     本文档
├── deploy.sh                 部署脚本
└── README.md
```

---

## 四、coolpilot-server（后端）

### 4.1 分层结构

| 包 | 文件数 | 职责 |
|---|---|---|
| `com.coolskyai` | 1 | 启动类 `BlogApiApplication` |
| `controller` | 12 | 前台公开接口 |
| `controller/admin` | 16 | 后台接口，统一 `/admin` 前缀 |
| `service` / `service.impl` | 19 + 19 | 业务接口与实现分离，全部 `@Service` |
| `mapper` | 18 | MyBatis Mapper 接口 |
| `entity` | 18 | 数据库实体 |
| `model/dto` | 12 | 入参对象 |
| `model/vo` | 20 | 出参对象 |
| `config` | 8 | Security / JWT / Redis / Web / 异常入口与拒绝处理器 |
| `config/properties` | 6 | `@ConfigurationProperties` 属性类 |
| `aspect` | 3 | 操作日志、访问日志、异常日志切面 |
| `annotation` | 3 | `@OperationLogger`、`@VisitLogger`、`@AccessLimit` |
| `constant` | 6 | Redis key、JWT 前缀、上传常量等 |
| `enums` | 3 | 状态枚举 |
| `exception` | 3 | `BadRequestException`、`NotFoundException` 等 |
| `handler` | 1 | `ControllerExceptionHandler` 全局异常 |
| `interceptor` | 1 | `AccessLimitInterceptor` 限流 |
| `task` | 2 | 定时任务执行体 |
| `util` | 40 | 含 comment / markdown / quartz / telegram / upload 五个子模块 |

`util` 细分：`comment`（1 + channel 4）、`markdown`（1 + cover 6 + heimu 6）、`quartz` 3、`telegram` 2、`upload`（1 + channel 5）、`common` 2、根 9。

### 4.2 技术栈

| 领域 | 选型 |
|---|---|
| Web | spring-boot-starter-web（Tomcat 11） |
| 持久层 | MyBatis Spring Boot Starter **4.1.0** + PageHelper **4.1.1** + mysql-connector-j |
| 安全 | Spring Security 7 + **jjwt 0.12.7**（api / impl / jackson） |
| 缓存 | spring-boot-starter-data-redis，序列化用 `JacksonJsonRedisSerializer`（Jackson 3） |
| JSON | **Jackson 3**（`tools.jackson`），`JacksonUtils` 统一封装 |
| 定时任务 | Quartz，任务配置存 `schedule_job` 表，支持后台动态增删改与手动触发 |
| Markdown | commonmark 0.18.1 + 4 个官方扩展 + 2 个自研扩展（cover 封面图、heimu 黑幕） |
| AOP | spring-boot-starter-**aspectj**（Boot 4 已更名） |
| 重试 | spring-retry 2.0.13（Boot 4 BOM 不再管理，需显式指定版本） |
| 访客画像 | ip2region 2.6.5（IP 归属地）+ yauaa 7.11（User-Agent 解析） |
| 通知 | Mail + Thymeleaf 模板；评论通知支持 Mail / Telegram 双渠道 |
| 上传 | 又拍云 java-sdk 4.2.3 + GitHub contents API + 本地磁盘 |
| 其他 | Lombok 1.18.42、hutool-crypto 5.8.11 |

### 4.3 接口清单（81 个）

#### 前台公开接口（12 个控制器）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/site` | 站点配置 + 分类 + 标签 + 推荐 + 随机文章，一次返回 |
| GET | `/blogs` | 文章分页列表 |
| GET | `/blog` | 文章详情 |
| GET | `/searchBlog` | 搜索 |
| POST | `/checkBlogPassword` | 私密文章验密 |
| GET | `/category` | 分类下文章 |
| GET | `/tag` | 标签下文章 |
| GET | `/archives` | 归档（时间线） |
| GET | `/about` | 关于我 |
| GET | `/friends` | 友链页 |
| GET | `/moments` | 动态列表 |
| GET | `/comments` | 评论列表 |
| POST | `/comment` | 提交评论 |
| POST | `/friend` | 申请友链 |
| POST | `/moment/like/{id}` | 动态点赞 |
| POST | `/login` | 平台所有者登录（仅 `ROLE_owner`，前台已不再使用） |
| POST | `/visitor/register` | **访客注册**（限流 60s/3 次） |
| POST | `/visitor/login` | **访客登录**（限流 60s/10 次） |
| POST/DELETE | `/favorite` | 收藏/取消收藏（幂等，需登录） |
| GET | `/favorites` | 我的收藏（分页） |
| GET | `/favorite/status` | 是否已收藏 |
| POST/DELETE | `/like` | 点赞/取消点赞（幂等，需登录） |
| GET | `/like/status` | 点赞状态与总数 |
| GET | `/messages` | 我的站内消息（分页） |
| GET | `/messages/unread-count` | 未读数 |
| PUT | `/message/{id}/read` | 标记已读 |
| POST | `/feedback` | 提交技术问题反馈（可匿名，限流 60s/3 次） |
| GET | `/products` | 商品列表（公开，按类型/分类/关键词筛选） |
| GET | `/product/{productNo}` | 商品详情（含 Markdown 渲染结果、课程信息、资源列表） |
| GET | `/product/{productNo}/catalog` | 课程目录（仅标题与试看标记） |
| GET | `/productCategories` | 商品分类 |
| GET | `/file/token` | 换取一次性下载凭证（需登录，5 分钟有效） |
| GET | `/file/download?token=` | 受控下载（靠 token 校验，不暴露直链） |
| GET | `/me` | **当前登录用户信息**（博主/访客共用，需登录） |
| POST | `/tg/{token}` | Telegram Bot Webhook |

#### 后台接口（16 个控制器，全部 `/admin/**`）

| 模块 | 路径 |
|---|---|
| 登录 | `POST /admin/login`（由 `JwtLoginFilter` 处理，非 Controller） |
| 账户 | `POST /admin/account` |
| 文章 | `GET/POST/PUT/DELETE /admin/blog`、`GET /admin/blogs`、`PUT /admin/blog/top`、`PUT /admin/blog/recommend`、`PUT /admin/blog/{id}/visibility`、`GET /admin/categoryAndTag` |
| 分类 | `GET /admin/categories`、`POST/PUT/DELETE /admin/category` |
| 标签 | `GET /admin/tags`、`POST/PUT/DELETE /admin/tag` |
| 评论 | `GET /admin/comments`、`PUT/DELETE /admin/comment`、`PUT /admin/comment/published`、`PUT /admin/comment/notice`、`GET /admin/blogIdAndTitle` |
| 动态 | `GET /admin/moments`、`GET/POST/PUT/DELETE /admin/moment`、`PUT /admin/moment/published` |
| 友链 | `GET/POST/PUT/DELETE /admin/friend(s)`、`GET /admin/friendInfo`、`PUT /admin/friendInfo/*` |
| 页面 | `GET/PUT /admin/about`、`GET/POST /admin/siteSettings`、`GET /admin/webTitleSuffix` |
| 定时任务 | `GET/POST/PUT/DELETE /admin/job(s)`、`POST /admin/job/run`、`PUT /admin/job/status`、`GET/DELETE /admin/job/log(s)` |
| 日志 | `GET/DELETE /admin/{loginLog,operationLog,exceptionLog,visitLog}s?` |
| 统计 | `GET /admin/dashboard`、`GET/DELETE /admin/visitor(s)` |

### 4.4 鉴权矩阵

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/admin/webTitleSuffix").permitAll()                     // 前台取标题后缀
    .requestMatchers(SecurityPaths.OWNER_ONLY).hasAuthority(Role.OWNER)       // 仅平台所有者
    .requestMatchers(SecurityPaths.AUTHENTICATED).hasAnyAuthority(Role.OWNER, Role.USER)
    .requestMatchers(SecurityPaths.PUBLIC).permitAll()                        // 显式公开白名单
    .anyRequest().denyAll()                                                   // 其余默认拒绝
)
```

> 路径清单集中在 `SecurityPaths`（`OWNER_ONLY` / `AUTHENTICATED` / `PUBLIC`），角色常量在 `Role`（`ROLE_owner` / `ROLE_user`）。
> **新增接口必须归入三者之一**：用户级接口加进 `AUTHENTICATED`，公开接口加进 `PUBLIC` 并写清理由。
> 漏归类不再被静默放行，而是返回 403 —— 这是 R-03 的收口方式，宁可上线前发现缺配置，也不要把数据裸露出去。
> 同理，未知路径现在返回 403 而不是 404，属于有意的「不暴露接口是否存在」。
>
> `JwtFilter` 会对**所有路径**解析 token：命中 `owner:` / `user:` 前缀（兼容历史 `admin:` / `visitor:`）才写入 `SecurityContext`，
> 同时把 `authorities` 里的历史角色值归一化为 `ROLE_owner` / `ROLE_user`，升级当刻不会把在线用户踢下线；
> 非后台路径遇到非法 token 按匿名放行（避免误判密码文章 token），后台路径仍立即返回 403。

- 无状态，不建 Session，token 通过 `Authorization` 头传递
- token 前缀区分身份：`owner:用户名` / `user:用户ID`（`JwtConstants`），历史前缀 `admin:` / `visitor:` 仍可解析
- 未登录 → `{"code":403,"msg":"请登录"}`；已登录但越权 → `{"code":403,"msg":"无权限访问"}`
- token 带 `tv`（身份版本号）：改密码、`POST /me/logout` 会递增 Redis 里的版本，旧 token 立即失效（R-11）；
  Redis 不可用时按 0 处理（fail-open），避免整站被踢下线
- **注意**：曾允许 `visitor` 只读 `/admin/**`（演示账号用途），开放访客自助注册后已收紧，否则构成越权漏洞

### 4.5 Redis 缓存键（11 个）

| 键 | 内容 |
|---|---|
| `homeBlogInfoList` | 首页文章列表 |
| `siteInfoMap` | 站点配置（Hash） |
| `categoryNameList` | 分类名列表 |
| `tagCloudList` | 标签云 |
| `newBlogList` | 最新文章 |
| `aboutInfoMap` | 关于我 |
| `friendInfoMap` | 友链页信息 |
| `archiveBlogMap` | 归档数据 |
| `blogViewsMap` | 文章浏览量计数器（**写 Redis，不写库**） |
| `identificationSet` | 当日访客去重标识 |
| `qqAvatarUrlMap` | QQ 头像 URL 缓存 |

### 4.6 定时任务

| Bean | 方法 | Cron | 作用 |
|---|---|---|---|
| `redisSyncScheduleTask` | `syncBlogViewsToDatabase` | `0 0 1 * * ?` | 凌晨 1 点把 Redis 浏览量回写 MySQL |
| `visitorSyncScheduleTask` | `syncVisitInfoToDatabase` | `0 0 0 * * ?` | 零点结算当日 PV/UV，清空访客标识 |

### 4.7 关键配置（application-dev.properties）

```properties
server.port=8091
blog.api=http://localhost:${server.port}
spring.datasource.url=jdbc:mysql://localhost:3306/coolpilot?...
spring.data.redis.host=localhost
spring.data.redis.password=***
upload.channel=local
upload.file.path=/Users/naccl/Desktop/upload/      # ← 仍是原作者 Mac 路径，见风险清单
upload.file.access-path=/image/**
upload.file.resources-locations=file:${upload.file.path}
```

---

### 4.8 V1.2 技术服务与需求中心

模块：`com.coolskyai.modules.service`（entity / mapper / service / controller）。
表：`service_item` `demand` `quote` `project` `milestone` `delivery` + `service_event`（状态流水），
迁移见 `V6__service_demand.sql`。

**流程**

```
用户提交需求(PENDING_REVIEW) → 后台审核 → PENDING_QUOTE → 报价(草稿→发送)
→ 用户确认(PENDING_CONFIRM → IN_PROGRESS，自动生成项目 + 首个里程碑)
→ 交付(DELIVERED) → 验收(ACCEPTED) → 全部里程碑通过 → 项目/需求 COMPLETED
异常分支：拒绝报价回到 PENDING_QUOTE（可出 v2 报价）、验收打回回到 IN_PROGRESS、用户取消
```

**接口**

| 端 | 路径 | 说明 |
|---|---|---|
| 前台 | `POST /demand`、`GET /demands`、`GET /demand/{no}`、`POST /demand/{no}/cancel` | 提交 / 我的需求 / 详情 / 取消 |
| 前台 | `POST /quote/{id}/accept`、`POST /quote/{id}/reject` | 确认或拒绝报价 |
| 前台 | `GET /projects`、`GET /project/{no}`、`POST /milestone/{id}/accept|reject` | 项目进度与验收 |
| 后台 | `GET /admin/demands`、`GET /admin/demand/{no}`、`POST /admin/demand/{no}/review`、`PUT /admin/demand/{no}/status` | 需求池与审核 |
| 后台 | `POST /admin/quote`、`PUT /admin/quote/{id}`、`POST /admin/quote/{id}/send` | 报价（版本化） |
| 后台 | `GET /admin/projects`、`GET /admin/project/{no}`、`POST /admin/project`、`PUT /admin/project/{no}/status` | 项目 |
| 后台 | `POST /admin/milestone`、`PUT /admin/milestone/{id}`、`POST /admin/milestone/{id}/deliver` | 里程碑与交付 |
| 后台 | `GET/PUT /admin/serviceItem/{productId}` | 服务商品扩展信息 |

**约束**

- 权限：`/demand**`、`/quote/**`、`/projects`、`/project/**`、`/milestone/**` 在 `SecurityPaths.AUTHENTICATED`；
  `/admin/**` 仅 `ROLE_owner`。归属校验在 Service 层（用户只能看自己的需求/项目）。
- 隐私：`demand.contact` 仅所有者可见，用户视角详情里被置空。
- 并发：状态流转全部带原状态条件（`WHERE status = #{from}`），抢不到就报「状态已变化」。
- 交付文件：私有存储，`FileService.resolveForDownload` 增加「交付物所属项目的客户可下载」一条通路。
- 固定价服务不建独立订单表：复用 `orders` + `payment` + `refund`，`project.order_no` 关联。

---

### 4.9 文章关联付费内容

表：`product_article`（V1 快照就有，此前一直没有代码使用）。用于「内容 → 付费」的转化入口。

- 后台：文章编辑页「关联付费内容」多选，整组替换（`PUT /admin/blog/{blogId}/products`），顺序即展示顺序
- 前台：`GET /blog/{blogId}/products`（公开）只返回**上架**商品；登录用户额外返回 `owned`
- 文章页在正文下方展示卡片，已购显示「已拥有」；未登录点击进入商品页
- 未绑定商品的文章不渲染该区块，不留空位

### 4.10 专题（合集）

模块：`com.coolskyai.modules.topic`；表：`topic` / `topic_article`（V8）。

- 一个专题 = 一组**有序**文章，顺序即阅读顺序；同一篇文章可以进多个专题
- 后台：`内容管理 → 专题管理`（列表、发布开关、编辑抽屉里搜文章并上下移调序）
- 前台：`/topic` 列表、`/topic/{slug}` 详情；文章页顶部显示「本篇属于专题 X · 共 N 篇」
- 接口：`GET /topics`、`GET /topic/{slug}`、`GET /blog/{id}/topics`（均公开）；
  `GET/POST /admin/topics`、`GET/PUT/DELETE /admin/topic/{id}`、`PUT /admin/topic/{id}/status|articles`
- **可见性**：专题页会过滤掉未发布文章，`articleCount` 也只算已发布；
  后台详情反而要看到全部，否则编辑调完顺序一保存就会把草稿踢出专题
- slug 必填且唯一（可留空自动生成 `topic-xxxx`），带进 `sitemap.xml`

### 4.12 业务通知（站内消息 + 邮件）

模块：`com.coolskyai.modules.notify`（`NotifyService`）；表：`message`（V1 已有）。
站内消息页与「未读数」接口此前一直存在，但**没有任何业务代码写入**，
列表恒为空；这次补上唯一出口并把交易、需求、项目的关键节点接进去。

```
业务动作 → NotifyService.toUser(用户) / toOwner(平台所有者)
              ├── 站内消息（跟随事务，回滚则一起消失）
              └── 邮件（事务提交后才发，失败只记 WARN）
```

已接入的节点：

| 域 | 事件 | 通知对象 |
|---|---|---|
| 支付 | 客户提交转账凭证 | 所有者 |
| 支付 | 确认收款、权益开通 | 客户 |
| 退款 | 申请 / 同意 / 拒绝 / 打款完成 | 所有者 / 客户 |
| 需求 | 提交需求 | 所有者 |
| 需求 | 审核通过、审核未通过 | 客户 |
| 报价 | 发出报价 | 客户 |
| 报价 | 客户接受 / 拒绝 | 所有者 |
| 项目 | 里程碑交付 | 客户 |
| 项目 | 验收通过 / 打回 / 项目完成 | 所有者 |

约定：

- **邮件在事务提交后发送**：业务回滚却已经发出「已发货」邮件，比不发更糟
- **邮件失败不影响业务**：`MailUtils` 内部吞异常并 WARN，不向上抛
- **未配置 SMTP 时静默降级**：`MAIL_PASSWORD` 为空则只写站内消息，启动时 INFO 一行说明
- 收件人：客户取 `user.email`；所有者取「站点设置 → 资料卡 → 邮箱」（去掉 `mailto:`），未填回落到发信账号
- 站内消息存相对路径（`/me?tab=ent`），邮件里由 `blog.view` / `blog.cms` 补成绝对地址
- 前台 `/me` 支持 `?tab=` 直达，通知点进去落在对应标签

### 4.13 智能问答（RAG，第二阶段）

模块：`com.coolskyai.modules.ai`；表：`ai_conversation` / `ai_message`（V9）。

```
提问 → 检索 Top6（复用 AiSearchService.retrieve，含权限过滤）
     → 拼上下文（[编号] 标题 链接 正文）→ 调模型 → 答案 + 引用
     → 登录时落库（会话 + 两条消息 + 引用快照）
```

**模型接法：一个实现覆盖本地与云端**

只依赖 OpenAI 兼容的 `/chat/completions`，靠配置切换：

| 部署 | base-url | 说明 |
|---|---|---|
| 本地 Ollama | `http://host.docker.internal:11434/v1` | 容器访问宿主机；不用 Key |
| 本地 vLLM / LM Studio | `http://host.docker.internal:8000/v1` | 同上 |
| DeepSeek | `https://api.deepseek.com/v1` | 需 `AI_LLM_API_KEY` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 需 Key |

- 配置项见 `application-dev.properties` 的 `ai.llm.*`；`base-url` 或 `model` 为空即视为未启用
- 接口：`POST /ai/chat`（公开，60 秒 12 次限流）、`GET /ai/conversations`、
  `GET/DELETE /ai/conversation/{id}`（登录）；
  `GET /admin/ai/llm`（状态，不返回 Key）、`POST /admin/ai/llm/test`（连通性自测）
- **降级**：未配置或调用失败时不报错，返回 `degraded=true` + `note` + 检索片段，
  功能仍然可用；前端在原位置提示原因
- **不检索就不调模型**：没命中任何片段时直接回「站内没有相关内容」，
  没有资料的生成只会编，还白花一次 token
- **追问补救**：检索空手且在多轮会话里时，用「本轮问题 + 上一轮问题」再检索一次
  （不是真正的指代消解，但零成本救回大部分「那退款呢」这类短追问）
- 会话只在登录后落库，匿名可问但无历史 —— 不给匿名用户建垃圾数据

**流式输出（SSE）**：`POST /ai/chat/stream`

| 事件 | 内容 | 时机 |
|---|---|---|
| `meta` | `conversationId`、`citations`、`model`、`degraded`、`note` | 检索完成后立刻发，来源先于答案出现 |
| `delta` | `{text}` | 上游每吐一段就转发一段 |
| `done` | `answer`、`tookMs`、`conversationId` | 生成结束（含落库） |
| `error` | `message`、`partial` | 生成中断；已生成的部分保留 |

- 检索与校验在主线程同步完成（毫秒级），生成放 `TaskExecutor`，
  控制器立刻返回 `SseEmitter`，不占着请求线程等模型
- **不写 `produces`**：经过 Nitro / nginx 代理时可能带 `Accept: application/json`，
  声明 `produces=text/event-stream` 会直接 406/500；`SseEmitter` 自己会设 Content-Type
- 响应头带 `X-Accel-Buffering: no`，gateway 的 `/api/` 也关掉了 `proxy_buffering`，
  否则增量会被攒成一次性下发，流式名存实亡
- 前端用 `fetch` + `ReadableStream` 手工解析 SSE（`EventSource` 只支持 GET，发不了请求体）；
  流式通道不可用时自动退回一次性接口 `/ai/chat`

**停止生成（前后端一起断）**

1. 前端 `AbortController.abort()` 掐断 fetch，气泡保留已收到的文字并标注「已停止生成」，不触发降级重试
2. 后端在 `emitter.onError / onTimeout` 上打取消标记；delta 回调看到标记就抛
   `StreamCancelledException`，从 `readStream` 里跳出并关闭上游连接 —— 用户走了就不再烧 token
3. `StreamCancelledException` 与 `LlmException` 严格分开：前者是「用户不看了」，不打 WARN、不算降级；
   `LlmException` 才是模型故障，要告诉用户原因
4. **无论怎么结束都落库**：断连时保存已生成的部分，历史里能看到半截而不是空白
5. 客户端断开还会让容器抛 `AsyncRequestNotUsableException`，
   全局异常处理单独接住它并只记 DEBUG —— 否则会往已断的 `text/event-stream` 里写 JSON，
   再抛一次 `HttpMessageNotWritableException`，日志里出现两段无意义堆栈

**答案内引用联动**

- 答案里的 `[1]` 渲染成标记，点击**滚到下方对应的来源卡片并高亮 1.6 秒**，不跳走；
  卡片本身仍是外链，想深入阅读再点卡片
- 渲染函数抽到 `composables/useCitation.ts`（问答页与分享页共用），`v-html` 里绑不了事件，
  所以用事件委托：容器上监听 click，读 `data-cite` 找锚点
- 模型输出一律按不可信文本处理：先转义 `& < > "`，再做编号替换；
  只对**确实存在于引用列表里的编号**生成标记，模型编出 `[9]` 时保持纯文本
- 换行仍交给 `white-space: pre-wrap`，不额外插 `<br/>`

**答案收藏与分享**

| 能力 | 实现 |
|---|---|
| 收藏 | 复用通用 `favorite` 表，`target_type=AI_ANSWER`、`target_id=ai_message.id`；**必须校验回答归属**（否则填别人 messageId 就能把内容收进自己列表） |
| 收藏列表 | 收藏页联表取答案开头，`target_ref` 带回会话 id，点击跳 `/ask?conversation=` |
| 分享 | `POST /ai/answer/{id}/share`（可带 `days`）生成 12 位 token 存 `ai_message.share_token`；**未过期就复用同一条链接**，过期后才换新 token |
| 有效期 | 默认 30 天、最长 365 天，`days=0` 为长期有效；到期后接口返回 `code=410`，前端提示「让作者重新分享」而不是「内容不存在」 |
| 公开页 | `/share/{token}` → `GET /share/answer/{token}`（无鉴权），只回问题、答案、引用、模型、时间与统计，不含任何用户信息 |
| 阅读/点赞 | `POST /share/answer/{token}/view` 与 `POST`/`DELETE /share/answer/{token}/like`，按 IP 去重（Redis，30 分钟 / 30 天），计数落在 `share_views` / `share_likes` |
| 撤销 | `DELETE /ai/answer/{id}/share` 清空 token 与到期时间，链接立即失效；删除对话时随消息一起消失 |
| 讨论 | 复用 `comment` 表（`target_type='SHARE'`、`target_id=ai_message.id`、`page=3`）；**只允许登录后发言**，作者与被回复者会收到站内消息 |
| 分页 | 只切顶楼：每页 20 条（上限 50），一页顶楼连带它们的全部回复一起返回；页码写在 URL 上（`?page=2`）可直链、可被逐页抓取，配 `rel=prev/next` 与 canonical；越界页码夹到最后一页而不是给空页（避免软 404） |
| 排序 | `?sort=newest`（默认）/ `?sort=oldest`；排序是同一内容的视图变体，canonical 始终指向默认排序 |
| 删除留言 | 作者或平台所有者可删，软删除（`is_published=0`），后台仍可回看 |
| 我的留言 | `GET /me/share-comments`（个人中心「我的留言」）：带上分享的问题标题与是否仍有效；分享被撤销/过期后留言仍列出，只是不可点 |
| 复制 | 前端把「问题 + 答案 + 来源列表」拼成纯文本写入剪贴板 |

### 4.11 V1.3 知识库检索（第一阶段）

模块：`com.coolskyai.modules.ai`；表：`kb_knowledge_base` / `kb_document` / `kb_chunk`（V7）；
决策记录见 `docs/ADR-0001-VECTOR-STORE.md`。

```
索引：文章（跳过密码文章） / 上架商品文案 / 课时正文
      → 清洗 Markdown → 350 字切块（60 字重叠）→ 向量化 → kb_chunk
检索：query 向量 → 按「公开 或 已购商品」筛候选 → 余弦打分 → 文档级去重 → TopN
```

- 向量存 MySQL（`MEDIUMBLOB`，512 维 float32），进程内快照做余弦检索；1e4 块约 20MB、毫秒级
- 权限在**打分前**过滤：付费课时只有持有权益才进入候选，避免越权召回
- 本地哈希向量带「共同词」护栏：无任何共同词时跳过，防止哈希碰撞造成误召回；
  接语义模型后该护栏自动失效（`tokenHashes` 返回空）
- 接口：`GET /ai/search?q=&limit=`（公开）、`POST /admin/ai/reindex`、`GET /admin/ai/stats`
- 后台页：`智能检索 → 知识库索引`（统计、重建、检索自测）；前台搜索页有「智能检索」区
- 内容有增删改后需重建索引；索引是全文替换，几秒完成

---

## 五、coolpilot-admin（后台）

### 5.1 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Vue **2.6.11** + Vue Router 3.3.4 + Vuex 3.5.1 |
| UI | Element UI 2.13.2 |
| 编辑器 | mavon-editor 2.9.1（Markdown，未挂载图片上传钩子） |
| 图表 | ECharts 4.9.0 |
| 请求 | axios 0.24.0，`baseURL: '/api/admin/'` |
| 其他 | moment 2.27、nprogress、v-viewer、cos-js-sdk-v5、lodash |
| 构建 | `@vue/cli-service` **4.5.0**（webpack 4），Sass，`publicPath: '/cms/'` |

### 5.2 目录与页面

```
src/
├── api/          19 个模块：blog category tag comment moment friend about
│                 siteSetting account dashboard github upyun
│                 login loginLog operationLog exceptionLog visitLog
│                 schedule visitor
├── components/   Breadcrumb / Hamburger / SvgIcon
├── layout/       index.vue + Navbar + Sidebar + AppMain（含 ResizeHandler）
├── router/       静态路由表，侧栏由 meta.title + meta.icon 生成
├── store/        app（侧栏折叠、设备）
├── util/         request.js（axios 封装）、validate、uuid、task-queue
├── icons/        SVG sprite
└── views/        25 个页面
```

**菜单结构（7 组）**

| 一级菜单 | 子页面 |
|---|---|
| Dashboard | 仪表盘（PV/UV/文章数/评论数 + 分类饼图 + 标签饼图 + 访客地图） |
| 博客管理 | 写文章、写动态、文章管理、动态管理、分类管理、标签管理、评论管理 |
| 页面管理 | 站点设置、友链管理、关于我 |
| 图床管理 | 配置、GitHub、又拍云、腾讯云 |
| 系统管理 | 修改账户、定时任务 |
| 日志管理 | 任务日志、登录日志、操作日志、异常日志、访问日志 |
| 数据统计 | 访客统计 |

### 5.3 请求与守卫

```js
// util/request.js
baseURL: '/api/admin/'
请求拦截：localStorage.token → Authorization 头；访客角色拦截非 GET
响应拦截：res.code !== 200 → Message.error + reject
路由守卫：无 token → 强制跳 /login
```

**注意**：后端所有业务错误都返回 HTTP 200，错误码放在响应体 `code` 字段。这是刻意设计——若改为真实 4xx，axios 会走 error 分支，中文提示会丢失，界面只剩 `Request failed with status code 400`。

---

## 六、coolpilot-web（前台）

### 6.1 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Vue **2.6.11** + Vue Router 3.3.4 + Vuex 3.5.1 |
| UI | **自研门户主题**（`portal.css`）+ Semantic UI CSS 2.4.1（遗留）+ Element UI 2.13.2（少量组件） |
| 请求 | axios 0.24.0，`baseURL: '/api/'` |
| 其他 | moment、nprogress、vue-lazyload、v-viewer、sanitize-html |
| 构建 | `@vue/cli-service` **4.4.0**（webpack 4） |

### 6.2 路由

| 路径 | 页面 | 说明 |
|---|---|---|
| `/login` | `visitor/Auth.vue` | 访客登录/注册双 Tab |
| `/` | `Index.vue` | 两栏框架（左目录 + 右内容） |
| `/home` | `home/Home.vue` | 门户首页：Hero + 分类卡 + 最新文章 |
| `/archives` | `archives/Archives.vue` | 时间线归档 |
| `/blog/:id` | `blog/Blog.vue` | 文章详情 + 文内目录 |
| `/tag/:name` | `tag/Tag.vue` | 标签文章 |
| `/category/:name` | `category/Category.vue` | 分类文章 |
| `/moments` | `moments/Moments.vue` | 动态 |
| `/friends` | `friends/Friends.vue` | 友链 |
| `/about` | `about/About.vue` | 关于我 |

### 6.3 主题与组件

- `assets/css/portal.css` — 设计变量与门户骨架（主色 `#0F766E`、深色 `#115E59`、强调 `#D97706`、容器 88rem）
- `assets/css/portal-content.css` — 内容页、文章页、时间线、友链卡片
- 核心组件：
  - `index/Nav.vue` — 双层顶栏（品牌栏 + 分类导航条），含搜索防抖与登录态
  - `index/PortalHero.vue` — eyebrow + clamp 大标题 + 按钮组 + 统计卡 + 装饰网格
  - `sidebar/DocSidebar.vue` — 左侧文档目录，分类折叠 + 文章懒加载
  - `sidebar/Tocbot.vue` — 文章内「本文目录」
  - `blog/BlogItem.vue` — 门户文章卡片
  - `index/Header.vue` — **已弃用**（旧版大图首页残留，无引用）

- 文案配置：`src/settings.js` 的 `portal` 块（站点名、标语、大标题、描述）

### 6.4 访客登录态

```
localStorage.visitorToken  ←→  Vuex state.visitor
Nav 未登录：显示「登录」→ /login
Nav 已登录：头像 + 昵称 + 退出
```

---

## 七、数据库（19 张表）

| 业务域 | 表 | 说明 |
|---|---|---|
| 内容 | `blog` | 文章（支持置顶、推荐、密码保护、草稿） |
| | `category` / `tag` / `blog_tag` | 分类、标签、多对多关联 |
| 互动 | `comment` | 评论（支持 QQ 头像、邮件通知、审核） |
| | `moment` | 动态（可发布/隐藏，支持点赞） |
| | `friend` | 友链（含审核状态） |
| 页面 | `about` | 关于我（多语言条目） |
| | `site_setting` | 站点配置，按 `type` 分组：1=基础 2=资料卡 4=友链页 |
| 账号 | `user` | `ROLE_owner`（平台所有者）/ `ROLE_user`（前台用户） |
| 统计 | `visit_log` | 访问明细 |
| | `visitor` | 访客标识（UV 去重） |
| | `city_visitor` / `visit_record` | 地域聚合、访问记录 |
| 日志 | `login_log` / `operation_log` / `exception_log` | 三类审计日志 |
| 任务 | `schedule_job` / `schedule_job_log` | Quartz 任务定义与执行记录 |

---

## 八、关键数据流

### 8.1 首页加载

```
浏览器 GET /api/site
  → IndexController.site()
    → Redis 查 siteInfoMap（未命中则查 MySQL 并回填）
    → 同时返回 categoryList / tagList / newBlogList / randomBlogList
  → Vuex 保存 siteInfo / categoryList / tagList
  → Home.vue 用 categoryList 渲染分类卡，用 /archives 的 count 渲染文章统计
```

### 8.2 浏览量统计

```
访客打开文章 → @VisitLogger 切面
  → Redis blogViewsMap 对应文章 +1（不写库）
  → 每天 01:00 Quartz 批量回写 MySQL blog.views
```

### 8.3 评论提交通知

```
POST /api/comment
  → CommentUtils：若填 QQ 号，下载 q.qlogo.cn 头像并转存到自有存储
  → 落库 comment
  → ChannelFactory 按配置选渠道（Mail / Telegram）下发通知
```

### 8.4 访客注册

```
POST /api/visitor/register
  → 限流校验（60s/3 次）
  → 用户名格式 + 密码长度校验 → BCrypt 加密
  → user 表插入 role=ROLE_user
  → 签发 user: 前缀 JWT 返回
```

---

## 九、启动手册

```bash
# 1. 依赖
net start MySQL80
redis-server.exe --requirepass <pwd> --port 6379

# 2. 后端（务必禁用 devtools，避免随机重启端口与宿主冲突）
cd coolpilot-server
mvn -DskipTests package
java -jar target/coolpilot-server-0.0.1.jar --server.port=8091 --spring.devtools.restart.enabled=false

# 3. 后台
cd coolpilot-admin && npx vue-cli-service serve --port 8079
# 访问 http://localhost:8079/cms/

# 4. 前台
cd coolpilot-web && npx vue-cli-service serve --port 8081
# 访问 http://localhost:8081/home
```

默认后台账号：`Admin / 123456`（README 中注明，库内存 bcrypt 哈希）。

---

## 十、技术债与风险清单

按影响面排序，供后续规划参考。

| # | 问题 | 位置 | 影响 | 建议 |
|---|---|---|---|---|
| 1 | Vue 2.6 已 EOL（2023-12 停止维护），构建链为 webpack 4 | 两个前端 | 无法获得安全补丁；新依赖生态不兼容 | 评估 Vue 3 + Vite 迁移，两前端可分批 |
| 2 | Semantic UI 约 530KB CSS 仍在打包，实际仅少量类在用 | coolpilot-web `main.js` | 首屏体积与样式冲突风险 | 逐类替换为 portal.css 后移除依赖 |
| 3 | 后端仅 1 个空壳测试类（`BlogApiApplicationTests`） | coolpilot-server `src/test` | 升级/重构无回归网，问题只能靠翻日志定位 | 优先覆盖登录鉴权、文章 CRUD、缓存读写 |
| 4 | `upload.file.path` 指向 `/Users/naccl/Desktop/upload/` | application-dev.properties | QQ 头像转存功能在 Windows 下失效 | 改为本机绝对路径或切换 GitHub 渠道 |
| 5 | 图床凭据仅存浏览器 localStorage | coolpilot-admin 图床管理 | 换机/清缓存即丢失；多人无法共用 | 如需多人，改为服务端加密存储 |
| 6 | 仓库内无生产 nginx 配置 | — | 部署链路不可追溯，`/api` 反代需手工搭建 | 补 `deploy/nginx.conf` 纳入版本管理 |
| 7 | 数据库密码、Redis 密码、默认口令明文入库 | application-dev.properties | 凭据泄露风险 | 外置为环境变量或改用 `.env`（不入库） |
| 8 | Tomcat 11 严格校验请求行，未编码中文参数直接 400 | 后端 | 手工调用脚本易踩坑（应用自身不受影响） | 已在 README 说明编码写法 |
| 9 | Jackson 3 默认把日期序列化为 ISO 字符串 | 全局 | 旧格式（时间戳）调用方需适配 | 前端已兼容；如需旧行为加 `write-dates-as-timestamps=true` |
| 10 | 后台所有错误返回 HTTP 200，错误码放 body | coolpilot-server | 不符合 REST 语义，但被前端拦截器依赖 | **不要"顺手规范化"**，会丢中文提示 |

---

## 十一、与上游原始版本的差异

| 项 | 上游 | 当前 |
|---|---|---|
| JDK | 1.8 | 21 |
| Spring Boot | 2.x | 4.1.2 |
| JSON | Jackson 2（`com.fasterxml`） | Jackson 3（`tools.jackson`） |
| JWT | jjwt 0.9.1（依赖 JAXB） | jjwt 0.12.7（无 JAXB 依赖，密钥派生长度要求） |
| Security 配置 | `WebSecurityConfigurerAdapter` | `SecurityFilterChain` Bean |
| 前台形态 | 三栏博客布局 | 文档门户布局（自研 portal 主题） |
| 前台登录 | 博主登录（复用 `/login`） | 访客注册/登录（`/visitor/*`） |
| 后台鉴权 | 允许 visitor 只读 `/admin/**` | 仅 `ROLE_owner` |
| 开发代理 | 无（依赖生产 nginx） | 两前端均补 `devServer.proxy` |
| 首页背景图 | 3 张 1.5MB 图打包进产物 | 已移除，产物减约 4.5MB |
