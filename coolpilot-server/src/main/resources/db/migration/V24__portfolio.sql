-- ============================================================
-- V24：项目展示（CP-WEB-08）
--
-- 注意命名：库里已经有一张 `project` 表，是**技术服务项目进度**
-- （project_no / demand_id / quote_id / order_no / amount / status），
-- 属于「客户看自己项目的里程碑」。开源项目展示是另一件事，
-- 所以这里叫 `portfolio`（作品集），不能复用也不能重名。
-- 前台路由仍然是 /projects，URL 语义对用户是清楚的，只有表名避让。
--
-- 为什么不需要第二张 project_detail 表：
--   详情页要的「背景 / 架构 / 核心功能 / 技术选型 / 实现 / 截图」全部写进
--   content（Markdown），用 ## 分段，前台按顺序渲染即可。
--   这条链路已经存在：后台商品详情就是 textarea + Markdown
--   （ProductList.vue），后端 MarkdownUtils.markdownToHtmlExtensions 已被
--   文章、商品、关于、动态、友链五处复用，前台统一 v-html。
--   截图直接用现有的粘贴贴图，bizType 传 PORTFOLIO。
--   所以一张表 + 一个 Markdown 字段，就能撑起完整详情页。
--
-- 「相关技术文章」首期在正文里手写链接，等确实需要自动关联时
-- 再加 portfolio_article 关联表（参照 topic_article），不为展示列表先加表。
-- ============================================================

CREATE TABLE IF NOT EXISTS `portfolio`
(
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `slug`          varchar(64)  NOT NULL COMMENT '前台地址 /projects/{slug}',
    `name`          varchar(100) NOT NULL COMMENT '项目名',
    `summary`       varchar(255)          DEFAULT NULL COMMENT '列表页一句话简介',
    `cover_url`     varchar(512)          DEFAULT NULL COMMENT '封面图地址',
    `tech_stack`    varchar(255)          DEFAULT NULL COMMENT '技术栈，逗号分隔',
    `repo_url`      varchar(512)          DEFAULT NULL COMMENT '代码仓库地址',
    `demo_url`      varchar(512)          DEFAULT NULL COMMENT '演示地址，可空',
    `status`        varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/MAINTAINING/WIP/ARCHIVED',
    `content`       longtext              DEFAULT NULL COMMENT '详情，Markdown',
    `sort`          int          NOT NULL DEFAULT 0 COMMENT '升序',
    `enabled`       bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否在前台展示',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`),
    KEY `idx_enabled_sort` (`enabled`, `sort`) USING BTREE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='项目展示（作品集）';

-- ---------------- 初始数据 ----------------
-- 只把「确凿存在、可以直接讲清楚」的项目放出来：
--   coolpilot-platform 就是这个站点本身，技术栈完全可考，不存在虚构风险。
-- 其余两个是进行中的项目，先建成「不展示」状态，站长补完内容再打开，
-- 避免上线时前台出现空壳项目或夸大描述。

INSERT INTO `portfolio` (`slug`, `name`, `summary`, `tech_stack`, `status`, `sort`, `enabled`, `content`)
SELECT 'coolpilot-platform',
       'CoolPilot Platform',
       '本网站本身：内容 + 知识产品 + 技术服务 + AI 问答的一体化平台',
       'Java 21, Spring Boot 4, MySQL 8, Redis, Nuxt 3, Vue 3',
       'ACTIVE', 10, b'1',
       '## 项目背景\n\n这个站点同时承担四件事：写作、卖知识产品、接技术服务、用 AI 回答访客问题。\n拆成四个系统会导致账号、订单、内容和权限各一套，所以做成了一个平台。\n\n## 系统架构\n\n- 前台 Nuxt 3（SSR，负责 SEO 与首屏）\n- 后台 Vue 3 + Element Plus（内容、商品、交易、服务、页面配置）\n- 接口 Spring Boot 4 + MySQL 8 + Redis\n- 业务边界按模块划分：内容 / 商品 / 交易 / 服务 / AI / 站点配置\n\n## 核心功能\n\n- 文章、专题、分类、标签与双语内容\n- 知识产品：下单、收款码、人工确认、权益开通\n- 技术服务：需求 → 报价 → 立项 → 里程碑 → 交付 → 验收\n- AI 问答：基于站内知识库检索并给出可点击来源\n\n## 技术选型\n\n选 Spring Boot 而不是 Node 后端，是因为交易与服务流程需要强事务；\n选 Nuxt 而不是纯 SPA，是因为内容站必须让搜索引擎拿到完整 HTML。\n\n## 技术实现\n\n- Flyway 管理全部表结构变更，每次迁移可追溯\n- 定时任务由 Quartz 统一调度，同一任务不并发执行\n- 图片走统一文件表，私有文件用临时 token 访问'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `portfolio` WHERE `slug` = 'coolpilot-platform');

INSERT INTO `portfolio` (`slug`, `name`, `summary`, `tech_stack`, `status`, `sort`, `enabled`, `content`)
SELECT 'coolpilot-business',
       'CoolPilot Business',
       '企业经营管理与 AI 应用系统',
       'Java, Spring Boot, Vue, MySQL',
       'WIP', 20, b'0',
       '## 项目背景\n\n（待补充：这个项目解决什么问题、面向谁）\n\n## 系统架构\n\n（待补充）\n\n## 核心功能\n\n（待补充）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `portfolio` WHERE `slug` = 'coolpilot-business');

INSERT INTO `portfolio` (`slug`, `name`, `summary`, `tech_stack`, `status`, `sort`, `enabled`, `content`)
SELECT 'ai-service',
       'AI Service',
       '模型接入、文档处理与 AI 能力服务',
       'Python, FastAPI, LLM',
       'WIP', 30, b'0',
       '## 项目背景\n\n（待补充）\n\n## 系统架构\n\n（待补充）\n\n## 核心功能\n\n（待补充）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `portfolio` WHERE `slug` = 'ai-service');
