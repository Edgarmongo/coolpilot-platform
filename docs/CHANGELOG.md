# 变更记录

本文件记录已发布版本的变更。版本号对应 Git tag，镜像由 `release.yml` 在同名 tag 上构建。

**当前线上版本：`v1.1.0`**

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
