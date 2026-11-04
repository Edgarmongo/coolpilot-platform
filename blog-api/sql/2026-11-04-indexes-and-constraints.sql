-- ============================================================
-- NBlog 索引与约束补齐（V1.0 前置）
-- 日期：2026-11-04
-- 依据：docs/engineering/02-database-audit.md §3
-- 说明：一次性迁移脚本，执行前请备份；执行后不可重复执行（MySQL 不支持 ADD INDEX IF NOT EXISTS）
-- ============================================================

-- 0. 前置检查：以下三条必须返回空，否则先清理重复数据
-- SELECT username, COUNT(*) c FROM user GROUP BY username HAVING c > 1;
-- SELECT blog_id, tag_id, COUNT(*) c FROM blog_tag GROUP BY blog_id, tag_id HAVING c > 1;
-- SELECT tag_name, COUNT(*) c FROM tag GROUP BY tag_name HAVING c > 1;

-- 1. 用户名唯一：防止并发注册产生同名账号（对应安全审计 R-05）
ALTER TABLE `user`
  ADD UNIQUE INDEX `uk_username`(`username`) USING BTREE;

-- 2. 评论查询：文章详情页按文章加载、后台按状态筛选
ALTER TABLE `comment`
  ADD INDEX `idx_blog_id`(`blog_id`) USING BTREE,
  ADD INDEX `idx_published_time`(`is_published`, `create_time`) USING BTREE;

-- 3. 文章-标签关联：原表无主键，补复合主键并给反向查询加索引
ALTER TABLE `blog_tag`
  ADD PRIMARY KEY (`blog_id`, `tag_id`) USING BTREE,
  ADD INDEX `idx_tag_id`(`tag_id`) USING BTREE;

-- 4. 日志表：后台按时间倒序分页
ALTER TABLE `visit_log`
  ADD INDEX `idx_create_time`(`create_time`) USING BTREE;

ALTER TABLE `operation_log`
  ADD INDEX `idx_create_time`(`create_time`) USING BTREE;

ALTER TABLE `login_log`
  ADD INDEX `idx_create_time`(`create_time`) USING BTREE;

ALTER TABLE `exception_log`
  ADD INDEX `idx_create_time`(`create_time`) USING BTREE;

-- 5. 标签名唯一：标签页按名称路由，重名会导致查询结果不确定
ALTER TABLE `tag`
  ADD UNIQUE INDEX `uk_tag_name`(`tag_name`) USING BTREE;

-- 6. 分类名唯一：同上
ALTER TABLE `category`
  ADD UNIQUE INDEX `uk_category_name`(`category_name`) USING BTREE;

-- ============================================================
-- 回滚脚本（仅在需要撤销时执行）
-- ============================================================
-- ALTER TABLE `user` DROP INDEX `uk_username`;
-- ALTER TABLE `comment` DROP INDEX `idx_blog_id`, DROP INDEX `idx_published_time`;
-- ALTER TABLE `blog_tag` DROP PRIMARY KEY, DROP INDEX `idx_tag_id`;
-- ALTER TABLE `visit_log` DROP INDEX `idx_create_time`;
-- ALTER TABLE `operation_log` DROP INDEX `idx_create_time`;
-- ALTER TABLE `login_log` DROP INDEX `idx_create_time`;
-- ALTER TABLE `exception_log` DROP INDEX `idx_create_time`;
-- ALTER TABLE `tag` DROP INDEX `uk_tag_name`;
-- ALTER TABLE `category` DROP INDEX `uk_category_name`;
