# 前端技术栈迁移方案（Vue 2 → Vue 3）

> 决策日期：2026-11-05
> 目标：`blog-cms` 改为 Vue 3 + Vite + Element Plus；`blog-view` 改为 Nuxt 3 + Vue 3 + TypeScript
> 原则：**迁移期间旧版本必须保持可运行**，不做「半成品替换」

---

## 一、为什么不能一次替换

| 端 | 页面/组件 | 代码量 | 主要难点 |
|---|---|---|---|
| `blog-cms` | 28 个页面 | 约 6,000 行 | Element UI → Element Plus 的 API 差异、`mavon-editor` 需换成 md 编辑器、ECharts 图表页 |
| `blog-view` | 30 个组件/页面 | 约 5,500 行 | Vuex → Pinia、`filters` 全局移除、评论/密码弹窗、需要 SSR 适配（`window`/`localStorage` 不能在服务端执行） |

两端合计约 11,500 行，且大量依赖旧生态（`vue-cli`、`vuex`、`element-ui`、`tocbot`、`mavon-editor`）。一次性替换会让站点在数天内处于不可用状态。

因此采用 **分支 + Git Worktree 并行开发**：

```
主工作区  coolpilot-platform/        # main 分支，旧的 Vue 2 版本继续跑在 Docker 里
并行工作区 coolpilot-platform-vue3/  # feature/frontend-vue3 分支，新版本在这里开发
```

新版本达到验收标准后再合并，合并当天切换容器，旧版本随时可从 `v0.1.0-baseline` 取回。

## 二、技术映射

### blog-cms

| 旧 | 新 | 说明 |
|---|---|---|
| vue-cli 5 | **Vite 6** | `vite.config.js` 里配 `base: '/cms/'`、`server.proxy['/api']` |
| Vue 2.6 | **Vue 3.5** | 全部改 `<script setup>` |
| Vuex 3 | **Pinia** | 只保留 `user` / `app` 两个 store |
| vue-router 3 | **vue-router 4** | 路由表结构不变，供侧栏生成菜单 |
| Element UI 2.13 | **Element Plus 2.14** | 见下方语法差异清单 |
| `mavon-editor` | `md-editor-v3` | 文章编辑器 |
| `echarts` + 手写 | `echarts` + `vue-echarts` | 仪表盘图表 |
| `js-cookie` | 沿用 | 无变化 |

### blog-view

| 旧 | 新 | 说明 |
|---|---|---|
| vue-cli 5 | **Nuxt 3** | `nuxt.config.ts` 中配 `ssr: true`、`runtimeConfig.public.apiBase` |
| Vue 2.6 | **Vue 3.5 + TS** | `<script setup lang="ts">` |
| Vuex 3 | **Pinia**（`@pinia/nuxt`） | 访客登录态 |
| vue-router 3 | Nuxt 文件路由 | `pages/` 目录即路由 |
| Element UI | **Element Plus**（按需引入） | 弹窗、消息、分页 |
| axios | `$fetch` / `useFetch` | SSR 友好，自动去重 |
| `moment` | `dayjs` | 体积小、API 兼容 |

## 三、Element UI → Element Plus 速查

迁移时高频踩坑点，逐条对照改：

| 旧写法 | 新写法 |
|---|---|
| `<template v-slot="scope">` | `<template #default="scope">` |
| `:visible.sync="visible"` | `v-model="visible"` |
| `size="mini"` | `size="small"`（`mini` 已移除） |
| `type="text"` 按钮 | `link` 属性 + `type="primary"` |
| `@keyup.enter.native` | `@keyup.enter` |
| `@onConfirm`（popconfirm） | `@confirm` |
| `{{ time \| dateFormat }}` | `{{ formatDate(time) }}`（filters 已移除） |
| `this.$message` | `ElMessage`（显式引入） |
| `this.$confirm` | `ElMessageBox.confirm` |
| `el-table-column` 的 `slot-scope` | `#default="scope"` |
| `el-dialog` 的 `:close-on-click-modal` | 同名，行为一致 |
| `el-form` 的 `.native` 修饰符 | 全部去掉 |

