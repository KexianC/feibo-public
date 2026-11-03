-- RBAC user-role constraint migration: read-only preflight.
--
-- This script never changes persistent schema or application data. It uses
-- session-scoped TEMPORARY TABLE gates so a schema/data blocker terminates the
-- client with an error after emitting the complete audit evidence.
--
-- Required client behavior:
--   * select the intended schema explicitly before sourcing this file;
--   * SET @rbac_expected_schema = 'approved_schema_name';
--   * do not use mysql --force (an error must stop the deployment pipeline).

SET @rbac_preflight_mysql_major =
    CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED);

SELECT COUNT(*)
  INTO @rbac_preflight_table_count
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('t_user', 't_role', 't_user_role_map');

SELECT COUNT(*)
  INTO @rbac_preflight_innodb_table_count
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('t_user', 't_role', 't_user_role_map')
   AND UPPER(engine) = 'INNODB';

SELECT COUNT(*)
  INTO @rbac_preflight_required_column_count
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND (
       (table_name = 't_user' AND column_name = 'user_id')
       OR (table_name = 't_role' AND column_name = 'role_id')
       OR (
           table_name = 't_user_role_map'
           AND column_name IN ('id', 'user_id', 'role_id', 'create_time')
       )
   );

SELECT LOWER(MAX(CASE
           WHEN table_name = 't_user' AND column_name = 'user_id'
               THEN column_type
       END)),
       LOWER(MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'user_id'
               THEN column_type
       END)),
       LOWER(MAX(CASE
           WHEN table_name = 't_role' AND column_name = 'role_id'
               THEN column_type
       END)),
       LOWER(MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'role_id'
               THEN column_type
       END))
  INTO @rbac_preflight_user_column_type,
       @rbac_preflight_map_user_column_type,
       @rbac_preflight_role_column_type,
       @rbac_preflight_map_role_column_type
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name IN ('t_user', 't_role', 't_user_role_map');

SELECT MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'user_id'
               THEN is_nullable
       END),
       MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'role_id'
               THEN is_nullable
       END),
       MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'user_id'
               THEN column_default
       END),
       MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'role_id'
               THEN column_default
       END),
       MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'user_id'
               THEN extra
       END),
       MAX(CASE
           WHEN table_name = 't_user_role_map' AND column_name = 'role_id'
               THEN extra
       END)
  INTO @rbac_preflight_map_user_nullable,
       @rbac_preflight_map_role_nullable,
       @rbac_preflight_map_user_default,
       @rbac_preflight_map_role_default,
       @rbac_preflight_map_user_extra,
       @rbac_preflight_map_role_extra
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name = 't_user_role_map'
   AND column_name IN ('user_id', 'role_id');

SELECT COUNT(*)
  INTO @rbac_preflight_user_unique_key_count
  FROM (
      SELECT index_name
        FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name = 't_user'
       GROUP BY index_name, non_unique
      HAVING MIN(non_unique) = 0
         AND COUNT(*) = 1
         AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
  ) AS user_unique_keys;

SELECT COUNT(*)
  INTO @rbac_preflight_role_unique_key_count
  FROM (
      SELECT index_name
        FROM information_schema.statistics
       WHERE table_schema = DATABASE()
         AND table_name = 't_role'
       GROUP BY index_name, non_unique
      HAVING MIN(non_unique) = 0
         AND COUNT(*) = 1
         AND SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
  ) AS role_unique_keys;

