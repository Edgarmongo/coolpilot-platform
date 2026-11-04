-- ============================================================
-- V5：角色语义迁移（T-103）
--
-- 旧：ROLE_admin（博主/管理员）、ROLE_visitor（前台注册用户）
-- 新：ROLE_owner（平台所有者）、ROLE_user（前台用户）
--
-- 为什么改：
--   1. admin 与 /admin 路径耦合，新增接口时到处硬编码 hasRole("admin")，
--      语义上也无法区分「管理员」与「平台所有者」。
--   2. visitor 指的是已注册并登录的用户，与真正的匿名访客混淆，
--      导致代码里 visitor / anonymous 反复打架（评论模块就踩过）。
--
-- 兼容：
--   * 应用侧读取角色时统一走 Role.normalize，旧值不会立刻报错；
--   * 旧 JWT 里的 authorities 在 JwtFilter 里归一化，升级不当场踢人；
--   * 本脚本负责把库里的存量数据一次性收敛，之后旧值只作为兜底存在。
--
-- 幂等性：UPDATE 带 WHERE 条件，重复执行不会误伤其它角色。
-- ============================================================

UPDATE `user` SET `role` = 'ROLE_owner' WHERE `role` IN ('ROLE_admin', 'admin');
UPDATE `user` SET `role` = 'ROLE_user' WHERE `role` IN ('ROLE_visitor', 'visitor');

-- 新注册用户默认角色，避免插入时漏字段导致 NOT NULL 报错
ALTER TABLE `user`
    MODIFY COLUMN `role` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'ROLE_user'
        COMMENT '角色：ROLE_owner 平台所有者，ROLE_user 前台用户';
