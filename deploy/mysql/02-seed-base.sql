-- ============================================================
-- 基础种子数据：分类与标签
-- 说明：上游 nblog.sql 只含表结构与配置项，不含分类/标签/文章，
--       demo-data.sql 依赖分类与标签已存在，故单独补一份。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO `category` (`id`, `category_name`) VALUES
  (1, '文章'),
  (2, '知识库'),
  (3, 'AI工具集')
ON DUPLICATE KEY UPDATE `category_name` = VALUES(`category_name`);

INSERT INTO `tag` (`id`, `tag_name`, `color`) VALUES
  (1, 'AI', 'teal')
ON DUPLICATE KEY UPDATE `tag_name` = VALUES(`tag_name`);
