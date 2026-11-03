SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `article_info` ADD COLUMN `sources` TEXT DEFAULT NULL COMMENT ''GEO/RAG sources JSON''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_info' AND COLUMN_NAME = 'sources'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `article_info` ADD COLUMN `citations` TEXT DEFAULT NULL COMMENT ''Public citations JSON''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_info' AND COLUMN_NAME = 'citations'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `article_info` ADD COLUMN `medical_disclaimer` VARCHAR(1000) DEFAULT NULL COMMENT ''Medical disclaimer''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_info' AND COLUMN_NAME = 'medical_disclaimer'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `article_info` ADD COLUMN `schema_json` LONGTEXT DEFAULT NULL COMMENT ''Structured JSON-LD''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_info' AND COLUMN_NAME = 'schema_json'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `article_info` ADD COLUMN `status_audit` LONGTEXT DEFAULT NULL COMMENT ''Compliance audit JSON''',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'article_info' AND COLUMN_NAME = 'status_audit'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `geo_query`
(
    `id`                 BIGINT       NOT NULL COMMENT 'Query ID',
    `doctor_id`          BIGINT       DEFAULT NULL COMMENT 'Doctor ID',
    `doctor_name`        VARCHAR(100) DEFAULT NULL COMMENT 'Doctor name',
    `department_id`      BIGINT       DEFAULT NULL COMMENT 'Department ID',
    `department_name`    VARCHAR(255) DEFAULT NULL COMMENT 'Department name',
    `hospital_id`        BIGINT       DEFAULT NULL COMMENT 'Hospital ID',
    `hospital_name`      VARCHAR(255) DEFAULT NULL COMMENT 'Hospital name',
    `region`             VARCHAR(255) DEFAULT NULL COMMENT 'Region',
    `disease`            VARCHAR(255) DEFAULT NULL COMMENT 'Disease',
    `treatment_scenario` VARCHAR(255) DEFAULT NULL COMMENT 'Treatment scenario',
    `query_cluster`      VARCHAR(255) DEFAULT NULL COMMENT 'Question cluster',
    `query_text`         VARCHAR(1000) NOT NULL COMMENT 'Question text',
    `expected_terms`     TEXT         DEFAULT NULL COMMENT 'Mention terms JSON',
    `source_type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1-generated,2-manual',
    `priority`           INT          NOT NULL DEFAULT 50 COMMENT 'Higher means more important',
    `monitor_interval_hours` INT      NOT NULL DEFAULT 24 COMMENT 'Per-query monitor interval in hours',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '0-disabled,1-enabled',
    `last_monitor_time`  DATETIME     DEFAULT NULL COMMENT 'Last monitor time',
    `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_query_doctor` (`doctor_id`),
    KEY `idx_geo_query_department` (`department_id`),
    KEY `idx_geo_query_enabled_priority` (`enabled`, `priority`),
    KEY `idx_geo_query_enabled_interval` (`enabled`, `monitor_interval_hours`),
    KEY `idx_geo_query_last_monitor` (`last_monitor_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO query library';

CREATE TABLE IF NOT EXISTS `geo_monitor_record`
(
    `id`              BIGINT       NOT NULL COMMENT 'Record ID',
    `query_id`        BIGINT       NOT NULL COMMENT 'GEO query ID',
    `engine`          VARCHAR(100) NOT NULL COMMENT 'AI search engine',
    `query_text`      VARCHAR(1000) NOT NULL COMMENT 'Question text',
    `answer_text`     LONGTEXT     DEFAULT NULL COMMENT 'Engine answer',
    `mentioned`       TINYINT      NOT NULL DEFAULT 0 COMMENT 'Whether expected terms were mentioned',
    `cited`           TINYINT      NOT NULL DEFAULT 0 COMMENT 'Whether URLs were cited',
    `cited_urls`      TEXT         DEFAULT NULL COMMENT 'Cited URLs JSON',
    `matched_terms`   TEXT         DEFAULT NULL COMMENT 'Matched terms JSON',
    `visibility_score` INT         DEFAULT 0 COMMENT 'GEO visibility score 0-100',
    `rank_position`   INT          DEFAULT NULL COMMENT 'First matched source rank',
    `source_count`    INT          DEFAULT 0 COMMENT 'Returned source count',
    `official_source_count` INT    DEFAULT 0 COMMENT 'Official source count',
    `competitor_mentions` TEXT     DEFAULT NULL COMMENT 'Competitor mentions JSON',
    `raw_payload`     LONGTEXT     DEFAULT NULL COMMENT 'Raw engine payload',
    `latency_ms`      BIGINT       DEFAULT NULL COMMENT 'Engine latency in milliseconds',
    `article_id`      BIGINT       DEFAULT NULL COMMENT 'Related article ID',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1-success,2-failed',
    `error_message`   VARCHAR(1000) DEFAULT NULL COMMENT 'Failure reason',
    `monitored_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Monitor time',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_monitor_query` (`query_id`),
    KEY `idx_geo_monitor_engine_time` (`engine`, `monitored_time`),
    KEY `idx_geo_monitor_mentioned` (`mentioned`),
    KEY `idx_geo_monitor_article` (`article_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO monitor record';

CREATE TABLE IF NOT EXISTS `geo_monitor_task`
(
    `id`                BIGINT        NOT NULL COMMENT 'Task ID',
    `query_id`          BIGINT        NOT NULL COMMENT 'GEO query ID',
    `engine`            VARCHAR(100)  NOT NULL COMMENT 'AI search engine',
    `query_text`        VARCHAR(1000) NOT NULL COMMENT 'Question text snapshot',
    `priority`          INT           NOT NULL DEFAULT 50 COMMENT 'Higher means more important',
    `status`            TINYINT       NOT NULL DEFAULT 0 COMMENT '0-pending,1-running,2-success,3-failed',
    `trigger_type`      VARCHAR(50)   NOT NULL DEFAULT 'scheduled' COMMENT 'manual,scheduled,retest',
    `attempt_count`     INT           NOT NULL DEFAULT 0 COMMENT 'Attempt count',
    `max_attempts`      INT           NOT NULL DEFAULT 2 COMMENT 'Max attempts',
    `planned_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Planned execution time',
    `started_time`      DATETIME      DEFAULT NULL COMMENT 'Started time',
    `finished_time`     DATETIME      DEFAULT NULL COMMENT 'Finished time',
    `next_retry_time`   DATETIME      DEFAULT NULL COMMENT 'Next retry time',
    `monitor_record_id` BIGINT        DEFAULT NULL COMMENT 'Monitor record ID',
    `error_message`     VARCHAR(1000) DEFAULT NULL COMMENT 'Failure reason',
    `created_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_task_status_plan` (`status`, `planned_time`, `priority`),
    KEY `idx_geo_task_query_engine` (`query_id`, `engine`),
    KEY `idx_geo_task_trigger` (`trigger_type`),
    KEY `idx_geo_task_record` (`monitor_record_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO monitor task queue';

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `visibility_score` INT DEFAULT 0 COMMENT ''GEO visibility score 0-100'' AFTER `matched_terms`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'visibility_score'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `rank_position` INT DEFAULT NULL COMMENT ''First matched source rank'' AFTER `visibility_score`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'rank_position'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `source_count` INT DEFAULT 0 COMMENT ''Returned source count'' AFTER `rank_position`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'source_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `official_source_count` INT DEFAULT 0 COMMENT ''Official source count'' AFTER `source_count`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'official_source_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `competitor_mentions` TEXT DEFAULT NULL COMMENT ''Competitor mentions JSON'' AFTER `official_source_count`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'competitor_mentions'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `raw_payload` LONGTEXT DEFAULT NULL COMMENT ''Raw engine payload'' AFTER `competitor_mentions`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'raw_payload'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `geo_monitor_record` ADD COLUMN `latency_ms` BIGINT DEFAULT NULL COMMENT ''Engine latency in milliseconds'' AFTER `raw_payload`',
        'SELECT 1'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geo_monitor_record' AND COLUMN_NAME = 'latency_ms'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `geo_feedback_suggestion`
(
    `id`                 BIGINT       NOT NULL COMMENT 'Suggestion ID',
    `query_id`           BIGINT       NOT NULL COMMENT 'GEO query ID',
    `monitor_record_id`  BIGINT       DEFAULT NULL COMMENT 'Monitor record ID',
    `article_id`         BIGINT       DEFAULT NULL COMMENT 'Related article ID',
    `suggestion_type`    VARCHAR(50)  NOT NULL COMMENT 'supplement,faq,structured_summary',
    `title`              VARCHAR(255) NOT NULL COMMENT 'Suggestion title',
    `content`            LONGTEXT     NOT NULL COMMENT 'Supplemental content',
    `faq_json`           TEXT         DEFAULT NULL COMMENT 'FAQ JSON',
    `structured_summary` TEXT         DEFAULT NULL COMMENT 'Structured summary JSON',
    `miss_reason`        VARCHAR(1000) DEFAULT NULL COMMENT 'Why the query missed',
    `cluster_key`        VARCHAR(500) DEFAULT NULL COMMENT 'Optimization cluster key',
    `compliance_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0-pending,1-passed,2-blocked',
    `compliance_result`  LONGTEXT     DEFAULT NULL COMMENT 'Compliance result JSON',
    `publish_status`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0-not published,1-queued,2-published,3-failed',
    `target_channels`    TEXT         DEFAULT NULL COMMENT 'Target publish/index channels JSON',
    `retest_status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-waiting,1-scheduled,2-running,3-done',
    `retest_plan`        TEXT         DEFAULT NULL COMMENT 'Retest plan JSON',
    `last_retest_time`   DATETIME     DEFAULT NULL COMMENT 'Last retest time',
    `status`             TINYINT      NOT NULL DEFAULT 0 COMMENT '0-new,1-applied,2-rejected',
    `created_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_feedback_query` (`query_id`),
    KEY `idx_geo_feedback_record` (`monitor_record_id`),
    KEY `idx_geo_feedback_article` (`article_id`),
    KEY `idx_geo_feedback_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO feedback suggestion';

CREATE TABLE IF NOT EXISTS `medical_compliance_rule`
(
    `id`           BIGINT       NOT NULL COMMENT 'Rule ID',
    `rule_type`    VARCHAR(50)  NOT NULL COMMENT 'effect_claim,personal_advice,exaggeration,sensitive_word,drug_recommendation',
    `pattern`      VARCHAR(500) NOT NULL COMMENT 'Keyword or regex',
    `description`  VARCHAR(500) DEFAULT NULL COMMENT 'Description',
    `severity`     VARCHAR(20)  NOT NULL DEFAULT 'high' COMMENT 'low,medium,high,critical',
    `enabled`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0-disabled,1-enabled',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_medical_rule_enabled` (`enabled`),
    KEY `idx_medical_rule_type` (`rule_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Medical compliance rule';

CREATE TABLE IF NOT EXISTS `geo_dialogue_probe`
(
    `id`                    BIGINT        NOT NULL COMMENT 'Probe ID',
    `query_id`              BIGINT        NOT NULL COMMENT 'GEO query ID',
    `engine`                VARCHAR(100)  NOT NULL COMMENT 'AI platform',
    `query_text`            VARCHAR(1000) NOT NULL COMMENT 'Baseline user-intent query',
    `probe_mode`            VARCHAR(50)   NOT NULL DEFAULT 'platform_self_review' COMMENT 'platform_self_review,evidence_analysis',
    `requested_rounds`      INT           NOT NULL DEFAULT 1 COMMENT 'Requested diagnostic rounds',
    `completed_rounds`      INT           NOT NULL DEFAULT 0 COMMENT 'Completed diagnostic rounds',
    `status`                TINYINT       NOT NULL DEFAULT 0 COMMENT '0-queued,1-running,2-success,3-failed',
    `mentioned`             TINYINT       NOT NULL DEFAULT 0 COMMENT 'Whether target entity was mentioned',
    `cited`                 TINYINT       NOT NULL DEFAULT 0 COMMENT 'Whether any URL was cited',
    `visibility_score`      INT           NOT NULL DEFAULT 0 COMMENT 'GEO visibility score',
    `official_source_count` INT           NOT NULL DEFAULT 0 COMMENT 'Official source count',
    `baseline_answer`       LONGTEXT      DEFAULT NULL COMMENT 'Baseline platform answer',
    `baseline_sources`      LONGTEXT      DEFAULT NULL COMMENT 'Baseline source evidence JSON',
    `dialogue_json`         LONGTEXT      DEFAULT NULL COMMENT 'Diagnostic dialogue JSON',
    `gap_type`              VARCHAR(100)  DEFAULT NULL COMMENT 'Classified GEO gap',
    `gap_summary`           VARCHAR(1000) DEFAULT NULL COMMENT 'Gap summary',
    `source_requirements`   LONGTEXT      DEFAULT NULL COMMENT 'Required public sources JSON',
    `recommended_actions`   LONGTEXT      DEFAULT NULL COMMENT 'Recommended actions JSON',
    `error_message`         VARCHAR(1000) DEFAULT NULL COMMENT 'Failure reason',
    `started_time`          DATETIME      DEFAULT NULL COMMENT 'Start time',
    `finished_time`         DATETIME      DEFAULT NULL COMMENT 'Finish time',
    `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_probe_status_created` (`status`, `created_time`),
    KEY `idx_geo_probe_query_engine` (`query_id`, `engine`),
    KEY `idx_geo_probe_gap` (`gap_type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO diagnostic dialogue probe';

CREATE TABLE IF NOT EXISTS `geo_source_asset`
(
    `id`                   BIGINT        NOT NULL COMMENT 'Source asset ID',
    `url_hash`             CHAR(64)      NOT NULL COMMENT 'SHA-256 URL hash',
    `url`                  VARCHAR(2000) NOT NULL COMMENT 'Public source URL',
    `title`                VARCHAR(500)  DEFAULT NULL COMMENT 'Source title',
    `domain`               VARCHAR(255)  DEFAULT NULL COMMENT 'Source domain',
    `source_type`          VARCHAR(50)   NOT NULL DEFAULT 'third_party' COMMENT 'official_site,government,academic,official_social,publishing_platform,third_party',
    `entity_type`          VARCHAR(50)   DEFAULT NULL COMMENT 'hospital,department,doctor,topic',
    `entity_id`            BIGINT        DEFAULT NULL COMMENT 'Related entity ID',
    `entity_name`          VARCHAR(255)  DEFAULT NULL COMMENT 'Related entity name',
    `official`             TINYINT       NOT NULL DEFAULT 0 COMMENT 'Whether configured as official source',
    `authority_score`      INT           NOT NULL DEFAULT 0 COMMENT 'Source authority score 0-100',
    `citation_count`       INT           NOT NULL DEFAULT 0 COMMENT 'Observed citation count',
    `engines`              TEXT          DEFAULT NULL COMMENT 'Observed platforms JSON',
    `verification_status`  TINYINT       NOT NULL DEFAULT 0 COMMENT '0-unverified,1-verified,2-rejected',
    `usage_recommendation` VARCHAR(1000) DEFAULT NULL COMMENT 'Recommended source usage',
    `first_seen_time`      DATETIME      DEFAULT NULL COMMENT 'First observed time',
    `last_seen_time`       DATETIME      DEFAULT NULL COMMENT 'Last observed time',
    `created_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_geo_source_url_hash` (`url_hash`),
    KEY `idx_geo_source_type_score` (`source_type`, `authority_score`),
    KEY `idx_geo_source_entity` (`entity_type`, `entity_id`),
    KEY `idx_geo_source_seen` (`last_seen_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='GEO cited source asset library';

CREATE TABLE IF NOT EXISTS `geo_site_recommendation`
(
    `id`                    BIGINT        NOT NULL COMMENT 'Recommendation ID',
    `query_id`              BIGINT        NOT NULL COMMENT 'GEO query ID',
    `probe_id`              BIGINT        DEFAULT NULL COMMENT 'Dialogue probe ID',
    `monitor_record_id`     BIGINT        DEFAULT NULL COMMENT 'Monitor record ID',
    `entity_type`           VARCHAR(50)   NOT NULL COMMENT 'hospital,department,doctor',
    `entity_id`             BIGINT        DEFAULT NULL COMMENT 'Target entity ID',
    `entity_name`           VARCHAR(255)  DEFAULT NULL COMMENT 'Target entity name',
    `recommendation_type`   VARCHAR(100)  NOT NULL COMMENT 'content,faq,schema,source_authority,indexing,internal_link',
    `title`                 VARCHAR(255)  NOT NULL COMMENT 'Recommendation title',
    `current_gap`           VARCHAR(1000) DEFAULT NULL COMMENT 'Observed gap',
    `recommended_change`    LONGTEXT      NOT NULL COMMENT 'Proposed official-site change',
    `content_brief`         LONGTEXT      DEFAULT NULL COMMENT 'Content brief JSON',
    `evidence_json`         LONGTEXT      DEFAULT NULL COMMENT 'Supporting evidence JSON',
    `execution_mode`        VARCHAR(50)   NOT NULL DEFAULT 'recommendation_only' COMMENT 'Always recommendation_only for official site',
    `external_owner`        VARCHAR(100)  NOT NULL DEFAULT 'hospital_website_owner' COMMENT 'External execution owner',
    `priority`              INT           NOT NULL DEFAULT 50 COMMENT 'Recommendation priority',
    `compliance_status`     TINYINT       NOT NULL DEFAULT 0 COMMENT '0-pending,1-passed,2-blocked',
    `compliance_result`     LONGTEXT      DEFAULT NULL COMMENT 'Compliance result JSON',
    `status`                TINYINT       NOT NULL DEFAULT 0 COMMENT '0-draft,1-submitted,2-accepted,3-rejected,4-completed',
    `status_note`           VARCHAR(1000) DEFAULT NULL COMMENT 'Manual handoff note',
    `submitted_time`        DATETIME      DEFAULT NULL COMMENT 'Submitted to website owner time',
    `completed_time`        DATETIME      DEFAULT NULL COMMENT 'Externally completed time',
    `created_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_site_status_priority` (`status`, `priority`),
    KEY `idx_geo_site_query` (`query_id`),
    KEY `idx_geo_site_probe` (`probe_id`),
    KEY `idx_geo_site_record` (`monitor_record_id`),
    KEY `idx_geo_site_entity` (`entity_type`, `entity_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Official website change recommendations; no automatic write access';

CREATE TABLE IF NOT EXISTS `geo_website_monitor`
(
    `id`                     BIGINT        NOT NULL COMMENT 'Website monitor ID',
    `hospital_id`            BIGINT        NOT NULL COMMENT 'Related hospital ID',
    `hospital_name`          VARCHAR(255)  NOT NULL COMMENT 'Hospital name snapshot',
    `site_name`              VARCHAR(255)  DEFAULT NULL COMMENT 'Display name for the monitored site',
    `site_url`               VARCHAR(2000) NOT NULL COMMENT 'URL entered by the operator',
    `normalized_url`         VARCHAR(2000) NOT NULL COMMENT 'Canonicalized public HTTP(S) URL',
    `url_hash`               CHAR(64)      NOT NULL COMMENT 'SHA-256 of normalized URL',
    `domain`                 VARCHAR(255)  NOT NULL COMMENT 'Canonical ASCII host name',
    `active`                 TINYINT       NOT NULL DEFAULT 1 COMMENT '0-archived,1-active',
    `monitor_enabled`        TINYINT       NOT NULL DEFAULT 1 COMMENT '0-manual only,1-scheduled checks enabled',
    `monitor_interval_hours` INT           NOT NULL DEFAULT 24 COMMENT 'Scheduled check interval in hours',
    `last_check_status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '0-pending/not checked,1-running,2-success,3-failed',
    `last_score`             INT           DEFAULT NULL COMMENT 'Latest deterministic website GEO score 0-100',
    `last_http_status`       INT           DEFAULT NULL COMMENT 'Latest final HTTP status',
    `last_final_url`         VARCHAR(2000) DEFAULT NULL COMMENT 'Latest validated final URL',
    `last_error`             VARCHAR(1000) DEFAULT NULL COMMENT 'Latest sanitized failure reason',
    `last_checked_time`      DATETIME      DEFAULT NULL COMMENT 'Latest completed check time',
    `next_check_time`        DATETIME      DEFAULT NULL COMMENT 'Next scheduled check time',
    `created_by`             BIGINT        DEFAULT NULL COMMENT 'Operator user ID from authenticated context',
    `created_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_geo_website_hospital_url` (`hospital_id`, `url_hash`),
    KEY `idx_geo_website_domain` (`domain`),
    KEY `idx_geo_website_schedule` (`active`, `monitor_enabled`, `next_check_time`),
    KEY `idx_geo_website_last_status` (`last_check_status`, `last_checked_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Official website GEO monitor configuration and latest status';

CREATE TABLE IF NOT EXISTS `geo_website_check`
(
    `id`                   BIGINT        NOT NULL COMMENT 'Website check ID',
    `monitor_id`           BIGINT        NOT NULL COMMENT 'Website monitor ID',
    `trigger_type`         VARCHAR(30)   NOT NULL DEFAULT 'manual' COMMENT 'manual,scheduled',
    `status`               TINYINT       NOT NULL DEFAULT 0 COMMENT '0-pending,1-running,2-success,3-failed',
    `requested_url`        VARCHAR(2000) NOT NULL COMMENT 'Normalized URL snapshot',
    `final_url`            VARCHAR(2000) DEFAULT NULL COMMENT 'Final URL after validated redirects',
    `http_status`          INT           DEFAULT NULL COMMENT 'Final HTTP status',
    `content_type`         VARCHAR(255)  DEFAULT NULL COMMENT 'Validated response content type',
    `page_title`           VARCHAR(500)  DEFAULT NULL COMMENT 'Extracted HTML title',
    `score`                INT           DEFAULT NULL COMMENT 'Deterministic website GEO score 0-100',
    `checks_json`          LONGTEXT      DEFAULT NULL COMMENT 'Deterministic check results JSON',
    `recommendations_json` LONGTEXT      DEFAULT NULL COMMENT 'Website improvement recommendations JSON',
    `content_hash`         CHAR(64)      DEFAULT NULL COMMENT 'SHA-256 of the bounded fetched HTML',
    `latency_ms`           BIGINT        DEFAULT NULL COMMENT 'End-to-end check latency in milliseconds',
    `error_message`        VARCHAR(1000) DEFAULT NULL COMMENT 'Sanitized failure reason',
    `started_time`         DATETIME      DEFAULT NULL COMMENT 'Worker start time',
    `checked_time`         DATETIME      DEFAULT NULL COMMENT 'Worker completion time',
    `created_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (`id`),
    KEY `idx_geo_website_check_monitor` (`monitor_id`, `created_time`),
    KEY `idx_geo_website_check_status` (`status`, `created_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Immutable official website GEO check evidence';

CREATE TABLE IF NOT EXISTS `geo_source_observation`
(
    `id`              BIGINT       NOT NULL COMMENT 'Observation ID',
    `source_asset_id` BIGINT       NOT NULL COMMENT 'Source asset ID',
    `origin_type`     VARCHAR(30)  NOT NULL COMMENT 'monitor,probe',
    `origin_id`       BIGINT       NOT NULL COMMENT 'Monitor record or probe ID',
    `query_id`        BIGINT       DEFAULT NULL COMMENT 'GEO query ID',
    `engine`          VARCHAR(100) DEFAULT NULL COMMENT 'AI platform',
    `observed_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Observation time',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_geo_source_observation` (`origin_type`, `origin_id`, `source_asset_id`),
    KEY `idx_geo_source_observation_asset` (`source_asset_id`, `observed_time`),
    KEY `idx_geo_source_observation_query` (`query_id`, `engine`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Idempotent GEO source citation observation';

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS modify_column_if_exists;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE modify_column_if_exists(IN p_table_name VARCHAR(64), IN p_column_name VARCHAR(64), IN p_column_definition TEXT)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` MODIFY COLUMN ', p_column_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

CREATE PROCEDURE add_index_if_missing(IN p_table_name VARCHAR(64), IN p_index_name VARCHAR(64), IN p_index_definition TEXT)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND INDEX_NAME = p_index_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `', p_index_name, '` ', p_index_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_column_if_missing('geo_feedback_suggestion', 'processing_note', '`processing_note` VARCHAR(2000) DEFAULT NULL');
CALL add_column_if_missing('geo_feedback_suggestion', 'handled_by', '`handled_by` BIGINT DEFAULT NULL');
CALL add_column_if_missing('geo_feedback_suggestion', 'manual_published_url', '`manual_published_url` VARCHAR(2000) DEFAULT NULL');
CALL add_column_if_missing('geo_feedback_suggestion', 'publication_confirmed_by', '`publication_confirmed_by` BIGINT DEFAULT NULL');
CALL add_column_if_missing('geo_feedback_suggestion', 'publication_confirmed_time', '`publication_confirmed_time` DATETIME DEFAULT NULL');
CALL add_column_if_missing('geo_feedback_suggestion', 'active_retest_batch_id', '`active_retest_batch_id` BIGINT DEFAULT NULL');
CALL add_column_if_missing('geo_monitor_task', 'feedback_suggestion_id', '`feedback_suggestion_id` BIGINT DEFAULT NULL');
CALL add_column_if_missing('geo_monitor_task', 'retest_batch_id', '`retest_batch_id` BIGINT DEFAULT NULL');
CALL add_index_if_missing('geo_monitor_task', 'idx_geo_feedback_retest_batch', '(`feedback_suggestion_id`, `retest_batch_id`, `status`)');

CALL add_column_if_missing('geo_feedback_suggestion', 'miss_reason', '`miss_reason` VARCHAR(1000) DEFAULT NULL COMMENT ''Why the query missed'' AFTER `structured_summary`');
CALL add_column_if_missing('geo_feedback_suggestion', 'cluster_key', '`cluster_key` VARCHAR(500) DEFAULT NULL COMMENT ''Optimization cluster key'' AFTER `miss_reason`');
CALL add_column_if_missing('geo_feedback_suggestion', 'compliance_status', '`compliance_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0-pending,1-passed,2-blocked'' AFTER `cluster_key`');
CALL add_column_if_missing('geo_feedback_suggestion', 'compliance_result', '`compliance_result` LONGTEXT DEFAULT NULL COMMENT ''Compliance result JSON'' AFTER `compliance_status`');
CALL add_column_if_missing('geo_feedback_suggestion', 'publish_status', '`publish_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0-not published,1-queued,2-published,3-failed'' AFTER `compliance_result`');
CALL add_column_if_missing('geo_feedback_suggestion', 'target_channels', '`target_channels` TEXT DEFAULT NULL COMMENT ''Target publish/index channels JSON'' AFTER `publish_status`');
CALL add_column_if_missing('geo_feedback_suggestion', 'retest_status', '`retest_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''0-waiting,1-scheduled,2-running,3-done'' AFTER `target_channels`');
CALL add_column_if_missing('geo_feedback_suggestion', 'retest_plan', '`retest_plan` TEXT DEFAULT NULL COMMENT ''Retest plan JSON'' AFTER `retest_status`');
CALL add_column_if_missing('geo_feedback_suggestion', 'last_retest_time', '`last_retest_time` DATETIME DEFAULT NULL COMMENT ''Last retest time'' AFTER `retest_plan`');
CALL add_column_if_missing('geo_feedback_suggestion', 'execution_mode', '`execution_mode` VARCHAR(50) NOT NULL DEFAULT ''recommendation_only'' COMMENT ''Execution boundary'' AFTER `target_channels`');
CALL add_column_if_missing('geo_feedback_suggestion', 'official_site_recommendation', '`official_site_recommendation` LONGTEXT DEFAULT NULL COMMENT ''Official-site recommendation JSON; never auto-written'' AFTER `execution_mode`');
CALL add_index_if_missing('geo_feedback_suggestion', 'idx_geo_feedback_compliance', '(`compliance_status`)');
CALL add_index_if_missing('geo_feedback_suggestion', 'idx_geo_feedback_publish', '(`publish_status`)');
CALL add_index_if_missing('geo_feedback_suggestion', 'idx_geo_feedback_retest', '(`retest_status`)');
CALL add_index_if_missing('geo_feedback_suggestion', 'idx_geo_feedback_cluster', '(`cluster_key`(191))');

CALL add_column_if_missing('geo_query', 'monitor_interval_hours', '`monitor_interval_hours` INT NOT NULL DEFAULT 24 COMMENT ''Per-query monitor interval in hours'' AFTER `priority`');
CALL add_index_if_missing('geo_query', 'idx_geo_query_enabled_interval', '(`enabled`, `monitor_interval_hours`)');
UPDATE `geo_query` SET `monitor_interval_hours` = 24 WHERE `monitor_interval_hours` IS NULL OR `monitor_interval_hours` < 1;

CALL add_column_if_missing('article_info', 'agent_type', '`agent_type` TINYINT NOT NULL DEFAULT 3 COMMENT ''Agent type: 1-hospital,2-department,3-doctor'' AFTER `id`');
CALL add_column_if_missing('article_info', 'hospital_id', '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Hospital ID'' AFTER `agent_type`');
CALL add_column_if_missing('article_info', 'department_id', '`department_id` BIGINT DEFAULT NULL COMMENT ''Department ID'' AFTER `hospital_id`');
CALL modify_column_if_exists('article_info', 'doctor_id', '`doctor_id` BIGINT DEFAULT NULL COMMENT ''Doctor ID''');
CALL add_index_if_missing('article_info', 'idx_article_agent_scope', '(`agent_type`, `hospital_id`, `department_id`, `doctor_id`)');
CALL add_index_if_missing('article_info', 'idx_article_hospital', '(`hospital_id`)');
CALL add_index_if_missing('article_info', 'idx_article_department', '(`department_id`)');
CALL add_index_if_missing('article_info', 'idx_article_doctor', '(`doctor_id`)');

