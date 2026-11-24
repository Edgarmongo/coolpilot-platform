# 变更记录

本文件记录已发布版本的变更。版本号对应 Git tag，镜像由 `release.yml` 在同名 tag 上构建。

**当前线上版本：`v1.3.0`**

---

## v1.3.0（2026-11-24）

**九篇文章的摘要素、问答段与内链：把 SEO 清单落成迁移**

v1.2.3–v1.2.6 解决的是「技术层能不能被正确解析」，这一版解决「内容层能不能被引用」。
`docs/SEO-CONTENT-PLAN.md` 的诊断是结构问题：文章是叙事型，结论藏在中段，
没有问句结构，正文之间互不相链。

- `V29__seo_content_enrich.sql`：9 篇 `description` 重写（原来存的是正文首段
  硬截断，直接进 `meta description`），每篇追加 H2「常见问题」+ 4 组 H3 问答，
  并补至少 3 条关键词锚文本内链；`web-intelligence-lab` 作为簇中心链全部 8 篇
- `pages/blog/[id].vue`：从正文 H2「常见问题」解析出问答，输出 `FAQPage`
  结构化数据（接入已有的 `@graph`）。没有该段就不输出——空 FAQ 是无效结构化数据
- `scripts/indexnow.sh` + `public/<key>.txt`：Google 与 Bing 的 sitemap ping
  都已下线，改用 IndexNow 主动推送 URL（覆盖 Bing / Yandex / Seznam / Naver）

取舍：内容改动写成 Flyway 迁移而不是后台逐篇编辑。后台改完就只存在于线上库，
既不能 review 也不能回滚；写成 SQL 至少能像代码一样被审查，出错重发即可。

## v1.2.6（2026-11-24）

**文章页规范地址与 canonical 对齐**

v1.2.5 把 `/en/**` 的 canonical 指向中文原页，但文章页自己又覆写了 `og:url`，
导致 `/en/blog/x` 的 `og:url` 是英文地址、canonical 却是中文地址，两边打架。

- `pageUrl` 按 canonical 规则计算（去掉 `/en` 前缀），JSON-LD 的 `@id`
  与 `mainEntityOfPage` 都跟着它走
- 删掉页面级 `og:url` 覆写，交给 `app.vue` 的 `canonicalUrl` 统一输出

## v1.2.5（2026-11-24）

**英文版 canonical 指向中文原页，撤掉 hreflang**

v1.2.3 给中英两版加了 hreflang 互指，上线后复核认为不严谨：hreflang 的前提是
「同一内容的不同语言版本」，而本站只有界面翻译、正文没有翻译 ——
`/en/blog/x` 与 `/blog/x` 的正文是同一份中文。给这种页面标 `hreflang="en"`，
等于告诉搜索引擎「这是英文内容」，而它实际是中文。

- canonical：`/en/**` 一律指向去掉 `/en` 的中文地址（`/en` → `/`，
  `/en/blog/x` → `/blog/x`），抽出 `canonicalPath` / `canonicalUrl`
  两个 computed 供 canonical 与 `og:url` 共用
- 移除 hreflang 互指，只保留 `x-default` 指向中文版
- sitemap：撤掉英文地址与 `xhtml:link`，只提交中文页（25 条）。
  canonical 已声明以中文为准，再把英文当独立页提交是自相矛盾

取舍：英文站仍可访问、分享、切换语言，但不参与英文搜索竞争，
与「英文站冻结、不投入内容」的策略一致。将来真翻译了正文，
换回 hreflang 互指即可（v1.2.3 的实现留在 git 历史里）。

---

## v1.2.4（2026-11-24）

**自定义错误页**

修完软 404 后暴露的下一个问题：真 404 会渲染 Nuxt 内置错误页——标题
「404 - Article not found | Nuxt」、正文英文、带框架品牌名。个人站出现框架水印
不专业，也浪费了「访客走错路还能留下来」的机会。

新增 `error.vue`：

- 中英双语按 locale 分支，不读 i18n 词条（错误页可能在 i18n 就绪前渲染）
- 404 与 5xx 分开措辞，5xx 不说「页面不存在」避免误导
- 给出路而不是死胡同：站内搜索 + 首页 + 技术文章 / 开源项目 / 技术服务
- `noindex, follow`：错误页不该被收录，但保留顺链抓取

