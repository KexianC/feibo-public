-- RBAC user-role constraint migration: explicitly approved quarantine/cleanup.
--
-- This script never guesses which rows may be removed. Before sourcing it, the
-- operator must set every session variable below from a fresh preflight result:
--
--   SET @rbac_migration_batch_id = UUID();
--   SET @rbac_expected_schema = 'approved_schema_name';
--   SET @rbac_change_ticket = 'CHANGE-OR-INCIDENT-ID';
--   SET @rbac_expected_duplicate_groups = 0;
--   SET @rbac_expected_orphan_user_rows = <fresh count>;
--   SET @rbac_expected_orphan_role_rows = <fresh count>;
--   SET @rbac_expected_orphan_distinct_rows = <fresh count>;
--   CREATE TEMPORARY TABLE `_rbac_approved_orphan_manifest` (...);
--   INSERT INTO `_rbac_approved_orphan_manifest`
--       (`source_id`, `source_row_sha256`) VALUES (...exact preflight rows...);
--
-- Do not use mysql --force. A count mismatch or any duplicate mapping raises
-- SQLSTATE 45000 and rolls back the quarantine/delete transaction.

-- This session-only gate runs before any persistent CREATE/DROP. A missing or
-- wrong approved schema therefore cannot leave migration objects behind in a
-- similarly shaped database selected by mistake.
DROP TEMPORARY TABLE IF EXISTS `_rbac_schema_write_gate`;
CREATE TEMPORARY TABLE `_rbac_schema_write_gate` (
    `approved` TINYINT NOT NULL
);
INSERT INTO `_rbac_schema_write_gate` (`approved`)
SELECT CASE
    WHEN DATABASE() IS NOT NULL
     AND DATABASE() <> ''
     AND @rbac_expected_schema IS NOT NULL
     AND CAST(DATABASE() AS BINARY) =
         CAST(TRIM(CAST(@rbac_expected_schema AS CHAR)) AS BINARY)
     AND VERSION() NOT LIKE '%MariaDB%'
     AND CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) >= 8
    THEN 1
    ELSE NULL
END;
DROP TEMPORARY TABLE `_rbac_schema_write_gate`;

