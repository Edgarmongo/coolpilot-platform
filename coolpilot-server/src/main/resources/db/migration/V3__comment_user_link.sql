-- ============================================================
-- V3：评论与访客账号打通
--
-- 上游评论是「昵称 + 邮箱」制，没有 user_id：
--   1. 无法做「我的评论」
--   2. 登录用户评论时，token（visitor:2）被当作文章密码解析成数字 → 500
--
-- 加一列即可支撑这两件事，历史评论的 user_id 为空（匿名），不影响展示。
-- ============================================================

ALTER TABLE `comment`
    ADD COLUMN `user_id` BIGINT NULL COMMENT '访客账号ID；匿名评论为空' AFTER `id`;

ALTER TABLE `comment`
    ADD INDEX `idx_comment_user_time` (`user_id`, `create_time`);