顺带修掉拼路径的坑：英文前缀单独用变量，直接拿 `/en` 拼 `/archives`
会得到 `/enarchives`。

## v1.2.3（2026-11-24）

**技术 SEO 与 GEO 基建：修软 404、hreflang、结构化数据、llms.txt、RSS**

对线上逐项实测后按优先级修。原状态并不差：TTFB 0.29s、HTTP/2、brotli、
canonical 全站都有、每页 h1 唯一。问题集中在三处硬伤与一块空白。

### 一、软 404（影响最大）

不存在的文章返回 **200**，标题还是「受密码保护」，等于让搜索引擎把所有拼错、
已删除的地址当正常页收录。现在后端 `code=404` 时抛真 404。
判断放在首次加载之后而不是 `fetchBlog` 内部——解锁流程也调它，
在里面抛会把人从密码框弹到 404 页。锁定页（有密码未解锁）补 `noindex`。

### 二、hreflang

`/` 与 `/en/` 结构完全一致却互不标注，会被当重复内容只收一个。
用 `switchLocalePath` 生成（自动处理带参数、带前缀的路由），
`x-default` 指向中文版。

### 三、结构化数据

- 全局：`Organization` + `WebSite`（含 SearchAction 站内搜索）
- 文章页：`BlogPosting`（作者、发布/修改时间、字数、阅读时长、分类、标签）
  + `BreadcrumbList`。字数与时长取后端已有字段，不额外查询。

AI 引擎解析页面时，JSON-LD 里的「谁在何时写了什么」最容易被抽取成事实，
只靠 meta 标签拿不到这层关系。

### 四、分享卡片

新增 `public/og-default.png`（1200×630，**浏览器渲染而非 AI 生图**，
中文字体可控）。文章无首图时兜底，避免微信/社群抓到空白卡片。
补 `og:url`、`og:image`、`og:locale`、`article:modified_time`、`article:tag`，
twitter card 升级为 `summary_large_image`。

### 五、sitemap

移除 `/search`（已 noindex）；每条内容输出中英两个地址并带 `xhtml:link`
hreflang —— 站点地图里的 hreflang 比页面 `<link>` 更权威。
线上结果：50 条 URL（25 × 2 语言）、150 条 xhtml:link。

### 六、llms.txt 与 RSS（GEO）

- `/llms.txt` 按 llmstxt.org 约定输出站名、定位、文章与项目索引、引用说明，
  内容实时从后端拉，新文章自动出现（当前 19 条索引）
- `/rss.xml` 输出最近 20 篇，聚合站与抓取管线靠 feed 发现更新

### 七、URL 规范化

新增 `server/middleware/canonical-url.ts`：`www` → 裸域 301
（只在该站规范域名不带 www 时生效，不按 host 强制跳，避免误伤 IP 访问
与健康检查）；尾斜杠 301 归一。canonical 只是建议，301 才是结论。

### 八、robots 显式放行 AI 爬虫

`GPTBot` / `OAI-SearchBot` / `ChatGPT-User` / `ClaudeBot` / `PerplexityBot` /
`Google-Extended` 单列 `Allow`。不只为表明意图，也为将来若有人加全局
`Disallow` 时不误伤。注意 Cloudflare 的 Bot Fight Mode 会绕过 robots.txt
直接拦，需在 CF 后台确认关闭。

---

## v1.2.1（2026-11-21）

**sitemap 修复 + 开源栏目首批 5 篇分析上线**

### sitemap 只收录最新 5 篇文章（既有缺陷，内容一多才暴露）

`sitemap.xml` 只请求 `/blogs?pageNum=1`，而后端该接口的每页条数是常量 5、
且不认 `pageSize` 参数。发布第 6 篇文章后，最早的一篇立刻从站点地图消失。

改为按 `totalPage` 翻页取全量；后端未返回总页数时用「不满一页」判断末页；
设 60 页上限防止后端异常时无限翻。本地对生产接口验证：6 篇全部收集到。

### 开源栏目首批内容

研究仓里 5 篇 Crawl4AI 稿件发布进栏目（此前栏目唯一关联的是一篇 SSH 加固实录，
属运维实践而非项目分析）：

