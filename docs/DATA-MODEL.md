# 数据模型设计：从 19 张基础表到 39 张业务表

> 版本：v1.0 草案 ｜ 日期：2026-11-04
> 范围：V1.0 – V1.2（AI 域仅预留结构，V1.3 再落库）
> 基线：MySQL 8.0.40 / `coolpilot` 库现有 19 张表

---

## 一、设计原则

| 编号 | 原则 | 理由 |
|---|---|---|
| P1 | **增量演进，不重建** | 内容域 19 张表已稳定运行，推翻重来成本远高于收益 |
| P2 | **无物理外键** | 沿用现有工程习惯；关联由业务层保证，避免分库/归档时锁表 |
| P3 | **商品与文章物理分离** | 文章负责传播，商品负责交易；不在 `blog` 加价格字段 |
| P4 | **订单 / 支付 / 权益三者分离** | 支付是渠道事实，权益是访问授权，订单是交易契约；三者生命周期不同 |
| P5 | **金额统一 `decimal(10,2)`，禁用 float** | 避免浮点误差导致对账不平 |
| P6 | **状态用 `varchar(16/32)` 存枚举字符串** | 可读、可扩展，避免 `tinyint` 魔数；状态流转在业务层用状态机校验 |
| P7 | **重要实体以 `xxx_no` 对外暴露** | 不自增 ID 暴露给前端，防止遍历 |
| P8 | **快照优先于关联** | 订单项存商品标题与价格快照，商品改价改名不影响历史订单 |

**建表规范**（与现有表保持一致）

```sql
`id` bigint(0) NOT NULL AUTO_INCREMENT
ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic
-- 每列必带 COMMENT；布尔用 bit(1)；时间用 datetime(0)
-- 索引：INDEX `idx_xxx`(`col`) USING BTREE
-- 说明：现有表因 Navicat 生成，每列重复声明 CHARACTER SET；新表统一用表级默认，不再逐列声明
```

---

## 二、现有 19 张表的处置

### 2.1 原样保留（10 张）

`about`、`site_setting`、`city_visitor`、`visit_log`、`visit_record`、`visitor`、`login_log`、`operation_log`、`exception_log`、`schedule_job`、`schedule_job_log`

理由：与业务域解耦，属通用支撑能力，无需改动。

### 2.2 改造（7 张）

| 表 | 改动 | 原因 |
|---|---|---|
| `blog` | 新增 `slug`、`seo_title`、`seo_keywords`、`seo_description` | SEO 与可读 URL；`slug` 加唯一索引 |
| `category` | 新增 `description`、`icon`、`sort` | 前台分类卡与排序 |
| `comment` | 新增 `user_id`（可空） | 关联注册用户，支持"我的评论" |
| `moment` | 新增 `moment_type`、`user_id` | 区分技术观点/项目进度/日常 |
| `user` | 新增 `status`、`bio`、`last_login_time`；`role` 值改为 `ROLE_owner` / `ROLE_user` | 账户状态与个人主页 |
| `friend` | 新增 `sort` | 友链排序 |
| `blog_tag` | 不变 | — |

**`user.role` 迁移**（关键，需数据迁移脚本）

```sql
UPDATE user SET role = 'ROLE_owner' WHERE role = 'ROLE_admin';
UPDATE user SET role = 'ROLE_user'  WHERE role = 'ROLE_visitor';
```

> 代码侧同步修改 `SecurityConfig` 的 `hasRole("owner")`、`JwtConstants` 前缀、前端角色判断。这一步影响登录链路，必须与后端重启同批完成。

### 2.3 改造 DDL（已在 MySQL 8.0.40 实测可执行）

> 验证方式：按 `CREATE TABLE ... LIKE` 复制真实表结构后逐条执行，全部通过，新列位置与注释均正确。

```sql
ALTER TABLE `blog`
  ADD COLUMN `slug` varchar(255) NULL DEFAULT NULL COMMENT 'URL别名，为空时走ID路由' AFTER `title`,
  ADD COLUMN `seo_title` varchar(255) NOT NULL DEFAULT '' COMMENT 'SEO标题' AFTER `slug`,
  ADD COLUMN `seo_keywords` varchar(255) NOT NULL DEFAULT '' COMMENT 'SEO关键词' AFTER `seo_title`,
  ADD COLUMN `seo_description` varchar(500) NOT NULL DEFAULT '' COMMENT 'SEO描述' AFTER `seo_keywords`,
  ADD UNIQUE INDEX `uk_slug`(`slug`) USING BTREE;

ALTER TABLE `category`
  ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '分类描述' AFTER `category_name`,
  ADD COLUMN `icon` varchar(255) NOT NULL DEFAULT '' COMMENT '分类图标' AFTER `description`,
  ADD COLUMN `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序，越大越前' AFTER `icon`;

ALTER TABLE `comment`
  ADD COLUMN `user_id` bigint(0) NULL DEFAULT NULL COMMENT '关联注册用户，游客评论为空' AFTER `qq`,
  ADD INDEX `idx_user`(`user_id`) USING BTREE;

ALTER TABLE `moment`
  ADD COLUMN `moment_type` varchar(16) NOT NULL DEFAULT 'DAILY' COMMENT 'DAILY/TECH/PROGRESS' AFTER `content`,
  ADD COLUMN `user_id` bigint(0) NULL DEFAULT NULL COMMENT '发布人' AFTER `moment_type`,
  ADD INDEX `idx_type_time`(`moment_type`, `create_time`) USING BTREE;

