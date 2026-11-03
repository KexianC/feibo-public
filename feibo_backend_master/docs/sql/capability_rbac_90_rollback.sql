-- DESTRUCTIVE rollback for the entire capability RBAC v1 subsystem.
-- MySQL DDL implicitly commits. Back up both tables and review this inventory.
SELECT permission_id, code, name, reserved FROM t_permission ORDER BY permission_id;
SELECT role_id, permission_id, create_time FROM t_role_permission_map ORDER BY role_id, permission_id;

-- Set only after backup and review:
-- SET @capability_rbac_rollback_confirmation = 'DROP_CAPABILITY_RBAC_V1';

DROP PROCEDURE IF EXISTS capability_rbac_rollback_guard_v1;
DELIMITER $$
CREATE PROCEDURE capability_rbac_rollback_guard_v1()
BEGIN
    DECLARE v_permissions INT DEFAULT 0;
    DECLARE v_manifest_permissions INT DEFAULT 0;
    DECLARE v_status VARCHAR(16);
    DECLARE v_manifest_hash CHAR(64);
    DECLARE v_expected_hash CHAR(64);
    DECLARE v_external_foreign_keys INT DEFAULT 0;

    IF COALESCE(@capability_rbac_rollback_confirmation, '') <> 'DROP_CAPABILITY_RBAC_V1' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rollback confirmation is missing';
    END IF;

    SET v_expected_hash = SHA2('capability-rbac-v1|admin.console|user.read|user.manage|identity.manage|audit.read|system.manage|finance.manage|content.manage|SUPER=ALL|ADMIN=admin.console,user.read,content.manage', 256);

    SELECT status, manifest_sha256 INTO v_status, v_manifest_hash
    FROM t_capability_rbac_migration
    WHERE migration_id = 'CAPABILITY_RBAC_V1';
    IF COALESCE(v_status, '') <> 'APPLIED' OR COALESCE(v_manifest_hash, '') <> v_expected_hash THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Capability RBAC v1 applied manifest is missing or mismatched';
    END IF;

    SELECT COUNT(*) INTO v_permissions FROM t_permission;
    SELECT COUNT(*) INTO v_manifest_permissions
    FROM t_permission
    WHERE reserved = 1
      AND code = CASE permission_id
          WHEN 861000000000000001 THEN 'admin.console'
          WHEN 861000000000000002 THEN 'user.read'
          WHEN 861000000000000003 THEN 'user.manage'
          WHEN 861000000000000004 THEN 'identity.manage'
          WHEN 861000000000000005 THEN 'audit.read'
          WHEN 861000000000000006 THEN 'system.manage'
          WHEN 861000000000000007 THEN 'finance.manage'
          WHEN 861000000000000008 THEN 'content.manage'
      END;
    IF v_permissions <> 8 OR v_manifest_permissions <> 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Catalog was extended or changed; automatic v1 rollback refused';
    END IF;

    SELECT COUNT(*) INTO v_external_foreign_keys
    FROM information_schema.key_column_usage
    WHERE referenced_table_schema = DATABASE()
      AND referenced_table_name IN ('t_permission', 't_role_permission_map')
      AND NOT (table_schema = DATABASE()
               AND table_name = 't_role_permission_map'
               AND constraint_name = 'fk_t_role_permission_permission');
    IF v_external_foreign_keys <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'External foreign keys depend on capability tables; rollback refused';
    END IF;
END$$
DELIMITER ;
CALL capability_rbac_rollback_guard_v1();
DROP PROCEDURE capability_rbac_rollback_guard_v1;

UPDATE t_capability_rbac_migration
SET status = 'ROLLING_BACK'
WHERE migration_id = 'CAPABILITY_RBAC_V1';

DROP TABLE t_role_permission_map;
DROP TABLE t_permission;

UPDATE t_capability_rbac_migration
SET status = 'ROLLED_BACK', rolled_back_at = NOW()
WHERE migration_id = 'CAPABILITY_RBAC_V1';

SELECT migration_id, status, applied_at, rolled_back_at
FROM t_capability_rbac_migration
WHERE migration_id = 'CAPABILITY_RBAC_V1';