| 文章 | 分类 | 关系 |
| --- | --- | --- |
| Crawl4AI 商业价值分析 | 开源项目商业观察 | business |
| 从跑起来到能改造：研究路线 | 开源技术研究 | research |
| Crawl4AI 操作手册（v0.9.5 实测） | 开源技术实践 | practice |
| Crawl4AI + Hermes Agent：采集与执行的组合 | 开源项目商业观察 | business |
| 用 24 篇文章研究透 Crawl4AI 与 Hermes | 开源技术研究 | research |

正文以 **Markdown 原文**入库，由后端 commonmark 渲染（含 GFM table 扩展），
不做 HTML 预转换。同时解除 SSH 加固实录与 Crawl4AI 项目的关联（文章仍可读）。

> 纯内容改动，无代码逻辑变更；版本号打出是因为 sitemap 需要重新构建镜像。

---

## v1.2.0（2026-11-21）

**后台经营管理驾驶舱 + 首页平台化改版**

本次是两个独立的界面层重构，共用同一批后端改动。数据库结构未变，**没有新增 Flyway 迁移**。

### 一、后台仪表盘：销售统计页 → 经营驾驶舱

- 新增后端模块 `modules/dashboard`，8 个接口：
  `overview` / `trends` / `todos` / `content` / `users` / `products` / `services` / `health`
  统一 `startTime` / `endTime` / `granularity` / `comparePeriod`
- `DashboardPeriod` 负责区间与时间桶：左闭右开；默认跨度跟随粒度，避免只传
  `granularity=HOUR` 时撞上「小时最多 7 天」直接 400
- 趋势 SQL 一律按日聚合，周/月由服务层二次合并，一次查询服务三种粒度
- **不可算的指标返回 `null` + 原因，不用 0 冒充**：独立访客、活跃用户目前算不出
  （代理头未透传，访客标识坍缩），页面显示「—」而不是「0」

**交易口径变更（会影响历史数字，需知悉）**

| 项目 | 旧 | 新 |
|---|---|---|
| `sumPaidAmount` 时间字段 | `create_time`（下单） | `paid_time`（支付确认） |
| 区间边界 | `<=`（闭区间） | `<`（左闭右开） |

成交按支付确认时间归属，退款按退款完成时间归属 —— 两者都是资金实际发生时间。
副作用：未支付订单不再计入成交额，跨期订单会从下单日移到到账日，**发版后历史数字会下降**。
旧方法保留为 `sumPaidAmountByCreateTime`（`@Deprecated`），需要对比时可用。
另：`onPaid()` 确认后立即 `markCompleted`，订单几乎不停留在 `PAID`，
凡只筛 `status='PAID'` 的查询都会返回 0，必须用 `PAID_STATUSES` 集合。

**为待办跳转补的能力**：项目列表支持 `overdue` 过滤与 `overdueCount`；
退款、权益、需求、项目四个列表页补 `route.query` 初始化 ——
原来页面存在但不读参数，从仪表盘点「待处理退款」进去看到的是全部数据。

**后台页面**：七区重写、时间预设、分区容错（单模块失败不影响其他区块）、
空状态、口径说明；手写 SVG 折线/柱状图（项目原本没有图表库，未引入 ECharts）。

### 二、前台首页：个人博客门户 → 平台门户

- 首页只展示平台提供什么，个人履历归 `/about`
- **核心是空态降级，而不是把空区块 `v-if` 掉**：原实现三个区块都是
  `v-if="xxx.length"`，线上数据为 0 时整块消失，首页退化成 Hero + 1 篇文章。
  现在文章不足 3 篇 → 右侧变「写作计划」；无主推项目 → 显示六步研究方法；
  0 商品 → 3 张「筹备中 + 预约通知」；0 服务 → 5 条服务目录
- 服务区 CTA 卡改为始终显示。原实现放在 `v-if="!services.length"` 里，
  一上架服务商品，咨询入口就会消失
- 移除首页的「技术专题」独立区块（与精选内容定位重复，导航也已去掉）
- 页脚改为四栏：品牌叙述 + 内容 / 商业 / 关于 + 版权行。
  三组入口写死在 layout、不读 `nav_menu` —— 页脚是兜底导航，
  不能跟着菜单配置一起被隐藏

### 三、导航调整（数据改动，需要执行 SQL）

