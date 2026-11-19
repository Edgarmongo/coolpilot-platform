-- ============================================================
-- V28：开源项目商业观察（CP-WEB-OS / OS-DEV-101）
--
-- 为什么要新建三张表，而不是复用 portfolio：
--   portfolio 是「我自己的作品」，用来展示能力；本栏目是「第三方开源项目的商业分析」，
--   有双状态机（研究/发布）、评估卡、关联文章，形状完全不同。
--   详见 docs/ADR-0004-OPEN-SOURCE-MODEL.md。
--
-- 只新增，不改任何现有表结构。全部语句幂等，重复执行不报错。
--
-- 两个「不建表」的决定：
--   1. 项目领域用枚举（5 个值），不建 oss_category：
--      单人运营、一年不变，建表 + 管理页 + 关联查询的成本大于收益。
--   2. 标签复用现有 `tag` / `blog_tag` 体系的 tag 表，只加关联表：
--      「Python」「RAG」这类标签在文章与项目之间本来就该通用。
-- ============================================================

-- 1) 项目档案
CREATE TABLE IF NOT EXISTS `oss_project`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `slug`            varchar(180) NOT NULL COMMENT '前台地址 /open-source/projects/{slug}',
    `name`            varchar(150) NOT NULL COMMENT '项目名',
    `summary`         varchar(500)          DEFAULT NULL COMMENT '列表页一句话简介',
    `description`     longtext              DEFAULT NULL COMMENT '详细介绍，Markdown',
    `repo_url`        varchar(500)          DEFAULT NULL COMMENT '代码仓库',
    `official_url`    varchar(500)          DEFAULT NULL COMMENT '官方网站',
    `docs_url`        varchar(500)          DEFAULT NULL COMMENT '官方文档',
    `license`         varchar(80)           DEFAULT NULL COMMENT 'SPDX 标识，如 MIT',
    `language`        varchar(80)           DEFAULT NULL COMMENT '主要语言',
    `category`        varchar(32)  NOT NULL DEFAULT 'ai-project' COMMENT '领域枚举码：ai-project/agent/data-search/devtool/enterprise',
    `cover_url`       varchar(512)          DEFAULT NULL COMMENT '封面图',
    `stars`           int                   DEFAULT NULL COMMENT 'star 数快照，仅作关注度参考，不参与排序',
    `stars_at`        datetime              DEFAULT NULL COMMENT 'star 采集时间',
    `verdict`         varchar(32)           DEFAULT NULL COMMENT '结论档：watch/deploy/extend/commercial/hold',
    `assessment_json` longtext              DEFAULT NULL COMMENT '评估卡 JSON，七项 level+note+evidence',
    `research_status` varchar(20)  NOT NULL DEFAULT 'todo' COMMENT 'todo/researching/done',
    `publish_status`  varchar(20)  NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
    `featured`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '栏目首页精选',
    `last_verified_at` datetime             DEFAULT NULL COMMENT '资料最近核验时间',
    `sort`            int          NOT NULL DEFAULT 0 COMMENT '升序',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oss_slug` (`slug`),
    KEY `idx_oss_pub` (`publish_status`, `featured`, `sort`),
    KEY `idx_oss_cat` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='开源项目档案';

-- 2) 项目 ↔ 文章
--
-- 主键用 (project_id, blog_id) 两列，而不是再加 relation_type：
-- 一篇文章对一个项目只扮演一个角色，两列主键下重复关联会直接冲突失败，
-- 三列唯一反而会让后台重复提交「成功」但出现两行。
CREATE TABLE IF NOT EXISTS `oss_project_article`
(
    `project_id`    bigint      NOT NULL COMMENT 'oss_project.id',
    `blog_id`       bigint      NOT NULL COMMENT 'blog.id',
    `relation_type` varchar(24) NOT NULL DEFAULT 'research'
        COMMENT 'business=商业分析 research=技术研究 practice=实践 compare=对比',
    `sort`          int         NOT NULL DEFAULT 0 COMMENT '升序',
    PRIMARY KEY (`project_id`, `blog_id`),
    KEY `idx_opa_blog` (`blog_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='开源项目与文章关联';

-- 3) 项目 ↔ 标签（tag 复用 blog 的标签表）
CREATE TABLE IF NOT EXISTS `oss_project_tag`
(
    `project_id` bigint NOT NULL,
    `tag_id`     bigint NOT NULL,
    PRIMARY KEY (`project_id`, `tag_id`),
    KEY `idx_opt_tag` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='开源项目标签关联，tag 复用文章标签表';

-- 4) 三个栏目分类
--
-- 只建实际会用到的三个：建了空栏目会让分类页出现「暂无文章」的死链。
-- 「商业实践与案例」等有内容后再加。
INSERT INTO `category` (`category_name`)
SELECT '开源项目商业观察' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `category_name` = '开源项目商业观察');

INSERT INTO `category` (`category_name`)
SELECT '开源技术研究' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `category_name` = '开源技术研究');

INSERT INTO `category` (`category_name`)
SELECT '开源技术实践' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `category` WHERE `category_name` = '开源技术实践');

-- 5) 导航第 7 项
--
-- sort=25 落在「技术文章 20」与「技术专题 30」之间：栏目属于内容类，与技术专题同组；
-- 25 不会改变其余各项的相对顺序。其余 sort 一律不动。
INSERT INTO `nav_menu` (`label`, `label_en`, `path`, `sort`, `visible`, `target`)
SELECT '开源商业观察', 'Insights', '/open-source', 25, b'1', '_self'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `nav_menu` WHERE `path` = '/open-source');
