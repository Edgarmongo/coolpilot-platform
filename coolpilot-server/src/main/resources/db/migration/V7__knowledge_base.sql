-- ============================================================
-- V7：V1.3 知识库索引（第一阶段：检索 + 引用）
--
-- 决策见 docs/ADR-0001-VECTOR-STORE.md：
--   向量直接存 MySQL（MEDIUMBLOB + dim + model），进程内做余弦检索。
--   语料 1e4 块时内存约 20MB、单次检索毫秒级，不值得为它引入独立向量库。
--
-- 权限字段（is_public / product_id）冗余在 chunk 上：
--   检索时先按「公开 或 已购商品」筛候选，从源头避免越权召回，
--   而不是先检索再过滤（那会把不该出现的片段读进内存）。
-- ============================================================

CREATE TABLE `kb_knowledge_base` (
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `name`        varchar(128) NOT NULL COMMENT '知识库名称',
    `description` varchar(500) NOT NULL DEFAULT '' COMMENT '说明',
    `create_time` datetime     NOT NULL COMMENT '创建时间',
    `update_time` datetime     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '知识库';

INSERT INTO `kb_knowledge_base` (`id`, `name`, `description`, `create_time`, `update_time`)
VALUES (1, '默认知识库', '站点公开内容与已购内容的统一索引', NOW(), NOW());

CREATE TABLE `kb_document` (
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `kb_id`       bigint       NOT NULL DEFAULT 1 COMMENT '知识库',
    `source_type` varchar(16)  NOT NULL COMMENT 'BLOG/PRODUCT/LESSON',
    `source_id`   bigint       NOT NULL COMMENT '来源主键',
    `product_id`  bigint       NULL DEFAULT NULL COMMENT '所属商品；空表示公开内容',
    `title`       varchar(255) NOT NULL COMMENT '标题（引用展示用）',
    `url`         varchar(255) NOT NULL DEFAULT '' COMMENT '前台链接',
    `is_public`   bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否公开可检索',
    `status`      varchar(16)  NOT NULL DEFAULT 'INDEXED' COMMENT 'INDEXED/FAILED',
    `chunk_count` int          NOT NULL DEFAULT 0 COMMENT '分块数',
    `update_time` datetime     NOT NULL COMMENT '索引时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_source`(`source_type`, `source_id`) USING BTREE,
    INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '知识库文档';

CREATE TABLE `kb_chunk` (
    `id`          bigint      NOT NULL AUTO_INCREMENT,
    `doc_id`      bigint      NOT NULL COMMENT '所属文档',
    `kb_id`       bigint      NOT NULL DEFAULT 1 COMMENT '知识库',
    `chunk_index` int         NOT NULL COMMENT '文档内序号',
    `content`     text        NOT NULL COMMENT '片段原文',
    `product_id`  bigint      NULL DEFAULT NULL COMMENT '权限归属商品',
    `is_public`   bit(1)      NOT NULL DEFAULT b'1' COMMENT '是否公开',
    `embedding`   mediumblob  NOT NULL COMMENT 'float32 小端向量',
    `dim`         int         NOT NULL COMMENT '维度',
    `model`       varchar(64) NOT NULL DEFAULT '' COMMENT '向量模型标识',
    `create_time` datetime    NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_doc`(`doc_id`) USING BTREE,
    INDEX `idx_perm`(`is_public`, `product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '知识库分块';