CREATE TABLE IF NOT EXISTS `t_rbac_user_role_constraint_migration` (
    `migration_batch_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `change_ticket` VARCHAR(128) NOT NULL,
    `expected_duplicate_groups` BIGINT UNSIGNED NOT NULL,
    `expected_orphan_user_rows` BIGINT UNSIGNED NOT NULL,
    `expected_orphan_role_rows` BIGINT UNSIGNED NOT NULL,
    `expected_orphan_distinct_rows` BIGINT UNSIGNED NOT NULL,
    `approved_manifest_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `quarantined_rows` BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `migration_status` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `cleanup_completed_at` DATETIME(6) NULL,
    `cleanup_executed_by` VARCHAR(288) NULL,
    `constraints_applied_at` DATETIME(6) NULL,
    `constraints_applied_by` VARCHAR(288) NULL,
    `made_user_id_not_null` TINYINT NULL,
    `made_role_id_not_null` TINYINT NULL,
    `original_user_id_column_type` VARCHAR(255) NULL,
    `original_role_id_column_type` VARCHAR(255) NULL,
    `original_user_id_column_comment` TEXT NULL,
    `original_role_id_column_comment` TEXT NULL,
    `created_unique_constraint` TINYINT NULL,
    `created_role_lookup_index` TINYINT NULL,
    `created_user_foreign_key` TINYINT NULL,
    `created_role_foreign_key` TINYINT NULL,
    `rollback_ticket` VARCHAR(128) NULL,
    `rollback_completed_at` DATETIME(6) NULL,
    `rollback_executed_by` VARCHAR(288) NULL,
    PRIMARY KEY (`migration_batch_id`),
    KEY `idx_rbac_constraint_migration_ticket` (`change_ticket`),
    CONSTRAINT `chk_rbac_constraint_migration_status`
        CHECK (`migration_status` IN (
            'QUARANTINED',
            'NOT_NULL_HARDENING',
            'NOT_NULL_HARDENED',
            'CONSTRAINTS_APPLYING',
            'CONSTRAINTS_APPLIED',
            'ROLLBACK_DROPPING_CONSTRAINTS',
            'ROLLBACK_CONSTRAINTS_DROPPED',
            'ROLLBACK_FINALIZING_INDEXES',
            'ROLLED_BACK'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='RBAC user-role integrity migration ledger';

CREATE TABLE IF NOT EXISTS `t_user_role_map_quarantine` (
    `quarantine_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `migration_batch_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_id` BIGINT NOT NULL,
    `source_user_id` BIGINT NULL,
    `source_role_id` BIGINT NULL,
    `source_create_time` DATETIME(6) NULL,
    `orphan_reason` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `source_row_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `approved_change_ticket` VARCHAR(128) NOT NULL,
    `quarantined_at` DATETIME(6) NOT NULL,
    `quarantined_by` VARCHAR(288) NOT NULL,
    `restored_at` DATETIME(6) NULL,
    `restored_by` VARCHAR(288) NULL,
    `restore_ticket` VARCHAR(128) NULL,
    PRIMARY KEY (`quarantine_id`),
    UNIQUE KEY `uk_user_role_quarantine_batch_source`
        (`migration_batch_id`, `source_id`),
    KEY `idx_user_role_quarantine_source_pair`
        (`source_user_id`, `source_role_id`),
    CONSTRAINT `chk_user_role_quarantine_reason`
        CHECK (`orphan_reason` IN (
            'ORPHAN_USER',
            'ORPHAN_ROLE',
            'ORPHAN_USER_AND_ROLE'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Append-preserved RBAC mappings removed before FK enforcement';

DROP PROCEDURE IF EXISTS `_rbac_quarantine_orphans`;

DELIMITER $$

CREATE PROCEDURE `_rbac_quarantine_orphans`()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE v_batch_id CHAR(36);
    DECLARE v_expected_schema VARCHAR(64);
    DECLARE v_change_ticket VARCHAR(128);
    DECLARE v_expected_duplicate_groups BIGINT UNSIGNED;
    DECLARE v_expected_orphan_user_rows BIGINT UNSIGNED;
    DECLARE v_expected_orphan_role_rows BIGINT UNSIGNED;
    DECLARE v_expected_orphan_distinct_rows BIGINT UNSIGNED;
    DECLARE v_actual_duplicate_groups BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_actual_orphan_user_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_actual_orphan_role_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_actual_orphan_distinct_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_unstable_source_ids BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_existing_batch_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_manifest_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_manifest_distinct_ids BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_manifest_valid_hashes BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_manifest_matching_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_manifest_sha256 CHAR(64);
    DECLARE v_quarantined_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_deleted_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_remaining_captured_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_lock_result INTEGER DEFAULT 0;
    DECLARE v_release_result INTEGER DEFAULT 0;
    DECLARE v_lock_name VARCHAR(64);
    DECLARE v_message VARCHAR(128);
    DECLARE v_innodb_table_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_required_ledger_columns BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_required_quarantine_columns BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_total_ledger_columns BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_total_quarantine_columns BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_ledger_column_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_column_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_write_trigger_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_compatible_key_pairs BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_ledger_primary_key_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_unique_key_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_ledger_status_check_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_reason_check_count BIGINT UNSIGNED DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF v_lock_result = 1 THEN
            SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;
        END IF;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: select the target schema explicitly';
    END IF;

    IF VERSION() LIKE '%MariaDB%'
       OR CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: Oracle MySQL 8 or newer is required';
    END IF;

    IF @rbac_migration_batch_id IS NULL
       OR @rbac_expected_schema IS NULL
       OR @rbac_change_ticket IS NULL
       OR @rbac_expected_duplicate_groups IS NULL
       OR @rbac_expected_orphan_user_rows IS NULL
       OR @rbac_expected_orphan_role_rows IS NULL
       OR @rbac_expected_orphan_distinct_rows IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: all approval/count variables are required';
    END IF;

    SET v_batch_id = TRIM(CAST(@rbac_migration_batch_id AS CHAR));
    SET v_expected_schema = TRIM(CAST(@rbac_expected_schema AS CHAR));
    SET v_change_ticket = TRIM(CAST(@rbac_change_ticket AS CHAR));

    IF CAST(DATABASE() AS BINARY) <> CAST(v_expected_schema AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: selected schema differs from approval';
    END IF;

    IF v_batch_id NOT REGEXP '^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: migration batch ID must be a UUID';
    END IF;

    IF CHAR_LENGTH(v_change_ticket) < 6 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: an approved change ticket is required';
    END IF;

    IF CAST(@rbac_expected_duplicate_groups AS CHAR) NOT REGEXP '^[0-9]+$'
       OR CAST(@rbac_expected_orphan_user_rows AS CHAR) NOT REGEXP '^[0-9]+$'
       OR CAST(@rbac_expected_orphan_role_rows AS CHAR) NOT REGEXP '^[0-9]+$'
       OR CAST(@rbac_expected_orphan_distinct_rows AS CHAR) NOT REGEXP '^[0-9]+$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: expected counts must be unsigned integers';
    END IF;

    SET v_expected_duplicate_groups = CAST(@rbac_expected_duplicate_groups AS UNSIGNED);
    SET v_expected_orphan_user_rows = CAST(@rbac_expected_orphan_user_rows AS UNSIGNED);
    SET v_expected_orphan_role_rows = CAST(@rbac_expected_orphan_role_rows AS UNSIGNED);
    SET v_expected_orphan_distinct_rows = CAST(@rbac_expected_orphan_distinct_rows AS UNSIGNED);

    IF v_expected_duplicate_groups <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: duplicate mappings require separate adjudication';
    END IF;

    SELECT COUNT(*)
      INTO v_innodb_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           't_user',
           't_role',
           't_user_role_map',
           't_rbac_user_role_constraint_migration',
           't_user_role_map_quarantine'
       )
       AND UPPER(engine) = 'INNODB';

    SELECT COUNT(*)
      INTO v_required_ledger_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_rbac_user_role_constraint_migration'
       AND column_name IN (
           'migration_batch_id', 'change_ticket',
           'expected_duplicate_groups', 'expected_orphan_user_rows',
           'expected_orphan_role_rows', 'expected_orphan_distinct_rows',
           'approved_manifest_sha256', 'quarantined_rows',
           'migration_status', 'cleanup_completed_at', 'cleanup_executed_by',
           'constraints_applied_at', 'constraints_applied_by',
           'made_user_id_not_null', 'made_role_id_not_null',
           'original_user_id_column_type', 'original_role_id_column_type',
           'original_user_id_column_comment', 'original_role_id_column_comment',
           'created_unique_constraint', 'created_role_lookup_index',
           'created_user_foreign_key', 'created_role_foreign_key',
           'rollback_ticket', 'rollback_completed_at', 'rollback_executed_by'
       );

    SELECT COUNT(*)
      INTO v_required_quarantine_columns
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map_quarantine'
       AND column_name IN (
           'quarantine_id', 'migration_batch_id', 'source_id',
           'source_user_id', 'source_role_id', 'source_create_time',
           'orphan_reason', 'source_row_sha256', 'approved_change_ticket',
           'quarantined_at', 'quarantined_by', 'restored_at',
           'restored_by', 'restore_ticket'
       );

    SELECT COUNT(*),
           COALESCE(SUM(CASE
               WHEN column_name = 'migration_batch_id'
                AND LOWER(column_type) = 'char(36)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name = 'change_ticket'
                AND LOWER(column_type) = 'varchar(128)'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name IN (
                    'expected_duplicate_groups', 'expected_orphan_user_rows',
                    'expected_orphan_role_rows', 'expected_orphan_distinct_rows',
                    'quarantined_rows'
                )
                AND LOWER(column_type) = 'bigint unsigned'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name = 'approved_manifest_sha256'
                AND LOWER(column_type) = 'char(64)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name = 'migration_status'
                AND LOWER(column_type) = 'varchar(40)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name IN (
                    'cleanup_completed_at', 'constraints_applied_at',
                    'rollback_completed_at'
                )
                AND LOWER(column_type) = 'datetime(6)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name IN (
                    'cleanup_executed_by', 'constraints_applied_by',
                    'rollback_executed_by'
                )
                AND LOWER(column_type) = 'varchar(288)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name IN (
                    'made_user_id_not_null', 'made_role_id_not_null',
                    'created_unique_constraint', 'created_role_lookup_index',
                    'created_user_foreign_key', 'created_role_foreign_key'
                )
                AND LOWER(column_type) = 'tinyint'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name IN (
                    'original_user_id_column_type',
                    'original_role_id_column_type'
                )
                AND LOWER(column_type) = 'varchar(255)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name IN (
                    'original_user_id_column_comment',
                    'original_role_id_column_comment'
                )
                AND LOWER(column_type) = 'text'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name = 'rollback_ticket'
                AND LOWER(column_type) = 'varchar(128)'
                AND is_nullable = 'YES' THEN 0
               ELSE 1
           END), 0)
      INTO v_total_ledger_columns, v_ledger_column_mismatches
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_rbac_user_role_constraint_migration';

    SELECT COUNT(*),
           COALESCE(SUM(CASE
               WHEN column_name = 'quarantine_id'
                AND LOWER(column_type) = 'bigint unsigned'
                AND is_nullable = 'NO'
                AND extra = 'auto_increment' THEN 0
               WHEN column_name = 'migration_batch_id'
                AND LOWER(column_type) = 'char(36)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name = 'source_id'
                AND LOWER(column_type) = 'bigint'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name IN ('source_user_id', 'source_role_id')
                AND LOWER(column_type) = 'bigint'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name = 'source_create_time'
                AND LOWER(column_type) = 'datetime(6)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name = 'orphan_reason'
                AND LOWER(column_type) = 'varchar(32)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name = 'source_row_sha256'
                AND LOWER(column_type) = 'char(64)'
                AND is_nullable = 'NO'
                AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin' THEN 0
               WHEN column_name = 'approved_change_ticket'
                AND LOWER(column_type) = 'varchar(128)'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name = 'quarantined_at'
                AND LOWER(column_type) = 'datetime(6)'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name = 'quarantined_by'
                AND LOWER(column_type) = 'varchar(288)'
                AND is_nullable = 'NO' THEN 0
               WHEN column_name = 'restored_at'
                AND LOWER(column_type) = 'datetime(6)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name = 'restored_by'
                AND LOWER(column_type) = 'varchar(288)'
                AND is_nullable = 'YES' THEN 0
               WHEN column_name = 'restore_ticket'
                AND LOWER(column_type) = 'varchar(128)'
                AND is_nullable = 'YES' THEN 0
               ELSE 1
           END), 0)
      INTO v_total_quarantine_columns, v_quarantine_column_mismatches
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map_quarantine';

    SELECT COUNT(*)
      INTO v_write_trigger_count
      FROM information_schema.triggers
     WHERE trigger_schema = DATABASE()
       AND event_object_table IN (
           't_user_role_map',
           't_rbac_user_role_constraint_migration',
           't_user_role_map_quarantine'
       );

    SELECT COUNT(*)
      INTO v_compatible_key_pairs
      FROM (
          SELECT child.column_name
            FROM information_schema.columns AS child
            INNER JOIN information_schema.columns AS parent
              ON parent.table_schema = child.table_schema
             AND parent.table_name = 't_user'
             AND parent.column_name = 'user_id'
           WHERE child.table_schema = DATABASE()
             AND child.table_name = 't_user_role_map'
             AND child.column_name = 'user_id'
             AND LOWER(child.column_type) = LOWER(parent.column_type)
          UNION ALL
          SELECT child.column_name
            FROM information_schema.columns AS child
            INNER JOIN information_schema.columns AS parent
              ON parent.table_schema = child.table_schema
             AND parent.table_name = 't_role'
             AND parent.column_name = 'role_id'
           WHERE child.table_schema = DATABASE()
             AND child.table_name = 't_user_role_map'
             AND child.column_name = 'role_id'
             AND LOWER(child.column_type) = LOWER(parent.column_type)
      ) AS compatible_key_pairs;

    SELECT COUNT(*)
      INTO v_ledger_primary_key_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_rbac_user_role_constraint_migration'
             AND index_name = 'PRIMARY'
           GROUP BY index_name
          HAVING COUNT(*) = 1
             AND MIN(non_unique) = 0
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND SUM(seq_in_index = 1 AND column_name = 'migration_batch_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS ledger_primary_keys;

    SELECT COUNT(*)
      INTO v_quarantine_unique_key_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map_quarantine'
             AND index_name = 'uk_user_role_quarantine_batch_source'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND COUNT(*) = 2
             AND SUM(seq_in_index = 1 AND column_name = 'migration_batch_id') = 1
             AND SUM(seq_in_index = 2 AND column_name = 'source_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS quarantine_unique_keys;

    SELECT COUNT(*)
      INTO v_ledger_status_check_count
      FROM information_schema.table_constraints AS table_rule
      INNER JOIN information_schema.check_constraints AS check_rule
        ON check_rule.constraint_schema = table_rule.constraint_schema
       AND check_rule.constraint_name = table_rule.constraint_name
     WHERE table_rule.constraint_schema = DATABASE()
       AND table_rule.table_name = 't_rbac_user_role_constraint_migration'
       AND table_rule.constraint_name = 'chk_rbac_constraint_migration_status'
       AND table_rule.constraint_type = 'CHECK'
       AND table_rule.enforced = 'YES'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])QUARANTINED([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])NOT_NULL_HARDENING([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])NOT_NULL_HARDENED([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])CONSTRAINTS_APPLYING([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])CONSTRAINTS_APPLIED([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ROLLBACK_DROPPING_CONSTRAINTS([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ROLLBACK_CONSTRAINTS_DROPPED([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ROLLBACK_FINALIZING_INDEXES([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ROLLED_BACK([^A-Z_]|$)';

    SELECT COUNT(*)
      INTO v_quarantine_reason_check_count
      FROM information_schema.table_constraints AS table_rule
      INNER JOIN information_schema.check_constraints AS check_rule
        ON check_rule.constraint_schema = table_rule.constraint_schema
       AND check_rule.constraint_name = table_rule.constraint_name
     WHERE table_rule.constraint_schema = DATABASE()
       AND table_rule.table_name = 't_user_role_map_quarantine'
       AND table_rule.constraint_name = 'chk_user_role_quarantine_reason'
       AND table_rule.constraint_type = 'CHECK'
       AND table_rule.enforced = 'YES'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ORPHAN_USER([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ORPHAN_ROLE([^A-Z_]|$)'
       AND check_rule.check_clause REGEXP '(^|[^A-Z_])ORPHAN_USER_AND_ROLE([^A-Z_]|$)';

    IF v_innodb_table_count <> 5
       OR v_required_ledger_columns <> 26
       OR v_required_quarantine_columns <> 14
       OR v_total_ledger_columns <> 26
       OR v_total_quarantine_columns <> 14
       OR v_ledger_column_mismatches <> 0
       OR v_quarantine_column_mismatches <> 0
       OR v_write_trigger_count <> 0
       OR v_compatible_key_pairs <> 2
       OR v_ledger_primary_key_count <> 1
       OR v_quarantine_unique_key_count <> 1
       OR v_ledger_status_check_count <> 1
       OR v_quarantine_reason_check_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: migration table/schema contract mismatch';
    END IF;

    SET v_lock_name = CONCAT('rbac-urm-', LEFT(SHA2(DATABASE(), 256), 48));
    SELECT GET_LOCK(v_lock_name, 10) INTO v_lock_result;
    IF v_lock_result <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: another RBAC migration holds the lock';
    END IF;

    SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
    START TRANSACTION WITH CONSISTENT SNAPSHOT;

    SELECT COUNT(*)
      INTO v_existing_batch_rows
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    IF v_existing_batch_rows <> 0
       OR EXISTS (
           SELECT 1
             FROM t_user_role_map_quarantine
            WHERE migration_batch_id = v_batch_id
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: migration batch ID has already been used';
    END IF;

    -- The operator creates this session-only manifest from the exact preflight
    -- rows. A missing table fails here before any application row is changed.
    SELECT COUNT(*),
           COUNT(DISTINCT source_id),
           SUM(
               source_row_sha256 REGEXP '^[[:xdigit:]]{64}$'
               AND CAST(source_row_sha256 AS BINARY) =
                   CAST(LOWER(source_row_sha256) AS BINARY)
           )
      INTO v_manifest_rows,
           v_manifest_distinct_ids,
           v_manifest_valid_hashes
      FROM `_rbac_approved_orphan_manifest`;

    SET v_manifest_valid_hashes = COALESCE(v_manifest_valid_hashes, 0);
    IF v_manifest_rows <> v_expected_orphan_distinct_rows
       OR v_manifest_distinct_ids <> v_manifest_rows
       OR v_manifest_valid_hashes <> v_manifest_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: approved row manifest is invalid';
    END IF;

    -- Normalize the operator-owned temp table into a migration-owned exact
    -- schema. Numeric coercion collisions, out-of-range IDs, truncation, or a
    -- non-binary hash definition fail here instead of producing a digest whose
    -- ordering differs from the persistent BIGINT quarantine ledger.
    DROP TEMPORARY TABLE IF EXISTS `_rbac_normalized_orphan_manifest`;
    CREATE TEMPORARY TABLE `_rbac_normalized_orphan_manifest` (
        `source_id` BIGINT NOT NULL,
        `source_row_sha256` CHAR(64)
            CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        PRIMARY KEY (`source_id`)
    ) ENGINE=InnoDB;
    INSERT INTO `_rbac_normalized_orphan_manifest`
        (`source_id`, `source_row_sha256`)
    SELECT source_id, source_row_sha256
      FROM `_rbac_approved_orphan_manifest`;
    IF ROW_COUNT() <> v_manifest_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: manifest normalization mismatch';
    END IF;

    IF v_manifest_rows * 86 > @@session.group_concat_max_len THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: manifest exceeds safe digest buffer';
    END IF;

    SELECT SHA2(
               GROUP_CONCAT(
                   CONCAT(source_id, ':', source_row_sha256)
                   ORDER BY source_id SEPARATOR '|'
               ),
               256
           )
      INTO v_manifest_sha256
      FROM `_rbac_normalized_orphan_manifest`;

    IF v_manifest_sha256 IS NULL THEN
        SET v_manifest_sha256 = SHA2('', 256);
    END IF;

    -- Lock the approved source rows and missing parent-key gaps. This prevents a
    -- captured mapping from becoming valid between quarantine and deletion.
    SELECT role_map.id
      FROM `_rbac_normalized_orphan_manifest` AS approved
      INNER JOIN t_user_role_map AS role_map
        ON role_map.id = approved.source_id
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE (user_record.user_id IS NULL OR role_record.role_id IS NULL)
       AND CAST(approved.source_row_sha256 AS BINARY) = CAST(SHA2(CONCAT(
           'id=', CAST(role_map.id AS CHAR), CHAR(31),
           'user_id=', COALESCE(CAST(role_map.user_id AS CHAR), '<NULL>'), CHAR(31),
           'role_id=', COALESCE(CAST(role_map.role_id AS CHAR), '<NULL>'), CHAR(31),
           'create_time=', COALESCE(
               DATE_FORMAT(role_map.create_time, '%Y-%m-%d %H:%i:%s.%f'),
               '<NULL>'
           )
       ), 256) AS BINARY)
     ORDER BY role_map.id
       FOR UPDATE;

    SELECT COUNT(*)
      INTO v_unstable_source_ids
      FROM (
          SELECT id
            FROM t_user_role_map
           GROUP BY id
          HAVING id IS NULL OR COUNT(*) > 1
      ) AS unstable_source_ids;

    SELECT COUNT(*)
      INTO v_actual_duplicate_groups
      FROM (
          SELECT user_id, role_id
            FROM t_user_role_map
           GROUP BY user_id, role_id
          HAVING COUNT(*) > 1
      ) AS duplicate_mappings;

    SELECT COUNT(*)
      INTO v_actual_orphan_user_rows
      FROM t_user_role_map AS role_map
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
     WHERE user_record.user_id IS NULL;

    SELECT COUNT(*)
      INTO v_actual_orphan_role_rows
      FROM t_user_role_map AS role_map
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE role_record.role_id IS NULL;

    SELECT COUNT(*)
      INTO v_actual_orphan_distinct_rows
      FROM t_user_role_map AS role_map
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE user_record.user_id IS NULL
        OR role_record.role_id IS NULL;

    SELECT COUNT(*)
      INTO v_manifest_matching_rows
      FROM `_rbac_normalized_orphan_manifest` AS approved
      INNER JOIN t_user_role_map AS role_map
        ON role_map.id = approved.source_id
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE (user_record.user_id IS NULL OR role_record.role_id IS NULL)
       AND CAST(approved.source_row_sha256 AS BINARY) = CAST(SHA2(CONCAT(
           'id=', CAST(role_map.id AS CHAR), CHAR(31),
           'user_id=', COALESCE(CAST(role_map.user_id AS CHAR), '<NULL>'), CHAR(31),
           'role_id=', COALESCE(CAST(role_map.role_id AS CHAR), '<NULL>'), CHAR(31),
           'create_time=', COALESCE(
               DATE_FORMAT(role_map.create_time, '%Y-%m-%d %H:%i:%s.%f'),
               '<NULL>'
           )
       ), 256) AS BINARY);

    IF v_unstable_source_ids <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: source IDs are not stable and unique';
    END IF;

    IF v_actual_duplicate_groups <> v_expected_duplicate_groups
       OR v_actual_orphan_user_rows <> v_expected_orphan_user_rows
       OR v_actual_orphan_role_rows <> v_expected_orphan_role_rows
       OR v_actual_orphan_distinct_rows <> v_expected_orphan_distinct_rows
       OR v_manifest_matching_rows <> v_expected_orphan_distinct_rows THEN
        SET v_message = CONCAT(
            'RBAC cleanup blocked: live counts differ from approved preflight'
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;

    INSERT INTO t_rbac_user_role_constraint_migration (
        migration_batch_id,
        change_ticket,
        expected_duplicate_groups,
        expected_orphan_user_rows,
        expected_orphan_role_rows,
        expected_orphan_distinct_rows,
        approved_manifest_sha256,
        quarantined_rows,
        migration_status,
        cleanup_completed_at,
        cleanup_executed_by
    ) VALUES (
        v_batch_id,
        v_change_ticket,
        v_expected_duplicate_groups,
        v_expected_orphan_user_rows,
        v_expected_orphan_role_rows,
        v_expected_orphan_distinct_rows,
        v_manifest_sha256,
        0,
        'QUARANTINED',
        NOW(6),
        CURRENT_USER()
    );

    INSERT INTO t_user_role_map_quarantine (
        migration_batch_id,
        source_id,
        source_user_id,
        source_role_id,
        source_create_time,
        orphan_reason,
        source_row_sha256,
        approved_change_ticket,
        quarantined_at,
        quarantined_by
    )
    SELECT
        v_batch_id,
        role_map.id,
        role_map.user_id,
        role_map.role_id,
        role_map.create_time,
        CASE
            WHEN user_record.user_id IS NULL AND role_record.role_id IS NULL
                THEN 'ORPHAN_USER_AND_ROLE'
            WHEN user_record.user_id IS NULL THEN 'ORPHAN_USER'
            ELSE 'ORPHAN_ROLE'
        END,
        SHA2(CONCAT(
            'id=', CAST(role_map.id AS CHAR), CHAR(31),
            'user_id=', COALESCE(CAST(role_map.user_id AS CHAR), '<NULL>'), CHAR(31),
            'role_id=', COALESCE(CAST(role_map.role_id AS CHAR), '<NULL>'), CHAR(31),
            'create_time=', COALESCE(
                DATE_FORMAT(role_map.create_time, '%Y-%m-%d %H:%i:%s.%f'),
                '<NULL>'
            )
        ), 256),
        v_change_ticket,
        NOW(6),
        CURRENT_USER()
      FROM `_rbac_normalized_orphan_manifest` AS approved
      INNER JOIN t_user_role_map AS role_map
        ON role_map.id = approved.source_id
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE (user_record.user_id IS NULL OR role_record.role_id IS NULL)
       AND CAST(approved.source_row_sha256 AS BINARY) = CAST(SHA2(CONCAT(
           'id=', CAST(role_map.id AS CHAR), CHAR(31),
           'user_id=', COALESCE(CAST(role_map.user_id AS CHAR), '<NULL>'), CHAR(31),
           'role_id=', COALESCE(CAST(role_map.role_id AS CHAR), '<NULL>'), CHAR(31),
           'create_time=', COALESCE(
               DATE_FORMAT(role_map.create_time, '%Y-%m-%d %H:%i:%s.%f'),
               '<NULL>'
           )
       ), 256) AS BINARY)
     ORDER BY role_map.id;

    SET v_quarantined_rows = ROW_COUNT();
    IF v_quarantined_rows <> v_expected_orphan_distinct_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: quarantine insert count mismatch';
    END IF;

    -- Delete only rows captured in this immutable batch, with every source
    -- value matched. There is deliberately no predicate-only orphan deletion.
    DELETE role_map
      FROM t_user_role_map AS role_map
      INNER JOIN t_user_role_map_quarantine AS quarantine
        ON quarantine.migration_batch_id = v_batch_id
       AND quarantine.source_id = role_map.id
       AND quarantine.source_user_id <=> role_map.user_id
       AND quarantine.source_role_id <=> role_map.role_id
       AND quarantine.source_create_time <=> role_map.create_time;

    SET v_deleted_rows = ROW_COUNT();
    IF v_deleted_rows <> v_quarantined_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: captured rows changed before deletion';
    END IF;

    SELECT COUNT(*)
      INTO v_remaining_captured_rows
      FROM t_user_role_map_quarantine AS quarantine
      INNER JOIN t_user_role_map AS role_map
        ON role_map.id = quarantine.source_id
     WHERE quarantine.migration_batch_id = v_batch_id;

    IF v_remaining_captured_rows <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC cleanup blocked: captured source rows remain';
    END IF;

    UPDATE t_rbac_user_role_constraint_migration
       SET quarantined_rows = v_quarantined_rows,
           cleanup_completed_at = NOW(6),
           cleanup_executed_by = CURRENT_USER()
     WHERE migration_batch_id = v_batch_id;

    COMMIT;
    DROP TEMPORARY TABLE IF EXISTS `_rbac_normalized_orphan_manifest`;
    SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;

    SELECT
        v_batch_id AS migration_batch_id,
        v_change_ticket AS change_ticket,
        v_quarantined_rows AS quarantined_and_removed_rows,
        'RERUN_PREFLIGHT_THEN_HARDEN_NOT_NULL' AS next_required_step;
END$$

DELIMITER ;

CALL `_rbac_quarantine_orphans`();
DROP PROCEDURE IF EXISTS `_rbac_quarantine_orphans`;
