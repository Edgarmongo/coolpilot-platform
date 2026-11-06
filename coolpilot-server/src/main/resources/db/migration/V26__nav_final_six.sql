-- ============================================================
-- V26：导航收敛为六项（CP-WEB-06）
--
-- 目标：首页 / 技术文章 / 技术专题 / 项目 / 知识产品 / 技术服务
--
-- 变化说明：
--   1) 「归档」改名为「技术文章」，「知识付费」改名「知识产品」——只改展示文字，
--      路由不动，已有链接和 SEO 不受影响。
--   2) 隐藏「动态」「问答」「友链」「关于」四个一级入口：
--      - 动态 0 条、友链 0 条，点进去是空页，不该占导航；
--      - 问答与关于改为首页区块 / 页脚入口。
--      用 visible=0 而不是 DELETE：随时可以在后台「页面管理 → 导航菜单」打开。
--   3) 新增「项目」/projects（由 V24 的 portfolio 表支撑）。
--   4) 重排 sort 为 10~60。
--
-- 注意执行顺序：先加项目、再统一重排，避免 sort 撞车。
-- 这条迁移只做一次，之后在后台手动调整的顺序不会被覆盖。
-- ============================================================

UPDATE `nav_menu` SET `label` = '技术文章', `label_en` = 'Articles' WHERE `path` = '/archives';
UPDATE `nav_menu` SET `label` = '知识产品', `label_en` = 'Products' WHERE `path` = '/knowledge';

INSERT INTO `nav_menu` (`label`, `path`, `sort`, `visible`, `target`)
SELECT '项目', '/projects', 40, b'1', '_self'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `nav_menu` WHERE `path` = '/projects');

UPDATE `nav_menu` SET `visible` = b'0' WHERE `path` IN ('/moments', '/ask', '/friends', '/about');

UPDATE `nav_menu` SET `sort` = 10 WHERE `path` = '/';
UPDATE `nav_menu` SET `sort` = 20 WHERE `path` = '/archives';
UPDATE `nav_menu` SET `sort` = 30 WHERE `path` = '/topic';
UPDATE `nav_menu` SET `sort` = 40 WHERE `path` = '/projects';
UPDATE `nav_menu` SET `sort` = 50 WHERE `path` = '/knowledge';
UPDATE `nav_menu` SET `sort` = 60 WHERE `path` = '/service';