ALTER TABLE `user`
  ADD COLUMN `status` varchar(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED' AFTER `role`,
  ADD COLUMN `bio` varchar(500) NOT NULL DEFAULT '' COMMENT '个人简介' AFTER `status`,
  ADD COLUMN `last_login_time` datetime(0) NULL DEFAULT NULL COMMENT '最后登录时间' AFTER `bio`;

ALTER TABLE `friend`
  ADD COLUMN `sort` int(0) NOT NULL DEFAULT 0 COMMENT '排序，越大越前' AFTER `views`;
```

> `uk_slug` 建在可空列上：MySQL 允许唯一索引存在多个 NULL，因此历史文章 `slug` 留空不会冲突，可渐进式补数据。

### 2.4 语义升级但结构不动（2 张）

| 表 | 升级点 |
|---|---|
| `about` | 继续承载「平台介绍」，前台首页引用 |
| `friend` | 继续承载友链，不并入社区 |

---

## 三、新增表总览（27 张核心 + 5 张 AI 预留）

| 域 | 表 | 版本 | 职责 |
|---|---|---|---|
| 商品域 | `product` | V1.1 | 统一商品主体（文档/课程/专栏/代码/服务） |
| | `product_category` | V1.1 | 商品分类 |
| | `product_price` | V1.1 | 价格策略（固定价 / 限时优惠） |
| | `product_article` | V1.1 | 专栏 ↔ 文章关联 |
| | `course` | V1.1 | 课程扩展信息 |
| | `lesson` | V1.1 | 课时 |
| | `product_resource` | V1.1 | 可下载资源（文档/代码包） |
| 交易域 | `orders` | V1.1 | 订单主表（商品单与服务单统一入口） |
| | `order_item` | V1.1 | 订单项（含价格快照） |
| | `payment` | V1.1 | 支付单（渠道事实，回调幂等） |
| | `refund` | V1.1 | 退款单 |
| 权益域 | `entitlement` | V1.1 | 访问授权（付费内容的唯一准入凭证） |
| | `entitlement_log` | V1.1 | 权益变更流水 |
| 服务域 | `service_item` | V1.2 | 服务商品扩展（交付周期、定价方式） |
| | `service_order` | V1.2 | 服务订单 |
| | `delivery` | V1.2 | 交付物 |
| 需求域 | `demand` | V1.2 | 客户需求 |
| | `quote` | V1.2 | 报价单 |
| | `project` | V1.2 | 项目 |
| | `milestone` | V1.2 | 里程碑 |
| 文件域 | `file` | V1.1a | 统一文件元数据 |
| | `file_version` | V1.1a | 文件版本（代码资源更新） |
| | `download_record` | V1.1a | 下载审计 |
| 社区域 | `favorite` | V1.0 | 收藏 |
| | `like_record` | V1.0 | 点赞明细 |
| | `message` | V1.0 | 站内消息 |
| | `feedback` | V1.0 | 技术问题反馈 |
| AI 域 | `kb_knowledge_base` / `kb_document` / `kb_chunk` / `ai_conversation` / `ai_message` | V1.3 | RAG 知识库与问答（预留） |

---

## 四、关键设计决策

| 编号 | 决策 | 备选方案 | 结论与理由 |
|---|---|---|---|
| D1 | 表名用 `orders` 而非 `order` | `order` | `ORDER` 是 MySQL 保留字，全链路需反引号，MyBatis 动态 SQL 易出错 |
| D2 | **不建 `role` / `permission` 表** | 蓝图建议三表 RBAC | 业务只有两种角色。真正的"资源权限"由 `entitlement` 承担，菜单/接口权限由前端路由白名单 + `SecurityConfig` 规则表达。建三表属过度设计，且与现有 `user.role` 冲突 |
| D3 | 商品统一 `product` + 类型扩展表 | 每种商品一张独立大表 | 统一列表、搜索、购物车逻辑；扩展信息按需 join，避免大量空列 |
| D4 | 支付与退款**跨业务共用** | 商品单、服务单各一套支付表 | 支付是渠道事实，与业务类型无关。用 `biz_type` 区分，对账时只查一张表 |
| D5 | 服务订单**独立于** `orders` | 全部塞进 `orders` | 服务交付是长周期、多里程碑的，与"付款即交付"的数字商品生命周期差异过大。但两者都回落到 `payment` 收款 |
| D6 | 权益必须**独立成表**，不能靠查订单 | 下单即视为有权限 | 退款需撤销、限时需过期、赠送需单独发放，这些都无法用订单状态表达 |
| D7 | 文件不存公网 URL，存 `storage_type + bucket + object_key` | 直接存完整 URL | 更换存储/迁移 CDN 时无需改数据；下载链接动态签发 |
| D8 | 软删除统一用 `is_deleted bit(1)` 还是物理删？ | — | **商品/订单/需求/项目一律禁止物理删除**，用状态字段（`ARCHIVED` / `CLOSED`）表达；评论、收藏、文件可物理删 |
| D9 | 需求与项目 **1:N** | 1:1 | 一个大需求可能拆多期项目，`project.demand_id` 不设唯一 |
| D10 | AI chunk 表冗余 `product_id` 与 `is_public` | 检索时 join 原表判断权限 | 检索是高频路径，join 会放大延迟；冗余字段在文档入库时一次性写入，配合向量库过滤 |

---

## 五、DDL：V1.0 社区域（先落这 4 张）

```sql
-- 收藏
CREATE TABLE `favorite` (
  `id`          bigint(0)    NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)    NOT NULL COMMENT '收藏人',
  `target_type` varchar(16)  NOT NULL COMMENT '目标类型：ARTICLE/PRODUCT/MOMENT',
  `target_id`   bigint(0)    NOT NULL COMMENT '目标ID',
  `create_time` datetime(0)  NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '收藏';

-- 点赞明细（替代 moment.likes 单字段累加，避免重复点赞）
CREATE TABLE `like_record` (
  `id`          bigint(0)   NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)   NOT NULL COMMENT '点赞人',
  `target_type` varchar(16) NOT NULL COMMENT '目标类型：ARTICLE/MOMENT/COMMENT',
  `target_id`   bigint(0)   NOT NULL COMMENT '目标ID',
  `create_time` datetime(0) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '点赞记录';

-- 站内消息
CREATE TABLE `message` (
  `id`          bigint(0)    NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)    NOT NULL COMMENT '接收人',
  `msg_type`    varchar(16)  NOT NULL COMMENT '类型：SYSTEM/ORDER/DEMAND/COMMENT/REPLY',
  `title`       varchar(255) NOT NULL COMMENT '消息标题',
  `content`     varchar(1000) NOT NULL DEFAULT '' COMMENT '消息内容',
  `biz_type`    varchar(16)  NULL DEFAULT NULL COMMENT '业务类型：ORDER/DEMAND/ARTICLE',
  `biz_id`      bigint(0)    NULL DEFAULT NULL COMMENT '业务ID，用于跳转',
  `link_url`    varchar(500) NULL DEFAULT NULL COMMENT '跳转地址',
  `is_read`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否已读',
  `read_time`   datetime(0)  NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime(0)  NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_read`(`user_id`, `is_read`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '站内消息';

-- 技术问题反馈
CREATE TABLE `feedback` (
  `id`          bigint(0)    NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)    NULL DEFAULT NULL COMMENT '提交人，未登录可为空',
  `content`     varchar(2000) NOT NULL COMMENT '反馈内容',
  `images`      varchar(2000) NOT NULL DEFAULT '' COMMENT '截图URL，逗号分隔',
  `contact`     varchar(255) NOT NULL DEFAULT '' COMMENT '联系方式',
  `status`      varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
  `reply`       varchar(2000) NULL DEFAULT NULL COMMENT '回复内容',
  `reply_time`  datetime(0)  NULL DEFAULT NULL COMMENT '回复时间',
  `create_time` datetime(0)  NOT NULL COMMENT '创建时间',
  `update_time` datetime(0)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '技术问题反馈';
```

---

## 六、DDL：V1.1a 文件服务（硬前置）

> 这套表是付费下载的基础。**没有它，付费文件无法授权、无法追责、无法换存储。**

```sql
CREATE TABLE `file` (
  `id`            bigint(0)    NOT NULL AUTO_INCREMENT,
  `file_uuid`     varchar(64)  NOT NULL COMMENT '对外唯一标识，不暴露自增ID',
  `owner_id`      bigint(0)    NOT NULL COMMENT '上传者',
  `biz_type`      varchar(16)  NOT NULL DEFAULT 'OTHER' COMMENT '业务类型：AVATAR/PRODUCT/DEMAND/DELIVERY/OTHER',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `storage_type`  varchar(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '存储：LOCAL/MINIO/S3',
  `bucket`        varchar(128) NOT NULL DEFAULT '' COMMENT '存储桶',
  `object_key`    varchar(500) NOT NULL COMMENT '对象键，不含域名',
  `file_size`     bigint(0)    NOT NULL DEFAULT 0 COMMENT '字节数',
  `mime_type`     varchar(128) NOT NULL DEFAULT '' COMMENT 'MIME类型',
  `sha256`        varchar(64)  NOT NULL DEFAULT '' COMMENT '内容摘要，用于去重与完整性校验',
  `is_public`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否公开可直读',
  `status`        varchar(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ARCHIVED',
  `create_time`   datetime(0)  NOT NULL COMMENT '创建时间',
  `update_time`   datetime(0)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_uuid`(`file_uuid`) USING BTREE,
  INDEX `idx_sha256`(`sha256`) USING BTREE,
  INDEX `idx_biz`(`biz_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '文件元数据';

CREATE TABLE `file_version` (
  `id`         bigint(0)   NOT NULL AUTO_INCREMENT,
  `file_id`    bigint(0)   NOT NULL COMMENT '所属文件',
  `version`    int(0)      NOT NULL DEFAULT 1 COMMENT '版本号，从1递增',
  `object_key` varchar(500) NOT NULL COMMENT '该版本的对象键',
  `file_size`  bigint(0)   NOT NULL DEFAULT 0 COMMENT '该版本字节数',
  `sha256`     varchar(64) NOT NULL DEFAULT '' COMMENT '该版本摘要',
  `change_log` varchar(500) NOT NULL DEFAULT '' COMMENT '变更说明',
  `create_time` datetime(0) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_version`(`file_id`, `version`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '文件版本';

CREATE TABLE `download_record` (
  `id`             bigint(0)   NOT NULL AUTO_INCREMENT,
  `file_id`        bigint(0)   NOT NULL COMMENT '文件',
  `file_version_id` bigint(0)  NULL DEFAULT NULL COMMENT '版本',
  `user_id`        bigint(0)   NOT NULL COMMENT '下载人',
  `entitlement_id` bigint(0)   NULL DEFAULT NULL COMMENT '凭哪个权益下载',
  `ip`             varchar(64) NOT NULL DEFAULT '' COMMENT '下载IP',
  `user_agent`     varchar(500) NOT NULL DEFAULT '' COMMENT 'UA',
  `create_time`    datetime(0) NOT NULL COMMENT '下载时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_file`(`file_id`) USING BTREE,
  INDEX `idx_user`(`user_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '下载记录';
```

**下载鉴权流程**

```
GET /file/download?fileId=xx&token=xxx
  → 校验 token（5 分钟有效，内容含 fileId + userId + 签发时间）
  → 校验 entitlement：SELECT 1 FROM entitlement
                     WHERE user_id=? AND product_id IN (该文件所属商品) AND status='ACTIVE'
  → 写 download_record
  → 流式返回文件，不重定向到对象存储公网地址
```

---

## 七、DDL：V1.1 商品域

```sql
CREATE TABLE `product` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `product_no`     varchar(32)   NOT NULL COMMENT '对外商品编号',
  `product_type`   varchar(16)   NOT NULL COMMENT 'DOC/COURSE/COLUMN/CODE/SERVICE',
  `category_id`    bigint(0)     NOT NULL DEFAULT 0 COMMENT '商品分类',
  `title`          varchar(255)  NOT NULL COMMENT '商品标题',
  `subtitle`       varchar(255)  NOT NULL DEFAULT '' COMMENT '副标题',
  `cover`          varchar(500)  NOT NULL DEFAULT '' COMMENT '封面图',
  `summary`        varchar(500)  NOT NULL DEFAULT '' COMMENT '列表摘要',
  `detail`         longtext      NOT NULL COMMENT '详情（Markdown）',
  `price`          decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前售价',
  `original_price` decimal(10,2) NULL DEFAULT NULL COMMENT '划线原价',
  `price_mode`     varchar(16)   NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED/STARTING/CUSTOM',
  `sales_count`    int(0)        NOT NULL DEFAULT 0 COMMENT '销量（支付成功后累加）',
  `view_count`     int(0)        NOT NULL DEFAULT 0 COMMENT '浏览量',
  `sort`           int(0)        NOT NULL DEFAULT 0 COMMENT '排序，越大越前',
  `status`         varchar(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ON_SALE/OFF_SALE/ARCHIVED',
  `is_top`         bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否置顶',
  `owner_id`       bigint(0)     NOT NULL COMMENT '创建者',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`    datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_no`(`product_no`) USING BTREE,
  INDEX `idx_type_status`(`product_type`, `status`) USING BTREE,
  INDEX `idx_category`(`category_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '商品主体';

CREATE TABLE `product_category` (
  `id`          bigint(0)    NOT NULL AUTO_INCREMENT,
  `name`        varchar(64)  NOT NULL COMMENT '分类名',
  `product_type` varchar(16) NOT NULL COMMENT '所属类型',
  `icon`        varchar(255) NOT NULL DEFAULT '' COMMENT '图标',
  `sort`        int(0)       NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime(0)  NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`product_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '商品分类';

CREATE TABLE `product_price` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `product_id`     bigint(0)     NOT NULL COMMENT '商品',
  `price`          decimal(10,2) NOT NULL COMMENT '促销价',
  `original_price` decimal(10,2) NULL DEFAULT NULL COMMENT '原价',
  `start_time`     datetime(0)   NOT NULL COMMENT '生效开始',
  `end_time`       datetime(0)   NOT NULL COMMENT '生效结束',
  `status`         varchar(16)   NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `remark`         varchar(255)  NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product_time`(`product_id`, `start_time`, `end_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '价格策略';

CREATE TABLE `course` (
  `id`             bigint(0)    NOT NULL AUTO_INCREMENT,
  `product_id`     bigint(0)    NOT NULL COMMENT '关联商品',
  `description`    varchar(1000) NOT NULL DEFAULT '' COMMENT '课程介绍',
  `lesson_count`   int(0)       NOT NULL DEFAULT 0 COMMENT '课时数',
  `total_duration` int(0)       NOT NULL DEFAULT 0 COMMENT '总时长（分钟）',
  `create_time`    datetime(0)  NOT NULL COMMENT '创建时间',
  `update_time`    datetime(0)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '课程扩展';

CREATE TABLE `lesson` (
  `id`              bigint(0)    NOT NULL AUTO_INCREMENT,
  `course_id`       bigint(0)    NOT NULL COMMENT '所属课程',
  `title`           varchar(255) NOT NULL COMMENT '课时标题',
  `sort`            int(0)       NOT NULL DEFAULT 0 COMMENT '学习顺序',
  `content_type`    varchar(16)  NOT NULL DEFAULT 'VIDEO' COMMENT 'VIDEO/DOC',
  `video_file_id`   bigint(0)    NULL DEFAULT NULL COMMENT '视频文件',
  `content`         longtext     NULL COMMENT '图文内容',
  `duration`        int(0)       NOT NULL DEFAULT 0 COMMENT '时长（分钟）',
  `is_free_preview` bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否免费试看',
  `create_time`     datetime(0)  NOT NULL COMMENT '创建时间',
  `update_time`     datetime(0)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_sort`(`course_id`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '课时';

CREATE TABLE `product_article` (
  `id`         bigint(0)   NOT NULL AUTO_INCREMENT,
  `product_id` bigint(0)   NOT NULL COMMENT '专栏商品',
  `blog_id`    bigint(0)   NOT NULL COMMENT '文章',
  `sort`       int(0)      NOT NULL DEFAULT 0 COMMENT '阅读顺序',
  `create_time` datetime(0) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_blog`(`product_id`, `blog_id`) USING BTREE,
  INDEX `idx_blog`(`blog_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '专栏-文章关联';

CREATE TABLE `product_resource` (
  `id`            bigint(0)    NOT NULL AUTO_INCREMENT,
  `product_id`    bigint(0)    NOT NULL COMMENT '商品',
  `resource_type` varchar(16)  NOT NULL COMMENT 'DOC/CODE',
  `file_id`       bigint(0)    NOT NULL COMMENT '文件',
  `display_name`  varchar(255) NOT NULL COMMENT '展示名（下载时的文件名）',
  `version`       varchar(32)  NOT NULL DEFAULT 'v1' COMMENT '资源版本（代码资源可更新）',
  `is_latest`     bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否最新版本',
  `sort`          int(0)       NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time`   datetime(0)  NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '商品可下载资源';
```

---

## 八、DDL：V1.1 交易域

```sql
CREATE TABLE `orders` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `order_no`       varchar(32)   NOT NULL COMMENT '对外订单号',
  `user_id`        bigint(0)     NOT NULL COMMENT '下单人',
  `biz_type`       varchar(16)   NOT NULL COMMENT 'PRODUCT/SERVICE',
  `total_amount`   decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `pay_amount`     decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
  `status`         varchar(16)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PAID/COMPLETED/CANCELLED/CLOSED/REFUNDING/REFUNDED',
  `pay_expire_time` datetime(0)  NULL DEFAULT NULL COMMENT '支付截止时间',
  `paid_time`      datetime(0)   NULL DEFAULT NULL COMMENT '支付时间',
  `cancel_time`    datetime(0)   NULL DEFAULT NULL COMMENT '取消时间',
  `remark`         varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`    datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user_time`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '订单';

CREATE TABLE `order_item` (
  `id`              bigint(0)     NOT NULL AUTO_INCREMENT,
  `order_id`        bigint(0)     NOT NULL COMMENT '订单',
  `product_id`      bigint(0)     NOT NULL COMMENT '商品',
  `product_type`    varchar(16)   NOT NULL COMMENT '类型快照',
  `title_snapshot`  varchar(255)  NOT NULL COMMENT '标题快照',
  `cover_snapshot`  varchar(500)  NOT NULL DEFAULT '' COMMENT '封面快照',
  `price_snapshot`  decimal(10,2) NOT NULL COMMENT '成交单价快照',
  `quantity`        int(0)        NOT NULL DEFAULT 1 COMMENT '数量',
  `amount`          decimal(10,2) NOT NULL COMMENT '小计',
  `create_time`     datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`order_id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '订单项';

CREATE TABLE `payment` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `payment_no`  varchar(32)   NOT NULL COMMENT '支付单号（商户生成的）',
  `biz_type`    varchar(16)   NOT NULL COMMENT 'PRODUCT/SERVICE',
  `biz_no`      varchar(32)   NOT NULL COMMENT '关联订单号（orders.order_no 或 service_order.order_no）',
  `channel`     varchar(16)   NOT NULL COMMENT 'ALIPAY/WECHAT/MANUAL',
  `trade_no`    varchar(64)   NULL DEFAULT NULL COMMENT '渠道交易号，唯一，用于幂等',
  `amount`      decimal(10,2) NOT NULL COMMENT '支付金额',
  `status`      varchar(16)   NOT NULL DEFAULT 'INIT' COMMENT 'INIT/SUCCESS/FAIL/CLOSED',
  `paid_time`   datetime(0)   NULL DEFAULT NULL COMMENT '支付成功时间',
  `notify_raw`  text          NULL COMMENT '渠道回调原文，便于排查',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time` datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_payment_no`(`payment_no`) USING BTREE,
  UNIQUE INDEX `uk_channel_trade`(`channel`, `trade_no`) USING BTREE,
  INDEX `idx_biz`(`biz_type`, `biz_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '支付单';

CREATE TABLE `refund` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `refund_no`   varchar(32)   NOT NULL COMMENT '退款单号',
  `biz_type`    varchar(16)   NOT NULL COMMENT 'PRODUCT/SERVICE',
  `biz_no`      varchar(32)   NOT NULL COMMENT '关联订单号',
  `payment_no`  varchar(32)   NOT NULL COMMENT '原支付单',
  `amount`      decimal(10,2) NOT NULL COMMENT '退款金额',
  `reason`      varchar(500)  NOT NULL DEFAULT '' COMMENT '退款原因',
  `status`      varchar(16)   NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED/APPROVED/REFUNDING/SUCCESS/REJECTED',
  `apply_time`  datetime(0)   NOT NULL COMMENT '申请时间',
  `audit_time`  datetime(0)   NULL DEFAULT NULL COMMENT '审核时间',
  `refund_time` datetime(0)   NULL DEFAULT NULL COMMENT '退款完成时间',
  `operator_id` bigint(0)     NULL DEFAULT NULL COMMENT '处理人',
  `remark`      varchar(500)  NOT NULL DEFAULT '' COMMENT '处理备注',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_refund_no`(`refund_no`) USING BTREE,
  INDEX `idx_biz`(`biz_type`, `biz_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '退款单';
```

> **幂等实现要点**：`uk_channel_trade(channel, trade_no)` 是数据库层的最后防线。回调处理逻辑必须「先 `UPDATE payment SET status='SUCCESS' WHERE payment_no=? AND status='INIT'`，判断影响行数为 1 才继续发放权益」，行数为 0 说明已处理过，直接返回成功。

---

## 九、DDL：V1.1 权益域

```sql
CREATE TABLE `entitlement` (
  `id`           bigint(0)   NOT NULL AUTO_INCREMENT,
  `user_id`      bigint(0)   NOT NULL COMMENT '持有人',
  `product_id`   bigint(0)   NOT NULL COMMENT '商品',
  `order_no`     varchar(32) NULL DEFAULT NULL COMMENT '来源订单',
  `ent_type`     varchar(16) NOT NULL DEFAULT 'ACCESS' COMMENT 'ACCESS/DOWNLOAD/LEARN',
  `source`       varchar(16) NOT NULL DEFAULT 'PURCHASE' COMMENT 'PURCHASE/GIFT/TRIAL',
  `start_time`   datetime(0) NOT NULL COMMENT '生效时间',
  `expire_time`  datetime(0) NULL DEFAULT NULL COMMENT '过期时间，NULL 为永久',
  `status`       varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
  `create_time`  datetime(0) NOT NULL COMMENT '创建时间',
  `update_time`  datetime(0) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product_type`(`user_id`, `product_id`, `ent_type`) USING BTREE,
  INDEX `idx_user_status`(`user_id`, `status`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '访问权益';

CREATE TABLE `entitlement_log` (
  `id`             bigint(0)   NOT NULL AUTO_INCREMENT,
  `entitlement_id` bigint(0)   NOT NULL COMMENT '权益ID',
  `user_id`        bigint(0)   NOT NULL COMMENT '持有人',
  `product_id`     bigint(0)   NOT NULL COMMENT '商品',
  `action`         varchar(16)  NOT NULL COMMENT 'GRANT/REVOKE/EXPIRE/RENEW',
  `reason`         varchar(255) NOT NULL DEFAULT '' COMMENT '变更原因',
  `operator_id`    bigint(0)   NULL DEFAULT NULL COMMENT '操作人，系统触发为空',
  `create_time`    datetime(0) NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_ent`(`entitlement_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '权益变更流水';
```

**为什么 `uk_user_product_type` 要带 `ent_type`**：同一商品可能同时有「阅读」和「下载」两种权益（例如买了专栏可读，另附赠代码包可下载）。但注意：**退款撤销时必须按 `order_no` 精确撤销该笔订单发放的权益**，不能只按 `user_id + product_id` 删，否则会误删用户此前合法购买的权益。

---

## 十、DDL：V1.2 服务域

```sql
CREATE TABLE `service_item` (
  `id`            bigint(0)    NOT NULL AUTO_INCREMENT,
  `product_id`    bigint(0)    NOT NULL COMMENT '关联 product（type=SERVICE）',
  `delivery_days` int(0)       NOT NULL DEFAULT 0 COMMENT '预计交付天数，0 表示面议',
  `scope`         text         NULL COMMENT '服务范围说明',
  `process`       text         NULL COMMENT '服务流程说明',
  `faq`           text         NULL COMMENT '常见问题',
  `create_time`   datetime(0)  NOT NULL COMMENT '创建时间',
  `update_time`   datetime(0)  NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务商品扩展';

CREATE TABLE `service_order` (
  `id`            bigint(0)     NOT NULL AUTO_INCREMENT,
  `order_no`      varchar(32)   NOT NULL COMMENT '对外订单号',
  `user_id`       bigint(0)     NOT NULL COMMENT '客户',
  `product_id`    bigint(0)     NOT NULL COMMENT '服务商品',
  `demand_id`     bigint(0)     NULL DEFAULT NULL COMMENT '关联需求',
  `quote_id`      bigint(0)     NULL DEFAULT NULL COMMENT '关联报价',
  `title`         varchar(255)  NOT NULL COMMENT '订单标题快照',
  `amount`        decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '成交金额',
  `status`        varchar(16)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PAID/IN_PROGRESS/PENDING_ACCEPT/COMPLETED/AFTER_SALE/CANCELLED/REFUNDED',
  `start_time`    datetime(0)   NULL DEFAULT NULL COMMENT '开始实施时间',
  `deliver_time`  datetime(0)   NULL DEFAULT NULL COMMENT '交付时间',
  `accept_time`   datetime(0)   NULL DEFAULT NULL COMMENT '验收时间',
  `create_time`   datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`   datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务订单';

CREATE TABLE `delivery` (
  `id`              bigint(0)    NOT NULL AUTO_INCREMENT,
  `service_order_id` bigint(0)   NOT NULL COMMENT '服务订单',
  `milestone_id`    bigint(0)    NULL DEFAULT NULL COMMENT '所属里程碑，可为空',
  `delivery_type`   varchar(16)  NOT NULL DEFAULT 'FILE' COMMENT 'FILE/LINK/TEXT',
  `file_id`         bigint(0)    NULL DEFAULT NULL COMMENT '交付文件',
  `content`         text         NULL COMMENT '交付说明/链接',
  `delivered_by`    bigint(0)    NOT NULL COMMENT '交付人',
  `delivered_time`  datetime(0)  NOT NULL COMMENT '交付时间',
  `accepted_time`   datetime(0)  NULL DEFAULT NULL COMMENT '验收时间',
  `status`          varchar(16)  NOT NULL DEFAULT 'DELIVERED' COMMENT 'DELIVERED/ACCEPTED/REJECTED',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`service_order_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务交付物';
```

---

## 十一、DDL：V1.2 需求域

```sql
CREATE TABLE `demand` (
  `id`              bigint(0)     NOT NULL AUTO_INCREMENT,
  `demand_no`       varchar(32)   NOT NULL COMMENT '对外需求号',
  `user_id`         bigint(0)     NOT NULL COMMENT '提交人',
  `title`           varchar(255)  NOT NULL COMMENT '需求标题',
  `project_type`    varchar(32)   NOT NULL COMMENT 'BACKEND/AI/DEPLOY/CONSULT/OTHER',
  `tech_stack`      varchar(500)  NOT NULL DEFAULT '' COMMENT '技术栈要求',
  `budget_min`      decimal(10,2) NULL DEFAULT NULL COMMENT '预算下限',
  `budget_max`      decimal(10,2) NULL DEFAULT NULL COMMENT '预算上限',
  `budget_negotiable` bit(1)      NOT NULL DEFAULT b'0' COMMENT '是否面议',
  `expect_date`     date          NULL DEFAULT NULL COMMENT '期望交付日期',
  `description`     longtext      NOT NULL COMMENT '需求描述',
  `attachment_file_id` bigint(0)  NULL DEFAULT NULL COMMENT '附件',
  `contact`         varchar(255)  NOT NULL DEFAULT '' COMMENT '联系方式（仅 OWNER 可见）',
  `status`          varchar(32)   NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '见状态机',
  `reject_reason`   varchar(500)  NOT NULL DEFAULT '' COMMENT '拒绝原因',
  `audit_time`      datetime(0)   NULL DEFAULT NULL COMMENT '审核时间',
  `create_time`     datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`     datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_demand_no`(`demand_no`) USING BTREE,
  INDEX `idx_user`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '客户需求';

CREATE TABLE `quote` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `quote_no`    varchar(32)   NOT NULL COMMENT '报价单号',
  `demand_id`   bigint(0)     NOT NULL COMMENT '需求',
  `version`     int(0)        NOT NULL DEFAULT 1 COMMENT '报价版本，支持多次改价',
  `amount`      decimal(10,2) NOT NULL COMMENT '报价金额',
  `currency`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `scope`       text          NULL COMMENT '工作范围说明',
  `valid_until` datetime(0)   NULL DEFAULT NULL COMMENT '报价有效期',
  `status`      varchar(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SENT/ACCEPTED/REJECTED/EXPIRED',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time` datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_quote_no`(`quote_no`) USING BTREE,
  UNIQUE INDEX `uk_demand_version`(`demand_id`, `version`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '报价单';

CREATE TABLE `project` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `project_no`  varchar(32)   NOT NULL COMMENT '项目编号',
  `demand_id`   bigint(0)     NOT NULL COMMENT '来源需求，一个需求可拆多期',
  `quote_id`    bigint(0)     NULL DEFAULT NULL COMMENT '确认的报价',
  `user_id`     bigint(0)     NOT NULL COMMENT '客户',
  `name`        varchar(255)  NOT NULL COMMENT '项目名',
  `amount`      decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '合同金额',
  `status`      varchar(16)   NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/IN_PROGRESS/PENDING_ACCEPT/COMPLETED/ARCHIVED',
  `start_date`  date          NULL DEFAULT NULL COMMENT '开始日期',
  `end_date`    date          NULL DEFAULT NULL COMMENT '结束日期',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time` datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_project_no`(`project_no`) USING BTREE,
  INDEX `idx_demand`(`demand_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '项目';

CREATE TABLE `milestone` (
  `id`              bigint(0)     NOT NULL AUTO_INCREMENT,
  `project_id`      bigint(0)     NOT NULL COMMENT '项目',
  `name`            varchar(255)  NOT NULL COMMENT '里程碑名称',
  `sort`            int(0)        NOT NULL DEFAULT 0 COMMENT '顺序',
  `amount`          decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '该阶段金额',
  `due_date`        date          NULL DEFAULT NULL COMMENT '截止日期',
  `status`          varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DELIVERING/DELIVERED/ACCEPTED/REJECTED',
  `delivered_time`  datetime(0)   NULL DEFAULT NULL COMMENT '交付时间',
  `accepted_time`   datetime(0)   NULL DEFAULT NULL COMMENT '验收时间',
  `remark`          varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_project_sort`(`project_id`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '项目里程碑';
```

---

## 十二、AI 域（V1.3 预留，暂不落库）

仅给出结构意图，待 RAG 技术选型（向量库用 pgvector / Milvus / Qdrant）确定后再定 DDL。

| 表 | 关键字段 | 说明 |
|---|---|---|
| `kb_knowledge_base` | id, name, description | 知识库 |
| `kb_document` | id, kb_id, source_type, source_id, product_id, status | 文档来源（文章/商品文件/课时），`product_id` 为权限归属 |
| `kb_chunk` | id, doc_id, kb_id, chunk_index, content, vector_id, **product_id, is_public** | 分块；冗余权限字段供检索过滤 |
| `ai_conversation` | id, user_id, title | 会话 |
| `ai_message` | id, conversation_id, role, content, citations | 消息，`citations` 存引用原文位置 |

**权限隔离的实现**（对应 PRD 修正建议 5）

```
检索时构造过滤条件：
  is_public = 1
  OR product_id IN (SELECT product_id FROM entitlement WHERE user_id=? AND status='ACTIVE')
再交给向量库做 filtered search，而不是先检索后过滤
```

---

## 十三、状态枚举字典

| 实体 | 字段 | 取值 |
|---|---|---|
| 商品 | `product.status` | `DRAFT` `ON_SALE` `OFF_SALE` `ARCHIVED` |
| 商品 | `product.price_mode` | `FIXED` `STARTING` `CUSTOM` |
| 订单 | `orders.status` | `PENDING_PAY` `PAID` `COMPLETED` `CANCELLED` `CLOSED` `REFUNDING` `REFUNDED` |
| 支付 | `payment.status` | `INIT` `SUCCESS` `FAIL` `CLOSED` |
| 支付 | `payment.channel` | `ALIPAY` `WECHAT` `MANUAL` |
| 退款 | `refund.status` | `APPLIED` `APPROVED` `REFUNDING` `SUCCESS` `REJECTED` |
| 权益 | `entitlement.status` | `ACTIVE` `EXPIRED` `REVOKED` |
| 权益 | `entitlement.ent_type` | `ACCESS` `DOWNLOAD` `LEARN` |
| 服务订单 | `service_order.status` | `PENDING_PAY` `PAID` `IN_PROGRESS` `PENDING_ACCEPT` `COMPLETED` `AFTER_SALE` `CANCELLED` `REFUNDED` |
| 需求 | `demand.status` | `PENDING_REVIEW` `PENDING_CONTACT` `EVALUATING` `PENDING_QUOTE` `PENDING_CONFIRM` `IN_PROGRESS` `PENDING_ACCEPT` `COMPLETED` `REJECTED` `CANCELLED` `DISPUTED` |
| 报价 | `quote.status` | `DRAFT` `SENT` `ACCEPTED` `REJECTED` `EXPIRED` |
| 项目 | `project.status` | `NOT_STARTED` `IN_PROGRESS` `PENDING_ACCEPT` `COMPLETED` `ARCHIVED` |
| 里程碑 | `milestone.status` | `PENDING` `DELIVERING` `DELIVERED` `ACCEPTED` `REJECTED` |
| 反馈 | `feedback.status` | `PENDING` `PROCESSING` `RESOLVED` `CLOSED` |

> 状态流转**必须在服务层集中校验**（建议每个实体一个 `XxxStateMachine` 类，`Map<当前状态, Set<允许的下一状态>>`），禁止在 Controller 里散落 `if (status == ...)`。

---

## 十四、索引与性能要点

| 场景 | 索引 | 说明 |
|---|---|---|
| 我的订单列表 | `orders(user_id, create_time)` | 覆盖排序，避免 filesort |
| 订单超时关闭任务 | `orders(status, pay_expire_time)` | 定时扫描 `PENDING_PAY` 且已过期 |
| 权益校验（最高频） | `entitlement(user_id, status)` + 唯一键 | 每次访问付费内容都会查，必须有索引 |
| 商品列表页 | `product(product_type, status)` | 分类 + 上架过滤 |
| 需求池 | `demand(status)` | 后台按状态筛选 |
| 下载审计 | `download_record(user_id, create_time)` | 用户下载历史 |
| 收藏列表 | 唯一键 `uk_user_target` | 天然覆盖 |
| 未读消息数 | `message(user_id, is_read)` | 前台顶部红点高频查询 |

**缓存策略**（沿用现有 Redis 体系）

| 键 | 内容 | 失效 |
|---|---|---|
| `product:{id}` | 商品详情 | 后台改商品时删 |
| `product:list:{type}:{page}` | 列表页 | TTL 5 分钟 |
| `entitlement:{userId}` | 用户权益集合（Set） | 发放/撤销时删 |
| `order:expire` | 待关闭订单（ZSet，按过期时间） | 定时任务消费 |

> `entitlement:{userId}` 是性能关键：付费内容每次访问都要鉴权，若每页都查库会成为瓶颈。用 Set 缓存用户全部 `product_id`，命中则 O(1) 判断。

---

## 十五、分阶段迁移顺序

| 阶段 | 新建表 | 改造表 | 说明 |
|---|---|---|---|
| **V1.0** | `favorite` `like_record` `message` `feedback` | `blog` `category` `comment` `moment` `user` | 无交易逻辑，风险最低，可先行 |
| **V1.1a** | `file` `file_version` `download_record` `product` `product_category` `product_price` `product_article` `course` `lesson` `product_resource` | — | 只有"建商品"，不涉及钱 |
| **V1.1b** | `orders` `order_item` `payment` `refund` `entitlement` `entitlement_log` | — | 涉及资金，必须先做幂等与对账 |
| **V1.2** | `service_item` `service_order` `delivery` `demand` `quote` `project` `milestone` | — | 无支付也可先跑需求收集 |
| **V1.3** | AI 域 5 张 | — | 依赖 RAG 选型 |

**执行原则**

1. 每阶段只加表，不改已有表结构（除 V1.0 的字段新增），保证可回滚
2. `user.role` 迁移必须单独一次发布，且准备好回滚脚本
3. 涉及金额的表（`orders` / `payment` / `refund`）上线前必须有：唯一键、状态机、对账 SQL

---

## 十六、与现有表的关联关系

无物理外键，以下为**逻辑关联**，需在 Service 层保证一致性。

```
user 1──N blog                （blog.user_id）
user 1──N comment             （comment.user_id，V1.0 新增）
user 1──N favorite            （favorite.user_id）
user 1──N orders              （orders.user_id）
user 1──N entitlement         （entitlement.user_id）
user 1──N demand              （demand.user_id）

product 1──1 course           （course.product_id）
product 1──N lesson           （经 course 中转）
product 1──N product_article  （关联 blog.id）
product 1──N product_resource （关联 file.id）
product 1──1 service_item     （服务类商品）
product 1──N entitlement

orders 1──N order_item
orders 1──1 payment           （payment.biz_no = orders.order_no）
orders 1──N refund

demand 1──N quote
demand 1──N project
project 1──N milestone
project 1──N delivery
service_order 1──N delivery

file 1──N file_version
file 1──N download_record
```

**一致性保障建议**

- 涉及多表的写操作（下单、支付回调、退款）统一加 `@Transactional`
- 支付回调先落 `<biz_no, trade_no>` 幂等表（已内置于 `payment` 唯一键）
- 定期对账任务：`orders.pay_amount` vs `payment.amount` vs `refund.amount` 三方比对，差异写告警

---

## 十七、待确认项

以下需要你拍板，会影响 DDL 细节：

| # | 问题 | 影响 |
|---|---|---|
| 1 | 支付渠道先接哪个？（支付宝 / 微信 / 先手动记账） | `payment.channel` 枚举与回调实现 |
| 2 | 是否需要发票/合同字段？ | 需给 `orders` 加 `invoice_*` 字段 |
| 3 | 课程视频存哪里？（本地/MinIO/对象存储+CDN） | `lesson.video_file_id` 的存储策略与播放鉴权 |
| 4 | 商品是否支持多规格（如课程分个人/企业授权） | 若需要，`order_item` 要加 `sku_id`，并新增 `product_sku` |
| 5 | 需求附件是否允许匿名查看？ | `demand.attachment_file_id` 的 `file.is_public` 策略 |
| 6 | 是否保留现有 `friend`（友链）功能 | 影响后台「社区管理」菜单结构 |
