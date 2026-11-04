# WB-001 工程基线审计报告

> 审计日期：2026-11-04 ｜ 方式：只读，未修改任何代码、配置与数据库
> 覆盖需求：WB-001 的 01–07 全部小节
> 说明：按原任务卡要求本应拆成 7 个文件，实测后合并为单文件更利于交叉引用（证据路径与结论在同一处）。目录结构对照见 `README.md`。

---

## 01 项目概览

| 项 | 内容 |
|---|---|
| 项目 | NBlog 二次开发（个人技术知识与服务平台） |
| 形态 | 前后端分离，三端同仓 |
| 后端 | `coolpilot-server`，Spring Boot 4.1.2，端口 8091 |
| 前台 | `coolpilot-web`，Vue 2.6.11，端口 8081 |
| 后台 | `coolpilot-admin`，Vue 2.6.11，端口 8079（`publicPath=/cms/`） |
| 数据库 | MySQL 8.0.40，库 `nblog`，19 张表 |
| 缓存 | Redis 5.0.14.1，端口 6379 |
| 版本管理 | Git，仓库根 `NBlog/` |

**规模**

| 端 | 度量 |
|---|---|
| coolpilot-server | 211 个 `.java`、18 个 mapper XML、81 个 HTTP 接口 |
| coolpilot-admin | 38 个 `.vue`（25 个业务页）、19 个 api 模块 |
| coolpilot-web | 28 个 `.vue`（10 个页面、17 个组件）、11 个 api 模块 |

---

## 02 后端结构

### 2.1 包结构（实测文件数）

```
com.coolskyai
├── BlogApiApplication            1
├── controller                    12   前台
├── controller/admin              16   后台
├── service                       19
├── service/impl                  19
├── mapper                        18
├── entity                        18
├── model/dto                     12
├── model/vo                      20
├── config                        8
├── config/properties             6
├── aspect                        3
├── annotation                    3
├── constant                      6
├── enums                         3
├── exception                     3
├── handler                       1
├── interceptor                    1
├── task                          2
└── util                          40
    ├── comment (1) + channel (4)
    ├── markdown (1) + cover (2+4) + heimu (2+4)
    ├── quartz (3) / telegram (2) / upload (1+5) / common (2)
```

合计 211，与 `find | wc -l` 一致。

### 2.2 控制器清单

**前台（12）**
`About` `Archive` `Blog` `Category` `Comment` `Friend` `Index` `Login` `Moment` `Tag` `TelegramBot` `VisitorAuth`

**后台（16）**
`AboutAdmin` `AccountAdmin` `BlogAdmin` `CategoryAdmin` `CommentAdmin` `DashboardAdmin` `ExceptionLog` `FriendAdmin` `LoginLog` `MomentAdmin` `OperationLog` `ScheduleJob` `SiteSettingAdmin` `TagAdmin` `VisitLog` `VisitorAdmin`

### 2.3 分层现状

水平分层（按技术角色分包），非按业务域。**这决定了新业务不能沿用此结构**，详见 `ED-02`。

---

## 03 前端结构

### 3.1 coolpilot-web（前台）

```
src/
├── api/          11 模块：about archives blog category comment friend home index moment tag visitor
├── assets/css/   portal.css portal-content.css base.css typo.css badge.css
├── components/   blog(3) comment(4) index(6) sidebar(5)
├── plugins/      axios.js + 3 个 dto mapper（aru/paopao/tv）
├── router/       10 条路由
├── store/        actions getters mutations types state index
└── views/        10 个页面
```

路由：`/login` `/` `/home` `/archives` `/blog/:id` `/tag/:name` `/category/:name` `/moments` `/friends` `/about`

**技术特征**：Semantic UI CSS 2.4.1 + Element UI 2.13.2 + 自研 `portal.css` 三套样式共存；`Header.vue` 为弃用残留。

### 3.2 coolpilot-admin（后台）

```
src/
├── api/          19 模块
├── components/   Breadcrumb Hamburger SvgIcon
├── layout/       index + Navbar Sidebar AppMain
├── router/       静态路由，侧栏由 meta 生成
├── store/        app settings
├── util/         request.js validate uuid task-queue
└── views/        25 页面
```

菜单 7 组：Dashboard、博客管理、页面管理、图床管理、系统管理、日志管理、数据统计。

### 3.3 请求封装