`coolpilot-server/sql/2026-11-21-nav-v2.sql`：收敛为 6 项
（首页 / 技术文章 / 开源项目 / 知识产品 / 技术服务 / 关于我）。
`/topic`、`/projects` 隐藏但页面保留，`/about` 显示并改名「关于我」，
`/open-source` 改名「开源项目」。回滚脚本同名 `-rollback.sql`。

> **执行后必须清 Redis 的 `"navMenuList:zh-CN"` / `"navMenuList:en"`**，
> 否则导航接口仍返回旧菜单（本地验证时踩过）。

### 四、测试

- `AdminDashboardServiceTest` 9 项：口径自洽（`netAmount == sales - refund`）、
  时间桶严格递增、三种粒度都可出数、8 类待办与跳转路径、不可算指标必须为 `null`
- `ProjectQueryTest` 2 项：守住 `ProjectMapper.COLUMNS` 的改动 ——
  它同时被带和不带 `<script>` 的查询复用，里面出现 `&lt;` 会让非 script 查询输出非法 SQL，
  写错会导致整个项目管理页挂掉
- CI 用 `-DskipTests package`（只编译不执行），这两组测试不会在流水线里跑

---

## v1.1.3（2026-11-21）

**代码来源整改（第三批）：util 整体重写（16 个文件 / 1,221 行）**

- 重写 8 个文件：`JacksonUtils` `StringUtils` `HashUtils` `UserAgentUtils`
  `SpringContextUtils` `ValidatorUtils` `UploadUtils` `LocalChannel`
- 相似度平均 0.96 → **0.49**（`JacksonUtils` 0.15、`StringUtils` 0.30 最低）
- **无功能变化**：方法签名、返回默认值、异常类型与文案全部保持原样；
  `getMurmurHash32` 的取模映射刻意保留 —— 改它会让已入库的评论头像全部错位
- 顺带修两处：抓取外链图片时 `ContentType` 为空会 NPE；畸形 User-Agent 会让日志落库失败
- 新增 15 个单测（String 6 / Hash 4 / Upload 3 / LocalChannel 2），不依赖 Spring 容器与网络
- 本地冒烟：启动、登录、搜索通配符拦截、异常堆栈落库、参数 JSON 序列化、UA 解析全部正常

**同批第二部分：评论工具与两个通知通道**

- 重写 `CommentUtils`（0.95 → 0.13）、`TelegramChannel`（0.97 → 0.35）、`MailChannel`（0.96 → 0.45）
- **修复并发串号**：`CommentPageEnum` 是枚举单例却带 `setTitle/setPath`，原实现把每篇文章的标题写进枚举，
  并发通知时邮件/Telegram 可能显示另一篇文章的标题。现拆为 `getPageTitle` / `getPagePath` 按评论各自计算
- 若干空值保护：父评论 `adminComment/notice` 为 null 不再 NPE；`judgeCommentState(null)` 返回 NOT_FOUND；
  博主账号缺失时抛明确异常
- 删除一个从未被调用的私有方法（QQ 头像上传），`CommentUtils` 不再依赖 Redis 与 QQInfoUtils。
  **注意：这意味着「昵称填 QQ 号自动换 QQ 头像」实际一直是关闭的**，如需启用要单独接回
- 新增 6 个单测（头像确定性、网址校验正反例、页面标题路径、未知 page 回落）
- 本地冒烟：评论状态判定、访客发评论（头像/非法网址置空/审核状态/IP 全部正确）、
  邮件模板渲染成功（本地无 SMTP，仅在发送时认证失败）、测试数据已清理

**同批第三部分：定时任务与剩余工具**

- 重写 `ScheduleUtils`（0.96 → 0.41）、`ScheduleRunnable`（0.96 → 0.64）、
  `GithubChannel`（0.96 → 0.39）、`UpyunChannel`（0.96 → 0.51）、`QQInfoUtils`（0.96 → 0.33）
- `ScheduleUtils` 的 7 段 try/catch 收敛为 `execute(失败说明, 操作)`；
  `resumeJob` 的失败文案原来错写成「暂停定时任务失败」，已更正
- `UpyunChannel` 的 `Response` 改为 try-with-resources（原来不关，连接会滞留在连接池）
- `GithubChannel` 补 token 未配置时的明确报错
- 新增 21 个单测（调度器增删改暂停恢复 7、反射调用 7、GitHub 2、又拍云 2、QQ 昵称 3）
- 本地冒烟：Quartz 初始化正常；立即执行落库 `status=true times=53ms`；
  暂停后 `nextFireTime=null`、恢复后重新排程；每 10 分钟的任务按计划执行

