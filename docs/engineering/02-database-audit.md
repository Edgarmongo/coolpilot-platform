# WB-002 数据库结构审计报告

> 审计日期：2026-11-04 ｜ 方式：只读（`information_schema` 查询），未执行任何 DDL/DML
> 覆盖需求：WB-002 的 01–07 全部小节

---

## 01 现有表清单（19 张）

行数取自 `SELECT COUNT(*)` 精确值（`information_schema.TABLE_ROWS` 是估算值，本次实测偏差较大，如 `visit_log` 估算 277、实际 403，已弃用）。

| # | 表 | 用途 | 行数 | 主键 | 二级索引 |
|---|---|---|---|---|---|
| 1 | `about` | 关于我（多语言条目） | 4 | `id` | ❌ 无 |
| 2 | `blog` | 文章 | 5 | `id` | ✅ `category_id`、`user_id` |
| 3 | `blog_tag` | 文章-标签关联 | 5 | 无 | ❌ 无 |
| 4 | `category` | 分类 | 3 | `id` | ❌ 无 |
| 5 | `city_visitor` | 城市 UV 聚合 | 0 | `city` | — |
| 6 | `comment` | 评论 | 0 | `id` | ❌ 无 |
| 7 | `exception_log` | 异常日志 | 3 | `id` | ❌ 无 |
| 8 | `friend` | 友链 | 0 | `id` | ❌ 无 |
| 9 | `login_log` | 登录日志 | 13 | `id` | ❌ 无 |
| 10 | `moment` | 动态 | 2 | `id` | ❌ 无 |
| 11 | `operation_log` | 操作日志 | 28 | `id` | ❌ 无 |
| 12 | `schedule_job` | 定时任务定义 | 2 | `job_id` | ❌ 无 |
| 13 | `schedule_job_log` | 任务执行日志 | 0 | `log_id` | ❌ 无 |
| 14 | `site_setting` | 站点配置（按 `type` 分组） | 24 | `id` | ❌ 无 |
| 15 | `tag` | 标签 | 1 | `id` | ❌ 无 |
| 16 | `user` | 用户 | 2 | `id` | ❌ 无 |
| 17 | `visit_log` | 访问明细 | 403 | `id` | ❌ 无 |
| 18 | `visit_record` | 访问记录 | 0 | `id` | ❌ 无 |
| 19 | `visitor` | 访客标识（UV 去重） | 12 | `id` | ✅ `uuid` UNIQUE |

**`blog_tag` 无主键**，是唯一一张连主键都没有的表。

---

## 02 字段要点

### 2.1 核心表关键字段

**`blog`**（21 列）

```
id, title, first_picture, content(longtext), description(longtext),
is_published, is_recommend, is_appreciation, is_comment_enabled,
create_time, update_time, views, words, read_time,
category_id, is_top, password, user_id
```

- 布尔统一 `bit(1)`，无 `status` 枚举字段
- `password` 可空，**注意：业务判定是 `!"".equals(password)`，所以必须存 `''` 而不是 `NULL`**，否则每篇文章都会被当成"密码保护"
- 缺 SEO 字段（`slug` / `seo_*`）

**`comment`**（15 列）

```
id, nickname, email, content(varchar 255), avatar, create_time, ip,
is_published, is_admin_comment, page(int), is_notice,
blog_id, parent_comment_id(非空，-1 表示根), website, qq
```

- `content` 只有 `varchar(255)`，长评论会被截断或拒绝（业务层限 250）
- `page`：0 普通文章 / 1 关于我 / 2 友链页
- 无 `user_id`，无法关联注册用户

**`user`**（9 列）

```
id, username, password, nickname, avatar, email, create_time, update_time, role
```

- `role` 为字符串，当前存 `ROLE_admin` / `ROLE_visitor`
- **`username` 无唯一索引**
- 无 `status`（禁用）、`bio`、`last_login_time`

**`site_setting` / `about`**

均为 `name_en / name_zh / value` 的 KV 结构，`site_setting` 靠 `type` 分组：

- 1 基础设置（10 项）
- 2 资料卡（12 项）
- 4 友链页（2 项）

> 表注释写的是「1基础设置，2页脚徽标，3资料卡，4友链信息」，但实际数据中 type=2 是资料卡、无 type=3 数据。**注释与数据不一致**，以数据为准。

### 2.2 规范一致性

| 项 | 现状 | 评价 |
|---|---|---|
| 引擎/字符集 | 全部 InnoDB + utf8mb4 + `utf8mb4_general_ci` | ✅ 统一 |
| 主键 | 均为 `bigint(0) AUTO_INCREMENT`，唯一例外 `city_visitor` 用 `city` 作主键 | ⚠️ 可接受（城市名唯一） |
| 时间类型 | `datetime(0)` | ✅ 统一 |
| 布尔 | `bit(1)` | ✅ 统一 |
| 注释 | 核心表每列有 COMMENT；`about` / `tag` / `category` 无 | ⚠️ 不一致 |
| 外键 | **0 个物理外键** | ✅ 符合项目习惯（逻辑关联 + 索引） |
| 软删除 | 无统一机制，靠 `is_published` 等业务字段表达 | ⚠️ 需在新增表中明确策略 |