SET @rbac_preflight_schema_ready = (
    DATABASE() IS NOT NULL
    AND DATABASE() <> ''
    AND @rbac_expected_schema IS NOT NULL
    AND CAST(DATABASE() AS BINARY) =
        CAST(TRIM(CAST(@rbac_expected_schema AS CHAR)) AS BINARY)
    AND VERSION() NOT LIKE '%MariaDB%'
    AND @rbac_preflight_mysql_major >= 8
    AND @rbac_preflight_table_count = 3
    AND @rbac_preflight_innodb_table_count = 3
    AND @rbac_preflight_required_column_count = 6
    AND @rbac_preflight_user_column_type IS NOT NULL
    AND @rbac_preflight_map_user_column_type IS NOT NULL
    AND @rbac_preflight_role_column_type IS NOT NULL
    AND @rbac_preflight_map_role_column_type IS NOT NULL
    AND @rbac_preflight_user_column_type = @rbac_preflight_map_user_column_type
    AND @rbac_preflight_role_column_type = @rbac_preflight_map_role_column_type
    AND @rbac_preflight_user_unique_key_count > 0
    AND @rbac_preflight_role_unique_key_count > 0
);

SELECT
    DATABASE() AS target_schema,
    @rbac_expected_schema AS approved_schema,
    VERSION() AS mysql_version,
    @rbac_preflight_table_count AS required_tables_found,
    @rbac_preflight_innodb_table_count AS required_innodb_tables,
    @rbac_preflight_required_column_count AS required_columns_found,
    @rbac_preflight_user_column_type AS parent_user_id_type,
    @rbac_preflight_map_user_column_type AS mapping_user_id_type,
    @rbac_preflight_role_column_type AS parent_role_id_type,
    @rbac_preflight_map_role_column_type AS mapping_role_id_type,
    @rbac_preflight_map_user_nullable AS mapping_user_id_nullable,
    @rbac_preflight_map_role_nullable AS mapping_role_id_nullable,
    @rbac_preflight_map_user_default AS mapping_user_id_default,
    @rbac_preflight_map_role_default AS mapping_role_id_default,
    @rbac_preflight_map_user_extra AS mapping_user_id_extra,
    @rbac_preflight_map_role_extra AS mapping_role_id_extra,
    @rbac_preflight_user_unique_key_count AS parent_user_unique_keys,
    @rbac_preflight_role_unique_key_count AS parent_role_unique_keys,
    @rbac_preflight_schema_ready AS schema_ready;

-- A NULL insert into this session-only NOT NULL gate is the fail-closed
-- equivalent of an anonymous assertion (which MySQL does not provide).
DROP TEMPORARY TABLE IF EXISTS `_rbac_preflight_schema_gate`;
CREATE TEMPORARY TABLE `_rbac_preflight_schema_gate` (
    `ready_for_rbac_data_audit` TINYINT NOT NULL
) ENGINE=MEMORY;
INSERT INTO `_rbac_preflight_schema_gate` (`ready_for_rbac_data_audit`)
VALUES (IF(@rbac_preflight_schema_ready, 1, NULL));
DROP TEMPORARY TABLE `_rbac_preflight_schema_gate`;

SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION READ ONLY;

SELECT COUNT(*)
  INTO @rbac_preflight_null_source_id_count
  FROM t_user_role_map
 WHERE id IS NULL;

SELECT COUNT(*)
  INTO @rbac_preflight_null_mapping_key_count
  FROM t_user_role_map
 WHERE user_id IS NULL OR role_id IS NULL;

SELECT COUNT(*)
  INTO @rbac_preflight_duplicate_source_id_count
  FROM (
      SELECT id
        FROM t_user_role_map
       WHERE id IS NOT NULL
       GROUP BY id
      HAVING COUNT(*) > 1
  ) AS duplicate_source_ids;

SELECT COUNT(*)
  INTO @rbac_preflight_duplicate_group_count
  FROM (
      SELECT user_id, role_id
        FROM t_user_role_map
       GROUP BY user_id, role_id
      HAVING COUNT(*) > 1
  ) AS duplicate_mappings;

SELECT COUNT(*)
  INTO @rbac_preflight_orphan_user_count
  FROM t_user_role_map AS role_map
  LEFT JOIN t_user AS user_record
    ON user_record.user_id = role_map.user_id
 WHERE user_record.user_id IS NULL;

SELECT COUNT(*)
  INTO @rbac_preflight_orphan_role_count
  FROM t_user_role_map AS role_map
  LEFT JOIN t_role AS role_record
    ON role_record.role_id = role_map.role_id
 WHERE role_record.role_id IS NULL;

