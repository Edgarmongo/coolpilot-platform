-- ============================================================
-- V1.0 社区模块建表
-- 日期：2026-11-04
-- 依据：docs/DATA-MODEL.md §五
-- ============================================================

CREATE TABLE IF NOT EXISTS `favorite` (
  `id`          bigint(0)    NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)    NOT NULL COMMENT '收藏人',
  `target_type` varchar(16)  NOT NULL COMMENT '目标类型：ARTICLE/MOMENT/PRODUCT',
  `target_id`   bigint(0)    NOT NULL COMMENT '目标ID',
  `create_time` datetime(0)  NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '收藏';

CREATE TABLE IF NOT EXISTS `like_record` (
  `id`          bigint(0)   NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)   NOT NULL COMMENT '点赞人',
  `target_type` varchar(16) NOT NULL COMMENT '目标类型：ARTICLE/MOMENT/COMMENT',
  `target_id`   bigint(0)   NOT NULL COMMENT '目标ID',
  `create_time` datetime(0) NOT NULL COMMENT '点赞时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_target`(`user_id`, `target_type`, `target_id`) USING BTREE,
  INDEX `idx_target`(`target_type`, `target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '点赞记录';

CREATE TABLE IF NOT EXISTS `message` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)     NOT NULL COMMENT '接收人',
  `msg_type`    varchar(16)   NOT NULL COMMENT '类型：SYSTEM/ORDER/DEMAND/COMMENT/REPLY',
  `title`       varchar(255)  NOT NULL COMMENT '消息标题',
  `content`     varchar(1000) NOT NULL DEFAULT '' COMMENT '消息内容',
  `biz_type`    varchar(16)   NULL DEFAULT NULL COMMENT '业务类型：ORDER/DEMAND/ARTICLE',
  `biz_id`      bigint(0)     NULL DEFAULT NULL COMMENT '业务ID，用于跳转',
  `link_url`    varchar(500)  NULL DEFAULT NULL COMMENT '跳转地址',
  `is_read`     bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否已读',
  `read_time`   datetime(0)   NULL DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_read`(`user_id`, `is_read`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '站内消息';

CREATE TABLE IF NOT EXISTS `feedback` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)     NULL DEFAULT NULL COMMENT '提交人，未登录可为空',
  `content`     varchar(2000) NOT NULL COMMENT '反馈内容',
  `images`      varchar(2000) NOT NULL DEFAULT '' COMMENT '截图URL，逗号分隔',
  `contact`     varchar(255)  NOT NULL DEFAULT '' COMMENT '联系方式',
  `status`      varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/RESOLVED/CLOSED',
  `reply`       varchar(2000) NULL DEFAULT NULL COMMENT '回复内容',
  `reply_time`  datetime(0)   NULL DEFAULT NULL COMMENT '回复时间',
  `create_time` datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time` datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '技术问题反馈';
