-- ============================================================
-- V16：品牌与页脚可配置
--
-- 品牌名、页脚文案原本写死在 coolpilot-web/layouts/default.vue。
-- 这次不新建表，复用 site_setting（type = 1 基础设置）：
-- 这类字段就是「一个名字对应一段文案」，为它单建表只会多一层 join。
--
-- 复用 blogName 作为品牌名，不另加 brandName ——
-- 同一个站点有两个「名字」字段，迟早出现改了一个另一个没改的情况。
-- 只把它的中文说明改成「站点名称（品牌名）」，后台更容易找。
--
-- 新增三项：
--   brandMark      左上角方块里的 1~2 个字，留空则不显示方块
--   footerText     页脚主文案
--   footerTagline  页脚副文案（与主文案之间自动加间隔点）
--
-- 页脚备案号复用已有的 beian（type = 1），有值才显示并链接到工信部。
-- 关于页大标题本来就在 about 表里（后台「关于我」可改），这里不动。
-- ============================================================

INSERT INTO `site_setting` (`name_en`, `name_zh`, `value`, `type`)
VALUES ('brandMark', '站点标记', 'CP', 1),
       ('footerText', '页脚主文案', 'CoolSkyAI 技术平台', 1),
       ('footerTagline', '页脚副文案', '技术知识与服务', 1);

UPDATE `site_setting`
SET `name_zh` = '站点名称（品牌名）'
WHERE `name_en` = 'blogName';