SELECT COUNT(*)
  INTO @rbac_preflight_orphan_distinct_count
  FROM t_user_role_map AS role_map
  LEFT JOIN t_user AS user_record
    ON user_record.user_id = role_map.user_id
  LEFT JOIN t_role AS role_record
    ON role_record.role_id = role_map.role_id
 WHERE user_record.user_id IS NULL
    OR role_record.role_id IS NULL;

SELECT
    DATABASE() AS target_schema,
    CURRENT_USER() AS executed_by,
    NOW(6) AS observed_at,
    (SELECT COUNT(*) FROM t_user_role_map) AS mapping_rows,
    @rbac_preflight_duplicate_group_count AS duplicate_mapping_groups,
    @rbac_preflight_orphan_user_count AS orphan_user_rows,
    @rbac_preflight_orphan_role_count AS orphan_role_rows,
    @rbac_preflight_orphan_distinct_count AS orphan_distinct_rows,
    @rbac_preflight_null_mapping_key_count AS null_mapping_key_rows,
    @rbac_preflight_null_source_id_count AS null_source_id_rows,
    @rbac_preflight_duplicate_source_id_count AS duplicate_source_id_groups;

SELECT
    role_map.id,
    role_map.user_id,
    role_map.role_id,
    role_map.create_time,
    CASE
        WHEN user_record.user_id IS NULL AND role_record.role_id IS NULL
            THEN 'ORPHAN_USER_AND_ROLE'
        WHEN user_record.user_id IS NULL THEN 'ORPHAN_USER'
        ELSE 'ORPHAN_ROLE'
    END AS blocker_reason,
    SHA2(CONCAT(
        'id=', CAST(role_map.id AS CHAR), CHAR(31),
        'user_id=', COALESCE(CAST(role_map.user_id AS CHAR), '<NULL>'), CHAR(31),
        'role_id=', COALESCE(CAST(role_map.role_id AS CHAR), '<NULL>'), CHAR(31),
        'create_time=', COALESCE(
            DATE_FORMAT(role_map.create_time, '%Y-%m-%d %H:%i:%s.%f'),
            '<NULL>'
        )
    ), 256) AS source_row_sha256
  FROM t_user_role_map AS role_map
  LEFT JOIN t_user AS user_record
    ON user_record.user_id = role_map.user_id
  LEFT JOIN t_role AS role_record
    ON role_record.role_id = role_map.role_id
 WHERE user_record.user_id IS NULL
    OR role_record.role_id IS NULL
 ORDER BY role_map.id;

SELECT
    user_id,
    role_id,
    COUNT(*) AS duplicate_rows,
    GROUP_CONCAT(id ORDER BY id) AS source_ids
  FROM t_user_role_map
 GROUP BY user_id, role_id
HAVING COUNT(*) > 1
 ORDER BY user_id, role_id;

COMMIT;

SET @rbac_preflight_data_ready = (
    @rbac_preflight_null_source_id_count = 0
    AND @rbac_preflight_null_mapping_key_count = 0
    AND @rbac_preflight_duplicate_source_id_count = 0
    AND @rbac_preflight_duplicate_group_count = 0
    AND @rbac_preflight_orphan_distinct_count = 0
    AND @rbac_preflight_map_user_nullable = 'NO'
    AND @rbac_preflight_map_role_nullable = 'NO'
);

DROP TEMPORARY TABLE IF EXISTS `_rbac_preflight_data_gate`;
CREATE TEMPORARY TABLE `_rbac_preflight_data_gate` (
    `ready_for_constraint_migration` TINYINT NOT NULL
) ENGINE=MEMORY;
INSERT INTO `_rbac_preflight_data_gate` (`ready_for_constraint_migration`)
VALUES (IF(@rbac_preflight_data_ready, 1, NULL));
DROP TEMPORARY TABLE `_rbac_preflight_data_gate`;

SELECT 'READY_FOR_CONSTRAINT_MIGRATION' AS preflight_status;
