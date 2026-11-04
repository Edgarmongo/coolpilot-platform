-- ============================================================
-- V4：文章 slug（可读 URL）
--
-- 现状：文章只有自增 id，URL 是 /blog/1。
-- 对搜索引擎和分享链接都不友好，也无法做「改标题不换链接」。
--
-- 处理方式：
--   1. 加 slug 列，唯一索引（MySQL 允许多行 NULL，未填 slug 的文章不受影响）
--   2. 已有文章回填 post-{id}，保证每篇都有稳定可访问的地址
--   3. 前台 /blog/:param 先按 id 匹配，匹配不到再按 slug 匹配，旧链接不会失效
--
-- 不做「按标题自动转拼音」：需要额外词典，且中文标题转出来往往又长又难读，
-- 由编辑在后台自己填更可控。
-- ============================================================

ALTER TABLE `blog`
    ADD COLUMN `slug` VARCHAR(120) NULL COMMENT 'URL 别名；为空时前台回落到 /blog/:id' AFTER `title`;

UPDATE `blog` SET `slug` = CONCAT('post-', `id`) WHERE `slug` IS NULL;

ALTER TABLE `blog`
    ADD UNIQUE INDEX `uk_blog_slug` (`slug`);
