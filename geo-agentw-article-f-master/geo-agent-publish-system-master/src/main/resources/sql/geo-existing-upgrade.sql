-- Repeatable, data-preserving upgrade for an existing GEO schema on MySQL 8.
--
-- Select the target Feibo schema before sourcing this file. This script intentionally
-- keeps legacy columns and values. It only adds missing columns/indexes, relaxes the
-- obsolete knowledge_document.file_update_time constraint, creates or completes the
-- vector-conversion table, and aligns the conversion-status default.

SET @geo_upgrade_previous_group_concat_max_len := @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;

-- ---------------------------------------------------------------------------
-- department_info: the legacy table did not carry its hospital scope.
-- Existing rows remain NULL and can be backfilled only after business validation.
-- ---------------------------------------------------------------------------
SET @geo_upgrade_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `department_info` ',
            GROUP_CONCAT(CONCAT('ADD COLUMN ', required_column.column_definition)
                         ORDER BY required_column.sequence_no SEPARATOR ', ')
        )
    )
    FROM (
        SELECT 1 AS sequence_no,
               'hospital_id' AS column_name,
               '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Owning hospital ID''' AS column_definition
        UNION ALL
        SELECT 2,
               'hospital_name',
               '`hospital_name` VARCHAR(255) DEFAULT NULL COMMENT ''Owning hospital name'''
    ) AS required_column
    LEFT JOIN information_schema.COLUMNS existing_column
      ON existing_column.TABLE_SCHEMA = DATABASE()
     AND existing_column.TABLE_NAME = 'department_info'
     AND existing_column.COLUMN_NAME = required_column.column_name
    WHERE existing_column.COLUMN_NAME IS NULL
      AND EXISTS (
          SELECT 1
          FROM information_schema.TABLES existing_table
          WHERE existing_table.TABLE_SCHEMA = DATABASE()
            AND existing_table.TABLE_NAME = 'department_info'
      )
);
SET @geo_upgrade_sql := COALESCE(@geo_upgrade_sql, 'SELECT 1');
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

SET @geo_upgrade_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLES existing_table
            WHERE existing_table.TABLE_SCHEMA = DATABASE()
              AND existing_table.TABLE_NAME = 'department_info'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS existing_index
            WHERE existing_index.TABLE_SCHEMA = DATABASE()
              AND existing_index.TABLE_NAME = 'department_info'
              AND existing_index.INDEX_NAME = 'idx_department_hospital'
        ),
        'ALTER TABLE `department_info` ADD INDEX `idx_department_hospital` (`hospital_id`)',
        'SELECT 1'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

-- ---------------------------------------------------------------------------
-- knowledge_document: add the entity-backed remark field. The old
-- file_update_time column is retained, but its legacy NOT NULL/no-default rule is
-- relaxed so current inserts (which no longer populate that column) keep working.
-- Its original SQL type is preserved.
-- ---------------------------------------------------------------------------
SET @geo_upgrade_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.TABLES existing_table
            WHERE existing_table.TABLE_SCHEMA = DATABASE()
              AND existing_table.TABLE_NAME = 'knowledge_document'
        )
        AND NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS existing_column
            WHERE existing_column.TABLE_SCHEMA = DATABASE()
              AND existing_column.TABLE_NAME = 'knowledge_document'
              AND existing_column.COLUMN_NAME = 'remark'
        ),
        'ALTER TABLE `knowledge_document` ADD COLUMN `remark` VARCHAR(1000) DEFAULT NULL COMMENT ''Remark''',
        'SELECT 1'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

