-- ============================================================
-- V12：评论支持「分享出去的问答」
--
-- 分享页要能讨论，但没必要再建一张评论表：
-- 昵称、头像、内容、时间、审核状态、父评论这些字段完全一样，
-- 再建一套意味着后台要维护两个审核入口、前端要写两套渲染。
--
-- 加两列做多态：
--   target_type  NULL = 历史文章评论（仍以 blog_id 为准）
--                SHARE = 分享的问答
--   target_id    target_type=SHARE 时为 ai_message.id
--
-- 刻意**不给文章评论回填** target_id：
-- 文章的真相在 blog_id，回填等于制造第二份真相，
-- 以后改一处忘一处就会出现「评论消失」或「评论跑到别的文章下」。
-- 新类型才用 target_*，老类型继续用 blog_id，边界清楚。
--
-- 另外约定 page=3 表示「分享问答评论」（0 文章 / 1 关于我 / 2 友链）：
-- 该列 NOT NULL，改可空会波及按 int 拆箱的老代码；
-- 这类评论不进 CommentUtils 的页面状态判断，只作为一个标记位存在。
-- ============================================================

ALTER TABLE `comment`
    ADD COLUMN `target_type` varchar(16) NULL DEFAULT NULL COMMENT '评论对象类型；NULL 为历史文章评论，SHARE 为分享的问答',
    ADD COLUMN `target_id` bigint NULL DEFAULT NULL COMMENT '评论对象ID；SHARE 时为 ai_message.id';

ALTER TABLE `comment`
    ADD INDEX `idx_comment_target` (`target_type`, `target_id`, `is_published`, `parent_comment_id`) USING BTREE;