## 四、迁移批次

### 批次 A：CMS 骨架（本次）

- [x] Vite + Vue 3 + Element Plus + Pinia + Router 工程搭建
- [x] 布局（侧栏 / 顶栏 / 面包屑）、登录页、请求层、用户 store
- [x] 交易管理三页（订单 / 退款 / 权益）—— 最新写的模块，先迁验证链路
- 验收：`npm run build` 通过；登录后台能看到交易管理并完成一次「确认收款」

### 批次 B：CMS 内容管理

- [ ] 文章列表 / 写文章（md-editor-v3）/ 分类 / 标签 / 动态 / 评论
- [ ] 页面管理（站点设置 / 友链 / 关于）
- 验收：能发一篇带图文章，前台能看到

### 批次 C：CMS 系统与日志

- [ ] 仪表盘（ECharts）、访问/登录/操作/异常日志、定时任务、账号、图床
- 验收：数据与旧版一致

### 批次 D：前台 Nuxt 骨架

- [ ] Nuxt 3 + TS 工程、布局、首页、文章列表/详情、分类/标签/归档
- 验收：`npm run build` 通过，首屏 SSR 输出正文（`curl` 能看到 HTML 内容）

### 批次 E：前台业务页

- [ ] 知识付费（列表/详情/收银台）、个人中心、访客登录、评论
- 验收：完整走通「浏览 → 下单 → 支付 → 看权益」

### 批次 F：切换

- [ ] 两端替换完成 → 合并 `feature/frontend-vue3` → 更新 compose 命令（Vite/Nuxt 构建产物用 nginx 或 node server）
- [ ] 旧版本打 tag `v0.1.0-baseline`，从该 tag 可取回

## 五、SSR 注意事项（前台）

Nuxt 3 默认服务端渲染，旧代码里这些用法必须改，否则构建期直接报错：

| 风险点 | 处理 |
|---|---|
| `window` / `document` / `localStorage` | 放进 `onMounted`，或 `<ClientOnly>` 包裹 |
| `store.state.visitor` 读 cookie | 改用 `useCookie('token')`，服务端可读 |
| 第三方脚本（tocbot、播放器） | `onMounted` 内动态 import |
| 接口鉴权头 | `useRequestHeaders(['cookie'])` 透传，避免首屏登录态丢失 |
| 图片懒加载 | 用 `v-lazy` 或原生 `loading="lazy"` |

## 六、验收标准（整体）

1. 两端 `npm run build` 无错误、无 TS 报错
2. CMS 所有旧功能可用，接口路径与后端不变（后端零改动）
3. 前台首屏 HTML 含文章正文（SSR 生效），Lighthouse 性能 ≥ 85
4. 交易链路回归通过：下单 → 确认收款 → 权益生效 → 退款撤销
5. 旧的 Vue 2 版本可从 `v0.1.0-baseline` 完整取回

## 七、风险

| 风险 | 影响 | 应对 |
|---|---|---|
| Element Plus 表单/表格行为差异 | 页面交互退化 | 逐页对照旧版验收，不靠「能打开就算过」 |
| md 编辑器替换 | 文章格式丢失 | 保留 Markdown 原文，不做富文本转换 |
| Nuxt SSR 与登录态 | 首屏闪登录/未登录 | cookie 存 token，服务端读取 |
| 迁移周期长 | 两套代码并存 | 分支隔离，主分支始终可发布 |

## 迁移进度（2026-11-05）

| 批次 | 状态 |
|---|---|
| A CMS 骨架 | 已完成 |
| B CMS 内容管理 | 待做 |
| C CMS 系统与日志 | 待做 |
| D 前台 Nuxt 骨架 | 已完成（首页/文章/知识付费/登录/注册） |
| E 前台业务页 | 部分完成（收银台、我的；缺分类/标签/归档/动态/友链/关于、评论、文章密码、搜索） |
| F 切换 | 已完成（旧容器已移除，新服务接管 8081/8079） |
