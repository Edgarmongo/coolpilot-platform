-- ============================================================
-- V25：知识星球配置（CP-WEB-03）
--
-- 只有 6 个字段、一条记录，不值得单建一张表，直接复用 site_setting，
-- 用 type=6 作为新分组。好处是后台「站点设置」页会按库里实际的 type
-- 自动生成 tab（SiteSetting.vue 的 groups 是动态分组的），
-- 后端读写接口也不用新增 —— 只插数据就能用。
--
-- name_en 是识别键（与 type=2 的 github/telegram 同理），后台的图片识别
-- 也是按 name_en 匹配 IMAGE_KEYS，所以封面和二维码必须叫 planetCover /
-- planetQr，并且要在 SiteSetting.vue 的 IMAGE_KEYS 里登记，才会出现上传按钮。
--
-- 明确边界：本表只承载「介绍页要展示什么」，不含会员、支付、内容权限。
-- 交易与成员管理全部由知识星球官方完成，本站只做介绍与跳转。
-- ============================================================

INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球名称', 'planetName', '', '', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetName');

INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球封面', 'planetCover', '', '', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetCover');

INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球介绍', 'planetIntro', '', '', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetIntro');

INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球链接', 'planetLink', '', '', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetLink');

INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球二维码', 'planetQr', '', '', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetQr');

-- 启用开关默认关闭：站长填完内容再打开，避免上线时前台出现一个空的星球页
INSERT INTO `site_setting` (`name_zh`, `name_en`, `value`, `value_en`, `type`)
SELECT '知识星球启用', 'planetEnabled', '0', '0', 6
WHERE NOT EXISTS (SELECT 1 FROM `site_setting` WHERE `type` = 6 AND `name_en` = 'planetEnabled');