---

## 03 索引现状（最严重的问题）

### 3.1 全库只有 3 个二级索引

| 表 | 索引名 | 列 | 类型 |
|---|---|---|---|
| `blog` | `type_id` | `category_id` | 普通 |
| `blog` | `user_id` | `user_id` | 普通 |
| `visitor` | `idx_uuid` | `uuid` | **唯一** |

**19 张表里 17 张只有主键。**

### 3.2 缺失索引的影响评估

| 表 | 缺失索引 | 影响 | 等级 |
|---|---|---|---|
| `user` | `UNIQUE(username)` | ① 登录按用户名查询全表扫；② **并发注册可产生重复用户名**（业务层先查后插，非原子） | **P1** |
| `comment` | `(blog_id)`、`(is_published, create_time)` | 文章详情页每次加载评论全表扫；后台评论列表按状态筛选也全扫 | **P1** |
| `blog_tag` | `(blog_id)`、`(tag_id)` | 标签页与文章标签查询全表扫 | P1 |
| `visit_log` | `(create_time)`、`(ip)` | 已有 403 行，后台按时间筛选统计会随增长线性劣化 | P2 |
| `operation_log` | `(create_time)`、`(username)` | 同上 | P2 |
| `login_log` | `(username)`、`(create_time)` | 同上 | P2 |
| `moment` | `(is_published, create_time)` | 行数少，暂不影响 | P3 |
| `site_setting` | `(type)` | 仅 24 行，可忽略 | P3 |

### 3.3 `user.username` 无唯一索引 —— 这是一个真实缺陷

业务层逻辑（`UserServiceImpl.registerVisitor`）：

```
1. 校验用户名格式
2. findByUsername(username) 判重
3. 插入
```

第 2、3 步之间没有锁也没有唯一约束。两个请求同时通过判重，就会插入两条同名记录。后果：

- 登录时 `loadUserByUsername` 返回第一条，**另一个账号永远登不上**
- 后期改成 `sys_user` 时无法补唯一键（脏数据）

**修复**：`ALTER TABLE user ADD UNIQUE INDEX uk_username(username);`（执行前先查重）

```sql
-- 先确认无重复
SELECT username, COUNT(*) c FROM user GROUP BY username HAVING c > 1;
```

---

## 04 关系与完整性

### 4.1 逻辑关联（无物理外键）

```
user 1──N blog           (blog.user_id 可空)
category 1──N blog       (blog.category_id)
blog N──M tag            (blog_tag.blog_id / tag_id)
blog 1──N comment        (comment.blog_id 可空，page=1/2 时为 null)
comment 1──N comment     (parent_comment_id，-1 为根)
blog 1──N blog           (无)
```

### 4.2 孤儿数据风险

无外键 → 删除文章不会清理 `blog_tag` 与 `comment`。当前数据量小（0 条评论），未暴露问题。新增的订单/权益类表**更不能靠外键级联**（历史交易必须保留），必须由业务层保证，且删除商品时禁止物理删除。

### 4.3 一致性检查结果

| 检查项 | 结果 |
|---|---|
| `blog_tag` 中孤立的 `blog_id` | 0（5 条全部有效） |
| `comment` 中孤立的 `blog_id` | 0（表为空） |
| `user` 重复用户名 | 0 |
| `blog.password` 为 NULL 的记录 | 0（已全部置为 `''`） |
| `blog.category_id` 指向不存在的分类 | 0 |

---

## 05 现有表 → 目标业务域映射

| 目标域 | 现有表 | 复用度 | 处置 |
|---|---|---|---|
| 用户权限 | `user` | 70% | 加 `status`/`bio`/`last_login_time`；`role` 值改 `ROLE_owner`/`ROLE_user`；补唯一索引 |
| 内容管理 | `blog` `category` `tag` `blog_tag` `comment` `moment` | 85% | 加 SEO 字段、分类排序、`comment.user_id`、`moment.moment_type` |
| 知识商品 | 无 | 0% | **新建** `product` + 6 张扩展表 |
| 订单支付 | 无 | 0% | **新建** `orders` `order_item` `payment` `refund` |
| 用户权益 | 无 | 0% | **新建** `entitlement` `entitlement_log` |
| 技术服务 | 无 | 0% | **新建** `service_item` `service_order` `delivery` |
| 客户需求 | 无 | 0% | **新建** `demand` `quote` `project` `milestone` |
| 文件管理 | 无（只有磁盘路径配置） | 0% | **新建** `file` `file_version` `download_record` |
| AI 知识库 | 无 | 0% | V1.3 再建 5 张 |
| 运营统计 | `visit_log` `visitor` `city_visitor` `visit_record` | 100% | 直接复用 |
| 审计日志 | `login_log` `operation_log` `exception_log` | 100% | 直接复用 |
| 任务调度 | `schedule_job` `schedule_job_log` | 100% | 直接复用 |
| 页面配置 | `about` `site_setting` | 100% | 直接复用 |

