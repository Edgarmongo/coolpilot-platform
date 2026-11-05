SET NAMES utf8mb4;

INSERT INTO blog (title, first_picture, content, description, is_published, is_recommend, is_appreciation, is_comment_enabled, create_time, update_time, views, words, read_time, category_id, is_top, password, user_id)
VALUES
('Spring Boot + MyBatis 后端分层实践：从 Controller 到 Service 的职责边界',
 '/img/header/bg1.png',
 '## 为什么要分层\n\n很多项目把业务逻辑直接写在 Controller 里，短期快，长期会让 Controller 膨胀到无法维护。本文梳理 Controller、Service、Mapper 三层的职责边界。\n\n### Controller 层\n\n只做三件事：参数接收与校验、调用 Service、组装响应。不要写业务分支。\n\n### Service 层\n\n承载业务逻辑与事务边界。事务注解应该出现在 Service 上，而不是 Controller。\n\n### Mapper 层\n\n只负责 SQL，不做业务判断。复杂查询可以拆成多个 Mapper 方法组合。\n\n> 经验：如果一个 Service 方法超过 80 行，基本可以拆了。',
 '很多项目把业务逻辑直接写在 Controller 里，短期快，长期会让 Controller 膨胀到无法维护。本文梳理 Controller、Service、Mapper 三层的职责边界。',
 b'1', b'1', b'0', b'1', '2026-10-28 10:24:00', '2026-10-28 10:24:00', 342, 3200, 12, 1, b'0', '', 1),

('Vue 3 大型项目的组件拆分与状态管理设计',
 '/img/header/bg2.png',
 '## 组件粒度\n\n一个组件只做一件事。当模板超过 200 行、props 超过 8 个，就该拆了。\n\nVue 3 里优先用 `<script setup>`：逻辑按「输入 → 状态 → 派生 → 动作 → 输出」的顺序写，读代码的人不用在 options 的几段配置之间来回跳。\n\n三个拆分信号：\n\n- 出现两组互不相关的 props / emit\n- 同一份数据要透传三层以上\n- 模板里挤着多个互不依赖的业务动作\n\n## 状态分层\n\n- 全局状态（Pinia）：跨页面共享的用户信息、站点配置、购物车\n- 局部状态：`ref` / `reactive` 留在组件内，别为了「以后可能复用」提前上提\n- 服务端状态：交给请求层（`useFetch`、`$fetch` 或 TanStack Query），不要塞进 Pinia\n\n### 常见误区\n\n把接口返回的原始数据整个塞进 Pinia，再让各组件自己裁剪。这会让状态难以追踪：同一个字段有五处在改，出问题无法定位是谁写的。\n\n正确做法是 store 只暴露业务语义的状态与动作，原始数据的整理在 store 内部完成。组件侧用 `storeToRefs` 解构，改状态一律走 action。\n\n## 从 Vue 2 迁过来的几个变化\n\n- `data` → `ref` / `reactive`，`this` 不再需要；组合式函数里直接引用变量\n- `Vuex` → `Pinia`：没有 mutations，action 直接改状态；`defineStore` 支持 setup 写法，与组件同构\n- `mixins` → 组合式函数 `useXxx()`：来源清晰、可组合、不污染命名空间\n- `filters` 已移除，格式化逻辑改成 `computed` 或普通函数\n- `v-model` 默认改为 `modelValue` + `update:modelValue`\n\n## 组合式函数的边界\n\n- 以 `use` 开头，返回 ref 与操作函数，不暴露组件内部实现细节\n- 副作用自己收尾：`onUnmounted` 里清定时器、解绑监听\n- 至少两个组件用到再抽，不要为「可能复用」提前抽象\n\n## 性能\n\n- 大对象（图表实例、地图、大数组）用 `shallowRef` / `markRaw` 跳过深层响应式\n- 长列表先考虑虚拟滚动，`v-memo` 只在确有重渲染热点时才加\n- 路由级代码分割，重组件用 `defineAsyncComponent` 懒加载\n\n## 迁移检查清单\n\n1. 入口：`new Vue()` → `createApp()`，插件改 `app.use()`\n2. 事件总线：`$on` / `$emit` → `mitt` 或 `provide` / `inject`\n3. `$listeners` 合并进 `$attrs`，`.native` 修饰符移除\n4. `v-if` / `v-else` 分支上仍要显式给 `key`\n5. Vuex 的 `mapState` → `storeToRefs(store)`',
 '组件粒度过大、状态层次混乱是大型前端项目的通病。本文以 Vue 3 为例，给出可落地的拆分、状态分层与迁移检查清单。',
 b'1', b'1', b'1', b'1', '2026-10-21 15:40:00', '2026-11-06 02:00:00', 519, 1136, 4, 1, b'0', '', 1),

('AI 编程助手接入现有工程：Prompt 与上下文组织',
 '/img/header/bg3.png',
 '## 上下文比 Prompt 更重要\n\n把 AI 接进真实工程时，难点不是写 Prompt，而是喂给它正确的上下文。\n\n### 有效的上下文组织\n\n1. 先给项目结构与依赖清单\n2. 再给要改的文件的真实内容\n3. 最后给明确的验收标准\n\n### 常见失败模式\n\n一次塞进整个仓库，让模型在无关文件里迷路。按模块切片、按依赖顺序喂，是更稳的做法。',
 '把 AI 编程助手接入真实工程时，难点不是写 Prompt，而是喂给它正确的上下文。本文分享可落地的上下文组织方法。',
 b'1', b'0', b'0', b'1', '2026-10-15 09:18:00', '2026-10-15 09:18:00', 276, 2800, 10, 3, b'0', '', 1),

('企业级权限设计：RBAC 模型落地时的三个坑',
 '/img/header/bg1.png',
 '## 坑一：权限点设计过细\n\n权限点粒度太细会导致维护爆炸。建议按「资源 + 操作」两个维度组合。\n\n## 坑二：只在前端做校验\n\n前端隐藏菜单不等于后端拒绝请求。所有权限判断必须在服务端重复执行。\n\n## 坑三：数据权限与功能权限混淆\n\n功能权限决定「能不能进这个页面」，数据权限决定「能看到哪些行」。两者要分开建模。',
 'RBAC 是企业系统的标配，但落地时容易踩坑。本文总结权限点设计、前后端分离校验、数据权限建模三个关键问题。',
 b'1', b'0', b'0', b'1', '2026-10-09 20:05:00', '2026-10-09 20:05:00', 194, 2400, 9, 2, b'0', '', 1),

('从零搭建个人博客系统：技术选型与踩坑记录',
 '/img/header/bg2.png',
 '## 选型思路\n\n个人博客的核心诉求是「写起来顺畅、部署简单、数据能自己掌控」。\n\n### 最终方案\n\n- 前端：Vue2 + Vue CLI\n- 后端：Spring Boot + MyBatis\n- 数据库：MySQL\n- 缓存：Redis\n\n### 踩过的坑\n\n1. 生产环境忘记配跨域\n2. 数据库连接密码硬编码在 properties\n3. 图片上传没做体积限制\n\n> 这些问题不影响开发，但影响上线。上线前一定要在真实域名下完整走一遍流程。',
 '从零搭建个人博客系统的完整技术选型过程与上线踩坑记录。',
 b'1', b'1', b'0', b'1', '2026-10-02 11:30:00', '2026-10-02 11:30:00', 631, 5200, 19, 1, b'0', '', 1);

-- 绑定标签
INSERT INTO blog_tag (blog_id, tag_id) VALUES (1,1),(2,1),(3,1),(4,1),(5,1);

SELECT COUNT(*) AS blog_count FROM blog;
