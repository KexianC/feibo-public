-- 智笔医言模型管理字段（幂等迁移，兼容 MySQL 5.7/8.0）。
--
-- 上线前必须配置：
--   AI_MODEL_KEY_ENCRYPTION_SECRET=<Base64 编码的 32 字节随机值>
-- 可用命令生成：openssl rand -base64 32
--
-- api_key_ciphertext 仅保存 AES-256-GCM 密文；既有环境变量 API Key
-- 不迁移入库，运行时在该列为空时继续作为 fallback。

SET @schema_name = DATABASE();

-- Preflight: this value MUST be 0. A null business ID cannot be routed and a
-- UNIQUE index alone permits NULL values.
SELECT COUNT(*) AS null_business_id_count
FROM t_ai_model_config
WHERE ai_model_config_id IS NULL;

-- Enforce the production business-ID type before adding feature columns.
-- Existing NULL values make this ALTER fail automatically, so the migration
-- cannot continue with unroutable/AAD-invalid rows.
ALTER TABLE t_ai_model_config
    MODIFY COLUMN ai_model_config_id BIGINT UNSIGNED NOT NULL
    COMMENT 'AI模型配置业务ID';

-- Runtime routing and AES-GCM AAD assume exactly one row per business ID.
-- Preserve an existing exact single-column unique key. Otherwise create one
-- before any feature-column changes; duplicate IDs make CREATE UNIQUE INDEX
-- fail and therefore abort the feature migration.
SET @has_business_id_unique = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = @schema_name
          AND TABLE_NAME = 't_ai_model_config'
        GROUP BY INDEX_NAME
        HAVING MAX(NON_UNIQUE) = 0
           AND COUNT(*) = 1
           AND MAX(CASE
                   WHEN COLUMN_NAME = 'ai_model_config_id'
                    AND SUB_PART IS NULL THEN 1 ELSE 0 END) = 1
    ) exact_business_id_unique
);
SET @ddl = IF(
    @has_business_id_unique = 0,
    'CREATE UNIQUE INDEX uq_ai_model_config_business_id ON t_ai_model_config (ai_model_config_id)',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 't_ai_model_config'
       AND COLUMN_NAME = 'display_name') = 0,
    'ALTER TABLE t_ai_model_config ADD COLUMN display_name VARCHAR(128) NULL COMMENT ''前台展示名称'' AFTER name',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 't_ai_model_config'
       AND COLUMN_NAME = 'display_description') = 0,
    'ALTER TABLE t_ai_model_config ADD COLUMN display_description VARCHAR(1000) NULL COMMENT ''前台展示简介（不得作为系统提示词）'' AFTER description',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 't_ai_model_config'
       AND COLUMN_NAME = 'visible') = 0,
    'ALTER TABLE t_ai_model_config ADD COLUMN visible TINYINT(1) NULL DEFAULT NULL COMMENT ''是否在智笔医言前台显示'' AFTER sort',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 't_ai_model_config'
       AND COLUMN_NAME = 'api_key_ciphertext') = 0,
    'ALTER TABLE t_ai_model_config ADD COLUMN api_key_ciphertext TEXT NULL COMMENT ''AES-GCM 加密的模型 API Key'' AFTER visible',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

-- Preserve the pre-migration labels and model list.
UPDATE t_ai_model_config SET display_name = name WHERE display_name IS NULL;
UPDATE t_ai_model_config SET visible = 1 WHERE visible IS NULL;

-- Pre-backfill inventory. Only these rows may be changed by the next statement.
SELECT provider.ai_model_config_id AS provider_id,
       COUNT(*) AS legacy_null_sort_count
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND leaf.sort IS NULL
  AND general_root.ai_model_config_id = 1970339278848659457
GROUP BY provider.ai_model_config_id
ORDER BY provider.ai_model_config_id;

-- Backfill only legacy leaves below the general-model root. Existing explicit
-- orders remain untouched; each remaining NULL starts after the non-negative
-- provider maximum in deterministic business-ID/physical-ID order, with gaps
-- of 10. Values saturate at 10000 and stable IDs break saturated ties. The
-- outer derived table prevents MySQL error 1093 while retaining MySQL 5.7
-- compatibility. Re-running this statement is a no-op because only NULL
-- leaf.sort values are eligible.
UPDATE t_ai_model_config AS leaf
JOIN (
    SELECT materialized.id, materialized.stable_sort
    FROM (
        SELECT candidate.id,
               LEAST(10000,
                   GREATEST(COALESCE((
                       SELECT MAX(explicit_order.sort)
                       FROM t_ai_model_config AS explicit_order
                       WHERE explicit_order.parent_id = candidate.parent_id
                         AND explicit_order.is_leaf = TRUE
                         AND explicit_order.sort IS NOT NULL
                   ), 0), 0) + 10 * (
                       SELECT COUNT(*)
                       FROM t_ai_model_config AS legacy_sibling
                       WHERE legacy_sibling.parent_id = candidate.parent_id
                         AND legacy_sibling.is_leaf = TRUE
                         AND legacy_sibling.sort IS NULL
                         AND (
                             legacy_sibling.ai_model_config_id < candidate.ai_model_config_id
                             OR (
                                 legacy_sibling.ai_model_config_id = candidate.ai_model_config_id
                                 AND legacy_sibling.id <= candidate.id
                             )
                         )
                   )
               ) AS stable_sort
        FROM t_ai_model_config AS candidate
        JOIN t_ai_model_config AS provider
          ON provider.id = candidate.parent_id
        JOIN t_ai_model_config AS general_root
          ON general_root.id = provider.parent_id
        WHERE candidate.is_leaf = TRUE
          AND candidate.sort IS NULL
          AND general_root.ai_model_config_id = 1970339278848659457
    ) AS materialized
) AS ranked
  ON ranked.id = leaf.id
SET leaf.sort = ranked.stable_sort,
    leaf.update_time = leaf.update_time
WHERE leaf.sort IS NULL;

-- New rows created by old scripts fail closed unless they explicitly opt in.
-- This ALTER also converges a partially applied migration that retained
-- DEFAULT 1 or a nullable column.
ALTER TABLE t_ai_model_config
    MODIFY COLUMN visible TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否在智笔医言前台显示';

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = @schema_name
       AND TABLE_NAME = 't_ai_model_config'
       AND INDEX_NAME = 'idx_ai_model_visible_parent_sort') = 0,
    'CREATE INDEX idx_ai_model_visible_parent_sort ON t_ai_model_config (visible, parent_id, sort)',
    'SELECT 1'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @schema_name
  AND TABLE_NAME = 't_ai_model_config'
  AND COLUMN_NAME IN (
      'display_name', 'display_description', 'visible', 'api_key_ciphertext')
ORDER BY ORDINAL_POSITION;

-- Verification: the following result sets MUST be empty/zero. Duplicate IDs
-- have already caused CREATE UNIQUE INDEX to fail before any new columns.
SELECT ai_model_config_id, COUNT(*) AS duplicate_count
FROM t_ai_model_config
GROUP BY ai_model_config_id
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS null_business_id_count
FROM t_ai_model_config
WHERE ai_model_config_id IS NULL;

-- Verification: must be zero after the scoped legacy-order backfill.
SELECT COUNT(*) AS general_leaf_null_sort_count
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND leaf.sort IS NULL
  AND general_root.ai_model_config_id = 1970339278848659457;
