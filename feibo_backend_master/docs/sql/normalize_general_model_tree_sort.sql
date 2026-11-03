-- 智笔医言通用模型树排序归一化（MySQL 5.7/8.0，幂等，无 DDL）。
--
-- 作用域仅包括业务根 1970339278848659457 的：
--   1. 直属非叶子供应商；
--   2. 上述每个供应商的直属叶子模型版本。
--
-- 稳定顺序：sort 非 NULL 在前且升序，NULL 最后；相同时依次按
-- ai_model_config_id、物理 id 升序兜底。归一化后供应商为 1..P，
-- 每个供应商的版本分别为 1..N。第二次执行不会更新任何行。
--
-- 执行要求：应用停止写入；使用未开启 --force 的 mysql 客户端。
-- 任一提交前校验失败时，脚本会引用不存在的表使执行中止；连接关闭
-- 后事务回滚。脚本不创建表、索引、存储过程或临时表。

SET @general_model_root_business_id = 1970339278848659457;

START TRANSACTION;

-- 与管理端写排序使用相同的根锁；部署期间仍应停止应用写入。
SELECT root.id AS locked_general_model_root_id
FROM t_ai_model_config AS root
WHERE root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id
FOR UPDATE;

SELECT COUNT(*) INTO @general_model_root_count
FROM t_ai_model_config AS root
WHERE root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id;

-- 迁移前盘点。重复、NULL、空洞均允许存在，后续会自动归一化。
SELECT provider.ai_model_config_id AS provider_id,
       provider.sort AS provider_sort,
       COUNT(leaf.id) AS version_count,
       SUM(CASE WHEN leaf.id IS NOT NULL AND leaf.sort IS NULL THEN 1 ELSE 0 END)
           AS null_version_sort_count,
       COUNT(leaf.sort) - COUNT(DISTINCT leaf.sort)
           AS duplicate_version_sort_count
FROM t_ai_model_config AS provider
JOIN t_ai_model_config AS root
  ON root.id = provider.parent_id
LEFT JOIN t_ai_model_config AS leaf
  ON leaf.parent_id = provider.id
 AND leaf.is_leaf = TRUE
WHERE provider.is_leaf = FALSE
  AND root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id
GROUP BY provider.id, provider.ai_model_config_id, provider.sort
ORDER BY (provider.sort IS NULL), provider.sort,
         (provider.ai_model_config_id IS NULL), provider.ai_model_config_id,
         provider.id;

-- 保存作用域外 sort 指纹。两个 UPDATE 的外层 JOIN 也会再次校验作用域；
-- 指纹是额外的 fail-closed 防线，用于证明没有作用域外排序被改动。
SELECT COUNT(*),
       COALESCE(SUM(CAST(CRC32(CONCAT(
           CAST(outside_row.id AS CHAR), '#',
           IF(outside_row.sort IS NULL, 'NULL', CAST(outside_row.sort AS CHAR))
       )) AS UNSIGNED)), 0),
       COALESCE(BIT_XOR(CAST(CRC32(CONCAT(
           CAST(outside_row.id AS CHAR), '#',
           IF(outside_row.sort IS NULL, 'NULL', CAST(outside_row.sort AS CHAR))
       )) AS UNSIGNED)), 0)
INTO @outside_sort_count_before,
     @outside_sort_sum_before,
     @outside_sort_xor_before
FROM t_ai_model_config AS outside_row
WHERE NOT (
    (
        outside_row.is_leaf = FALSE
        AND EXISTS (
            SELECT 1
            FROM t_ai_model_config AS provider_root
            WHERE provider_root.id = outside_row.parent_id
              AND provider_root.parent_id = 0
              AND provider_root.ai_model_config_id =
                  @general_model_root_business_id
        )
    )
    OR
    (
        outside_row.is_leaf = TRUE
        AND EXISTS (
            SELECT 1
            FROM t_ai_model_config AS leaf_provider
            JOIN t_ai_model_config AS leaf_root
              ON leaf_root.id = leaf_provider.parent_id
            WHERE leaf_provider.id = outside_row.parent_id
              AND leaf_provider.is_leaf = FALSE
              AND leaf_root.parent_id = 0
              AND leaf_root.ai_model_config_id =
                  @general_model_root_business_id
        )
    )
);

