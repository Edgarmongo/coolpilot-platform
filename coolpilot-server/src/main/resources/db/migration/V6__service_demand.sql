-- ============================================================
-- V6：V1.2 技术服务与需求中心
--
-- 六张业务表：
--   service_item  服务商品扩展（交付周期 / 范围 / 流程 / FAQ）
--   demand        客户需求
--   quote         报价单（同一需求多版本）
--   project       项目（一个需求可拆多期）
--   milestone     里程碑
--   delivery      交付物（文件 / 链接 / 文本）
--
-- 另加一张 service_event：状态流水。
-- PRD 6.4 第 5 条要求「每个状态变更都有操作记录（谁、何时、从什么到什么）」，
-- 现有 operation_log 只记后台接口调用，记不了用户侧的「接受报价 / 验收通过」，
-- 因此单独建一张按业务对象归档的流水表。
--
-- 与 DATA-MODEL.md 的差异（有意为之，均已同步文档）：
--   1. 不建 service_order。固定价服务的支付复用 orders + payment + refund，
--      再建一张订单表只会让对账分叉；project.order_no 用于关联已支付的订单。
--   2. project.demand_id 允许为空：固定价服务可以直接从订单立项，没有需求单。
--   3. demand 增加 product_id：用户从某个服务商品页发起需求时带上，便于定价与交付周期推算。
-- ============================================================

CREATE TABLE `service_item` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `product_id`    bigint       NOT NULL COMMENT '关联 product（type=SERVICE）',
    `delivery_days` int          NOT NULL DEFAULT 0 COMMENT '预计交付天数，0 表示面议',
    `scope`         text         NULL COMMENT '服务范围说明',
    `process`       text         NULL COMMENT '服务流程说明',
    `faq`           text         NULL COMMENT '常见问题',
    `create_time`   datetime     NOT NULL COMMENT '创建时间',
    `update_time`   datetime     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务商品扩展';

