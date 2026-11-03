-- 智笔医言通用模型版本排序回填（MySQL 5.7/8.0，幂等，无 DDL）。
-- 前提：模型管理 schema 已上线；应用应在执行期间保持停止状态。

SET @general_model_root_business_id = 1970339278848659457;

-- Pre-migration inventory. First production execution is expected to report
-- 17 total NULL rows; an idempotent re-run reports zero.
SELECT provider.ai_model_config_id AS provider_id,
       COUNT(*) AS legacy_null_sort_count
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND leaf.sort IS NULL
  AND general_root.ai_model_config_id = @general_model_root_business_id
GROUP BY provider.ai_model_config_id
ORDER BY provider.ai_model_config_id;

SELECT COUNT(*) AS general_leaf_null_sort_before
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND leaf.sort IS NULL
  AND general_root.ai_model_config_id = @general_model_root_business_id;

START TRANSACTION;

-- Preserve every explicit order. NULL versions are appended after their real
-- provider's non-negative current maximum, in business-ID/physical-ID order,
-- using gaps of 10. Values saturate at the public/admin contract maximum 10000;
-- the stable ID tie-break still makes saturated siblings deterministic. The
-- double-derived table avoids MySQL error 1093 on 5.7 and 8.0.
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
          AND general_root.ai_model_config_id = @general_model_root_business_id
    ) AS materialized
) AS ranked
  ON ranked.id = leaf.id
SET leaf.sort = ranked.stable_sort,
    leaf.update_time = leaf.update_time
WHERE leaf.sort IS NULL;

SELECT ROW_COUNT() AS general_leaf_rows_backfilled;

SET @general_leaf_null_sort_after = (
    SELECT COUNT(*)
    FROM t_ai_model_config AS leaf
    JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
    JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
    WHERE leaf.is_leaf = TRUE
      AND leaf.sort IS NULL
      AND general_root.ai_model_config_id = @general_model_root_business_id
);
SELECT @general_leaf_null_sort_after AS general_leaf_null_sort_after;

-- Fail closed without requiring CREATE ROUTINE privileges. A failure closes
-- the mysql client connection before COMMIT, causing the transaction to roll
-- back. This statement is never prepared on a successful or idempotent run.
SET @verify_sql = IF(
    @general_leaf_null_sort_after = 0,
    'SELECT 1 AS ordering_backfill_verified',
    'SELECT * FROM __ai_model_ordering_backfill_failed__'
);
PREPARE ordering_verify_stmt FROM @verify_sql;
EXECUTE ordering_verify_stmt;
DEALLOCATE PREPARE ordering_verify_stmt;

COMMIT;

SELECT provider.ai_model_config_id AS provider_id,
       leaf.ai_model_config_id AS model_id,
       leaf.sort AS model_order
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS general_root ON general_root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND general_root.ai_model_config_id = @general_model_root_business_id
ORDER BY (provider.sort IS NULL), provider.sort, provider.ai_model_config_id,
         (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id;