-- 供应商：用“排在当前行之前的兄弟数 + 1”计算稳定名次。
-- GROUP BY 使派生表必须物化，避免 MySQL 5.7/8.0 的 1093 自更新限制。
UPDATE t_ai_model_config AS target_provider
JOIN (
    SELECT materialized.id,
           MAX(materialized.normalized_sort) AS normalized_sort
    FROM (
        SELECT candidate.id,
               1 + (
                   SELECT COUNT(*)
                   FROM t_ai_model_config AS predecessor
                   WHERE predecessor.parent_id = candidate.parent_id
                     AND predecessor.is_leaf = FALSE
                     AND ROW(
                         predecessor.sort IS NULL,
                         COALESCE(predecessor.sort, 0),
                         predecessor.ai_model_config_id IS NULL,
                         COALESCE(predecessor.ai_model_config_id, 0),
                         predecessor.id
                     ) < ROW(
                         candidate.sort IS NULL,
                         COALESCE(candidate.sort, 0),
                         candidate.ai_model_config_id IS NULL,
                         COALESCE(candidate.ai_model_config_id, 0),
                         candidate.id
                     )
               ) AS normalized_sort
        FROM t_ai_model_config AS candidate
        JOIN t_ai_model_config AS root
          ON root.id = candidate.parent_id
        WHERE candidate.is_leaf = FALSE
          AND root.parent_id = 0
          AND root.ai_model_config_id = @general_model_root_business_id
    ) AS materialized
    GROUP BY materialized.id
) AS ranked_provider
  ON ranked_provider.id = target_provider.id
JOIN t_ai_model_config AS target_root
  ON target_root.id = target_provider.parent_id
SET target_provider.sort = ranked_provider.normalized_sort,
    target_provider.update_time = target_provider.update_time
WHERE target_provider.is_leaf = FALSE
  AND target_root.parent_id = 0
  AND target_root.ai_model_config_id = @general_model_root_business_id
  AND NOT (target_provider.sort <=> ranked_provider.normalized_sort);

SET @provider_rows_normalized = ROW_COUNT();
SELECT @provider_rows_normalized AS provider_rows_normalized;

-- 版本：在各自供应商内独立计算稳定名次并归一为 1..N。
UPDATE t_ai_model_config AS target_leaf
JOIN (
    SELECT materialized.id,
           MAX(materialized.normalized_sort) AS normalized_sort
    FROM (
        SELECT candidate.id,
               1 + (
                   SELECT COUNT(*)
                   FROM t_ai_model_config AS predecessor
                   WHERE predecessor.parent_id = candidate.parent_id
                     AND predecessor.is_leaf = TRUE
                     AND ROW(
                         predecessor.sort IS NULL,
                         COALESCE(predecessor.sort, 0),
                         predecessor.ai_model_config_id IS NULL,
                         COALESCE(predecessor.ai_model_config_id, 0),
                         predecessor.id
                     ) < ROW(
                         candidate.sort IS NULL,
                         COALESCE(candidate.sort, 0),
                         candidate.ai_model_config_id IS NULL,
                         COALESCE(candidate.ai_model_config_id, 0),
                         candidate.id
                     )
               ) AS normalized_sort
        FROM t_ai_model_config AS candidate
        JOIN t_ai_model_config AS provider
          ON provider.id = candidate.parent_id
        JOIN t_ai_model_config AS root
          ON root.id = provider.parent_id
        WHERE candidate.is_leaf = TRUE
          AND provider.is_leaf = FALSE
          AND root.parent_id = 0
          AND root.ai_model_config_id = @general_model_root_business_id
    ) AS materialized
    GROUP BY materialized.id
) AS ranked_leaf
  ON ranked_leaf.id = target_leaf.id
JOIN t_ai_model_config AS target_provider
  ON target_provider.id = target_leaf.parent_id
JOIN t_ai_model_config AS target_root
  ON target_root.id = target_provider.parent_id
SET target_leaf.sort = ranked_leaf.normalized_sort,
    target_leaf.update_time = target_leaf.update_time
WHERE target_leaf.is_leaf = TRUE
  AND target_provider.is_leaf = FALSE
  AND target_root.parent_id = 0
  AND target_root.ai_model_config_id = @general_model_root_business_id
  AND NOT (target_leaf.sort <=> ranked_leaf.normalized_sort);

SET @leaf_rows_normalized = ROW_COUNT();
SELECT @leaf_rows_normalized AS leaf_rows_normalized;

-- 提交前校验供应商必须恰好为 1..P；空供应商集合也视为有效。
SELECT COUNT(*),
       SUM(provider.sort IS NULL),
       MIN(provider.sort),
       MAX(provider.sort),
       COUNT(DISTINCT provider.sort)
INTO @provider_count,
     @provider_null_sort_count,
     @provider_min_sort,
     @provider_max_sort,
     @provider_distinct_sort_count
FROM t_ai_model_config AS provider
JOIN t_ai_model_config AS root
  ON root.id = provider.parent_id
WHERE provider.is_leaf = FALSE
  AND root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id;

SET @provider_sort_invalid = IF(
    @provider_count = 0,
    0,
    NOT (
        @provider_null_sort_count = 0
        AND @provider_min_sort = 1
        AND @provider_max_sort = @provider_count
        AND @provider_distinct_sort_count = @provider_count
    )
);

