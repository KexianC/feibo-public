-- Add the lookup index used by model-scoped history lookups and integrity audits.
-- Safe to rerun on MySQL 8.x. This migration changes no message data and does
-- not add a foreign key because legacy orphan model IDs must be handled in a
-- separate, explicitly approved data-quality migration.

SET @schema_name = DATABASE();

SELECT COUNT(*) INTO @table_exists
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message';

SELECT COUNT(*) INTO @column_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message'
  AND COLUMN_NAME = 'ai_model_config_id';

SELECT COUNT(DISTINCT INDEX_NAME) INTO @compatible_index_count
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message'
  AND COLUMN_NAME = 'ai_model_config_id'
  AND SEQ_IN_INDEX = 1;

SELECT COUNT(*) INTO @reserved_name_count
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message'
  AND INDEX_NAME = 'idx_ai_message_model_config_id';

SET @preflight_sql = IF(
    @table_exists = 1
        AND @column_exists = 1
        AND NOT (@compatible_index_count = 0 AND @reserved_name_count > 0),
    'SELECT 1',
    'SELECT * FROM __ai_message_model_index_preflight_failed__'
);
PREPARE ai_message_model_index_preflight_stmt FROM @preflight_sql;
EXECUTE ai_message_model_index_preflight_stmt;
DEALLOCATE PREPARE ai_message_model_index_preflight_stmt;

SET @index_sql = IF(
    @compatible_index_count > 0,
    'SELECT 0 AS ai_message_model_index_created',
    'ALTER TABLE t_ai_message ADD INDEX idx_ai_message_model_config_id (ai_model_config_id), ALGORITHM=INPLACE, LOCK=NONE'
);
PREPARE ai_message_model_index_stmt FROM @index_sql;
EXECUTE ai_message_model_index_stmt;
DEALLOCATE PREPARE ai_message_model_index_stmt;

SELECT COUNT(DISTINCT INDEX_NAME) INTO @compatible_index_after
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message'
  AND COLUMN_NAME = 'ai_model_config_id'
  AND SEQ_IN_INDEX = 1;

SET @verify_sql = IF(
    @compatible_index_after > 0,
    'SELECT 1 AS ai_message_model_index_ready',
    'SELECT * FROM __ai_message_model_index_verification_failed__'
);
PREPARE ai_message_model_index_verify_stmt FROM @verify_sql;
EXECUTE ai_message_model_index_verify_stmt;
DEALLOCATE PREPARE ai_message_model_index_verify_stmt;

SELECT INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_message'
  AND COLUMN_NAME = 'ai_model_config_id'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