CALL add_column_if_missing('knowledge_document', 'agent_type', '`agent_type` TINYINT NOT NULL DEFAULT 3 COMMENT ''Agent type: 1-hospital,2-department,3-doctor'' AFTER `id`');
CALL add_column_if_missing('knowledge_document', 'hospital_id', '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Hospital ID'' AFTER `agent_type`');
CALL add_column_if_missing('knowledge_document', 'hospital_name', '`hospital_name` VARCHAR(255) DEFAULT NULL COMMENT ''Hospital name'' AFTER `hospital_id`');
CALL add_column_if_missing('knowledge_document', 'department_id', '`department_id` BIGINT DEFAULT NULL COMMENT ''Department ID'' AFTER `hospital_name`');
CALL add_column_if_missing('knowledge_document', 'department_name', '`department_name` VARCHAR(255) DEFAULT NULL COMMENT ''Department name'' AFTER `department_id`');
CALL add_column_if_missing('knowledge_document', 'doctor_name', '`doctor_name` VARCHAR(100) DEFAULT NULL COMMENT ''Doctor name'' AFTER `doctor_id`');
CALL modify_column_if_exists('knowledge_document', 'doctor_id', '`doctor_id` BIGINT DEFAULT NULL COMMENT ''Doctor ID''');

CALL add_column_if_missing('knowledge_vector_conversion', 'agent_type', '`agent_type` TINYINT NOT NULL DEFAULT 3 COMMENT ''Agent type: 1-hospital,2-department,3-doctor'' AFTER `document_id`');
CALL add_column_if_missing('knowledge_vector_conversion', 'hospital_id', '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Hospital ID'' AFTER `agent_type`');
CALL add_column_if_missing('knowledge_vector_conversion', 'hospital_name', '`hospital_name` VARCHAR(255) DEFAULT NULL COMMENT ''Hospital name'' AFTER `hospital_id`');
CALL add_column_if_missing('knowledge_vector_conversion', 'department_id', '`department_id` BIGINT DEFAULT NULL COMMENT ''Department ID'' AFTER `hospital_name`');
CALL add_column_if_missing('knowledge_vector_conversion', 'department_name', '`department_name` VARCHAR(255) DEFAULT NULL COMMENT ''Department name'' AFTER `department_id`');
CALL add_column_if_missing('knowledge_vector_conversion', 'doctor_name', '`doctor_name` VARCHAR(100) DEFAULT NULL COMMENT ''Doctor name'' AFTER `doctor_id`');
CALL modify_column_if_exists('knowledge_vector_conversion', 'doctor_id', '`doctor_id` BIGINT DEFAULT NULL COMMENT ''Doctor ID''');

