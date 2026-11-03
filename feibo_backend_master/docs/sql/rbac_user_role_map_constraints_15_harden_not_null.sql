-- RBAC user-role constraint migration: explicit type-preserving NOT NULL step.
--
-- Run after quarantine cleanup and before final constraints. The script reads
-- each live COLUMN_TYPE and COMMENT from information_schema; it never assumes
-- BIGINT. Nullable columns are changed only after all NULL/orphan rows are gone.
-- Its DDL ownership plan is committed first, so a disconnect is resumable.
--
-- Required session variables:
--   SET @rbac_migration_batch_id = '<approved UUID>';
--   SET @rbac_expected_schema = 'approved_schema_name';
--   SET @rbac_change_ticket = 'CHANGE-OR-INCIDENT-ID';
--   SET @rbac_expected_quarantined_rows = <ledger count>;

-- Refuse persistent helper-procedure DDL until the selected schema exactly
-- matches the operator-approved schema. Failure leaves only a session temp
-- table, which disappears with the dedicated migration connection.
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

DROP PROCEDURE IF EXISTS `_rbac_harden_user_role_not_null`;

DELIMITER $$

CREATE PROCEDURE `_rbac_harden_user_role_not_null`()
SQL SECURITY INVOKER
MODIFIES SQL DATA
BEGIN
    DECLARE v_batch_id CHAR(36);
    DECLARE v_expected_schema VARCHAR(64);
    DECLARE v_change_ticket VARCHAR(128);
    DECLARE v_expected_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_audit_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_audit_status VARCHAR(40);
    DECLARE v_audit_ticket VARCHAR(128);
    DECLARE v_audit_quarantined_rows BIGINT UNSIGNED;
    DECLARE v_audit_manifest_sha256 CHAR(64);
    DECLARE v_current_manifest_sha256 CHAR(64);
    DECLARE v_quarantine_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_quarantine_mismatches BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_user_parent_type VARCHAR(255);
    DECLARE v_role_parent_type VARCHAR(255);
    DECLARE v_user_type VARCHAR(255);
    DECLARE v_role_type VARCHAR(255);
    DECLARE v_user_nullable VARCHAR(3);
    DECLARE v_role_nullable VARCHAR(3);
    DECLARE v_user_default TEXT;
    DECLARE v_role_default TEXT;
    DECLARE v_user_extra VARCHAR(255);
    DECLARE v_role_extra VARCHAR(255);
    DECLARE v_user_comment TEXT;
    DECLARE v_role_comment TEXT;
    DECLARE v_user_character_set VARCHAR(64);
    DECLARE v_role_character_set VARCHAR(64);
    DECLARE v_original_user_type VARCHAR(255);
    DECLARE v_original_role_type VARCHAR(255);
    DECLARE v_original_user_comment TEXT;
    DECLARE v_original_role_comment TEXT;
    DECLARE v_make_user_not_null TINYINT DEFAULT 0;
    DECLARE v_make_role_not_null TINYINT DEFAULT 0;
    DECLARE v_null_key_rows BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_owned_expected BIGINT UNSIGNED DEFAULT 0;
    DECLARE v_owned_present BIGINT UNSIGNED DEFAULT 0;
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

    IF DATABASE() IS NULL OR DATABASE() = ''
       OR VERSION() LIKE '%MariaDB%'
       OR CAST(SUBSTRING_INDEX(VERSION(), '.', 1) AS UNSIGNED) < 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: select schema on Oracle MySQL 8+';
    END IF;

    IF @rbac_migration_batch_id IS NULL
       OR @rbac_expected_schema IS NULL
       OR @rbac_change_ticket IS NULL
       OR @rbac_expected_quarantined_rows IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: approval variables are required';
    END IF;

    SET v_batch_id = TRIM(CAST(@rbac_migration_batch_id AS CHAR));
    SET v_expected_schema = TRIM(CAST(@rbac_expected_schema AS CHAR));
    SET v_change_ticket = TRIM(CAST(@rbac_change_ticket AS CHAR));

    IF CAST(DATABASE() AS BINARY) <> CAST(v_expected_schema AS BINARY)
       OR v_batch_id NOT REGEXP '^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$'
       OR CHAR_LENGTH(v_change_ticket) < 6
       OR CAST(@rbac_expected_quarantined_rows AS CHAR) NOT REGEXP '^[0-9]+$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: invalid approval variables';
    END IF;

    SET v_expected_quarantined_rows = CAST(@rbac_expected_quarantined_rows AS UNSIGNED);
    SET v_lock_name = CONCAT('rbac-urm-', LEFT(SHA2(DATABASE(), 256), 48));
    SELECT GET_LOCK(v_lock_name, 10) INTO v_lock_result;
    IF v_lock_result <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: migration lock unavailable';
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
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: engine/trigger contract failed';
    END IF;

    SELECT COUNT(*)
      INTO v_audit_rows
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    IF v_audit_rows <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: cleanup batch not found';
    END IF;

    SELECT migration_status,
           change_ticket,
           quarantined_rows,
           approved_manifest_sha256,
           made_user_id_not_null,
           made_role_id_not_null,
           original_user_id_column_type,
           original_role_id_column_type,
           original_user_id_column_comment,
           original_role_id_column_comment
      INTO v_audit_status,
           v_audit_ticket,
           v_audit_quarantined_rows,
           v_audit_manifest_sha256,
           v_make_user_not_null,
           v_make_role_not_null,
           v_original_user_type,
           v_original_role_type,
           v_original_user_comment,
           v_original_role_comment
      FROM t_rbac_user_role_constraint_migration
     WHERE migration_batch_id = v_batch_id;

    SELECT COUNT(*),
           COALESCE(SUM(restored_at IS NOT NULL
                        OR CAST(approved_change_ticket AS BINARY) <>
                           CAST(v_change_ticket AS BINARY)
                        OR CAST(source_row_sha256 AS BINARY) <>
                           CAST(SHA2(CONCAT(
                               'id=', CAST(source_id AS CHAR), CHAR(31),
                               'user_id=', COALESCE(CAST(source_user_id AS CHAR), '<NULL>'), CHAR(31),
                               'role_id=', COALESCE(CAST(source_role_id AS CHAR), '<NULL>'), CHAR(31),
                               'create_time=', COALESCE(
                                   DATE_FORMAT(source_create_time, '%Y-%m-%d %H:%i:%s.%f'),
                                   '<NULL>'
                               )
                           ), 256) AS BINARY)), 0)
      INTO v_quarantine_rows, v_quarantine_mismatches
      FROM t_user_role_map_quarantine
     WHERE migration_batch_id = v_batch_id;

    IF CAST(v_audit_ticket AS BINARY) <> CAST(v_change_ticket AS BINARY)
       OR v_audit_status NOT IN (
           'QUARANTINED', 'NOT_NULL_HARDENING', 'NOT_NULL_HARDENED'
       )
       OR v_audit_quarantined_rows <> v_expected_quarantined_rows
       OR v_quarantine_rows <> v_expected_quarantined_rows
       OR v_quarantine_mismatches <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: cleanup ledger mismatch';
    END IF;

    IF v_quarantine_rows * 86 > @@session.group_concat_max_len THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: manifest digest buffer too small';
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
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: manifest digest mismatch';
    END IF;

    SELECT LOWER(column_type)
      INTO v_user_parent_type
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user'
       AND column_name = 'user_id';

    SELECT LOWER(column_type)
      INTO v_role_parent_type
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_role'
       AND column_name = 'role_id';

    SELECT LOWER(column_type), is_nullable, column_default, extra,
           column_comment, character_set_name
      INTO v_user_type, v_user_nullable, v_user_default, v_user_extra,
           v_user_comment, v_user_character_set
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name = 'user_id';

    SELECT LOWER(column_type), is_nullable, column_default, extra,
           column_comment, character_set_name
      INTO v_role_type, v_role_nullable, v_role_default, v_role_extra,
           v_role_comment, v_role_character_set
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_user_role_map'
       AND column_name = 'role_id';

    IF v_user_type IS NULL OR v_role_type IS NULL
       OR v_user_type <> v_user_parent_type
       OR v_role_type <> v_role_parent_type THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: live parent/child types differ';
    END IF;

    IF v_user_character_set IS NOT NULL OR v_role_character_set IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: character key needs reviewed full-definition DDL';
    END IF;

    SELECT COUNT(*)
      INTO v_null_key_rows
      FROM t_user_role_map
     WHERE user_id IS NULL OR role_id IS NULL;

    IF v_null_key_rows <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: NULL mapping rows still exist';
    END IF;

    IF v_audit_status = 'QUARANTINED' THEN
        IF (v_user_nullable = 'YES'
            AND (v_user_default IS NOT NULL OR COALESCE(v_user_extra, '') <> ''))
           OR (v_role_nullable = 'YES'
            AND (v_role_default IS NOT NULL OR COALESCE(v_role_extra, '') <> '')) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: nullable key has default/extra';
        END IF;

        SET v_make_user_not_null = (v_user_nullable = 'YES');
        SET v_make_role_not_null = (v_role_nullable = 'YES');
        SET v_original_user_type = v_user_type;
        SET v_original_role_type = v_role_type;
        SET v_original_user_comment = COALESCE(v_user_comment, '');
        SET v_original_role_comment = COALESCE(v_role_comment, '');

        UPDATE t_rbac_user_role_constraint_migration
           SET migration_status = 'NOT_NULL_HARDENING',
               made_user_id_not_null = v_make_user_not_null,
               made_role_id_not_null = v_make_role_not_null,
               original_user_id_column_type = v_original_user_type,
               original_role_id_column_type = v_original_role_type,
               original_user_id_column_comment = v_original_user_comment,
               original_role_id_column_comment = v_original_role_comment
         WHERE migration_batch_id = v_batch_id
           AND migration_status = 'QUARANTINED';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: failed to persist DDL plan';
        END IF;
        COMMIT;
        SET v_audit_status = 'NOT_NULL_HARDENING';
    ELSEIF v_make_user_not_null IS NULL
       OR v_make_role_not_null IS NULL
       OR v_original_user_type IS NULL
       OR v_original_role_type IS NULL
       OR v_user_type <> v_original_user_type
       OR v_role_type <> v_original_role_type THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: pending definition drift';
    END IF;

    IF v_audit_status = 'NOT_NULL_HARDENED' THEN
        IF v_user_nullable <> 'NO' OR v_role_nullable <> 'NO' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: hardened ledger drift';
        END IF;
        SELECT v_batch_id AS migration_batch_id,
               'ALREADY_NOT_NULL_HARDENED' AS hardening_status;
        SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;
    ELSE
        SET v_owned_expected = v_make_user_not_null + v_make_role_not_null;
        SET v_owned_present =
            (v_make_user_not_null = 1 AND v_user_nullable = 'NO')
            + (v_make_role_not_null = 1 AND v_role_nullable = 'NO');

        IF (v_make_user_not_null = 0 AND v_user_nullable <> 'NO')
           OR (v_make_role_not_null = 0 AND v_role_nullable <> 'NO')
           OR v_owned_present NOT IN (0, v_owned_expected) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL blocked: partial hardening DDL state';
        END IF;

        IF v_owned_present = 0 AND v_owned_expected > 0 THEN
            IF v_make_user_not_null = 1 THEN
                SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                    CONCAT(
                        'MODIFY COLUMN `user_id` ', v_original_user_type,
                        ' NOT NULL COMMENT ', QUOTE(v_original_user_comment)
                    ));
            END IF;
            IF v_make_role_not_null = 1 THEN
                SET v_ddl_clauses = CONCAT_WS(', ', NULLIF(v_ddl_clauses, ''),
                    CONCAT(
                        'MODIFY COLUMN `role_id` ', v_original_role_type,
                        ' NOT NULL COMMENT ', QUOTE(v_original_role_comment)
                    ));
            END IF;

            SET @rbac_not_null_ddl = CONCAT(
                'ALTER TABLE `t_user_role_map` ', v_ddl_clauses
            );
            PREPARE rbac_not_null_statement FROM @rbac_not_null_ddl;
            EXECUTE rbac_not_null_statement;
            DEALLOCATE PREPARE rbac_not_null_statement;
        END IF;

        SELECT MAX(CASE WHEN column_name = 'user_id' THEN is_nullable END),
               MAX(CASE WHEN column_name = 'role_id' THEN is_nullable END)
          INTO v_user_nullable, v_role_nullable
          FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 't_user_role_map'
           AND column_name IN ('user_id', 'role_id');

        IF v_user_nullable <> 'NO' OR v_role_nullable <> 'NO' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL failed: post-DDL verification';
        END IF;

        UPDATE t_rbac_user_role_constraint_migration
           SET migration_status = 'NOT_NULL_HARDENED'
         WHERE migration_batch_id = v_batch_id
           AND migration_status = 'NOT_NULL_HARDENING';
        IF ROW_COUNT() <> 1 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'RBAC NOT NULL failed: audit finalization conflict';
        END IF;
        COMMIT;
        SELECT RELEASE_LOCK(v_lock_name) INTO v_release_result;

        SELECT v_batch_id AS migration_batch_id,
               v_make_user_not_null AS made_user_id_not_null,
               v_make_role_not_null AS made_role_id_not_null,
               'NOT_NULL_HARDENED' AS hardening_status;
    END IF;
END$$

DELIMITER ;

CALL `_rbac_harden_user_role_not_null`();
DROP PROCEDURE IF EXISTS `_rbac_harden_user_role_not_null`;