-- 每个供应商的版本必须分别恰好为 1..N；无版本供应商不算失败。
SELECT COUNT(*) INTO @invalid_leaf_provider_count
FROM (
    SELECT provider.id
    FROM t_ai_model_config AS provider
    JOIN t_ai_model_config AS root
      ON root.id = provider.parent_id
    LEFT JOIN t_ai_model_config AS leaf
      ON leaf.parent_id = provider.id
     AND leaf.is_leaf = TRUE
    WHERE provider.is_leaf = FALSE
      AND root.parent_id = 0
      AND root.ai_model_config_id = @general_model_root_business_id
    GROUP BY provider.id
    HAVING COUNT(leaf.id) > 0
       AND (
           SUM(CASE WHEN leaf.sort IS NULL THEN 1 ELSE 0 END) <> 0
           OR MIN(leaf.sort) <> 1
           OR MAX(leaf.sort) <> COUNT(leaf.id)
           OR COUNT(DISTINCT leaf.sort) <> COUNT(leaf.id)
       )
) AS invalid_leaf_provider;

-- 重新计算作用域外 sort 指纹，必须与迁移前完全一致。
SELECT COUNT(*),
       COALESCE(SUM(CAST(CRC32(CONCAT(
           CAST(outside_row.id AS CHAR), '#',
           IF(outside_row.sort IS NULL, 'NULL', CAST(outside_row.sort AS CHAR))
       )) AS UNSIGNED)), 0),
       COALESCE(BIT_XOR(CAST(CRC32(CONCAT(
           CAST(outside_row.id AS CHAR), '#',
           IF(outside_row.sort IS NULL, 'NULL', CAST(outside_row.sort AS CHAR))
       )) AS UNSIGNED)), 0)
INTO @outside_sort_count_after,
     @outside_sort_sum_after,
     @outside_sort_xor_after
FROM t_ai_model_config AS outside_row
WHERE NOT (
    (
        outside_row.is_leaf = FALSE
        AND EXISTS (
            SELECT 1
            FROM t_ai_model_config AS provider_root
            WHERE provider_root.id = outside_row.parent_id
              AND provider_root.parent_id = 0
              AND provider_root.ai_model_config_id =
                  @general_model_root_business_id
        )
    )
    OR
    (
        outside_row.is_leaf = TRUE
        AND EXISTS (
            SELECT 1
            FROM t_ai_model_config AS leaf_provider
            JOIN t_ai_model_config AS leaf_root
              ON leaf_root.id = leaf_provider.parent_id
            WHERE leaf_provider.id = outside_row.parent_id
              AND leaf_provider.is_leaf = FALSE
              AND leaf_root.parent_id = 0
              AND leaf_root.ai_model_config_id =
                  @general_model_root_business_id
        )
    )
);

SET @outside_sort_changed = NOT (
    @outside_sort_count_before <=> @outside_sort_count_after
    AND @outside_sort_sum_before <=> @outside_sort_sum_after
    AND @outside_sort_xor_before <=> @outside_sort_xor_after
);

SELECT @general_model_root_count AS general_model_root_count,
       @provider_count AS provider_count,
       @provider_sort_invalid AS provider_sort_invalid,
       @invalid_leaf_provider_count AS invalid_leaf_provider_count,
       @outside_sort_changed AS outside_sort_changed;

-- 无需 CREATE ROUTINE/SIGNAL 权限的 fail-closed 校验。正常路径只执行 SELECT 1；
-- 失败路径引用不存在的表，未使用 --force 的 mysql 客户端会在 COMMIT 前退出。
SET @verify_sql = IF(
    @general_model_root_count = 1
    AND @provider_sort_invalid = 0
    AND @invalid_leaf_provider_count = 0
    AND @outside_sort_changed = 0,
    'SELECT 1 AS general_model_tree_sort_verified',
    'SELECT * FROM __general_model_tree_sort_normalization_failed__'
);
PREPARE ordering_verify_stmt FROM @verify_sql;
EXECUTE ordering_verify_stmt;
DEALLOCATE PREPARE ordering_verify_stmt;

COMMIT;

-- 最终明细：人工复核时供应商排序应连续为 1..P，各供应商版本应为 1..N。
SELECT provider.sort AS provider_sort,
       provider.ai_model_config_id AS provider_id,
       leaf.sort AS version_sort,
       leaf.ai_model_config_id AS model_id,
       leaf.model_code
FROM t_ai_model_config AS provider
JOIN t_ai_model_config AS root
  ON root.id = provider.parent_id
LEFT JOIN t_ai_model_config AS leaf
  ON leaf.parent_id = provider.id
 AND leaf.is_leaf = TRUE
WHERE provider.is_leaf = FALSE
  AND root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id
ORDER BY provider.sort, provider.ai_model_config_id, provider.id,
         leaf.sort, leaf.ai_model_config_id, leaf.id;

-- 幂等验收：立即原样再执行本文件，provider_rows_normalized 与
-- leaf_rows_normalized 必须都为 0，其余校验仍须全部为 0/verified。
