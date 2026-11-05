-- ============================================================
-- V11：分享的生命周期与传播数据
--
-- V10 只存了一个 token，链接一旦分享出去就永久有效，
-- 用户只能靠「取消分享」或删对话来收回 —— 忘了就是长期挂着。
-- 这里补三列：
--   share_expires_at  到期时间，NULL 表示长期有效（老数据保持原语义，不追溯）
--   share_views       分享页阅读数（按 IP 30 分钟去重，由应用层计数）
--   share_likes       点赞数（按 IP 去重，应用层用 Redis 记状态）
--
-- 计数直接冗余在 ai_message 上：分享页是公开高频读，多一张统计表就多一次 join，
-- 而这两个值没有其它维度可聚合，冗余一列最省事。
-- ============================================================

ALTER TABLE `ai_message`
    ADD COLUMN `share_expires_at` datetime NULL DEFAULT NULL COMMENT '分享到期时间，NULL 表示长期有效',
    ADD COLUMN `share_views` int NOT NULL DEFAULT 0 COMMENT '分享页阅读数',
    ADD COLUMN `share_likes` int NOT NULL DEFAULT 0 COMMENT '分享页点赞数';