CREATE TABLE `demand` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT,
    `demand_no`          varchar(32)   NOT NULL COMMENT '对外需求号',
    `user_id`            bigint        NOT NULL COMMENT '提交人',
    `product_id`         bigint        NULL DEFAULT NULL COMMENT '来源服务商品，可为空',
    `title`              varchar(255)  NOT NULL COMMENT '需求标题',
    `project_type`       varchar(32)   NOT NULL COMMENT 'BACKEND/AI/DEPLOY/CONSULT/OTHER',
    `tech_stack`         varchar(500)  NOT NULL DEFAULT '' COMMENT '技术栈要求',
    `budget_min`         decimal(10,2) NULL DEFAULT NULL COMMENT '预算下限',
    `budget_max`         decimal(10,2) NULL DEFAULT NULL COMMENT '预算上限',
    `budget_negotiable`  bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否面议',
    `expect_date`        date          NULL DEFAULT NULL COMMENT '期望交付日期',
    `description`        longtext      NOT NULL COMMENT '需求描述',
    `attachment_file_id` bigint        NULL DEFAULT NULL COMMENT '附件',
    `contact`            varchar(255)  NOT NULL DEFAULT '' COMMENT '联系方式（仅平台所有者可见）',
    `status`             varchar(32)   NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '见状态机',
    `reject_reason`      varchar(500)  NOT NULL DEFAULT '' COMMENT '拒绝原因',
    `audit_time`         datetime      NULL DEFAULT NULL COMMENT '审核时间',
    `create_time`        datetime      NOT NULL COMMENT '创建时间',
    `update_time`        datetime      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_demand_no`(`demand_no`) USING BTREE,
    INDEX `idx_user`(`user_id`, `create_time`) USING BTREE,
    INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '客户需求';

CREATE TABLE `quote` (
    `id`          bigint        NOT NULL AUTO_INCREMENT,
    `quote_no`    varchar(32)   NOT NULL COMMENT '报价单号',
    `demand_id`   bigint        NOT NULL COMMENT '需求',
    `version`     int           NOT NULL DEFAULT 1 COMMENT '报价版本，支持多次改价',
    `amount`      decimal(10,2) NOT NULL COMMENT '报价金额',
    `currency`    varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `scope`       text          NULL COMMENT '工作范围说明',
    `valid_until` datetime      NULL DEFAULT NULL COMMENT '报价有效期',
    `status`      varchar(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SENT/ACCEPTED/REJECTED/EXPIRED',
    `create_time` datetime      NOT NULL COMMENT '创建时间',
    `update_time` datetime      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_quote_no`(`quote_no`) USING BTREE,
    UNIQUE INDEX `uk_demand_version`(`demand_id`, `version`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '报价单';

CREATE TABLE `project` (
    `id`          bigint        NOT NULL AUTO_INCREMENT,
    `project_no`  varchar(32)   NOT NULL COMMENT '项目编号',
    `demand_id`   bigint        NULL DEFAULT NULL COMMENT '来源需求，一个需求可拆多期',
    `quote_id`    bigint        NULL DEFAULT NULL COMMENT '确认的报价',
    `order_no`    varchar(32)   NULL DEFAULT NULL COMMENT '固定价服务关联的订单号',
    `user_id`     bigint        NOT NULL COMMENT '客户',
    `name`        varchar(255)  NOT NULL COMMENT '项目名',
    `amount`      decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '合同金额',
    `status`      varchar(16)   NOT NULL DEFAULT 'NOT_STARTED' COMMENT 'NOT_STARTED/IN_PROGRESS/PENDING_ACCEPT/COMPLETED/ARCHIVED',
    `start_date`  date          NULL DEFAULT NULL COMMENT '开始日期',
    `end_date`    date          NULL DEFAULT NULL COMMENT '结束日期',
    `create_time` datetime      NOT NULL COMMENT '创建时间',
    `update_time` datetime      NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_project_no`(`project_no`) USING BTREE,
    INDEX `idx_demand`(`demand_id`) USING BTREE,
    INDEX `idx_user`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '项目';

CREATE TABLE `milestone` (
    `id`             bigint        NOT NULL AUTO_INCREMENT,
    `project_id`     bigint        NOT NULL COMMENT '项目',
    `name`           varchar(255)  NOT NULL COMMENT '里程碑名称',
    `sort`           int           NOT NULL DEFAULT 0 COMMENT '顺序',
    `amount`         decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '该阶段金额',
    `due_date`       date          NULL DEFAULT NULL COMMENT '截止日期',
    `status`         varchar(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DELIVERING/DELIVERED/ACCEPTED/REJECTED',
    `delivered_time` datetime      NULL DEFAULT NULL COMMENT '交付时间',
    `accepted_time`  datetime      NULL DEFAULT NULL COMMENT '验收时间',
    `remark`         varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_project_sort`(`project_id`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '项目里程碑';

CREATE TABLE `delivery` (
    `id`               bigint       NOT NULL AUTO_INCREMENT,
    `service_order_id` bigint       NOT NULL DEFAULT 0 COMMENT '服务订单；当前复用 orders，此处存 0',
    `project_id`       bigint       NOT NULL COMMENT '所属项目',
    `milestone_id`     bigint       NULL DEFAULT NULL COMMENT '所属里程碑，可为空',
    `delivery_type`    varchar(16)  NOT NULL DEFAULT 'FILE' COMMENT 'FILE/LINK/TEXT',
    `file_id`          bigint       NULL DEFAULT NULL COMMENT '交付文件',
    `content`          text         NULL COMMENT '交付说明/链接',
    `delivered_by`     bigint       NOT NULL COMMENT '交付人',
    `delivered_time`   datetime     NOT NULL COMMENT '交付时间',
    `accepted_time`    datetime     NULL DEFAULT NULL COMMENT '验收时间',
    `status`           varchar(16)  NOT NULL DEFAULT 'DELIVERED' COMMENT 'DELIVERED/ACCEPTED/REJECTED',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_order`(`service_order_id`) USING BTREE,
    INDEX `idx_project`(`project_id`) USING BTREE,
    INDEX `idx_milestone`(`milestone_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务交付物';

CREATE TABLE `service_event` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `biz_type`      varchar(16)  NOT NULL COMMENT 'DEMAND/QUOTE/PROJECT/MILESTONE/DELIVERY',
    `biz_id`        bigint       NOT NULL COMMENT '业务对象ID',
    `biz_no`        varchar(32)  NOT NULL DEFAULT '' COMMENT '业务编号，便于按号检索',
    `from_status`   varchar(32)  NULL DEFAULT NULL COMMENT '变更前状态',
    `to_status`     varchar(32)  NULL DEFAULT NULL COMMENT '变更后状态',
    `action`        varchar(32)  NOT NULL COMMENT '动作：SUBMIT/REVIEW/QUOTE/SEND/ACCEPT/REJECT/DELIVER/PASS/CANCEL...',
    `operator_id`   bigint       NULL DEFAULT NULL COMMENT '操作人ID，0 表示系统',
    `operator_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '操作人显示名',
    `remark`        varchar(500) NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`   datetime     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_biz`(`biz_type`, `biz_id`, `id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '服务域状态流水';