SET @geo_upgrade_sql := (
    SELECT COALESCE(
        MAX(CONCAT(
            'ALTER TABLE `knowledge_document` MODIFY COLUMN `file_update_time` ',
            existing_column.COLUMN_TYPE,
            ' NULL DEFAULT NULL COMMENT ''Legacy file update time; retained for compatibility'''
        )),
        'SELECT 1'
    )
    FROM information_schema.COLUMNS existing_column
    WHERE existing_column.TABLE_SCHEMA = DATABASE()
      AND existing_column.TABLE_NAME = 'knowledge_document'
      AND existing_column.COLUMN_NAME = 'file_update_time'
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

-- ---------------------------------------------------------------------------
-- knowledge_vector_conversion: create the final table when absent.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_vector_conversion`
(
    `id`              BIGINT        NOT NULL COMMENT 'Primary ID',
    `document_id`     BIGINT        NOT NULL COMMENT 'knowledge_document.id',
    `agent_type`      TINYINT       NOT NULL DEFAULT 3 COMMENT 'Agent type: 1-hospital, 2-department, 3-doctor',
    `hospital_id`     BIGINT                 DEFAULT NULL COMMENT 'Hospital ID',
    `hospital_name`   VARCHAR(255)           DEFAULT NULL COMMENT 'Hospital name',
    `department_id`   BIGINT                 DEFAULT NULL COMMENT 'Department ID',
    `department_name` VARCHAR(255)           DEFAULT NULL COMMENT 'Department name',
    `doctor_id`       BIGINT                 DEFAULT NULL COMMENT 'Doctor ID',
    `doctor_name`     VARCHAR(100)           DEFAULT NULL COMMENT 'Doctor name',
    `model`           VARCHAR(255)           DEFAULT NULL COMMENT 'Embedding model',
    `vector_count`    INT                    DEFAULT 0 COMMENT 'Vector count',
    `status`          TINYINT       NOT NULL DEFAULT 3 COMMENT '0-pending, 1-success, 2-failed, 3-processing',
    `error_message`   VARCHAR(1000)          DEFAULT NULL COMMENT 'Failure reason',
    `convert_time`    DATETIME               DEFAULT NULL COMMENT 'Conversion time',
    `file_name`       VARCHAR(255)            DEFAULT NULL COMMENT 'Original file name',
    PRIMARY KEY (`id`),
    KEY `idx_conversion_document` (`document_id`),
    KEY `idx_conversion_status` (`status`),
    KEY `idx_conversion_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT ='Knowledge vector conversion records';

-- Complete a legacy conversion table without overwriting any existing value.
-- id/document_id are added nullable when missing so populated legacy tables are not
-- assigned fabricated identifiers. The diagnostics at the end flag rows that need
-- an explicit, reviewed backfill before stronger constraints can be applied.
SET @geo_upgrade_sql := (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT 1',
        CONCAT(
            'ALTER TABLE `knowledge_vector_conversion` ',
            GROUP_CONCAT(CONCAT('ADD COLUMN ', required_column.column_definition)
                         ORDER BY required_column.sequence_no SEPARATOR ', ')
        )
    )
    FROM (
        SELECT 1 AS sequence_no,
               'id' AS column_name,
               '`id` BIGINT DEFAULT NULL COMMENT ''Primary ID; manual backfill required for legacy rows''' AS column_definition
        UNION ALL
        SELECT 2,
               'document_id',
               '`document_id` BIGINT DEFAULT NULL COMMENT ''knowledge_document.id; manual backfill required for legacy rows'''
        UNION ALL
        SELECT 3,
               'agent_type',
               '`agent_type` TINYINT NOT NULL DEFAULT 3 COMMENT ''Agent type: 1-hospital, 2-department, 3-doctor'''
        UNION ALL
        SELECT 4, 'hospital_id', '`hospital_id` BIGINT DEFAULT NULL COMMENT ''Hospital ID'''
        UNION ALL
        SELECT 5, 'hospital_name', '`hospital_name` VARCHAR(255) DEFAULT NULL COMMENT ''Hospital name'''
        UNION ALL
        SELECT 6, 'department_id', '`department_id` BIGINT DEFAULT NULL COMMENT ''Department ID'''
        UNION ALL
        SELECT 7, 'department_name', '`department_name` VARCHAR(255) DEFAULT NULL COMMENT ''Department name'''
        UNION ALL
        SELECT 8, 'doctor_id', '`doctor_id` BIGINT DEFAULT NULL COMMENT ''Doctor ID'''
        UNION ALL
        SELECT 9, 'doctor_name', '`doctor_name` VARCHAR(100) DEFAULT NULL COMMENT ''Doctor name'''
        UNION ALL
        SELECT 10, 'model', '`model` VARCHAR(255) DEFAULT NULL COMMENT ''Embedding model'''
        UNION ALL
        SELECT 11, 'vector_count', '`vector_count` INT DEFAULT 0 COMMENT ''Vector count'''
        UNION ALL
        SELECT 12, 'status', '`status` TINYINT NOT NULL DEFAULT 3 COMMENT ''0-pending, 1-success, 2-failed, 3-processing'''
        UNION ALL
        SELECT 13, 'error_message', '`error_message` VARCHAR(1000) DEFAULT NULL COMMENT ''Failure reason'''
        UNION ALL
        SELECT 14, 'convert_time', '`convert_time` DATETIME DEFAULT NULL COMMENT ''Conversion time'''
        UNION ALL
        SELECT 15, 'file_name', '`file_name` VARCHAR(255) DEFAULT NULL COMMENT ''Original file name'''
    ) AS required_column
    LEFT JOIN information_schema.COLUMNS existing_column
      ON existing_column.TABLE_SCHEMA = DATABASE()
     AND existing_column.TABLE_NAME = 'knowledge_vector_conversion'
     AND existing_column.COLUMN_NAME = required_column.column_name
    WHERE existing_column.COLUMN_NAME IS NULL
);
SET @geo_upgrade_sql := COALESCE(@geo_upgrade_sql, 'SELECT 1');
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

-- Align future inserts with KnowledgeDocumentConstants.STATUS_PROCESSING = 3.
-- Existing status values are deliberately not remapped.
ALTER TABLE `knowledge_vector_conversion`
    ALTER COLUMN `status` SET DEFAULT 3;

