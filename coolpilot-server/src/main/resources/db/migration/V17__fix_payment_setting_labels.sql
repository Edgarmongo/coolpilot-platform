-- ============================================================
-- V17：修站点设置里两组失效的配置
--
-- 1) 收款码四项的中文名是乱码
--    V2 种子里这几条被「UTF-8 当成 CP1252 再存成 UTF-8」写过一遍
--    （å¾®ä¿¡æ”¶æ¬¾ç 之类），后台就算能看到也没法认。
--    直接按 name_en 覆写成正确文案 —— 值（图片路径）本身没问题，不用动。
--
-- 2) 顺带让后台能真正看到这两组
--    代码侧已改为按库里实际 type 动态分组（原先 switch 只放行 1/2/3），
--    这里不需要改数据，只把可读的名字补上：
--      type 4 友链说明（原本就正常）
--      type 5 支付设置
--
-- 不改 V2 文件本身：Flyway 会校验已执行脚本的 checksum，
-- 回头改历史迁移会让所有已有库 validate 失败。
-- ============================================================

UPDATE `site_setting` SET `name_zh` = '微信收款码' WHERE `name_en` = 'wechatQr';
UPDATE `site_setting` SET `name_zh` = '支付宝收款码' WHERE `name_en` = 'alipayQr';
UPDATE `site_setting` SET `name_zh` = 'PayPal 收款链接' WHERE `name_en` = 'paypalUrl';
UPDATE `site_setting` SET `name_zh` = '收款人姓名' WHERE `name_en` = 'payeeName';
