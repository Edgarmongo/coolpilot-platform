-- ============================================================
-- V23：社交账号表（CP-WEB-04）
--
-- 背景：站点设置 type=2 里其实已经存了 GitHub / Telegram / QQ / bilibili /
-- 网易云 / 邮箱 六条账号，但它是「一行一个链接」的 KV，缺三样东西：
--   1) 二维码（引流的真正落点，尤其是微信）
--   2) 排序（前端要按站长心里的重要程度排）
--   3) 启用开关（没运营的平台先关掉，不要露出来）
--
-- 所以这里建结构化表，并把 type=2 的存量迁过来，避免出现
-- 「我明明配过 GitHub，新页面却是空的」。
--
-- 设计决定：
--   1) qr_url 存图片地址，不存 file.id —— 与项目现有图片字段保持一致
--      （product.cover、site_setting 里的收款码/头像等都是存 URL）。
--      后台上传接口 isPublic=true 时直接返回可访问地址，落库即可用；
--      存 file.id 的话前台每次渲染都要多一次转换，收益不抵成本。
--   2) platform 用枚举字符串，不用数字 —— 后台/前台/日志里都可读，
--      加平台只要加一个值，不需要同步三处的字典。
--   3) 迁移不删 type=2 的旧行：历史数据留着不影响任何逻辑，删了反而丢信息；
--      前台已改为读新表，旧行自然不再被消费。
--   4) 全部 INSERT ... WHERE NOT EXISTS，重复执行不会插重复数据。
--   5) 迁移时的 enabled 判断：值为空、或以 '/' 结尾、或以 '=' 结尾，都视为
--      「还没填完整」。库里 bilibili 存的是 https://space.bilibili.com/、
--      网易云存的是 ...?id=、QQ 存的是 ...?IDKEY= —— 都是没有账号 ID 的
--      占位链接，直接展示出去就是坏链，所以先置为不展示，等站长补全再打开。
-- ============================================================

CREATE TABLE IF NOT EXISTS `social_account`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT,
    `platform`     varchar(32) NOT NULL COMMENT 'GITHUB/TELEGRAM/QQ/BILIBILI/NETEASE/EMAIL/WECHAT/DOUYIN/XIAOHONGSHU/ZHIHU/CUSTOM',
    `account_name` varchar(64) NOT NULL COMMENT '展示用账号名',
    `intro`        varchar(255)         DEFAULT NULL COMMENT '一句简介',
    `home_url`     varchar(512)         DEFAULT NULL COMMENT '主页链接，纯二维码平台可留空',
    `qr_url`      varchar(512)         DEFAULT NULL COMMENT '二维码图片地址',
    `icon`         varchar(32)          DEFAULT NULL COMMENT '前端图标标识',
    `sort`         int         NOT NULL DEFAULT 0 COMMENT '升序',
    `enabled`      bit(1)      NOT NULL DEFAULT b'1' COMMENT '是否在前台展示',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_enabled_sort` (`enabled`, `sort`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='社交账号（关注我）';

-- ---------------- 从 site_setting type=2 迁移存量 ----------------
-- name_en 是识别键，库里是小写（github / telegram / qq / bilibili / netease / email）

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'GITHUB', 'GitHub', NULLIF(TRIM(`value`), ''), 'github', 10,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'github'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'GITHUB');

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'TELEGRAM', 'Telegram', NULLIF(TRIM(`value`), ''), 'telegram', 20,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'telegram'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'TELEGRAM');

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'QQ', 'QQ', NULLIF(TRIM(`value`), ''), 'qq', 30,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'qq'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'QQ');

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'BILIBILI', 'bilibili', NULLIF(TRIM(`value`), ''), 'bilibili', 40,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'bilibili'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'BILIBILI');

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'NETEASE', '网易云音乐', NULLIF(TRIM(`value`), ''), 'netease', 50,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'netease'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'NETEASE');

INSERT INTO `social_account` (`platform`, `account_name`, `home_url`, `icon`, `sort`, `enabled`)
SELECT 'EMAIL', '邮箱', NULLIF(TRIM(`value`), ''), 'email', 60,
       IF(TRIM(`value`) = '' OR RIGHT(TRIM(`value`), 1) IN ('/', '='), b'0', b'1')
FROM `site_setting`
WHERE `type` = 2 AND `name_en` = 'email'
  AND NOT EXISTS (SELECT 1 FROM `social_account` WHERE `platform` = 'EMAIL');
