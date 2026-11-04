-- ============================================================
-- V8：专题（合集）
--
-- 目的：把系列文章组织成可分享的入口，也是付费专栏的天然载体。
--   前台 /topic/{slug} 按顺序列出文章，形成「读一篇 → 顺链读完整系列」的路径。
--
-- 两个刻意的设计：
--   1. slug 唯一且必填：专题要被分享、被 SEO 收录，靠 id 的链接没有意义
--   2. topic_article 用 (topic_id, blog_id) 联合主键 + sort：
--      同一篇文章可以进多个专题，排序按编辑指定顺序，不依赖发布时间
-- ============================================================

CREATE TABLE `topic` (
    `id`           bigint       NOT NULL AUTO_INCREMENT,
    `slug`         varchar(120) NOT NULL COMMENT 'URL 别名，全局唯一',
    `title`        varchar(120) NOT NULL COMMENT '专题名称',
    `description`  varchar(500) NOT NULL DEFAULT '' COMMENT '简介，列表与分享卡片用',
    `cover`        varchar(255) NOT NULL DEFAULT '' COMMENT '封面图地址',
    `is_published` bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否公开',
    `sort`         int          NOT NULL DEFAULT 0 COMMENT '排序，越大越靠前',
    `create_time`  datetime     NOT NULL COMMENT '创建时间',
    `update_time`  datetime     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_slug`(`slug`) USING BTREE,
    INDEX `idx_published_sort`(`is_published`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '专题';

CREATE TABLE `topic_article` (
    `topic_id` bigint NOT NULL COMMENT '专题',
    `blog_id`  bigint NOT NULL COMMENT '文章',
    `sort`     int    NOT NULL DEFAULT 0 COMMENT '专题内顺序',
    PRIMARY KEY (`topic_id`, `blog_id`) USING BTREE,
    INDEX `idx_blog`(`blog_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '专题文章关联';
