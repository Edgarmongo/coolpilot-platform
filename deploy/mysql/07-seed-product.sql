-- ============================================================
-- V1.1a 演示商品数据
-- 说明：便于前台商品页有内容可看；正式运营时在后台创建即可
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO `product_category` (`id`, `name`, `product_type`, `icon`, `sort`, `create_time`) VALUES
  (1, '后端与架构', 'DOC', '', 10, NOW()),
  (2, 'AI 应用', 'COURSE', '', 20, NOW()),
  (3, '工程脚手架', 'CODE', '', 30, NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `product`
  (`id`, `product_no`, `product_type`, `category_id`, `title`, `subtitle`, `cover`, `summary`, `detail`,
   `price`, `original_price`, `price_mode`, `sales_count`, `view_count`, `sort`, `status`, `is_top`, `owner_id`,
   `create_time`, `update_time`)
VALUES
  (1, 'P202611050001', 'COURSE', 2, 'Java 工程师的 AI 应用开发实战',
   '从 Spring Boot 到 RAG，把 AI 能力接进真实业务系统',
   '', '12 章 36 课时，覆盖模型接入、RAG 检索、Agent 编排与生产部署，全部基于真实项目代码。',
   '## 这门课解决什么问题\n\n大多数 AI 教程停留在调用 API 的层面，回到业务系统里仍然不知道怎么落地。这门课按真实项目推进：先把模型接进已有的 Spring Boot 服务，再解决知识库检索、上下文管理、成本控制、灰度发布这些工程问题。\n\n## 适合谁\n\n- 有 Java 后端经验，想把 AI 能力落到业务里\n- 正在做企业知识库、智能客服、文档问答\n- 需要评估 RAG 方案可行性\n\n## 你会得到\n\n- 一套可运行的企业知识库代码\n- 完整的检索调优记录（分块、Embedding、Rerank）\n- 部署与成本控制的实践清单',
   199.00, 299.00, 'FIXED', 0, 0, 100, 'ON_SALE', b'1', 1, NOW(), NOW()),

  (2, 'P202611050002', 'DOC', 1, '企业级 RAG 系统设计手册',
   '从文档解析到线上灰度，一份可执行的工程方案',
   '', '58 页 PDF，含架构图、容量估算、检索评估指标与常见故障排查表。',
   '## 内容大纲\n\n1. 需求澄清与场景切分\n2. 文档解析与清洗（PDF/Word/扫描件）\n3. 分块策略与元数据设计\n4. Embedding 选型与成本测算\n5. 混合检索与 Rerank\n6. 权限隔离与多租户\n7. 评估体系与灰度上线\n8. 故障排查手册',
   49.00, 79.00, 'FIXED', 0, 0, 90, 'ON_SALE', b'0', 1, NOW(), NOW()),

  (3, 'P202611050003', 'CODE', 3, 'Spring Boot 4 工程脚手架',
   '开箱即用的分层结构、Docker 编排与 CI 模板',
   '', '包含统一响应、异常处理、鉴权、缓存、审计日志、Docker Compose 与测试骨架。',
   '## 包含内容\n\n- Spring Boot 4 + JDK 21 基础工程\n- 统一响应与全局异常\n- JWT 鉴权与角色隔离\n- Redis 缓存封装\n- Docker Compose（MySQL/Redis/应用）\n- GitHub Actions 构建模板\n\n## 版本\n\n购买后可持续获取更新版本。',
   99.00, 149.00, 'FIXED', 0, 0, 80, 'ON_SALE', b'0', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

INSERT INTO `course` (`id`, `product_id`, `description`, `lesson_count`, `total_duration`, `create_time`, `update_time`)
VALUES (1, 1, '12 章 36 课时，配套完整代码仓库', 3, 95, NOW(), NOW())
ON DUPLICATE KEY UPDATE `description` = VALUES(`description`);

INSERT INTO `lesson` (`id`, `course_id`, `title`, `sort`, `content_type`, `video_file_id`, `content`, `duration`, `is_free_preview`, `create_time`, `update_time`)
VALUES
  (1, 1, '第 1 讲：为什么业务系统需要自己的 AI 层', 10, 'DOC', NULL, '试看内容：模型能力与业务系统的三类集成方式，以及各自的成本与可控性差异。', 20, b'1', NOW(), NOW()),
  (2, 1, '第 2 讲：把模型接进 Spring Boot 服务', 20, 'DOC', NULL, NULL, 35, b'0', NOW(), NOW()),
  (3, 1, '第 3 讲：RAG 检索链路搭建', 30, 'DOC', NULL, NULL, 40, b'0', NOW(), NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);
