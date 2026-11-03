CREATE TABLE IF NOT EXISTS `article_info`
(
    `id`                 BIGINT        NOT NULL COMMENT 'Article ID',
    `agent_type`         TINYINT       NOT NULL DEFAULT 3 COMMENT 'Agent type: 1-hospital,2-department,3-doctor',
    `hospital_id`        BIGINT        DEFAULT NULL COMMENT 'Hospital ID',
    `department_id`      BIGINT        DEFAULT NULL COMMENT 'Department ID',
    `doctor_id`          BIGINT        DEFAULT NULL COMMENT 'Doctor ID',
    `title`              VARCHAR(255)  NOT NULL COMMENT 'Article title',
    `content`            LONGTEXT      NOT NULL COMMENT 'Article content',
    `cover_url`          VARCHAR(1024) DEFAULT NULL COMMENT 'Cover URL',
    `status`             TINYINT       NOT NULL DEFAULT 0 COMMENT '0-draft,1-reviewing,2-approved,3-published,4-rejected',
    `word_count`         INT           DEFAULT 0 COMMENT 'Word count',
    `remark`             VARCHAR(1000) DEFAULT NULL COMMENT 'Remark',
    `sources`            TEXT          DEFAULT NULL COMMENT 'GEO/RAG sources JSON',
    `citations`          TEXT          DEFAULT NULL COMMENT 'Public citations JSON',
    `medical_disclaimer` VARCHAR(1000) DEFAULT NULL COMMENT 'Medical disclaimer',
    `schema_json`        LONGTEXT      DEFAULT NULL COMMENT 'Structured JSON-LD',
    `status_audit`       LONGTEXT      DEFAULT NULL COMMENT 'Compliance audit JSON',
    `created_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_article_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`),
    KEY `idx_article_hospital` (`hospital_id`),
    KEY `idx_article_department` (`department_id`),
    KEY `idx_article_doctor` (`doctor_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_time` (`created_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Article information';

CREATE TABLE IF NOT EXISTS `article_generation_record`
(
    `id`                BIGINT        NOT NULL COMMENT 'Generation record ID',
    `agent_type`        TINYINT       NOT NULL DEFAULT 3 COMMENT 'Agent type: 1-hospital,2-department,3-doctor',
    `hospital_id`       BIGINT        DEFAULT NULL COMMENT 'Hospital ID',
    `hospital_name`     VARCHAR(255)  DEFAULT NULL COMMENT 'Hospital name',
    `department_id`     BIGINT        DEFAULT NULL COMMENT 'Department ID',
    `department_name`   VARCHAR(255)  DEFAULT NULL COMMENT 'Department name',
    `doctor_id`         BIGINT        DEFAULT NULL COMMENT 'Doctor ID',
    `doctor_name`       VARCHAR(100)  DEFAULT NULL COMMENT 'Doctor name',
    `query_text`        TEXT          NOT NULL COMMENT 'Generation prompt',
    `knowledge_sources` TEXT          DEFAULT NULL COMMENT 'Knowledge sources JSON',
    `response_title`    VARCHAR(255)  DEFAULT NULL COMMENT 'Generated title',
    `response_content`  LONGTEXT      DEFAULT NULL COMMENT 'Generated content',
    `article_id`        BIGINT        DEFAULT NULL COMMENT 'Article ID',
    `status`            TINYINT       NOT NULL DEFAULT 1 COMMENT '1-success,2-failed',
    `error_message`     VARCHAR(1000) DEFAULT NULL COMMENT 'Error message',
    `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (`id`),
    KEY `idx_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`),
    KEY `idx_hospital_id` (`hospital_id`),
    KEY `idx_department_id` (`department_id`),
    KEY `idx_doctor_id` (`doctor_id`),
    KEY `idx_article_id` (`article_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Article generation record';
