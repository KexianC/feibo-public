-- RBAC user-role constraint migration: final integrity constraints.
--
-- Run only after the same batch has completed quarantine/cleanup, the explicit
-- NOT NULL hardening step, and a final preflight returns
-- READY_FOR_CONSTRAINT_MIGRATION.
--
-- Required session variables (same connection values used for cleanup):
--   SET @rbac_migration_batch_id = '<approved UUID>';
--   SET @rbac_expected_schema = 'approved_schema_name';
--   SET @rbac_change_ticket = 'CHANGE-OR-INCIDENT-ID';
--   SET @rbac_expected_quarantined_rows = <ledger count>;
--
-- Do not use mysql --force. ALTER TABLE is atomic on supported MySQL 8/InnoDB,
-- but it implicitly commits; use the companion rollback script if necessary.

-- Refuse persistent helper-procedure DDL until the selected schema exactly
-- matches the operator-approved schema.
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

DROP PROCEDURE IF EXISTS `_rbac_apply_user_role_constraints`;

DELIMITER $$

CREATE PROCEDURE `_rbac_apply_user_role_constraints`()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE v_batch_id CHAR(36);
    DECLARE v_expected_schema VARCHAR(64);
    DECLARE v_change_ticket VARCHAR(128);
    DECLARE v_expected_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_audit_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_audit_ticket VARCHAR(128);
    DECLARE v_audit_status VARCHAR(40);
    DECLARE v_audit_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_quarantine_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_table_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_innodb_table_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_user_column_type VARCHAR(255);
    DECLARE v_map_user_column_type VARCHAR(255);
    DECLARE v_role_column_type VARCHAR(255);
    DECLARE v_map_role_column_type VARCHAR(255);
    DECLARE v_map_user_nullable VARCHAR(3);
    DECLARE v_map_role_nullable VARCHAR(3);
    DECLARE v_map_user_default TEXT;
    DECLARE v_map_role_default TEXT;
    DECLARE v_map_user_extra VARCHAR(255);
    DECLARE v_map_role_extra VARCHAR(255);
    DECLARE v_map_user_comment TEXT;
    DECLARE v_map_role_comment TEXT;
    DECLARE v_user_unique_key_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_role_unique_key_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_duplicate_group_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_orphan_distinct_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_unique_entries BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_unique_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_unique_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_role_index_entries BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_role_index_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_role_index_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_add_unique TINYINT DEFAULT 0;
    DECLARE v_add_role_index TINYINT DEFAULT 0;
    DECLARE v_add_user_fk TINYINT DEFAULT 0;
    DECLARE v_add_role_fk TINYINT DEFAULT 0;
    DECLARE v_make_user_not_null TINYINT DEFAULT 0;
    DECLARE v_make_role_not_null TINYINT DEFAULT 0;
    DECLARE v_quarantine_hash_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_ticket_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_restored_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_write_trigger_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_audit_manifest_sha256 CHAR(64);
    DECLARE v_current_manifest_sha256 CHAR(64);
    DECLARE v_owned_objects_present BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_owned_objects_expected BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_planned_null_flags BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_original_user_column_type VARCHAR(255);
    DECLARE v_original_role_column_type VARCHAR(255);
    DECLARE v_original_user_column_comment TEXT;
    DECLARE v_original_role_column_comment TEXT;
    DECLARE v_ddl_clauses LONGTEXT DEFAULT '';
    DECLARE v_lock_result INTEGER DEFAULT 0;
    DECLARE v_release_result INTEGER DEFAULT 0;
    DECLARE v_lock_name VARCHAR(64);

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
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: select the target schema';
    END IF;

    IF VERSION() LIKE '%MariaDB%'
       OR CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: MySQL 8 or newer is required';
    END IF;

    IF @rbac_migration_batch_id IS NULL
       OR @rbac_expected_schema IS NULL
       OR @rbac_change_ticket IS NULL
       OR @rbac_expected_quarantined_rows IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: approval variables are required';
    END IF;

    SET v_batch_id = TRIM(CAST(@rbac_migration_batch_id AS CHAR));
    SET v_expected_schema = TRIM(CAST(@rbac_expected_schema AS CHAR));
    SET v_change_ticket = TRIM(CAST(@rbac_change_ticket AS CHAR));

    IF CAST(DATABASE() AS BINARY) <> CAST(v_expected_schema AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: schema differs from approval';
    END IF;

    IF v_batch_id NOT REGEXP '^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$'
       OR CHAR_LENGTH(v_change_ticket) < 6
       OR CAST(@rbac_expected_quarantined_rows AS CHAR) NOT REGEXP '^[0-9]+$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: invalid approval variables';
    END IF;

    SET v_expected_quarantined_rows = CAST(@rbac_expected_quarantined_rows AS UNSIGNED);
    SET v_lock_name = CONCAT('rbac-urm-', LEFT(SHA2(DATABASE(), 256), 48));
    SELECT GET_LOCK(v_lock_name, 10) INTO v_lock_result;
    IF v_lock_result <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: migration lock unavailable';
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           't_user',
           't_role',
           't_user_role_map',
           't_rbac_user_role_constraint_migration',
           't_user_role_map_quarantine'
       );

    IF v_table_count <> 5 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: phase-one tables are missing';
    END IF;

    SELECT COUNT(*)
      INTO v_innodb_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           't_user', 't_role', 't_user_role_map',
           't_rbac_user_role_constraint_migration',
           't_user_role_map_quarantine'
       )
       AND UPPER(engine) = 'INNODB';

    SELECT COUNT(*)
      INTO v_write_trigger_count
      FROM information_schema.triggers
     WHERE trigger_schema = DATABASE()
       AND event_object_table IN (
           't_user_role_map',
           't_rbac_user_role_constraint_migration',
           't_user_role_map_quarantine'
       );

    IF v_innodb_table_count <> 5 OR v_write_trigger_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: engine/trigger contract failed';
    END IF;

    SELECT COUNT(*)
      INTO v_audit_rows
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    IF v_audit_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: approved cleanup batch not found';
    END IF;

    SELECT change_ticket,
           migration_status,
           quarantined_rows,
           approved_manifest_sha256,
           COALESCE(made_user_id_not_null, 0),
           COALESCE(made_role_id_not_null, 0),
           COALESCE(created_unique_constraint, 0),
           COALESCE(created_role_lookup_index, 0),
           COALESCE(created_user_foreign_key, 0),
           COALESCE(created_role_foreign_key, 0),
           original_user_id_column_type,
           original_role_id_column_type,
           original_user_id_column_comment,
           original_role_id_column_comment
      INTO v_audit_ticket,
           v_audit_status,
           v_audit_quarantined_rows,
           v_audit_manifest_sha256,
           v_make_user_not_null,
           v_make_role_not_null,
           v_add_unique,
           v_add_role_index,
           v_add_user_fk,
           v_add_role_fk,
           v_original_user_column_type,
           v_original_role_column_type,
           v_original_user_column_comment,
           v_original_role_column_comment
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    SELECT (made_user_id_not_null IS NULL)
           + (made_role_id_not_null IS NULL)
           + (created_unique_constraint IS NULL)
           + (created_role_lookup_index IS NULL)
           + (created_user_foreign_key IS NULL)
           + (created_role_foreign_key IS NULL)
      INTO v_planned_null_flags
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    IF v_audit_status = 'CONSTRAINTS_APPLYING'
       AND v_planned_null_flags <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: pending DDL plan is incomplete';
    END IF;

    SELECT COUNT(*),
           COALESCE(SUM(restored_at IS NOT NULL), 0),
           COALESCE(SUM(CAST(approved_change_ticket AS BINARY) <>
                        CAST(v_change_ticket AS BINARY)), 0),
           COALESCE(SUM(CAST(source_row_sha256 AS BINARY) <>
                        CAST(SHA2(CONCAT(
                            'id=', CAST(source_id AS CHAR), CHAR(31),
                            'user_id=', COALESCE(CAST(source_user_id AS CHAR), '<NULL>'), CHAR(31),
                            'role_id=', COALESCE(CAST(source_role_id AS CHAR), '<NULL>'), CHAR(31),
                            'create_time=', COALESCE(
                                DATE_FORMAT(source_create_time, '%Y-%m-%d %H:%i:%s.%f'),
                                '<NULL>'
                            )
                        ), 256) AS BINARY)), 0)
      INTO v_quarantine_rows,
           v_quarantine_restored_rows,
           v_quarantine_ticket_mismatches,
           v_quarantine_hash_mismatches
      FROM t_user_role_map_quarantine
     WHERE migration_batch_id = v_batch_id;

    IF CAST(v_audit_ticket AS BINARY) <> CAST(v_change_ticket AS BINARY)
       OR v_audit_status NOT IN ('NOT_NULL_HARDENED', 'CONSTRAINTS_APPLYING')
       OR v_audit_quarantined_rows <> v_expected_quarantined_rows
       OR v_quarantine_rows <> v_expected_quarantined_rows
       OR v_quarantine_restored_rows <> 0
       OR v_quarantine_ticket_mismatches <> 0
       OR v_quarantine_hash_mismatches <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: cleanup ledger mismatch';
    END IF;

    IF v_quarantine_rows * 86 > @@session.group_concat_max_len THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: manifest digest buffer too small';
    END IF;

    SELECT SHA2(
               GROUP_CONCAT(
                   CONCAT(source_id, ':', source_row_sha256)
                   ORDER BY source_id SEPARATOR '|'
               ),
               256
           )
      INTO v_current_manifest_sha256
      FROM t_user_role_map_quarantine
     WHERE migration_batch_id = v_batch_id;

    IF v_current_manifest_sha256 IS NULL THEN
        SET v_current_manifest_sha256 = SHA2('', 256);
    END IF;

    IF CAST(v_current_manifest_sha256 AS BINARY) <>
       CAST(v_audit_manifest_sha256 AS BINARY) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: manifest digest mismatch';
    END IF;

    SELECT LOWER(column_type)
      INTO v_user_column_type
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user'
       AND column_name = 'user_id';

    SELECT LOWER(column_type),
           is_nullable,
           column_default,
           extra,
           column_comment
      INTO v_map_user_column_type,
           v_map_user_nullable,
           v_map_user_default,
           v_map_user_extra,
           v_map_user_comment
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name = 'user_id';

    SELECT LOWER(column_type)
      INTO v_role_column_type
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_role'
       AND column_name = 'role_id';

    SELECT LOWER(column_type),
           is_nullable,
           column_default,
           extra,
           column_comment
      INTO v_map_role_column_type,
           v_map_role_nullable,
           v_map_role_default,
           v_map_role_extra,
           v_map_role_comment
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name = 'role_id';

    IF v_user_column_type IS NULL
       OR v_map_user_column_type IS NULL
       OR v_role_column_type IS NULL
       OR v_map_role_column_type IS NULL
       OR v_user_column_type <> v_map_user_column_type
       OR v_role_column_type <> v_map_role_column_type THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: FK key types are incompatible';
    END IF;

    IF v_map_user_nullable <> 'NO' OR v_map_role_nullable <> 'NO' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: run explicit NOT NULL step';
    END IF;

    SELECT COUNT(*)
      INTO v_user_unique_key_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND COUNT(*) = 1
             AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS user_unique_keys;

    SELECT COUNT(*)
      INTO v_role_unique_key_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_role'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND COUNT(*) = 1
             AND SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS role_unique_keys;

    IF v_user_unique_key_count = 0 OR v_role_unique_key_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: parent IDs are not unique';
    END IF;

    SELECT COUNT(*)
      INTO v_duplicate_group_count
      FROM (
          SELECT user_id, role_id
            FROM t_user_role_map
           GROUP BY user_id, role_id
          HAVING COUNT(*) > 1
      ) AS duplicate_mappings;

    SELECT COUNT(*)
      INTO v_orphan_distinct_count
      FROM t_user_role_map AS role_map
      LEFT JOIN t_user AS user_record
        ON user_record.user_id = role_map.user_id
      LEFT JOIN t_role AS role_record
        ON role_record.role_id = role_map.role_id
     WHERE user_record.user_id IS NULL
        OR role_record.role_id IS NULL;

    IF v_duplicate_group_count <> 0 OR v_orphan_distinct_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: live integrity check failed';
    END IF;

    SELECT COUNT(*)
      INTO v_named_unique_entries
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND index_name = 'uk_t_user_role_map_user_role';

    SELECT COUNT(*)
      INTO v_exact_unique_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
             AND index_name = 'uk_t_user_role_map_user_role'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND COUNT(*) = 2
             AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
             AND SUM(seq_in_index = 2 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS exact_unique_keys;

    SELECT COUNT(*)
      INTO v_equivalent_unique_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND COUNT(*) = 2
             AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
             AND SUM(seq_in_index = 2 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS equivalent_unique_keys;

    IF v_named_unique_entries <> 0 AND v_exact_unique_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: canonical unique name conflicts';
    END IF;

    SELECT COUNT(*)
      INTO v_named_role_index_entries
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND index_name = 'idx_t_user_role_map_role_id';

    SELECT COUNT(*)
      INTO v_exact_role_index_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
             AND index_name = 'idx_t_user_role_map_role_id'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 1
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND COUNT(*) = 1
             AND SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS exact_role_indexes;

    SELECT COUNT(*)
      INTO v_equivalent_role_index_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
           GROUP BY index_name
          HAVING SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS equivalent_role_indexes;

    IF v_named_role_index_entries <> 0 AND v_exact_role_index_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: canonical role index name conflicts';
    END IF;

    SELECT COUNT(*)
      INTO v_named_user_fk_count
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND constraint_name = 'fk_t_user_role_map_user'
       AND constraint_type = 'FOREIGN KEY';

    SELECT COUNT(*)
      INTO v_exact_user_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND key_usage.constraint_name = 'fk_t_user_role_map_user'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'user_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_user'
                 AND key_usage.referenced_column_name = 'user_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS exact_user_foreign_keys;

    SELECT COUNT(*)
      INTO v_equivalent_user_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'user_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_user'
                 AND key_usage.referenced_column_name = 'user_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS equivalent_user_foreign_keys;

    IF v_named_user_fk_count <> 0 AND v_exact_user_fk_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: canonical user FK name conflicts';
    END IF;

    SELECT COUNT(*)
      INTO v_named_role_fk_count
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND constraint_name = 'fk_t_user_role_map_role'
       AND constraint_type = 'FOREIGN KEY';

    SELECT COUNT(*)
      INTO v_exact_role_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND key_usage.constraint_name = 'fk_t_user_role_map_role'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'role_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_role'
                 AND key_usage.referenced_column_name = 'role_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS exact_role_foreign_keys;

    SELECT COUNT(*)
      INTO v_equivalent_role_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'role_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_role'
                 AND key_usage.referenced_column_name = 'role_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS equivalent_role_foreign_keys;

    IF v_named_role_fk_count <> 0 AND v_exact_role_fk_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: canonical role FK name conflicts';
    END IF;

    IF v_audit_status = 'NOT_NULL_HARDENED' THEN
        IF v_make_user_not_null IS NULL
           OR v_make_role_not_null IS NULL
           OR v_original_user_column_type IS NULL
           OR v_original_role_column_type IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC constraint apply blocked: NOT NULL ledger incomplete';
        END IF;
        SET v_add_unique = (v_equivalent_unique_count = 0);
        SET v_add_role_index = (v_equivalent_role_index_count = 0);
        SET v_add_user_fk = (v_equivalent_user_fk_count = 0);
        SET v_add_role_fk = (v_equivalent_role_fk_count = 0);

        -- Persist the ownership plan before the implicit-commit DDL. If the
        -- client disconnects, a retry reconciles this exact plan with schema.
        UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'CONSTRAINTS_APPLYING',
               created_unique_constraint = v_add_unique,
               created_role_lookup_index = v_add_role_index,
               created_user_foreign_key = v_add_user_fk,
               created_role_foreign_key = v_add_role_fk
         WHERE migration_batch_id = v_batch_id
           AND migration_status = 'NOT_NULL_HARDENED';

        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC constraint apply blocked: failed to persist DDL plan';
        END IF;
        COMMIT;
        SET v_audit_status = 'CONSTRAINTS_APPLYING';
    ELSE
        IF v_original_user_column_type IS NULL
           OR v_original_role_column_type IS NULL
           OR v_map_user_column_type <> v_original_user_column_type
           OR v_map_role_column_type <> v_original_role_column_type THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC constraint apply blocked: pending column definition drift';
        END IF;
    END IF;

    IF (v_add_unique = 0 AND v_equivalent_unique_count = 0)
       OR (v_add_role_index = 0 AND v_equivalent_role_index_count = 0)
       OR (v_add_user_fk = 0 AND v_equivalent_user_fk_count = 0)
       OR (v_add_role_fk = 0 AND v_equivalent_role_fk_count = 0)
       OR (v_add_unique = 1 AND v_exact_unique_count = 0
           AND v_equivalent_unique_count <> 0)
       OR (v_add_role_index = 1 AND v_exact_role_index_count = 0
           AND v_equivalent_role_index_count <> 0)
       OR (v_add_user_fk = 1 AND v_exact_user_fk_count = 0
           AND v_equivalent_user_fk_count <> 0)
       OR (v_add_role_fk = 1 AND v_exact_role_fk_count = 0
           AND v_equivalent_role_fk_count <> 0) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: pending DDL plan drift';
    END IF;

    SET v_owned_objects_expected =
        v_add_unique + v_add_role_index + v_add_user_fk + v_add_role_fk;
    SET v_owned_objects_present =
        (v_add_unique = 1 AND v_exact_unique_count = 1)
        + (v_add_role_index = 1 AND v_exact_role_index_count = 1)
        + (v_add_user_fk = 1 AND v_exact_user_fk_count = 1)
        + (v_add_role_fk = 1 AND v_exact_role_fk_count = 1);

    IF v_owned_objects_present NOT IN (0, v_owned_objects_expected) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply blocked: partial atomic DDL state';
    END IF;

    IF v_owned_objects_present = 0 AND v_owned_objects_expected > 0 THEN
        IF v_add_unique = 1 THEN
            SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                'ADD CONSTRAINT `uk_t_user_role_map_user_role` UNIQUE (`user_id`, `role_id`)');
        END IF;

        IF v_add_role_index = 1 THEN
            SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                'ADD INDEX `idx_t_user_role_map_role_id` (`role_id`)');
        END IF;

        IF v_add_user_fk = 1 THEN
            SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                'ADD CONSTRAINT `fk_t_user_role_map_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`user_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
        END IF;

        IF v_add_role_fk = 1 THEN
            SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                'ADD CONSTRAINT `fk_t_user_role_map_role` FOREIGN KEY (`role_id`) REFERENCES `t_role` (`role_id`) ON UPDATE RESTRICT ON DELETE RESTRICT');
        END IF;

        SET @rbac_constraint_ddl = CONCAT(
            'ALTER TABLE `t_user_role_map` ',
            v_ddl_clauses
        );
        PREPARE rbac_constraint_statement FROM @rbac_constraint_ddl;
        EXECUTE rbac_constraint_statement;
        DEALLOCATE PREPARE rbac_constraint_statement;
    END IF;

    -- Re-evaluate all equivalent constraints after the atomic ALTER TABLE.
    SELECT MAX(CASE WHEN column_name = 'user_id' THEN is_nullable END),
           MAX(CASE WHEN column_name = 'role_id' THEN is_nullable END)
      INTO v_map_user_nullable, v_map_role_nullable
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name IN ('user_id', 'role_id');

    SELECT COUNT(*)
      INTO v_exact_unique_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
             AND index_name = 'uk_t_user_role_map_user_role'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND COUNT(*) = 2
             AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
             AND SUM(seq_in_index = 2 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS exact_unique_keys;

    SELECT COUNT(*)
      INTO v_exact_role_index_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
             AND index_name = 'idx_t_user_role_map_role_id'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 1
             AND MIN(index_type) = 'BTREE'
             AND MIN(is_visible) = 'YES'
             AND COUNT(*) = 1
             AND SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
             AND SUM(collation <> 'A' OR collation IS NULL) = 0
      ) AS exact_role_indexes;

    SELECT COUNT(*)
      INTO v_exact_user_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND key_usage.constraint_name = 'fk_t_user_role_map_user'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'user_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_user'
                 AND key_usage.referenced_column_name = 'user_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS exact_user_foreign_keys;

    SELECT COUNT(*)
      INTO v_exact_role_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND key_usage.constraint_name = 'fk_t_user_role_map_role'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'role_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_role'
                 AND key_usage.referenced_column_name = 'role_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS exact_role_foreign_keys;

    SELECT COUNT(*)
      INTO v_equivalent_unique_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
           GROUP BY index_name, non_unique
          HAVING MIN(non_unique) = 0
             AND COUNT(*) = 2
             AND SUM(seq_in_index = 1 AND column_name = 'user_id') = 1
             AND SUM(seq_in_index = 2 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS equivalent_unique_keys;

    SELECT COUNT(*)
      INTO v_equivalent_user_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'user_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_user'
                 AND key_usage.referenced_column_name = 'user_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS equivalent_user_foreign_keys;

    SELECT COUNT(*)
      INTO v_equivalent_role_index_count
      FROM (
          SELECT index_name
            FROM information_schema.statistics
           WHERE table_schema = DATABASE()
             AND table_name = 't_user_role_map'
           GROUP BY index_name
          HAVING SUM(seq_in_index = 1 AND column_name = 'role_id') = 1
             AND SUM(sub_part IS NOT NULL) = 0
      ) AS equivalent_role_indexes;

    SELECT COUNT(*)
      INTO v_equivalent_role_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.referential_constraints AS reference_rule
              ON reference_rule.constraint_schema = key_usage.constraint_schema
             AND reference_rule.table_name = key_usage.table_name
             AND reference_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
           GROUP BY key_usage.constraint_name,
                    reference_rule.update_rule,
                    reference_rule.delete_rule
          HAVING COUNT(*) = 1
             AND SUM(
                 key_usage.column_name = 'role_id'
                 AND key_usage.ordinal_position = 1
                 AND key_usage.referenced_table_schema = DATABASE()
                 AND key_usage.referenced_table_name = 't_role'
                 AND key_usage.referenced_column_name = 'role_id'
             ) = 1
             AND reference_rule.update_rule = 'RESTRICT'
             AND reference_rule.delete_rule = 'RESTRICT'
      ) AS equivalent_role_foreign_keys;

    IF v_map_user_nullable <> 'NO'
       OR v_map_role_nullable <> 'NO'
       OR v_equivalent_unique_count = 0
       OR v_equivalent_role_index_count = 0
       OR v_equivalent_user_fk_count = 0
       OR v_equivalent_role_fk_count = 0
       OR (v_add_unique = 1 AND v_exact_unique_count <> 1)
       OR (v_add_role_index = 1 AND v_exact_role_index_count <> 1)
       OR (v_add_user_fk = 1 AND v_exact_user_fk_count <> 1)
       OR (v_add_role_fk = 1 AND v_exact_role_fk_count <> 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply failed: post-DDL verification failed';
    END IF;

    UPDATE t_rbac_user_role_constraint_migration
       SET migration_status = 'CONSTRAINTS_APPLIED',
           constraints_applied_at = NOW(6),
           constraints_applied_by = CURRENT_USER(),
           created_unique_constraint = v_add_unique,
           created_role_lookup_index = v_add_role_index,
           created_user_foreign_key = v_add_user_fk,
           created_role_foreign_key = v_add_role_fk
     WHERE migration_batch_id = v_batch_id
       AND migration_status = 'CONSTRAINTS_APPLYING';
    IF ROW_COUNT() <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC constraint apply failed: audit finalization conflict';
    END IF;
    COMMIT;

    SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;

    SELECT
        v_batch_id AS migration_batch_id,
        v_change_ticket AS change_ticket,
        v_make_user_not_null AS made_user_id_not_null,
        v_make_role_not_null AS made_role_id_not_null,
        v_add_unique AS created_unique_constraint,
        v_add_role_index AS created_role_lookup_index,
        v_add_user_fk AS created_user_foreign_key,
        v_add_role_fk AS created_role_foreign_key,
        'CONSTRAINTS_APPLIED' AS migration_status;
END$$

DELIMITER ;

CALL `_rbac_apply_user_role_constraints`();
DROP PROCEDURE IF EXISTS `_rbac_apply_user_role_constraints`;
