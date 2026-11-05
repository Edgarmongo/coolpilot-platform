-- ============================================================
-- V18：站长配置内容支持英文
--
-- 界面文案走 i18n 语言包，但导航、站点名、页脚这些是「站长配置的内容」，
-- 语言包里没有它们，只能在数据行上多存一份英文。
--
-- 两个刻意的决定：
--   1) 英文列允许为空。没填就回落到中文原文 —— 英文站少一句翻译，
--      总比整块留白好；后台也不用强迫站长一次填完。
--   2) 不新建翻译表。要翻的只是几个短字段，一张 key-value 式的
--      翻译表反而让「读一个导航项」变成两次查询。
--
-- 关于页正文暂不在此列：那段是 Markdown，翻译属于内容工作，
-- 等有英文稿再单独加列，不在本期混进来。
-- ============================================================

ALTER TABLE `nav_menu`
    ADD COLUMN `label_en` varchar(20) NULL COMMENT '英文菜单文字，留空则用 label' AFTER `label`;

ALTER TABLE `site_setting`
    ADD COLUMN `value_en` text NULL COMMENT '英文值，留空则用 value' AFTER `value`;

-- 初始英文与当前中文一一对应，避免上线后英文站还是中文
UPDATE `nav_menu` SET `label_en` = 'Home'      WHERE `path` = '/';
UPDATE `nav_menu` SET `label_en` = 'Archive'   WHERE `path` = '/archives';
UPDATE `nav_menu` SET `label_en` = 'Topics'    WHERE `path` = '/topic';
UPDATE `nav_menu` SET `label_en` = 'Moments'   WHERE `path` = '/moments';
UPDATE `nav_menu` SET `label_en` = 'Links'     WHERE `path` = '/friends';
UPDATE `nav_menu` SET `label_en` = 'About'     WHERE `path` = '/about';
UPDATE `nav_menu` SET `label_en` = 'Knowledge' WHERE `path` = '/knowledge';
UPDATE `nav_menu` SET `label_en` = 'Services'  WHERE `path` = '/service';
UPDATE `nav_menu` SET `label_en` = 'Ask AI'    WHERE `path` = '/ask';
UPDATE `nav_menu` SET `label_en` = 'Search'    WHERE `path` = '/search';

UPDATE `site_setting` SET `value_en` = 'CoolSkyAI' WHERE `name_en` = 'blogName';
UPDATE `site_setting` SET `value_en` = 'CP' WHERE `name_en` = 'brandMark';
UPDATE `site_setting` SET `value_en` = 'CoolSkyAI Platform' WHERE `name_en` = 'footerText';
UPDATE `site_setting` SET `value_en` = 'Engineering, knowledge and services' WHERE `name_en` = 'footerTagline';
