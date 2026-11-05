-- ============================================================
-- V19：关于页支持英文
--
-- about 是 key-value 表（name_en -> value），所以不新增列，
-- 直接加两行 titleEn / contentEn：读的时候英文站优先取它们，
-- 为空则回落中文，与导航、站点名保持同一套「留空回落」规则。
--
-- 英文正文留空起步：没有英文稿时英文站显示中文原文，
-- 比显示空白页友好。
-- ============================================================

-- about.id 没有 AUTO_INCREMENT（历史遗留），必须显式给主键。
-- V2 种子占了 1-4，这里用 5、6。
--
-- 写成 INSERT ... SELECT ... WHERE NOT EXISTS：迁移重复执行不会撞主键。
-- 第一版直接 INSERT VALUES，在「行已经手工补过」的库上会因主键冲突整条失败。
INSERT INTO `about` (`id`, `name_en`, `name_zh`, `value`)
SELECT 5, 'titleEn', '英文标题', ''
WHERE NOT EXISTS (SELECT 1 FROM `about` WHERE `id` = 5);

INSERT INTO `about` (`id`, `name_en`, `name_zh`, `value`)
SELECT 6, 'contentEn', '英文正文', ''
WHERE NOT EXISTS (SELECT 1 FROM `about` WHERE `id` = 6);
