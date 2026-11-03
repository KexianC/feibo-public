-- RBAC user-role constraint migration: emergency rollback.
--
-- The rollback accepts every durable phase from QUARANTINED through applied
-- constraints and is resumable across implicit-commit DDL boundaries. It
-- drops only objects whose exact definitions and ownership flags match the
-- chosen batch, restores hash-verified rows, and retains quarantine forever.
--
-- Required session variables:
--   SET @rbac_migration_batch_id = '<applied batch UUID>';
--   SET @rbac_expected_schema = 'approved_schema_name';
--   SET @rbac_rollback_ticket = 'INCIDENT-OR-ROLLBACK-ID';
--   SET @rbac_expected_quarantined_rows = <ledger count>;
--   SET @rbac_confirm_writes_blocked = 1; -- after DB privilege revocation
--
-- Stop application writes and use a dedicated clean session. Do not use
-- mysql --force.

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

DROP PROCEDURE IF EXISTS `_rbac_rollback_user_role_constraints`;

DELIMITER $$

CREATE PROCEDURE `_rbac_rollback_user_role_constraints`()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE v_batch_id CHAR(36);
    DECLARE v_expected_schema VARCHAR(64);
    DECLARE v_rollback_ticket VARCHAR(128);
    DECLARE v_expected_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_audit_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_audit_status VARCHAR(40);
    DECLARE v_audit_change_ticket VARCHAR(128);
    DECLARE v_audit_rollback_ticket VARCHAR(128);
    DECLARE v_audit_manifest_sha256 CHAR(64);
    DECLARE v_current_manifest_sha256 CHAR(64);
    DECLARE v_audit_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_made_user_not_null TINYINT DEFAULT 0;
    DECLARE v_made_role_not_null TINYINT DEFAULT 0;
    DECLARE v_created_unique TINYINT DEFAULT 0;
    DECLARE v_created_role_index TINYINT DEFAULT 0;
    DECLARE v_created_user_fk TINYINT DEFAULT 0;
    DECLARE v_created_role_fk TINYINT DEFAULT 0;
    DECLARE v_original_user_type VARCHAR(255);
    DECLARE v_original_role_type VARCHAR(255);
    DECLARE v_original_user_comment TEXT;
    DECLARE v_original_role_comment TEXT;
    DECLARE v_current_user_type VARCHAR(255);
    DECLARE v_current_role_type VARCHAR(255);
    DECLARE v_current_user_nullable VARCHAR(3);
    DECLARE v_current_role_nullable VARCHAR(3);
    DECLARE v_current_user_comment TEXT;
    DECLARE v_current_role_comment TEXT;
    DECLARE v_quarantine_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_already_restored_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_hash_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_ticket_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_source_id_collisions BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_source_pair_collisions BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_unique_entries BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_role_index_entries BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_named_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_unique_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_role_index_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_exact_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_user_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_equivalent_role_fk_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_pre_restore_owned_expected BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_pre_restore_owned_present BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_final_owned_expected BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_final_owned_present BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_restored_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_innodb_table_count BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_write_trigger_count BIGINT UNSIGNED DEFAULT 0;
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
            SET MESSAGE_TEXT = 'RBAC rollback blocked: select the target schema explicitly';
    END IF;

    IF VERSION() LIKE '%MariaDB%'
       OR CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: Oracle MySQL 8 or newer is required';
    END IF;

    IF @rbac_migration_batch_id IS NULL
       OR @rbac_expected_schema IS NULL
       OR @rbac_rollback_ticket IS NULL
       OR @rbac_expected_quarantined_rows IS NULL
       OR @rbac_confirm_writes_blocked IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: approval and write barrier required';
    END IF;

    SET v_batch_id = TRIM(CAST(@rbac_migration_batch_id AS CHAR));
    SET v_expected_schema = TRIM(CAST(@rbac_expected_schema AS CHAR));
    SET v_rollback_ticket = TRIM(CAST(@rbac_rollback_ticket AS CHAR));

    IF CAST(DATABASE() AS BINARY) <> CAST(v_expected_schema AS BINARY)
       OR v_batch_id NOT REGEXP '^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$'
       OR CHAR_LENGTH(v_rollback_ticket) < 6
       OR CAST(@rbac_expected_quarantined_rows AS CHAR) NOT REGEXP '^[0-9]+$'
       OR CAST(@rbac_confirm_writes_blocked AS CHAR) <> '1' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: invalid approval variables';
    END IF;

    SET v_expected_quarantined_rows = CAST(@rbac_expected_quarantined_rows AS UNSIGNED);
    SET v_lock_name = CONCAT('rbac-urm-', LEFT(SHA2(DATABASE(), 256), 48));
    SELECT GET_LOCK(v_lock_name, 10) INTO v_lock_result;
    IF v_lock_result <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: migration lock unavailable';
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
            SET MESSAGE_TEXT = 'RBAC rollback blocked: engine/trigger contract failed';
    END IF;

    SELECT COUNT(*)
      INTO v_audit_rows
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    IF v_audit_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: migration batch not found';
    END IF;

    SELECT migration_status,
           change_ticket,
           rollback_ticket,
           approved_manifest_sha256,
           quarantined_rows,
           made_user_id_not_null,
           made_role_id_not_null,
           created_unique_constraint,
           created_role_lookup_index,
           created_user_foreign_key,
           created_role_foreign_key,
           original_user_id_column_type,
           original_role_id_column_type,
           original_user_id_column_comment,
           original_role_id_column_comment
      INTO v_audit_status,
           v_audit_change_ticket,
           v_audit_rollback_ticket,
           v_audit_manifest_sha256,
           v_audit_quarantined_rows,
           v_made_user_not_null,
           v_made_role_not_null,
           v_created_unique,
           v_created_role_index,
           v_created_user_fk,
           v_created_role_fk,
           v_original_user_type,
           v_original_role_type,
           v_original_user_comment,
           v_original_role_comment
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    SELECT LOWER(MAX(CASE WHEN column_name = 'user_id' THEN column_type END)),
           LOWER(MAX(CASE WHEN column_name = 'role_id' THEN column_type END)),
           MAX(CASE WHEN column_name = 'user_id' THEN is_nullable END),
           MAX(CASE WHEN column_name = 'role_id' THEN is_nullable END),
           MAX(CASE WHEN column_name = 'user_id' THEN column_comment END),
           MAX(CASE WHEN column_name = 'role_id' THEN column_comment END)
      INTO v_current_user_type,
           v_current_role_type,
           v_current_user_nullable,
           v_current_role_nullable,
           v_current_user_comment,
           v_current_role_comment
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name IN ('user_id', 'role_id');

    -- Before hardening/apply, ownership fields are intentionally incomplete.
    -- Normalize them in memory only; the audited transition is persisted after
    -- quarantine/hash/schema verification below.
    IF v_audit_status = 'QUARANTINED' THEN
        IF v_made_user_not_null IS NOT NULL
           OR v_made_role_not_null IS NOT NULL
           OR v_created_unique IS NOT NULL
           OR v_created_role_index IS NOT NULL
           OR v_created_user_fk IS NOT NULL
           OR v_created_role_fk IS NOT NULL
           OR v_original_user_type IS NOT NULL
           OR v_original_role_type IS NOT NULL
           OR v_audit_rollback_ticket IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: quarantined ownership ledger drift';
        END IF;
        SET v_made_user_not_null = 0;
        SET v_made_role_not_null = 0;
        SET v_created_unique = 0;
        SET v_created_role_index = 0;
        SET v_created_user_fk = 0;
        SET v_created_role_fk = 0;
        SET v_original_user_type = v_current_user_type;
        SET v_original_role_type = v_current_role_type;
        SET v_original_user_comment = COALESCE(v_current_user_comment, '');
        SET v_original_role_comment = COALESCE(v_current_role_comment, '');
    ELSEIF v_audit_status IN ('NOT_NULL_HARDENING', 'NOT_NULL_HARDENED') THEN
        IF v_made_user_not_null IS NULL
           OR v_made_role_not_null IS NULL
           OR v_original_user_type IS NULL
           OR v_original_role_type IS NULL
           OR v_created_unique IS NOT NULL
           OR v_created_role_index IS NOT NULL
           OR v_created_user_fk IS NOT NULL
           OR v_created_role_fk IS NOT NULL
           OR v_audit_rollback_ticket IS NOT NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: hardening ownership ledger drift';
        END IF;
        SET v_created_unique = 0;
        SET v_created_role_index = 0;
        SET v_created_user_fk = 0;
        SET v_created_role_fk = 0;
    END IF;

    IF v_made_user_not_null IS NULL
       OR v_made_role_not_null IS NULL
       OR v_created_unique IS NULL
       OR v_created_role_index IS NULL
       OR v_created_user_fk IS NULL
       OR v_created_role_fk IS NULL
       OR v_original_user_type IS NULL
       OR v_original_role_type IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: object ownership ledger incomplete';
    END IF;

    SELECT COUNT(*),
           COALESCE(SUM(restored_at IS NOT NULL), 0),
           COALESCE(SUM(CAST(approved_change_ticket AS BINARY) <>
                        CAST(v_audit_change_ticket AS BINARY)), 0),
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
           v_already_restored_rows,
           v_quarantine_ticket_mismatches,
           v_quarantine_hash_mismatches
      FROM t_user_role_map_quarantine
     WHERE migration_batch_id = v_batch_id;

    IF v_audit_quarantined_rows <> v_expected_quarantined_rows
       OR v_quarantine_rows <> v_expected_quarantined_rows
       OR v_quarantine_ticket_mismatches <> 0
       OR v_quarantine_hash_mismatches <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: quarantine ledger mismatch';
    END IF;

    IF v_quarantine_rows * 86 > @@session.group_concat_max_len THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: manifest digest buffer too small';
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
            SET MESSAGE_TEXT = 'RBAC rollback blocked: manifest digest mismatch';
    END IF;

    SELECT LOWER(MAX(CASE WHEN column_name = 'user_id' THEN column_type END)),
           LOWER(MAX(CASE WHEN column_name = 'role_id' THEN column_type END)),
           MAX(CASE WHEN column_name = 'user_id' THEN is_nullable END),
           MAX(CASE WHEN column_name = 'role_id' THEN is_nullable END)
      INTO v_current_user_type,
           v_current_role_type,
           v_current_user_nullable,
           v_current_role_nullable
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name IN ('user_id', 'role_id');

    IF v_current_user_type <> v_original_user_type
       OR v_current_role_type <> v_original_role_type THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC rollback blocked: key column type drift';
    END IF;

    SELECT COUNT(*)
      INTO v_named_unique_entries
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND index_name = 'uk_t_user_role_map_user_role';

    SELECT COUNT(*)
      INTO v_named_role_index_entries
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND index_name = 'idx_t_user_role_map_role_id';

    SELECT COUNT(*)
      INTO v_named_user_fk_count
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND constraint_name = 'fk_t_user_role_map_user';

    SELECT COUNT(*)
      INTO v_named_role_fk_count
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND constraint_name = 'fk_t_user_role_map_role';

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
      INTO v_equivalent_user_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.table_constraints AS table_rule
              ON table_rule.constraint_schema = key_usage.constraint_schema
             AND table_rule.table_name = key_usage.table_name
             AND table_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND table_rule.constraint_type = 'FOREIGN KEY'
           GROUP BY key_usage.constraint_name
          HAVING COUNT(*) = 1
             AND SUM(key_usage.column_name = 'user_id'
                     AND key_usage.ordinal_position = 1) = 1
      ) AS user_foreign_keys;

    SELECT COUNT(*)
      INTO v_equivalent_role_fk_count
      FROM (
          SELECT key_usage.constraint_name
            FROM information_schema.key_column_usage AS key_usage
            INNER JOIN information_schema.table_constraints AS table_rule
              ON table_rule.constraint_schema = key_usage.constraint_schema
             AND table_rule.table_name = key_usage.table_name
             AND table_rule.constraint_name = key_usage.constraint_name
           WHERE key_usage.constraint_schema = DATABASE()
             AND key_usage.table_name = 't_user_role_map'
             AND table_rule.constraint_type = 'FOREIGN KEY'
           GROUP BY key_usage.constraint_name
          HAVING COUNT(*) = 1
             AND SUM(key_usage.column_name = 'role_id'
                     AND key_usage.ordinal_position = 1) = 1
      ) AS role_foreign_keys;

    IF v_audit_status = 'ROLLED_BACK' THEN
        IF v_already_restored_rows <> v_expected_quarantined_rows
           OR (v_created_unique = 1 AND v_named_unique_entries <> 0)
           OR (v_created_role_index = 1 AND v_named_role_index_entries <> 0)
           OR (v_created_user_fk = 1 AND v_named_user_fk_count <> 0)
           OR (v_created_role_fk = 1 AND v_named_role_fk_count <> 0) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: completed ledger is inconsistent';
        END IF;
        SELECT v_batch_id AS migration_batch_id, 'ALREADY_ROLLED_BACK' AS rollback_status;
        SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;
    ELSE
        IF v_audit_status NOT IN (
            'QUARANTINED',
            'NOT_NULL_HARDENING',
            'NOT_NULL_HARDENED',
            'CONSTRAINTS_APPLYING',
            'CONSTRAINTS_APPLIED',
            'ROLLBACK_DROPPING_CONSTRAINTS',
            'ROLLBACK_CONSTRAINTS_DROPPED',
            'ROLLBACK_FINALIZING_INDEXES'
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: batch is not in rollback state';
        END IF;

        IF v_audit_status IN (
            'QUARANTINED',
            'NOT_NULL_HARDENING',
            'NOT_NULL_HARDENED',
            'CONSTRAINTS_APPLYING',
            'CONSTRAINTS_APPLIED',
            'ROLLBACK_DROPPING_CONSTRAINTS',
            'ROLLBACK_CONSTRAINTS_DROPPED'
        ) AND v_already_restored_rows <> 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: partial restored ledger requires review';
        END IF;

        IF v_audit_status IN (
            'ROLLBACK_DROPPING_CONSTRAINTS',
            'ROLLBACK_CONSTRAINTS_DROPPED',
            'ROLLBACK_FINALIZING_INDEXES'
        ) AND CAST(v_audit_rollback_ticket AS BINARY) <>
                  CAST(v_rollback_ticket AS BINARY) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: retry ticket differs from ledger';
        END IF;

        IF v_audit_status IN (
            'QUARANTINED', 'NOT_NULL_HARDENING', 'NOT_NULL_HARDENED'
        ) THEN
            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLBACK_DROPPING_CONSTRAINTS',
                   rollback_ticket = v_rollback_ticket,
                   made_user_id_not_null = v_made_user_not_null,
                   made_role_id_not_null = v_made_role_not_null,
                   original_user_id_column_type = v_original_user_type,
                   original_role_id_column_type = v_original_role_type,
                   original_user_id_column_comment = v_original_user_comment,
                   original_role_id_column_comment = v_original_role_comment,
                   created_unique_constraint = 0,
                   created_role_lookup_index = 0,
                   created_user_foreign_key = 0,
                   created_role_foreign_key = 0
             WHERE migration_batch_id = v_batch_id
               AND migration_status = v_audit_status;
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: pre-apply transition conflict';
            END IF;
            COMMIT;
            SET v_audit_status = 'ROLLBACK_DROPPING_CONSTRAINTS';
        END IF;

        IF v_audit_status = 'CONSTRAINTS_APPLYING' THEN
            IF v_audit_rollback_ticket IS NOT NULL THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: apply-pending ticket drift';
            END IF;

            SET v_final_owned_expected =
                v_created_unique + v_created_role_index
                + v_created_user_fk + v_created_role_fk;
            SET v_final_owned_present =
                (v_created_unique = 1 AND v_exact_unique_count = 1)
                + (v_created_role_index = 1 AND v_exact_role_index_count = 1)
                + (v_created_user_fk = 1 AND v_exact_user_fk_count = 1)
                + (v_created_role_fk = 1 AND v_exact_role_fk_count = 1);

            IF (v_created_unique = 1
                AND v_named_unique_entries <> 0
                AND v_exact_unique_count <> 1)
               OR (v_created_role_index = 1
                AND v_named_role_index_entries <> 0
                AND v_exact_role_index_count <> 1)
               OR (v_created_user_fk = 1
                AND v_named_user_fk_count <> 0
                AND v_exact_user_fk_count <> 1)
               OR (v_created_role_fk = 1
                AND v_named_role_fk_count <> 0
                AND v_exact_role_fk_count <> 1)
               OR v_final_owned_present NOT IN (0, v_final_owned_expected) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: partial apply-pending DDL state';
            END IF;

            IF v_final_owned_present = 0 THEN
                SET v_created_unique = 0;
                SET v_created_role_index = 0;
                SET v_created_user_fk = 0;
                SET v_created_role_fk = 0;
            END IF;

            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLBACK_DROPPING_CONSTRAINTS',
                   rollback_ticket = v_rollback_ticket,
                   created_unique_constraint = v_created_unique,
                   created_role_lookup_index = v_created_role_index,
                   created_user_foreign_key = v_created_user_fk,
                   created_role_foreign_key = v_created_role_fk
             WHERE migration_batch_id = v_batch_id
               AND migration_status = 'CONSTRAINTS_APPLYING';
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: apply-pending transition conflict';
            END IF;
            COMMIT;
            SET v_audit_status = 'ROLLBACK_DROPPING_CONSTRAINTS';
        END IF;

        IF v_audit_status = 'CONSTRAINTS_APPLIED' THEN
            IF (v_created_unique = 1 AND v_exact_unique_count <> 1)
               OR (v_created_role_index = 1 AND v_exact_role_index_count <> 1)
               OR (v_created_user_fk = 1 AND v_exact_user_fk_count <> 1)
               OR (v_created_role_fk = 1 AND v_exact_role_fk_count <> 1)
               OR (v_made_user_not_null = 1 AND v_current_user_nullable <> 'NO')
               OR (v_made_role_not_null = 1 AND v_current_role_nullable <> 'NO') THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: exact owned definition drift';
            END IF;

            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLBACK_DROPPING_CONSTRAINTS',
                   rollback_ticket = v_rollback_ticket
             WHERE migration_batch_id = v_batch_id
               AND migration_status = 'CONSTRAINTS_APPLIED';
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: failed to persist DDL plan';
            END IF;
            COMMIT;
            SET v_audit_status = 'ROLLBACK_DROPPING_CONSTRAINTS';
        END IF;

        IF v_expected_quarantined_rows > 0
           AND (
               v_equivalent_user_fk_count <>
                   (v_created_user_fk = 1 AND v_exact_user_fk_count = 1)
               OR v_equivalent_role_fk_count <>
                   (v_created_role_fk = 1 AND v_exact_role_fk_count = 1)
           ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC rollback blocked: non-batch FK requires review';
        END IF;

        IF v_audit_status = 'ROLLBACK_DROPPING_CONSTRAINTS' THEN
            SET v_pre_restore_owned_expected =
                v_created_user_fk + v_created_role_fk
                + v_made_user_not_null + v_made_role_not_null;
            SET v_pre_restore_owned_present =
                (v_created_user_fk = 1 AND v_exact_user_fk_count = 1)
                + (v_created_role_fk = 1 AND v_exact_role_fk_count = 1)
                + (v_made_user_not_null = 1 AND v_current_user_nullable = 'NO')
                + (v_made_role_not_null = 1 AND v_current_role_nullable = 'NO');

            IF v_pre_restore_owned_present NOT IN (0, v_pre_restore_owned_expected) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: partial pre-restore DDL state';
            END IF;

            IF v_pre_restore_owned_present = v_pre_restore_owned_expected
               AND v_pre_restore_owned_expected > 0 THEN
                IF v_created_user_fk = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        'DROP FOREIGN KEY `fk_t_user_role_map_user`');
                END IF;
                IF v_created_role_fk = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        'DROP FOREIGN KEY `fk_t_user_role_map_role`');
                END IF;
                IF v_made_user_not_null = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        CONCAT(
                            'MODIFY COLUMN `user_id` ', v_original_user_type,
                            ' NULL DEFAULT NULL COMMENT ', QUOTE(COALESCE(v_original_user_comment, ''))
                        ));
                END IF;
                IF v_made_role_not_null = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        CONCAT(
                            'MODIFY COLUMN `role_id` ', v_original_role_type,
                            ' NULL DEFAULT NULL COMMENT ', QUOTE(COALESCE(v_original_role_comment, ''))
                        ));
                END IF;

                SET @rbac_rollback_pre_restore_ddl = CONCAT(
                    'ALTER TABLE `t_user_role_map` ', v_ddl_clauses
                );
                PREPARE rbac_rollback_pre_restore_statement
                    FROM @rbac_rollback_pre_restore_ddl;
                EXECUTE rbac_rollback_pre_restore_statement;
                DEALLOCATE PREPARE rbac_rollback_pre_restore_statement;
            END IF;

            SELECT MAX(CASE WHEN column_name = 'user_id' THEN is_nullable END),
                   MAX(CASE WHEN column_name = 'role_id' THEN is_nullable END)
              INTO v_current_user_nullable, v_current_role_nullable
              FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND column_name IN ('user_id', 'role_id');

            SELECT COUNT(*)
              INTO v_exact_user_fk_count
              FROM information_schema.table_constraints
             WHERE constraint_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND constraint_name = 'fk_t_user_role_map_user'
               AND constraint_type = 'FOREIGN KEY';

            SELECT COUNT(*)
              INTO v_exact_role_fk_count
              FROM information_schema.table_constraints
             WHERE constraint_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND constraint_name = 'fk_t_user_role_map_role'
               AND constraint_type = 'FOREIGN KEY';

            IF (v_created_user_fk = 1 AND v_exact_user_fk_count <> 0)
               OR (v_created_role_fk = 1 AND v_exact_role_fk_count <> 0)
               OR (v_made_user_not_null = 1 AND v_current_user_nullable <> 'YES')
               OR (v_made_role_not_null = 1 AND v_current_role_nullable <> 'YES') THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback failed: pre-restore DDL verification';
            END IF;

            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLBACK_CONSTRAINTS_DROPPED'
             WHERE migration_batch_id = v_batch_id
               AND migration_status = 'ROLLBACK_DROPPING_CONSTRAINTS';
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback failed: DDL state finalization conflict';
            END IF;
            COMMIT;
            SET v_audit_status = 'ROLLBACK_CONSTRAINTS_DROPPED';
        END IF;

        IF v_audit_status = 'ROLLBACK_CONSTRAINTS_DROPPED' THEN
            START TRANSACTION;

            SELECT COUNT(*)
              INTO v_source_id_collisions
              FROM t_user_role_map_quarantine AS quarantine
              INNER JOIN t_user_role_map AS role_map
                ON role_map.id = quarantine.source_id
             WHERE quarantine.migration_batch_id = v_batch_id;

            SELECT COUNT(*)
              INTO v_source_pair_collisions
              FROM t_user_role_map_quarantine AS quarantine
              INNER JOIN t_user_role_map AS role_map
                ON role_map.user_id <=> quarantine.source_user_id
               AND role_map.role_id <=> quarantine.source_role_id
             WHERE quarantine.migration_batch_id = v_batch_id;

            IF v_source_id_collisions <> 0 OR v_source_pair_collisions <> 0 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: restored rows collide with live data';
            END IF;

            -- The unique mapping index remains until this restore commits, so
            -- concurrent non-NULL pair collisions fail at the database layer.
            INSERT INTO t_user_role_map (id, user_id, role_id, create_time)
            SELECT source_id, source_user_id, source_role_id, source_create_time
              FROM t_user_role_map_quarantine
             WHERE migration_batch_id = v_batch_id
               AND restored_at IS NULL
             ORDER BY source_id;

            SET v_restored_rows = ROW_COUNT();
            IF v_restored_rows <> v_expected_quarantined_rows THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: restored row count mismatch';
            END IF;

            UPDATE t_user_role_map_quarantine
               SET restored_at = NOW(6),
                   restored_by = CURRENT_USER(),
                   restore_ticket = v_rollback_ticket
             WHERE migration_batch_id = v_batch_id
               AND restored_at IS NULL;

            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLBACK_FINALIZING_INDEXES'
             WHERE migration_batch_id = v_batch_id
               AND migration_status = 'ROLLBACK_CONSTRAINTS_DROPPED';
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: restore state conflict';
            END IF;
            COMMIT;
            SET v_audit_status = 'ROLLBACK_FINALIZING_INDEXES';
            SET v_already_restored_rows = v_expected_quarantined_rows;
        END IF;

        IF v_audit_status = 'ROLLBACK_FINALIZING_INDEXES' THEN
            IF v_already_restored_rows <> v_expected_quarantined_rows THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: finalization restore mismatch';
            END IF;

            -- Refresh exact definitions because this state is a DDL retry point.
            SELECT COUNT(*)
              INTO v_named_unique_entries
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND index_name = 'uk_t_user_role_map_user_role';

            SELECT COUNT(*)
              INTO v_named_role_index_entries
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND index_name = 'idx_t_user_role_map_role_id';

            SELECT COUNT(*)
              INTO v_named_user_fk_count
              FROM information_schema.table_constraints
             WHERE constraint_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND constraint_name = 'fk_t_user_role_map_user';

            SELECT COUNT(*)
              INTO v_named_role_fk_count
              FROM information_schema.table_constraints
             WHERE constraint_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND constraint_name = 'fk_t_user_role_map_role';

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

            IF (v_created_unique = 1
                AND v_named_unique_entries <> 0
                AND v_exact_unique_count <> 1)
               OR (v_created_role_index = 1
                AND v_named_role_index_entries <> 0
                AND v_exact_role_index_count <> 1)
               OR (v_created_user_fk = 1 AND v_named_user_fk_count <> 0)
               OR (v_created_role_fk = 1 AND v_named_role_fk_count <> 0) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: owned canonical name drift';
            END IF;

            SET v_final_owned_expected = v_created_unique + v_created_role_index;
            SET v_final_owned_present =
                (v_created_unique = 1 AND v_exact_unique_count = 1)
                + (v_created_role_index = 1 AND v_exact_role_index_count = 1);

            IF v_final_owned_present NOT IN (0, v_final_owned_expected) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback blocked: partial final DDL state';
            END IF;

            IF v_final_owned_present = v_final_owned_expected
               AND v_final_owned_expected > 0 THEN
                SET v_ddl_clauses = '';
                IF v_created_unique = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        'DROP INDEX `uk_t_user_role_map_user_role`');
                END IF;
                IF v_created_role_index = 1 THEN
                    SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                        'DROP INDEX `idx_t_user_role_map_role_id`');
                END IF;

                SET @rbac_rollback_final_ddl = CONCAT(
                    'ALTER TABLE `t_user_role_map` ', v_ddl_clauses
                );
                PREPARE rbac_rollback_final_statement FROM @rbac_rollback_final_ddl;
                EXECUTE rbac_rollback_final_statement;
                DEALLOCATE PREPARE rbac_rollback_final_statement;
            END IF;

            SELECT COUNT(*)
              INTO v_exact_unique_count
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND index_name = 'uk_t_user_role_map_user_role';
            SELECT COUNT(*)
              INTO v_exact_role_index_count
              FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 't_user_role_map'
               AND index_name = 'idx_t_user_role_map_role_id';

            IF (v_created_unique = 1 AND v_exact_unique_count <> 0)
               OR (v_created_role_index = 1 AND v_exact_role_index_count <> 0) THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback failed: final DDL verification';
            END IF;

            UPDATE t_rbac_user_role_constraint_migration
               SET migration_status = 'ROLLED_BACK',
                   rollback_ticket = v_rollback_ticket,
                   rollback_completed_at = NOW(6),
                   rollback_executed_by = CURRENT_USER()
             WHERE migration_batch_id = v_batch_id
               AND migration_status = 'ROLLBACK_FINALIZING_INDEXES';
            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'RBAC rollback failed: final audit conflict';
            END IF;
            COMMIT;

            SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;
            SELECT v_batch_id AS migration_batch_id,
                   v_rollback_ticket AS rollback_ticket,
                   v_expected_quarantined_rows AS restored_rows,
                   'ROLLED_BACK' AS rollback_status;
        END IF;
    END IF;
END$$

DELIMITER ;

CALL `_rbac_rollback_user_role_constraints`();
DROP PROCEDURE IF EXISTS `_rbac_rollback_user_role_constraints`;