**发布结果（2026-11-21 01:00）**

- tag `v1.1.3` → CI 约 3 分钟出齐 4 个镜像
- 部署：`.env` 备份为 `.env.bak-v1.1.2-*`，`TAG=v1.1.3` → pull → up
- 迁移：`Schema 'coolpilot' is up to date. No migration necessary.`（本版无 DDL）
- 容器：api / admin / web / gateway 全部 healthy；**mysql（2 周）与 redis（27 小时）未重启**
- 本版为纯内部重构 + 若干缺陷修复，**功能与界面均无变化**
- 与 NBlog 逐字一致的文件：101 个 / 5,111 行（31.3%）→ 85 个 / 3,890 行（23.4%）

**生产验证**

| 项 | 结果 |
|---|---|
| 页面 | `/` `/archives` `/open-source` `/blog/...` `/knowledge` 全部 200，0.25–0.68s |
| 通配符搜索 | `query=%` 被 `hasSpecialChar` 拦下，返回「参数错误」（重写后的 StringUtils 生效） |
| 评论接口 | `/api/comments?page=0&blogId=11` 正常返回空结构 |
| 收款渠道 | `MANUAL_WECHAT / MANUAL_ALIPAY / MANUAL_PAYPAL` 顺序不变 |
| 定时任务 | 3 个任务 cron 与状态正确；手动触发 jobId=3 落库 `17:03:34Z status=true 5ms`，**晚于容器启动 16:59:58Z**，确认跑的是新代码 |
| 后台分页 | 权益、附件、任务日志分页均 200 |

---

## v1.1.2（2026-11-21）

**代码来源整改（第二批）：交易与权益服务**

- `PaymentService` 相似度 0.96 → 0.49，`EntitlementService` 0.97 → 0.71（后者剩余部分为实体字段赋值序列）
- **无功能变化**：接口路径、参数、返回结构、状态流转条件、异常文案全部保持原样
- 修复两处状态错位（详见 [TRADE-STATE-MACHINE.md](TRADE-STATE-MACHINE.md) §七）：
  - 超时关单（`closeExpired` 只关订单不关支付单）后，用户仍可提交转账凭证，
    导致「支付单等确认 / 订单已关闭 / 后台收到待确认通知」。现在订单侧推进失败即回滚
  - 转账时间由宽松解析改为严格解析，`2026-02-31` 不再被悄悄修正成 3 月 3 日
- `MANUAL_CHANNELS` 由可变 Map 改为不可变视图（顺序不变，前端下拉不受影响）
- 本地完整资金链路回归 24 项 + 守卫 4 项 + 单测 17 个全部通过；测试数据零残留

**发布结果（2026-11-21 00:17）**

- tag `v1.1.2` → CI 约 3 分钟出齐 4 个镜像
- 部署：`.env` 备份为 `.env.bak-v1.1.1-*`，`TAG=v1.1.2` → pull → up
- 迁移：`Schema 'coolpilot' is up to date. No migration necessary.`（本版无 DDL）
- 容器：api / admin / web / gateway 全部 healthy；**mysql（2 周）与 redis（27 小时）未重启**
- 页面：`/` `/archives` `/open-source` `/blog/...` 全部 200，0.30–0.75s
- 接口：`/payment/channels` 三个渠道顺序不变；后台支付单 / 权益 / 权益日志 / 订单分页与销售统计均 200
- 生产库当前 0 笔订单、0 条权益，本次未产生任何测试数据

> 本版无数据库迁移，回滚只需把 `TAG` 改回 `v1.1.1` 再 pull + up -d。

---

## v1.1.1（2026-11-20）

**代码来源整改（第一批）：19 个文件重写为独立实现**

目标是把服务端里仍属上游原文的代码替换掉。**本版无功能变化、无数据库迁移**，
所有接口路径、参数、返回结构与文案保持不变。

