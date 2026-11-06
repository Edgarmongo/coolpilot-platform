# 变更记录

本文件记录已发布版本的变更。版本号对应 Git tag，镜像由 `release.yml` 在同名 tag 上构建。

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