| 端 | baseURL | 拦截逻辑 |
|---|---|---|
| view | `/api/` | 注入 identification；响应取 `res.data` |
| cms | `/api/admin/` | 注入 `Authorization: token`；`res.code!==200` 弹错并 reject |

---

## 04 API 清单（81 个）

### 4.1 前台（19）

| 方法 | 路径 |
|---|---|
| GET | `/site` `/blogs` `/blog` `/searchBlog` `/category` `/tag` `/archives` `/about` `/friends` `/moments` `/comments` |
| POST | `/comment` `/friend` `/checkBlogPassword` `/moment/like/{id}` `/login` `/visitor/register` `/visitor/login` |
| POST | `/tg/{token}` |

> **纠错**：`POST /friend` 不是"申请友链"，实际是 `FriendController.addViews(@RequestParam nickname)`，用于**友链浏览量 +1**。此前架构文档描述有误，以此为准。

### 4.2 后台（62）

| 模块 | 接口数 | 路径族 |
|---|---|---|
| 账户 | 1 | `POST /admin/account` |
| 文章 | 9 | `/admin/blog(s)` `/admin/blog/top` `/admin/blog/recommend` `/admin/blog/{id}/visibility` `/admin/categoryAndTag` |
| 分类 | 4 | `/admin/category` `/admin/categories` |
| 标签 | 4 | `/admin/tag` `/admin/tags` |
| 评论 | 6 | `/admin/comment(s)` `/admin/comment/published` `/admin/comment/notice` `/admin/blogIdAndTitle` |
| 动态 | 6 | `/admin/moment(s)` `/admin/moment/published` |
| 友链 | 8 | `/admin/friend(s)` `/admin/friendInfo*` `/admin/friend/published` |
| 页面 | 5 | `/admin/about` `/admin/siteSettings` `/admin/webTitleSuffix` |
| 任务 | 8 | `/admin/job(s)` `/admin/job/run` `/admin/job/status` `/admin/job/log(s)` |
| 日志 | 8 | `/admin/{login,operation,exception,visit}Log(s)` |
| 统计 | 3 | `/admin/dashboard` `/admin/visitor(s)` |

> `POST /admin/login` 不计入 81 —— 它由 `JwtLoginFilter` 过滤器处理，不是 Controller。

---

## 05 依赖

### 5.1 后端（28 个直接依赖）

| 用途 | 依赖 | 版本 |
|---|---|---|
| 父 POM | spring-boot-starter-parent | 4.1.2 |
| 编译 | Java | 21（`maven.compiler.release=21`） |
| Web/Security/AOP | starter-web / security / **aspectj** | BOM |
| 持久层 | mybatis-spring-boot-starter / pagehelper | 4.1.0 / 4.1.1 |
| 驱动 | mysql-connector-j | BOM，runtime |
| JWT | jjwt-api/impl/jackson | 0.12.7 |
| Markdown | commonmark + 4 ext | 0.18.1 |
| 缓存/邮件/模板/任务 | redis / mail / thymeleaf / quartz | BOM |
| IP/UA | ip2region / yauaa | 2.6.5 / 7.11.0 |
| 存储 | upyun java-sdk | 4.2.3 |
| 其他 | lombok 1.18.42 / spring-retry 2.0.13 / hutool-crypto 5.8.11 | — |

**注意**：`spring-retry` 在 Boot 4 的 BOM 中已移除，必须显式写版本；`starter-aop` 已更名为 `starter-aspectj`。

### 5.2 前端

| coolpilot-admin | 版本 | coolpilot-web | 版本 |
|---|---|---|---|
| vue | 2.6.11 | vue | 2.6.11 |
| element-ui | 2.13.2 | element-ui | 2.13.2 |
| vue-router | 3.3.4 | vue-router | 3.3.4 |
| vuex | 3.5.1 | vuex | 3.5.1 |
| axios | 0.24.0 | axios | 0.24.0 |
| mavon-editor | 2.9.1 | semantic-ui-css | 2.4.1 |
| echarts | 4.9.0 | vue-lazyload | 1.3.5 |
| cos-js-sdk-v5 | ^1.4.10 | sanitize-html | 2.3.3 |
| — | — | v-viewer | 1.5.1 |
| @vue/cli-service | 4.5.0 | @vue/cli-service | 4.4.0 |

### 5.3 迁移阻塞项（Vue 3）