CALL add_column_if_missing('article_generation_record', 'agent_type', '`agent_type` TINYINT NOT NULL DEFAULT 3 COMMENT ''Agent type: 1-hospital,2-department,3-doctor'' AFTER `id`');
CALL add_column_if_missing('article_generation_record', 'hospital_id', '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Hospital ID'' AFTER `agent_type`');
CALL add_column_if_missing('article_generation_record', 'hospital_name', '`hospital_name` VARCHAR(255) DEFAULT NULL COMMENT ''Hospital name'' AFTER `hospital_id`');
CALL add_column_if_missing('article_generation_record', 'department_id', '`department_id` BIGINT DEFAULT NULL COMMENT ''Department ID'' AFTER `hospital_name`');
CALL add_column_if_missing('article_generation_record', 'department_name', '`department_name` VARCHAR(255) DEFAULT NULL COMMENT ''Department name'' AFTER `department_id`');
CALL add_column_if_missing('article_generation_record', 'doctor_name', '`doctor_name` VARCHAR(100) DEFAULT NULL COMMENT ''Doctor name'' AFTER `doctor_id`');
CALL modify_column_if_exists('article_generation_record', 'doctor_id', '`doctor_id` BIGINT DEFAULT NULL COMMENT ''Doctor ID''');

CALL add_index_if_missing('knowledge_document', 'idx_knowledge_agent_scope', '(`agent_type`, `hospital_id`, `department_id`, `doctor_id`)');
CALL add_index_if_missing('knowledge_document', 'idx_knowledge_hospital', '(`hospital_id`)');
CALL add_index_if_missing('knowledge_document', 'idx_knowledge_department', '(`department_id`)');
CALL add_index_if_missing('knowledge_document', 'idx_knowledge_doctor', '(`doctor_id`)');
CALL add_index_if_missing('knowledge_vector_conversion', 'idx_conversion_agent_scope', '(`agent_type`, `hospital_id`, `department_id`, `doctor_id`)');
CALL add_index_if_missing('article_generation_record', 'idx_generation_agent_scope', '(`agent_type`, `hospital_id`, `department_id`, `doctor_id`)');