- 逐字一致的上游代码从 159 个文件 / 9,946 行降到 **143 个 / 7,802 行**（占后端 31.3% → 24.6%）
- 覆盖：2 个工具类、6 个 service、3 个 controller、2 个切面、2 个社区服务、1 个订单服务
- 顺带修复：
  - 文章访问日志在返回结构异常时会 `ClassCastException`，把正常 404 变成 500
  - 异常日志切面在非 Web 线程下 NPE，会把原始异常盖掉
  - 删除分类/标签时只清列表缓存、不清首页缓存，导致首页残留已删项
  - 收藏/点赞的取消操作未校验 `targetId`，传 0 或负数会静默执行
- 为降风险保留原样的部分（有意）：`MomentServiceImpl`(0.66)、`MessageService`(0.66)、
  `OrderService`(0.70) —— 这三个类的表达空间被契约锁死，强行重构只会引入风险

**验证**：本地逐项实测（起服务 + 真实请求），覆盖文章保存、评论树与级联、访问与异常日志、
订单下单/幂等/取消、收藏点赞幂等；新增 11 个单元测试（评论树 4 + IP 解析 7）。

**部署注意**：本版无 Flyway 迁移、未改动导航与站点配置，**不需要清缓存**。

---

## v1.1.0（2026-11-20）

**新栏目：开源项目商业观察（`/open-source`）**

用实测证据判断「这个开源项目能不能变成生意」，与作品集（`/projects`）完全独立。

- **数据**：V28 新建 `oss_project` / `oss_project_article` / `oss_project_tag` 三张表，
  新增 3 个栏目分类，导航第 7 项 `/open-source`（sort=25，落在技术文章与技术专题之间）。
  不改任何现有表结构。
- **接口**：`/open-source/**` 9 个公开接口（项目列表/档案/关联文章/文章流/领域/标签/精选/搜索/文章反查项目），
  白名单一条通配放行；后台 `/admin/oss/**` 6 个接口。
  公开接口只认 `publish_status='published'`，参数无法绕过。
- **前台**：栏目首页（首屏 + 领域 + 精选 + 项目研究 + 研究方法）、全部项目列表、
  项目档案（评估卡七维 + 证据等级 + 关联分析 + 相关项目）、领域页、标签页、栏目搜索；
  文章页新增「所属项目」卡片，与档案双向内链。
- **后台**：内容管理下新增「开源观察」，支持双状态机、评估卡逐项填写、标签与关联文章全量提交。
- **SEO**：sitemap 收录栏目、已发布项目与有内容的领域页；项目页输出 BreadcrumbList；
  draft / archived 项目前台 404 且不进 sitemap。

**部署注意**

- 导航走 Redis 缓存，Flyway 插入的新菜单不会自动生效。
  部署后需执行一次：`redis-cli DEL '"navMenuList:zh-CN"' '"navMenuList:en"'`
  （键名带双引号，是 key 序列化的结果，不能省）。
- 本版含 Flyway V28，发布前先 `mysqldump` 备库；迁移不可回退，
  应用退回 v1.0.4 时三张 `oss_*` 表会保留（旧代码不读，无影响）。

**发布结果（2026-11-20）**

- 迁移：`Successfully applied 1 migration ... now at version v28`（0.109s）
- 部署前备份：全库 8.3MB + `.env.bak-v1.0.4-*`
- 导航缓存已清，第 7 项 `/open-source` 生效
- 生产录入 Crawl4AI 档案（`oss_project.id=1`），关联分析文章 `blog_id=11`
- sitemap 从 12 条增至 16 条，新增栏目、项目档案与有内容的领域页
- 六个容器 healthy，mysql（13 天）与 redis 未重启

---

## v1.0.4（2026-11-20）

**修复**

- 正文渲染对 HTML 与 Markdown 双格式都安全，不再二次解析。
  `markdownToHtmlExtensions()` 在解析前把块级 HTML 元素（`pre` / `table` / `div` …）
  整体抽成占位注释，交给 commonmark 只处理真正的 Markdown，渲染完再原样放回。
  纯 Markdown 行为不变，纯 HTML 等价透传，混排两边都正确。
- 触发场景：正文里存在 `<pre>` + 代码内空行 + `# 1. xxx` 形式的行时，
  commonmark 会在空行处截断 HTML 块，代码里的井号行被当成标题渲染，
  页面表现为「深色代码块里混着大号标题」。
- 影响面：`BlogServiceImpl`（详情、列表摘要）、`AboutServiceImpl`、`PortfolioService`、
  `MomentServiceImpl`、`FriendServiceImpl` 共 5 类调用点一并受益。
