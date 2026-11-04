-- ============================================================
-- V1.1a 文件服务 + 知识商品
-- 日期：2026-11-05
-- 依据：docs/DATA-MODEL.md §六 §七
-- ============================================================

-- ---------------- 文件域 ----------------
CREATE TABLE IF NOT EXISTS `file` (
  `id`            bigint(0)     NOT NULL AUTO_INCREMENT,
  `file_uuid`     varchar(64)   NOT NULL COMMENT '对外唯一标识，不暴露自增ID',
  `owner_id`      bigint(0)     NOT NULL COMMENT '上传者',
  `biz_type`      varchar(16)   NOT NULL DEFAULT 'OTHER' COMMENT 'AVATAR/PRODUCT/DEMAND/DELIVERY/OTHER',
  `original_name` varchar(255)  NOT NULL COMMENT '原始文件名',
  `storage_type`  varchar(16)   NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL/MINIO/S3',
  `bucket`        varchar(128)  NOT NULL DEFAULT '' COMMENT '存储桶',
  `object_key`    varchar(500)  NOT NULL COMMENT '对象键，不含域名',
  `file_size`     bigint(0)     NOT NULL DEFAULT 0 COMMENT '字节数',
  `mime_type`     varchar(128)  NOT NULL DEFAULT '' COMMENT 'MIME类型',
  `sha256`        varchar(64)   NOT NULL DEFAULT '' COMMENT '内容摘要',
  `is_public`     bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否公开可直读',
  `status`        varchar(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/ARCHIVED',
  `create_time`   datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`   datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_uuid`(`file_uuid`) USING BTREE,
  INDEX `idx_sha256`(`sha256`) USING BTREE,
  INDEX `idx_biz`(`biz_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '文件元数据';

CREATE TABLE IF NOT EXISTS `file_version` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `file_id`     bigint(0)     NOT NULL COMMENT '所属文件',
  `version`     int(0)        NOT NULL DEFAULT 1 COMMENT '版本号',
  `object_key`  varchar(500)  NOT NULL COMMENT '该版本对象键',
  `file_size`   bigint(0)     NOT NULL DEFAULT 0 COMMENT '字节数',
  `sha256`      varchar(64)   NOT NULL DEFAULT '' COMMENT '摘要',
  `change_log`  varchar(500)  NOT NULL DEFAULT '' COMMENT '变更说明',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_file_version`(`file_id`, `version`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '文件版本';

CREATE TABLE IF NOT EXISTS `download_record` (
  `id`              bigint(0)   NOT NULL AUTO_INCREMENT,
  `file_id`         bigint(0)   NOT NULL COMMENT '文件',
  `file_version_id` bigint(0)   NULL DEFAULT NULL COMMENT '版本',
  `user_id`         bigint(0)   NOT NULL COMMENT '下载人',
  `entitlement_id`  bigint(0)   NULL DEFAULT NULL COMMENT '凭哪个权益下载',
  `ip`              varchar(64) NOT NULL DEFAULT '' COMMENT '下载IP',
  `user_agent`      varchar(500) NOT NULL DEFAULT '' COMMENT 'UA',
  `create_time`     datetime(0) NOT NULL COMMENT '下载时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_file`(`file_id`) USING BTREE,
  INDEX `idx_user`(`user_id`, `create_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '下载记录';

-- ---------------- 商品域 ----------------
CREATE TABLE IF NOT EXISTS `product` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `product_no`     varchar(32)   NOT NULL COMMENT '对外商品编号',
  `product_type`   varchar(16)   NOT NULL COMMENT 'DOC/COURSE/COLUMN/CODE/SERVICE',
  `category_id`    bigint(0)     NOT NULL DEFAULT 0 COMMENT '商品分类',
  `title`          varchar(255)  NOT NULL COMMENT '标题',
  `subtitle`       varchar(255)  NOT NULL DEFAULT '' COMMENT '副标题',
  `cover`          varchar(500)  NOT NULL DEFAULT '' COMMENT '封面',
  `summary`        varchar(500)  NOT NULL DEFAULT '' COMMENT '列表摘要',
  `detail`         longtext      NULL COMMENT '详情（Markdown）',
  `price`          decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '当前售价',
  `original_price` decimal(10,2) NULL DEFAULT NULL COMMENT '划线原价',
  `price_mode`     varchar(16)   NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED/STARTING/CUSTOM',
  `sales_count`    int(0)        NOT NULL DEFAULT 0 COMMENT '销量',
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

CREATE TABLE IF NOT EXISTS `product_category` (
  `id`           bigint(0)    NOT NULL AUTO_INCREMENT,
  `name`         varchar(64)  NOT NULL COMMENT '分类名',
  `product_type` varchar(16)  NOT NULL COMMENT '所属类型',
  `icon`         varchar(255) NOT NULL DEFAULT '' COMMENT '图标',
  `sort`         int(0)       NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time`  datetime(0)  NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_type`(`product_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '商品分类';

CREATE TABLE IF NOT EXISTS `product_price` (
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

CREATE TABLE IF NOT EXISTS `course` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `product_id`     bigint(0)     NOT NULL COMMENT '关联商品',
  `description`    varchar(1000) NOT NULL DEFAULT '' COMMENT '课程介绍',
  `lesson_count`   int(0)        NOT NULL DEFAULT 0 COMMENT '课时数',
  `total_duration` int(0)        NOT NULL DEFAULT 0 COMMENT '总时长（分钟）',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`    datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '课程扩展';

CREATE TABLE IF NOT EXISTS `lesson` (
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

CREATE TABLE IF NOT EXISTS `product_article` (
  `id`          bigint(0)   NOT NULL AUTO_INCREMENT,
  `product_id`  bigint(0)   NOT NULL COMMENT '专栏商品',
  `blog_id`     bigint(0)   NOT NULL COMMENT '文章',
  `sort`        int(0)      NOT NULL DEFAULT 0 COMMENT '阅读顺序',
  `create_time` datetime(0) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_product_blog`(`product_id`, `blog_id`) USING BTREE,
  INDEX `idx_blog`(`blog_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '专栏-文章关联';

CREATE TABLE IF NOT EXISTS `product_resource` (
  `id`            bigint(0)     NOT NULL AUTO_INCREMENT,
  `product_id`    bigint(0)     NOT NULL COMMENT '商品',
  `resource_type` varchar(16)   NOT NULL COMMENT 'DOC/CODE',
  `file_id`       bigint(0)     NOT NULL COMMENT '文件',
  `display_name`  varchar(255)  NOT NULL COMMENT '下载展示名',
  `version`       varchar(32)   NOT NULL DEFAULT 'v1' COMMENT '资源版本',
  `is_latest`     bit(1)        NOT NULL DEFAULT b'1' COMMENT '是否最新版本',
  `sort`          int(0)        NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time`   datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '商品可下载资源';
