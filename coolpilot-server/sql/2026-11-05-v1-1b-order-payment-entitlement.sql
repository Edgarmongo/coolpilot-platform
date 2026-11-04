-- ============================================================
-- V1.1b 交易域 + 权益域
-- 日期：2026-11-05
-- 依据：docs/DATA-MODEL.md §八 §九
--
-- 设计约束（务必先读）：
-- 1. 订单 / 支付 / 权益三者分离：订单是交易契约，支付是渠道事实，权益是访问授权。
-- 2. 订单项存价格与标题快照，商品后续改价改名不影响历史订单。
-- 3. 发放权益必须由 payment 的条件更新驱动（影响行数为 1 才继续），保证回调/人工确认幂等。
-- 4. 撤销权益必须按 order_no 精确撤销，不能按 user_id + product_id 删。
-- ============================================================

-- ---------------- 交易域 ----------------
CREATE TABLE IF NOT EXISTS `orders` (
  `id`              bigint(0)     NOT NULL AUTO_INCREMENT,
  `order_no`        varchar(32)   NOT NULL COMMENT '对外订单号',
  `user_id`         bigint(0)     NOT NULL COMMENT '下单人',
  `biz_type`        varchar(16)   NOT NULL DEFAULT 'PRODUCT' COMMENT 'PRODUCT/SERVICE',
  `total_amount`    decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品总额',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `pay_amount`      decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付金额',
  `status`          varchar(20)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT 'PENDING_PAY/PENDING_CONFIRM/PAID/COMPLETED/CANCELLED/CLOSED/REFUNDING/REFUNDED',
  `pay_expire_time` datetime(0)   NULL DEFAULT NULL COMMENT '支付截止时间',
  `paid_time`       datetime(0)   NULL DEFAULT NULL COMMENT '支付时间',
  `cancel_time`     datetime(0)   NULL DEFAULT NULL COMMENT '取消时间',
  `remark`          varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
  `create_time`     datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`     datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_no`(`order_no`) USING BTREE,
  INDEX `idx_user_time`(`user_id`, `create_time`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_expire`(`status`, `pay_expire_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '订单';

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `order_id`       bigint(0)     NOT NULL COMMENT '订单',
  `product_id`     bigint(0)     NOT NULL COMMENT '商品',
  `product_no`     varchar(32)   NOT NULL DEFAULT '' COMMENT '商品编号快照',
  `product_type`   varchar(16)   NOT NULL COMMENT '类型快照',
  `title_snapshot` varchar(255)  NOT NULL COMMENT '标题快照',
  `cover_snapshot` varchar(500)  NOT NULL DEFAULT '' COMMENT '封面快照',
  `price_snapshot` decimal(10,2) NOT NULL COMMENT '成交单价快照',
  `quantity`       int(0)        NOT NULL DEFAULT 1 COMMENT '数量',
  `amount`         decimal(10,2) NOT NULL COMMENT '小计',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_order`(`order_id`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '订单项';

CREATE TABLE IF NOT EXISTS `payment` (
  `id`             bigint(0)     NOT NULL AUTO_INCREMENT,
  `payment_no`     varchar(32)   NOT NULL COMMENT '支付单号（商户生成的）',
  `biz_type`       varchar(16)   NOT NULL DEFAULT 'PRODUCT' COMMENT 'PRODUCT/SERVICE',
  `biz_no`         varchar(32)   NOT NULL COMMENT '关联订单号（orders.order_no 或 service_order.order_no）',
  `user_id`        bigint(0)     NOT NULL COMMENT '付款人',
  `channel`        varchar(24)   NOT NULL COMMENT 'MANUAL_WECHAT/MANUAL_ALIPAY/MANUAL_PAYPAL/ALIPAY/WECHAT/PAYPAL',
  `trade_no`       varchar(64)   NULL DEFAULT NULL COMMENT '渠道交易号，唯一，用于幂等',
  `amount`         decimal(10,2) NOT NULL COMMENT '支付金额',
  `status`         varchar(16)   NOT NULL DEFAULT 'INIT' COMMENT 'INIT/WAIT_CONFIRM/SUCCESS/FAIL/CLOSED',
  `paid_time`      datetime(0)   NULL DEFAULT NULL COMMENT '支付成功时间',
  `payer_name`     varchar(64)   NOT NULL DEFAULT '' COMMENT '人工模式下付款人姓名',
  `transfer_time`  datetime(0)   NULL DEFAULT NULL COMMENT '人工模式下用户自述转账时间',
  `proof_file_id`  bigint(0)     NULL DEFAULT NULL COMMENT '人工模式下付款截图文件',
  `confirm_user_id` bigint(0)    NULL DEFAULT NULL COMMENT '确认收款的操作人',
  `remark`         varchar(500)  NOT NULL DEFAULT '' COMMENT '处理备注',
  `notify_raw`     text          NULL COMMENT '渠道回调原文，便于排查',
  `create_time`    datetime(0)   NOT NULL COMMENT '创建时间',
  `update_time`    datetime(0)   NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_payment_no`(`payment_no`) USING BTREE,
  UNIQUE INDEX `uk_channel_trade`(`channel`, `trade_no`) USING BTREE,
  INDEX `idx_biz`(`biz_type`, `biz_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '支付单';

CREATE TABLE IF NOT EXISTS `refund` (
  `id`          bigint(0)     NOT NULL AUTO_INCREMENT,
  `refund_no`   varchar(32)   NOT NULL COMMENT '退款单号',
  `biz_type`    varchar(16)   NOT NULL DEFAULT 'PRODUCT' COMMENT 'PRODUCT/SERVICE',
  `biz_no`      varchar(32)   NOT NULL COMMENT '关联订单号',
  `payment_no`  varchar(32)   NOT NULL COMMENT '原支付单',
  `user_id`     bigint(0)     NOT NULL COMMENT '申请人',
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
  INDEX `idx_biz`(`biz_type`, `biz_no`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '退款单';

-- ---------------- 权益域 ----------------
CREATE TABLE IF NOT EXISTS `entitlement` (
  `id`          bigint(0)   NOT NULL AUTO_INCREMENT,
  `user_id`     bigint(0)   NOT NULL COMMENT '持有人',
  `product_id`  bigint(0)   NOT NULL COMMENT '商品',
  `order_no`    varchar(32) NULL DEFAULT NULL COMMENT '来源订单',
  `ent_type`    varchar(16) NOT NULL DEFAULT 'ACCESS' COMMENT 'ACCESS/DOWNLOAD/LEARN',
  `source`      varchar(16) NOT NULL DEFAULT 'PURCHASE' COMMENT 'PURCHASE/GIFT/TRIAL',
  `start_time`  datetime(0) NOT NULL COMMENT '生效时间',
  `expire_time` datetime(0) NULL DEFAULT NULL COMMENT '过期时间，NULL 为永久',
  `status`      varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
  `create_time` datetime(0) NOT NULL COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_product_type`(`user_id`, `product_id`, `ent_type`) USING BTREE,
  INDEX `idx_user_status`(`user_id`, `status`) USING BTREE,
  INDEX `idx_product`(`product_id`) USING BTREE,
  INDEX `idx_order`(`order_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '访问权益';

CREATE TABLE IF NOT EXISTS `entitlement_log` (
  `id`             bigint(0)   NOT NULL AUTO_INCREMENT,
  `entitlement_id` bigint(0)   NOT NULL COMMENT '权益ID',
  `user_id`        bigint(0)   NOT NULL COMMENT '持有人',
  `product_id`     bigint(0)   NOT NULL COMMENT '商品',
  `action`         varchar(16) NOT NULL COMMENT 'GRANT/REVOKE/EXPIRE/RENEW',
  `reason`         varchar(255) NOT NULL DEFAULT '' COMMENT '变更原因',
  `operator_id`    bigint(0)   NULL DEFAULT NULL COMMENT '操作人，系统触发为空',
  `create_time`    datetime(0) NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_ent`(`entitlement_id`) USING BTREE,
  INDEX `idx_user`(`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic COMMENT = '权益变更流水';

-- ---------------- 收款配置（写入站点设置，后台可改） ----------------
INSERT INTO `site_setting` (`name_en`, `name_zh`, `value`, `type`)
SELECT 'wechatQr', '微信收款码', '/img/wechat-qr.png', 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `name_en` = 'wechatQr');

INSERT INTO `site_setting` (`name_en`, `name_zh`, `value`, `type`)
SELECT 'alipayQr', '支付宝收款码', '/img/alipay-qr.png', 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `name_en` = 'alipayQr');

INSERT INTO `site_setting` (`name_en`, `name_zh`, `value`, `type`)
SELECT 'paypalUrl', 'PayPal 收款链接', 'https://paypal.me/yourname', 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `name_en` = 'paypalUrl');

INSERT INTO `site_setting` (`name_en`, `name_zh`, `value`, `type`)
SELECT 'payeeName', '收款人姓名', '', 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `name_en` = 'payeeName');
