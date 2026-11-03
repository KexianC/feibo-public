-- Add tombstone state for reversible-at-the-database-layer model retirement.
-- The application hides tombstones from all management/public/runtime entry
-- queries, but retains their business IDs so already-started chats can persist.
-- Safe to rerun on production MySQL 8.0.36 and later MySQL 8.x. Unsupported
-- online DDL fails closed; there is no
-- fallback to a table-copying or write-blocking algorithm.
-- Deployment gate: run this file to completion before deploying application
-- code that reads is_deleted, using the mysql client without --force. Continue
-- only after ai_model_soft_delete_ready=1 is returned.

SET @schema_name = DATABASE();

SELECT COUNT(*) INTO @table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config';

SELECT COUNT(*) INTO @visible_column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config'
  AND COLUMN_NAME = 'visible';

SELECT COUNT(*),
       MAX(DATA_TYPE),
       MAX(IS_NULLABLE),
       MAX(CAST(COLUMN_DEFAULT AS CHAR))
INTO @deleted_column_count,
     @deleted_data_type,
     @deleted_is_nullable,
     @deleted_default
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config'
  AND COLUMN_NAME = 'is_deleted';

SET @deleted_column_compatible =
    @deleted_column_count = 1
    AND @deleted_data_type = 'tinyint'
    AND @deleted_is_nullable = 'NO'
    AND @deleted_default = '0';

SET @preflight_sql = IF(
    @table_exists = 1
        AND @visible_column_exists = 1
        AND (@deleted_column_count = 0 OR @deleted_column_compatible),
    'SELECT 1 AS ai_model_soft_delete_preflight_ready',
    'SELECT * FROM __ai_model_soft_delete_preflight_failed__'
);
PREPARE ai_model_soft_delete_preflight_stmt FROM @preflight_sql;
EXECUTE ai_model_soft_delete_preflight_stmt;
DEALLOCATE PREPARE ai_model_soft_delete_preflight_stmt;

SET @add_column_sql = IF(
    @deleted_column_count = 1,
    'SELECT 0 AS ai_model_soft_delete_column_created',
    'ALTER TABLE t_ai_model_config ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否已从模型管理中移除'' AFTER visible, ALGORITHM=INSTANT'
);
PREPARE ai_model_soft_delete_add_column_stmt FROM @add_column_sql;
EXECUTE ai_model_soft_delete_add_column_stmt;
DEALLOCATE PREPARE ai_model_soft_delete_add_column_stmt;

SELECT COUNT(*),
       MAX(DATA_TYPE),
       MAX(IS_NULLABLE),
       MAX(CAST(COLUMN_DEFAULT AS CHAR))
INTO @deleted_column_count_after,
     @deleted_data_type_after,
     @deleted_is_nullable_after,
     @deleted_default_after
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config'
  AND COLUMN_NAME = 'is_deleted';

SELECT COUNT(*) INTO @deleted_null_count
FROM t_ai_model_config
WHERE is_deleted IS NULL;

SET @verify_sql = IF(
    @deleted_column_count_after = 1
        AND @deleted_data_type_after = 'tinyint'
        AND @deleted_is_nullable_after = 'NO'
        AND @deleted_default_after = '0'
        AND @deleted_null_count = 0,
    'SELECT 1 AS ai_model_soft_delete_ready',
    'SELECT * FROM __ai_model_soft_delete_verification_failed__'
);
PREPARE ai_model_soft_delete_verify_stmt FROM @verify_sql;
EXECUTE ai_model_soft_delete_verify_stmt;
DEALLOCATE PREPARE ai_model_soft_delete_verify_stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config'
  AND COLUMN_NAME = 'is_deleted';