-- Add the expected lookup indexes when their canonical names are absent.
SET @geo_upgrade_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS existing_index
            WHERE existing_index.TABLE_SCHEMA = DATABASE()
              AND existing_index.TABLE_NAME = 'knowledge_vector_conversion'
              AND existing_index.INDEX_NAME = 'idx_conversion_document'
        ),
        'SELECT 1',
        'ALTER TABLE `knowledge_vector_conversion` ADD INDEX `idx_conversion_document` (`document_id`)'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

SET @geo_upgrade_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS existing_index
            WHERE existing_index.TABLE_SCHEMA = DATABASE()
              AND existing_index.TABLE_NAME = 'knowledge_vector_conversion'
              AND existing_index.INDEX_NAME = 'idx_conversion_status'
        ),
        'SELECT 1',
        'ALTER TABLE `knowledge_vector_conversion` ADD INDEX `idx_conversion_status` (`status`)'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

SET @geo_upgrade_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.STATISTICS existing_index
            WHERE existing_index.TABLE_SCHEMA = DATABASE()
              AND existing_index.TABLE_NAME = 'knowledge_vector_conversion'
              AND existing_index.INDEX_NAME = 'idx_conversion_agent_scope'
        ),
        'SELECT 1',
        'ALTER TABLE `knowledge_vector_conversion` ADD INDEX `idx_conversion_agent_scope` (`agent_type`, `hospital_id`, `department_id`, `doctor_id`)'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

-- Add a primary key only when doing so is demonstrably safe. If a populated legacy
-- table lacks usable IDs, leave it untouched and report the issue below.
SET @geo_upgrade_sql := (
    SELECT IF(
        NOT EXISTS (
            SELECT 1
            FROM information_schema.TABLE_CONSTRAINTS existing_constraint
            WHERE existing_constraint.CONSTRAINT_SCHEMA = DATABASE()
              AND existing_constraint.TABLE_NAME = 'knowledge_vector_conversion'
              AND existing_constraint.CONSTRAINT_TYPE = 'PRIMARY KEY'
        )
        AND NOT EXISTS (
            SELECT 1 FROM `knowledge_vector_conversion` WHERE `id` IS NULL
        )
        AND NOT EXISTS (
            SELECT `id`
            FROM `knowledge_vector_conversion`
            GROUP BY `id`
            HAVING COUNT(*) > 1
        ),
        'ALTER TABLE `knowledge_vector_conversion` ADD PRIMARY KEY (`id`)',
        'SELECT 1'
    )
);
PREPARE geo_upgrade_stmt FROM @geo_upgrade_sql;
EXECUTE geo_upgrade_stmt;
DEALLOCATE PREPARE geo_upgrade_stmt;

-- Diagnostics are intentionally read-only. Any non-zero count requires an explicit
-- data backfill decision; this repeatable script never invents or deletes IDs.
SELECT 'knowledge_vector_conversion.null_id' AS check_name, COUNT(*) AS issue_count
FROM `knowledge_vector_conversion`
WHERE `id` IS NULL
UNION ALL
SELECT 'knowledge_vector_conversion.duplicate_id', COUNT(*)
FROM (
    SELECT `id`
    FROM `knowledge_vector_conversion`
    WHERE `id` IS NOT NULL
    GROUP BY `id`
    HAVING COUNT(*) > 1
) duplicate_ids
UNION ALL
SELECT 'knowledge_vector_conversion.null_document_id', COUNT(*)
FROM `knowledge_vector_conversion`
WHERE `document_id` IS NULL
UNION ALL
SELECT 'knowledge_vector_conversion.id_is_nullable', COUNT(*)
FROM information_schema.COLUMNS existing_column
WHERE existing_column.TABLE_SCHEMA = DATABASE()
  AND existing_column.TABLE_NAME = 'knowledge_vector_conversion'
  AND existing_column.COLUMN_NAME = 'id'
  AND existing_column.IS_NULLABLE = 'YES'
UNION ALL
SELECT 'knowledge_vector_conversion.document_id_is_nullable', COUNT(*)
FROM information_schema.COLUMNS existing_column
WHERE existing_column.TABLE_SCHEMA = DATABASE()
  AND existing_column.TABLE_NAME = 'knowledge_vector_conversion'
  AND existing_column.COLUMN_NAME = 'document_id'
  AND existing_column.IS_NULLABLE = 'YES'
UNION ALL
SELECT 'knowledge_vector_conversion.primary_key_not_id', IF(
    EXISTS (
        SELECT 1
        FROM information_schema.KEY_COLUMN_USAGE key_column
        WHERE key_column.CONSTRAINT_SCHEMA = DATABASE()
          AND key_column.TABLE_NAME = 'knowledge_vector_conversion'
          AND key_column.CONSTRAINT_NAME = 'PRIMARY'
          AND key_column.COLUMN_NAME = 'id'
          AND key_column.ORDINAL_POSITION = 1
    ),
    0,
    1
)
UNION ALL
SELECT 'knowledge_vector_conversion.null_status', COUNT(*)
FROM `knowledge_vector_conversion`
WHERE `status` IS NULL;

SET SESSION group_concat_max_len = @geo_upgrade_previous_group_concat_max_len;