- 新增 `MarkdownUtilsTest`（6 个用例），覆盖 HTML 保真、纯 Markdown、混排、
  幂等、嵌套标签配对、空输入；已验证去掉修复后测试会失败。

---

## v1.0.3（2026-11-20）

**前端**

- `coolpilot-web` 全站按新视觉稿重构：白底、1px 青边、12px 圆角、1200 容器。
  `assets/css/main.css` 整体重写为站点基础层，但**保留全部旧类名**，
  未逐页重写的页面自动继承新视觉，不会出现半新半旧。
- 头部由深绿满色条改为白色吸顶双行（胶囊搜索、下划线选中态），页脚改为双行结构。
- 新增 `AppCover`：按 `id + 标题` 生成确定性青线封面，替代无图时的空灰块。
- 首页改为「首屏 + 头条 + 专题 + 知识产品 + 技术服务 + 关注」结构，
  文章不足三篇时第一篇走横版头条，不留大片空白；无数据的区块自动隐藏。
- 文章列表新增列表/时间线双视图（`/archives?view=timeline`）；
  文章详情新增目录侧栏（渲染时给正文 h2/h3 注入锚点，不改库、不改 CMS 契约）。

**内容修复**

- 文章《44,256 次失败登录之后》的正文在库里存的是已渲染的 HTML，
  而详情接口读取时会无条件再跑一遍 commonmark 转换：`<pre>` 内的空行把 HTML 块截断，
  代码块里的 `# 2. 安全组件状态` 被当成标题渲染，页面上出现「深色块里混着大号标题」。
  已把正文转回 Markdown 存库（CMS 契约本就要求 Markdown），
  转换后逐项比对：23 个代码块内容完全一致，正文 3097 字完全一致。
  渲染层的根本修复见 v1.0.4。

---

## v1.0.2（2026-11-19）

**修复**

- 密码为 `NULL` 的文章被判成加密，正文对访客不可见。
  公开端约定用「空串」表示文章未设密码，但判断写成了 `!"".equals(password)`，
  当 `password` 为 `NULL` 时结果仍为 `true`，详情页直接返回「此文章受密码保护」。
  改用 `StringUtils.isEmpty()` 判空，共 5 处：详情页 1 处、列表脱敏 4 处
  （新文章 / 文章列表 / 归档 / 随机推荐）。
- 保存与更新文章时把空密码归一化为空串（`BlogServiceImpl.normalizePassword`）。
  此前是否写入 `NULL` 取决于前端有没有传值，现在写库前统一处理；
  历史 `NULL` 数据在该文章下次被编辑保存时自动修正。

**部署**

- 修复最初以热更方式上线（基于 `v1.0.1` 镜像替换 `app.jar` 重建 `api` 容器），
  随后按 `release.yml` 正式发布 `v1.0.2` 镜像，线上改用 CI 产出的镜像，不再保留手工拼装层。
- 回滚：`sed -i 's/^TAG=.*/TAG=v1.0.1/' .env` 后重新 `pull` + `up -d`。

---

## v1.0.1（2026-11-07）

品牌名按语言区分：中文 **酷天AI**，英文 **CoolSkyAI**。
数据侧早已可配（`site_setting.blogName`），但有三处代码把英文名写死了，导致中文模式改不过来。

**修复**

- `pages/archives.vue` 硬编码 `useHead({title: '归档 - CoolSkyAI'})`
  → 英文站标题显示中文「归档」；品牌名一改还会拼出双后缀。改用词条 `archives.headTitle`（归档 / Archive）
- `app.vue` 的 `titleTemplate` 用固定正则 `/ - CoolSkyAI$/` 剥后缀，品牌名换成中文后剥不掉
  → 改为按当前品牌名匹配，并兼容历史写死的值
- 后台三处写死 `CoolSkyAI`：`index.html` 静态标题、侧边栏 logo、登录页大标题
  → 新增 `layout.brand` 词条（酷天AI / CoolSkyAI）
- 中文词条 `site.description` 里的「CoolSkyAI 技术平台」→「酷天AI 技术平台」（`<meta description>` 来源）

**部署**

- `docker-compose.prod.yml` 的 web 服务补 `NUXT_PUBLIC_SITE_URL: ${SITE_URL}`
  → 漏了会导致 `canonical` / `sitemap` / `robots` 全部指向 `http://localhost:8081`