| 依赖 | 状态 | 影响 |
|---|---|---|
| `element-ui` 2.13 | 无 Vue 3 版本 | 必须换 Element Plus，**全部表单/表格组件重写** |
| `semantic-ui-css` | 纯 CSS，可保留 | 但与 portal.css 冲突 |
| `mavon-editor` 2.9 | 无官方 Vue 3 版 | 需换 md-editor-v3 或自研 |
| `v-viewer` 1.5 | 有 Vue 3 版（next） | 需改注册方式 |
| `vue-lazyload` | Vue 3 版为 beta | 可用 `v-lazy` 替代方案 |
| `echarts` 4.9 | API 兼容 | 建议升 5.x |
| `@vue/cli-service` 4.x | webpack 4 | 换 Vite，`require()` 全改 ESM |

> `coolpilot-admin` 用了 4 处 `require('@/assets/...')`（`settings.js` 已改），迁移时注意 CommonJS 残留。

---

## 06 部署审计

| 项 | 现状 | 结论 |
|---|---|---|
| Dockerfile | 不存在 | ❌ 缺 |
| nginx 配置 | 不存在（生产靠外部配置，未入库） | ❌ 缺，`/api` 反代不可追溯 |
| CI/CD | 不存在 | ❌ 缺 |
| 部署脚本 | `deploy.sh`（2.6KB） | ⚠️ 存在但未验证 |
| 环境变量 | 全部硬编码在 `application-dev.properties` | ❌ 含明文密码 |
| 健康检查 | 无 actuator 端点 | ❌ 缺（`management.server.port` 曾用于规避端口冲突） |
| 日志 | `logback-spring.xml`，按天滚动 10MB | ✅ 可用 |

**当前启动方式（实测可用）**

```bash
# 依赖
net start MySQL80
redis-server.exe --requirepass *** --port 6379
# 后端（必须禁用 devtools，否则随机重启端口会与宿主进程冲突）
java -jar coolpilot-server/target/coolpilot-server-0.0.1.jar --server.port=8091 --spring.devtools.restart.enabled=false
# 前端
cd coolpilot-admin && npx vue-cli-service serve --port 8079
cd coolpilot-web && npx vue-cli-service serve --port 8081
```

---

## 07 已知问题

| # | 问题 | 证据 | 等级 |
|---|---|---|---|
| 1 | 前台访客 token 签发后无人消费 | `JwtFilter:34-37` 非 `/admin` 直接 return | **P0** |
| 2 | `anyRequest().permitAll()` 使新增用户接口默认匿名可调 | `SecurityConfig:80` | **P0** |
| 3 | 有效单测为 0 | `src/test` 仅 1 个空壳 `BlogApiApplicationTests` | **P0** |
| 4 | 上传路径指向 `/Users/naccl/Desktop/upload/`，本机不存在 | `application-dev.properties:79` | P1 |
| 5 | `/image/**` 目录不存在时返回 `code:500 异常错误` 而非 404 | 实测 `GET /image/probe.jpg` | P2 |
| 6 | 参数校验失败返回 `code:500` 而非 400 | 实测 `POST /comment {}` | P1 |
| 7 | 数据库/Redis 密码、JWT 密钥明文入库 | `application-dev.properties` | P1 |
| 8 | 无 Dockerfile / nginx / CI | 目录扫描 | P1 |
| 9 | `Header.vue` 弃用残留，仍保留 3 张背景图引用痕迹 | `components/index/Header.vue` | P3 |
| 10 | 前台加载 Semantic UI 约 530KB，实际少量使用 | `main.js` | P2 |
| 11 | 架构文档曾把 `POST /friend` 记为"申请友链" | 本次审计纠错 | 已修正 |
| 12 | 图床凭据存浏览器 localStorage | `views/pictureHosting/Setting.vue` | P2 |

---

## 审计结论

1. **工程基线清晰，可安全演进**：三端结构稳定，后端分层规范，无隐藏的第四端或脚本依赖。
2. **不能直接进入 Vue 3 迁移**：`element-ui` 与 `mavon-editor` 无 Vue 3 版本，等于重写全部 UI 交互，必须先做迁移评估（见 `05-dependencies` §5.3）。
3. **存在 2 个 P0 问题必须先修**：前台身份链路断裂、测试为零。这两项不解决，后续任何"用户级功能"和"交易功能"都建立在流沙上。
4. **数据库并非"只有架构说明"**：本次审计已获取全部 19 张表的真实字段、索引与行数，见 `02-database-audit.md`；DDL 已在临时库实测通过。