UPDATE `knowledge_document` kd
JOIN `doctor_info` d ON kd.`doctor_id` = d.`id`
SET kd.`agent_type` = 3,
    kd.`hospital_id` = d.`hospital_id`,
    kd.`hospital_name` = d.`hospital_name`,
    kd.`department_id` = d.`department_id`,
    kd.`department_name` = d.`department_name`,
    kd.`doctor_name` = d.`doctor_name`
WHERE kd.`doctor_id` IS NOT NULL
  AND (kd.`hospital_id` IS NULL OR kd.`department_id` IS NULL OR kd.`doctor_name` IS NULL);

UPDATE `knowledge_vector_conversion` vc
JOIN `doctor_info` d ON vc.`doctor_id` = d.`id`
SET vc.`agent_type` = 3,
    vc.`hospital_id` = d.`hospital_id`,
    vc.`hospital_name` = d.`hospital_name`,
    vc.`department_id` = d.`department_id`,
    vc.`department_name` = d.`department_name`,
    vc.`doctor_name` = d.`doctor_name`
WHERE vc.`doctor_id` IS NOT NULL
  AND (vc.`hospital_id` IS NULL OR vc.`department_id` IS NULL OR vc.`doctor_name` IS NULL);

UPDATE `article_generation_record` agr
JOIN `doctor_info` d ON agr.`doctor_id` = d.`id`
SET agr.`agent_type` = 3,
    agr.`hospital_id` = d.`hospital_id`,
    agr.`hospital_name` = d.`hospital_name`,
    agr.`department_id` = d.`department_id`,
    agr.`department_name` = d.`department_name`,
    agr.`doctor_name` = d.`doctor_name`
WHERE agr.`doctor_id` IS NOT NULL
  AND (agr.`hospital_id` IS NULL OR agr.`department_id` IS NULL OR agr.`doctor_name` IS NULL);

UPDATE `article_info` ai
JOIN `doctor_info` d ON ai.`doctor_id` = d.`id`
SET ai.`agent_type` = 3,
    ai.`hospital_id` = d.`hospital_id`,
    ai.`department_id` = d.`department_id`
WHERE ai.`doctor_id` IS NOT NULL
  AND (ai.`hospital_id` IS NULL OR ai.`department_id` IS NULL OR ai.`agent_type` IS NULL);

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS modify_column_if_exists;
DROP PROCEDURE IF EXISTS add_index_if_missing;
