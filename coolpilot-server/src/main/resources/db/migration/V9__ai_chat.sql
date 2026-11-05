-- ============================================================
-- V9：AI 问答会话
--
-- 为什么落库：问答的价值一半在答案，一半在「用户到底在问什么」。
-- 这些问题是选题和商品需求的直接线索，只放在前端内存里就浪费了。
--
-- citations 存 JSON 数组（[{"index":1,"title":...,"url":...}]）：
-- 引用是「回答那一刻的检索快照」，文章标题以后改了也不该影响历史记录，
-- 所以不做外键关联，直接留副本。
-- ============================================================

CREATE TABLE `ai_conversation` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `user_id`       bigint       NOT NULL COMMENT '提问用户',
    `title`         varchar(128) NOT NULL DEFAULT '' COMMENT '取首个问题前 40 字',
    `message_count` int          NOT NULL DEFAULT 0 COMMENT '消息条数（含问答）',
    `create_time`   datetime     NOT NULL,
    `update_time`   datetime     NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_user_time`(`user_id`, `update_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = 'AI 问答会话';

CREATE TABLE `ai_message` (
    `id`              bigint      NOT NULL AUTO_INCREMENT,
    `conversation_id` bigint      NOT NULL COMMENT '所属会话',
    `role`            varchar(16) NOT NULL COMMENT 'USER / ASSISTANT',
    `content`         mediumtext  NOT NULL COMMENT '正文；助手内容已去除引用标记以外的排版',
    `citations`       text        NULL COMMENT '引用快照 JSON',
    `model`           varchar(64) NOT NULL DEFAULT '' COMMENT '生成该条回答的模型',
    `create_time`     datetime    NOT NULL,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_conversation`(`conversation_id`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = 'AI 问答消息';
