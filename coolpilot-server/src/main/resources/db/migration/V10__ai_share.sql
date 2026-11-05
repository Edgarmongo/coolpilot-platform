-- ============================================================
-- V10：答案分享
--
-- 只加一列：分享是「把某条回答变成公开快照」，
-- 存 token 就够了 —— 内容仍从 ai_message 读，
-- 对话删除后链接自然失效（比留一份可能过期的副本更诚实）。
--
-- token 由服务端生成（随机 12 位），用户主动点「分享」才写入，
-- 不设过期：撤销的方式是删除对话或重新生成 token（后续可加）。
-- ============================================================

ALTER TABLE `ai_message`
    ADD COLUMN `share_token` varchar(32) NULL DEFAULT NULL COMMENT '分享令牌，为空表示未分享',
    ADD UNIQUE INDEX `uk_share_token`(`share_token`) USING BTREE;