**结论**：19 张表中 **10 张可原样复用**（占现有表的 53%），6 张需加列，3 张结构不动，**0 张需要重建**。目标模型需新增 27 张表，详见 `DATA-MODEL.md`。

---

## 06 目标模型缺口

| 缺口类型 | 数量 | 说明 |
|---|---|---|
| 需新增 | 27 张 | 商品 7、交易 4、权益 2、服务 3、需求 4、文件 3、社区 4 |
| 需加列 | 6 张 | `blog`(4) `category`(3) `comment`(1) `moment`(2) `user`(3) `friend`(1) |
| 需补索引 | 7 张 | 见 §3.2 |
| 需改数据 | 1 项 | `user.role` 值迁移 |
| 需删/改结构 | **0** | 不删列、不改类型 |

### 关键设计冲突与处理

| 蓝图建议 | 实际 | 处理 |
|---|---|---|
| `sys_user` / `sys_role` / `sys_permission` 三表 | 已有 `user` 且只有 2 种角色 | **不建 RBAC 三表**，沿用 `user.role`；"资源权限"由 `entitlement` 承担 |
| `product_spu` | 无命名约束 | 用 `product`，与既有 19 张表的无前缀风格一致 |
| `trade_order` | `order` 是保留字 | 用 `orders` |
| `product_document` / `product_column` 各建扩展表 | 文档与代码资源结构几乎相同 | 合并为 `product_resource`（`resource_type` 区分 DOC/CODE），减少 2 张表 |
| `sys_file` | 无 | 用 `file` |

---

## 07 迁移计划

### 7.1 分阶段（按依赖，非时间）

| 阶段 | 变更 | 风险 | 回滚 |
|---|---|---|---|
| **V1.0** | 建 4 表（`favorite` `like_record` `message` `feedback`）+ 6 表加列 + `user.role` 数据迁移 + 补索引 | 低（只加不减） | 删新增列/表即可 |
| **V1.1a** | 建 10 表（文件 3 + 商品 7） | 低（独立新表） | `DROP` 新表 |
| **V1.1b** | 建 6 表（交易 4 + 权益 2） | **高**（涉及资金） | 需先备份，回滚 = 停用新功能 + 保留数据 |
| **V1.2** | 建 7 表（服务 3 + 需求 4） | 中 | `DROP` 新表 |

### 7.2 V1.0 迁移脚本要求

1. 幂等：可重复执行不报错（先查 `information_schema` 再 `ADD COLUMN`）
2. 先查重再建唯一索引：`SELECT username,COUNT(*) ... HAVING COUNT(*)>1`
3. 角色迁移与代码发布解耦：先 `hasAnyRole("owner","admin")`，再收口
4. 提供回滚脚本：`DROP INDEX` / `DROP COLUMN`
5. 执行前 `mysqldump` 备份

### 7.3 关于 Flyway

当前项目**未使用** Flyway/Liquibase，表结构靠 `schema.sql` 手工导入。建议 V1.0 引入 Flyway：

```
src/main/resources/db/migration/
├── V1__baseline.sql          # 当前 19 张表的完整结构（从现网导出）
├── V2__v1_0_community.sql    # 4 张新表 + 6 表加列
├── V3__v1_1a_file_product.sql
├── V4__v1_1b_trade.sql
└── V5__v1_2_service_demand.sql
```

**注意**：`V1__baseline.sql` 必须是"当前真实结构"，不能写 `CREATE TABLE` 后再让 Flyway 重复建表。首次引入时应 `baseline-on-migrate=true` 并把版本设为当前状态。

---

## 审计结论

1. **表结构健康度：中上**。字符集、主键、时间类型、布尔类型全部统一，无历史脏结构。
2. **索引是最大短板**：17/19 张表只有主键，`user.username` 连唯一索引都没有。这些在数据量小时不痛，一旦有真实用户和订单就会暴露。**建议在 V1.0 一并补齐**，成本极低。
3. **不需要重建任何表**：19 张里 10 张原样复用、6 张加列、3 张不动。
4. **DDL 已可执行**：27 张新表的完整建表语句已在临时库执行验证通过（`nblog_schema_check`，验证后已删除），可直接用于 `V3`–`V5` 迁移脚本。
