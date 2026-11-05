-- ============================================================
-- V13：用户状态 + 最后登录时间 + 超时关单定时任务
--
-- 1) user 加两列
--    status          0 正常 / 1 已禁用。开放自助注册后，
--                    总要有个「停掉某个账号」的手段，否则只能改数据库。
--    last_login_time 运营最常问的一句是「这人还活跃吗」，
--                    有它就不必去翻登录日志。
--
-- 2) 把「关闭超时订单」变成定时任务
--    之前 OrderMapper.closeExpired 只有后台一个按钮，
--    没人点就永远不关 —— 未支付订单会一直挂在列表里，
--    也挡着同一商品再次下单（同商品存在 PENDING_PAY 时不再新建）。
--    每 10 分钟跑一次，代价是一条 UPDATE。
-- ============================================================

ALTER TABLE `user`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 正常，1 已禁用',
    ADD COLUMN `last_login_time` datetime NULL DEFAULT NULL COMMENT '最后一次登录时间';

INSERT INTO `schedule_job` (`bean_name`, `method_name`, `params`, `cron`, `status`, `remark`, `create_time`)
VALUES ('orderScheduleTask', 'closeExpiredOrders', '', '0 */10 * * * ?', 1,
        '每 10 分钟关闭超时未支付订单（PENDING_PAY / PENDING_CONFIRM 且已过支付截止时间）', NOW());
