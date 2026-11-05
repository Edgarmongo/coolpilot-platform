-- ============================================================
-- V15：前台导航菜单可配置
--
-- 之前导航写死在 coolpilot-web/layouts/default.vue，改一个字都要重新发布前台。
-- 菜单天然是「一组有序条目」，塞进 site_setting 的 key-value 只能是 JSON 字符串，
-- 后台改起来比改代码还难，所以单建一张表。
--
-- 几个刻意的决定：
--   1) path 同时承载站内路径与外链（http 开头即外链），不另设 type 列 ——
--      多一列就多一处「两个字段互相矛盾」的可能。
--   2) visible 用 bit，与库里其他「显示/隐藏」列保持一致。
--   3) sort 升序（0 在最前）。与专题的降序不同，导航从左到右是升序直觉。
--   4) 初始数据即当前写死的 10 项，上线后菜单不会突然变样，
--      站长是在「已经是对的」基础上做修改。
-- ============================================================

CREATE TABLE IF NOT EXISTS `nav_menu`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `label`       varchar(20)  NOT NULL COMMENT '菜单文字，前台直接显示',
    `path`        varchar(255) NOT NULL COMMENT '站内路径以 / 开头；http(s) 开头视为外链',
    `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序，升序',
    `visible`     bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否在前台显示',
    `target`      varchar(10)  NOT NULL DEFAULT '_self' COMMENT '_self 本页打开 / _blank 新窗口',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_visible_sort` (`visible`, `sort`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='前台导航菜单';

INSERT INTO `nav_menu` (`label`, `path`, `sort`, `visible`, `target`)
VALUES ('首页', '/', 10, b'1', '_self'),
       ('归档', '/archives', 20, b'1', '_self'),
       ('专题', '/topic', 30, b'1', '_self'),
       ('动态', '/moments', 40, b'1', '_self'),
       ('友链', '/friends', 50, b'1', '_self'),
       ('关于', '/about', 60, b'1', '_self'),
       ('知识付费', '/knowledge', 70, b'1', '_self'),
       ('技术服务', '/service', 80, b'1', '_self'),
       ('问答', '/ask', 90, b'1', '_self'),
       ('搜索', '/search', 100, b'1', '_self');
