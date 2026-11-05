-- ============================================================
-- V20：AI 运行时配置 + 调用用量
--
-- 背景：模型参数原本全部来自 .env，改一个模型名就要重建容器。
-- 后台需要能在线改，所以把「可热改」的那部分放进表里：
-- 表里没有的键一律回落到 application-dev.properties 的默认值，
-- 这样升级到这一版时行为不变，不用先灌数据。
--
-- ai_usage 只服务「用量统计」：每次调用模型记一行，用来回答
-- 「今天用了多少 token」「哪个模型最费」。不存请求/响应正文，
-- 避免二次存储对话内容（正文本来就在 ai_message 里）。
-- ============================================================

CREATE TABLE IF NOT EXISTS `ai_setting`
(
	`id`            BIGINT      NOT NULL AUTO_INCREMENT,
	`setting_key`   VARCHAR(64) NOT NULL COMMENT '点分键，如 llm.model',
	`setting_value` TEXT COMMENT '值；密钥类存 AES 密文',
	`encrypted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '1=值已加密',
	`update_time`   DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	UNIQUE KEY `uk_ai_setting_key` (`setting_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 运行时配置（为空则回落配置文件）';

CREATE TABLE IF NOT EXISTS `ai_usage`
(
	`id`                BIGINT      NOT NULL AUTO_INCREMENT,
	`scene`             VARCHAR(24) NOT NULL COMMENT 'CHAT / REWRITE / RERANK / TEST',
	`model`             VARCHAR(128)         DEFAULT NULL,
	`prompt_tokens`     INT         NOT NULL DEFAULT 0,
	`completion_tokens` INT         NOT NULL DEFAULT 0,
	`total_tokens`      INT         NOT NULL DEFAULT 0,
	`duration_ms`       INT         NOT NULL DEFAULT 0,
	`success`           TINYINT     NOT NULL DEFAULT 1,
	`estimated`         TINYINT     NOT NULL DEFAULT 0 COMMENT '1=token 由字符估算，接口未返回 usage',
	`user_id`           BIGINT               DEFAULT NULL,
	`create_time`       DATETIME    NOT NULL,
	PRIMARY KEY (`id`),
	KEY `idx_ai_usage_time` (`create_time`),
	KEY `idx_ai_usage_scene` (`scene`, `create_time`),
	KEY `idx_ai_usage_model` (`model`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 调用用量（token 与耗时）';
