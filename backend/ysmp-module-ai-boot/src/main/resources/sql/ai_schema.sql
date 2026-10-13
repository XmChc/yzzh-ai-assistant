-- AI 助手 SDK 表结构（MySQL 8+）
-- SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用',
  `base_url` varchar(512) DEFAULT NULL COMMENT 'OpenAI 兼容根地址',
  `api_key` varchar(512) DEFAULT NULL COMMENT 'API Key',
  `model` varchar(128) DEFAULT NULL COMMENT '模型名',
  `timeout_ms` int NOT NULL DEFAULT 60000 COMMENT '超时毫秒',
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型配置（单行）';

CREATE TABLE IF NOT EXISTS `ai_tool_api` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tool_code` varchar(128) NOT NULL COMMENT '工具编码',
  `tool_type` varchar(32) NOT NULL DEFAULT 'OPENAPI' COMMENT 'OPENAPI/BUILTIN',
  `name` varchar(128) NOT NULL,
  `description` varchar(1024) DEFAULT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `http_method` varchar(16) DEFAULT NULL,
  `path` varchar(512) DEFAULT NULL,
  `operation_id` varchar(128) DEFAULT NULL,
  `module` varchar(128) DEFAULT NULL COMMENT '功能模块（OpenAPI tag / 路径首段）',
  `param_schema_json` text COMMENT '入参 JSON Schema（OpenAI function parameters）',
  `response_schema_json` text COMMENT '出参 JSON Schema（成功响应 application/json）',
  `required_permissions` varchar(512) DEFAULT NULL COMMENT 'JSON 数组字符串',
  `sort` int NOT NULL DEFAULT 0,
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_tool_code` (`tool_code`),
  KEY `idx_ai_tool_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Tool 接口配置';

-- 已有库升级（按需执行）：
-- ALTER TABLE `ai_tool_api` ADD COLUMN `module` varchar(128) DEFAULT NULL COMMENT '功能模块' AFTER `operation_id`;
-- ALTER TABLE `ai_tool_api` ADD KEY `idx_ai_tool_module` (`module`);
-- ALTER TABLE `ai_tool_api` ADD COLUMN `response_schema_json` text COMMENT '出参 JSON Schema' AFTER `param_schema_json`;
-- 若曾加过 read_only 可删除：ALTER TABLE `ai_tool_api` DROP COLUMN `read_only`;

CREATE TABLE IF NOT EXISTS `ai_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `skill_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `system_prompt` text,
  `knowledge_base_id` bigint DEFAULT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_code` (`skill_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 技能';

CREATE TABLE IF NOT EXISTS `ai_skill_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `skill_id` bigint NOT NULL,
  `tool_code` varchar(128) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能-工具';

CREATE TABLE IF NOT EXISTS `ai_agent` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  `system_prompt` text,
  `access_permission` varchar(128) NOT NULL COMMENT '如 ai:agent:default',
  `agent_mode` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT 'SCENARIO|GENERAL',
  `default_flag` bit(1) NOT NULL DEFAULT b'0',
  `remark` varchar(255) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent';

-- 已有库升级：
-- ALTER TABLE `ai_agent` ADD COLUMN `agent_mode` varchar(32) NOT NULL DEFAULT 'GENERAL' COMMENT 'SCENARIO|GENERAL' AFTER `access_permission`;

CREATE TABLE IF NOT EXISTS `ai_agent_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` bigint NOT NULL,
  `skill_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent-技能';

CREATE TABLE IF NOT EXISTS `ai_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `agent_code` varchar(64) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `context_summary` mediumtext COMMENT '滚动对话摘要（旧轮次压缩）',
  `summary_until_id` bigint NOT NULL DEFAULT 0 COMMENT '摘要已覆盖到的 ai_chat_message.id',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话';

-- 已有库升级：
-- ALTER TABLE `ai_chat_session` ADD COLUMN `context_summary` mediumtext COMMENT '滚动对话摘要' AFTER `title`;
-- ALTER TABLE `ai_chat_session` ADD COLUMN `summary_until_id` bigint NOT NULL DEFAULT 0 COMMENT '摘要覆盖到的 message.id' AFTER `context_summary`;

CREATE TABLE IF NOT EXISTS `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) NOT NULL,
  `role` varchar(32) NOT NULL COMMENT 'user/assistant/tool',
  `content` mediumtext,
  `tool_summary` varchar(1024) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_msg_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 消息';

-- 默认 Agent（可按环境修改）
INSERT INTO `ai_agent` (`agent_code`, `name`, `enabled`, `system_prompt`, `access_permission`, `agent_mode`, `default_flag`)
SELECT 'default', '默认助手', b'1', '你是企业管理端助手。', 'ai:agent:default', 'GENERAL', b'1'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_agent` WHERE `agent_code` = 'default');