- `.env.example` 的 `SERVER_NAME` 加引号 → `backup.sh` 会 `source` 它，
  多个域名不加引号时 bash 把第二个域名当命令执行，备份静默失败

---

## v1.0.0（2026-11-06）

首个正式版。从上游 Vue 2 基线（`v0.1.0-baseline`）演进而来，累计 119 个提交。

### 内容与写作

- 文章正文支持**粘贴/拖拽截图**自动上传并插入 Markdown（占位标记 + 上传中禁止保存）
- 抽出 `useImagePaste`，动态、关于页、商品详情同样可用
- 修 Markdown 图片全部不显示：渲染层曾删掉 `img` 的 `src` 只留 `data-src`，前台又没有懒加载
- 修动态列表把 HTML 当纯文本输出（`{{ }}` → `v-html`）

### 站点配置化

- 顶部导航改成后台可配（`nav_menu`，含排序、显隐、站内/外链校验、中英双列）
- 品牌名、页脚主副文案、备案号纳入站点设置（`/brand` 投影 5 字段 + Redis 缓存）
- 修站点设置只放行 type 1/2/3 导致「友链说明」「支付设置」两组不显示
- 图片类配置支持缩略图预览与上传；收银台补上收款码与 PayPal 按钮

### 中英双语

- 前台 `/` 中文、`/en/` 英文（`prefix_except_default`，不做浏览器自动跳转）
- 覆盖全部页面：首页、归档、专题、动态、友链、关于、搜索、知识付费、文章详情、评论、
  登录注册、分享页、问答页、服务列表、收银台、需求提交/详情、项目详情、个人中心
- 导航、站点设置、关于页增加英文字段（V18/V19），空值回落中文
- 后台 620 条短句接入双语：中文原文即 key + 英文映射表，缺词回落中文

### AI 服务

- 模型配置在线修改（密钥只回掩码，AES-GCM 加密存储），改库后 10 秒内自动生效 + 手动 reload 接口
- 用量统计：按场景记录 token 数与耗时
- 检索链路：混合检索、追问改写、重排，决策见 ADR-0001/0002/0003

### 商品与交易

- 服务类商品新增成交模式 `deal_mode`（DIRECT / QUOTE / BOTH），空值按商品属性推导
- 存量「服务 + 固定价」迁移为 BOTH，避免升级后购买按钮消失

### 引流与展示（本期新增）

- **知识星球**：`/planet` 页 + 站点设置 type=6 六项配置 + 首页卡片 + 页脚入口，站外成交不接支付
- **社交媒体**：`social_account` 表 + 存量迁移 + 后台管理 + `/follow` 页 + 首页图标行
- **项目展示**：`portfolio` 表 + `/projects` 列表与详情（详情用 Markdown，不建第二张表）
- 导航收敛为六项：首页 / 技术文章 / 技术专题 / 项目 / 知识产品 / 技术服务

### 运维与安全

- 附件管理：分页、统计、引用检查（正文 + 封面 + 头像 + 二维码 + 站点设置）、删除
- 找回密码（邮箱验证码）、改邮箱验证、管理员重置访客密码
- 定时任务管理页（System → 定时任务）
- Quartz 并发安全：`@DisallowConcurrentExecution`；修掉 `ScheduleJob` 每执行一次泄漏一条线程的问题
- 移除上传白名单里的 `svg`（存储型 XSS：SVG 可内嵌脚本且与站点同源）
- SEO：canonical、OG 卡片、文章独立 description、sitemap 补齐新页面

### 部署

- 四个生产镜像推 GHCR（server / admin / web / gateway），服务器无需 JDK / Node
- `docker-compose.prod.yml` 只暴露 gateway 的 80/443，其余走内网
- gateway 首启自签名证书兜底 + Let's Encrypt 续期脚本
- 备份：`mysqldump` + 上传目录打包，systemd timer 每天 03:30，保留 14 天
- Flyway 累计 27 个迁移、59 张业务表

---

## v0.1.0-baseline（2026-11-05）

上游导入基线：Vue 2 前台 + Vue 3 后台 + Spring Boot 4.1 后端。
保留此 tag 用于对照前端迁移（见 `docs/FRONTEND-MIGRATION.md`），不再维护。
