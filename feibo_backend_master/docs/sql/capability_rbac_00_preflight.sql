-- Capability RBAC v1 preflight. This file does not modify business rows.
-- Select the intended schema explicitly before running it.

SELECT DATABASE() AS selected_schema,
       CASE WHEN DATABASE() IS NULL THEN 'FAIL' ELSE 'PASS' END AS schema_selected;

SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_role', 't_user_role_map', 't_permission', 't_role_permission_map')
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable, column_key
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_role' AND column_name IN ('id', 'role_id', 'name'))
       OR (table_name = 't_user_role_map' AND column_name IN ('user_id', 'role_id')))
ORDER BY table_name, ordinal_position;

SELECT UPPER(TRIM(name)) AS canonical_role, COUNT(*) AS role_count
FROM t_role
WHERE UPPER(TRIM(name)) IN ('ADMIN', 'SUPER_ADMIN', 'SUPERADMIN')
GROUP BY UPPER(TRIM(name));

DROP PROCEDURE IF EXISTS capability_rbac_preflight_v1;
DELIMITER $$
CREATE PROCEDURE capability_rbac_preflight_v1()
BEGIN
    DECLARE v_schema VARCHAR(64);
    DECLARE v_role_table INT DEFAULT 0;
    DECLARE v_user_role_table INT DEFAULT 0;
    DECLARE v_role_engine VARCHAR(64);
    DECLARE v_role_id_type VARCHAR(64);
    DECLARE v_role_unique INT DEFAULT 0;
    DECLARE v_admin_roles INT DEFAULT 0;
    DECLARE v_super_roles INT DEFAULT 0;

    SET v_schema = DATABASE();
    IF v_schema IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target schema before capability RBAC migration';
    END IF;

    SELECT COUNT(*) INTO v_role_table
    FROM information_schema.tables
    WHERE table_schema = v_schema AND table_name = 't_role';
    SELECT COUNT(*) INTO v_user_role_table
    FROM information_schema.tables
    WHERE table_schema = v_schema AND table_name = 't_user_role_map';
    IF v_role_table <> 1 OR v_user_role_table <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required RBAC base tables are missing';
    END IF;

    SELECT engine INTO v_role_engine
    FROM information_schema.tables
    WHERE table_schema = v_schema AND table_name = 't_role';
    IF UPPER(v_role_engine) <> 'INNODB' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_role must use InnoDB for locking and foreign keys';
    END IF;

    SELECT column_type INTO v_role_id_type
    FROM information_schema.columns
    WHERE table_schema = v_schema AND table_name = 't_role' AND column_name = 'role_id';
    IF v_role_id_type <> 'bigint' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_role.role_id must be signed BIGINT';
    END IF;

    SELECT COUNT(*) INTO v_role_unique
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = v_schema
          AND table_name = 't_role'
          AND non_unique = 0
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'role_id'
    ) unique_role_id;
    IF v_role_unique = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 't_role.role_id requires a single-column unique key';
    END IF;

    SELECT COUNT(*) INTO v_admin_roles
    FROM t_role WHERE UPPER(TRIM(name)) = 'ADMIN';
    SELECT COUNT(*) INTO v_super_roles
    FROM t_role WHERE UPPER(TRIM(name)) IN ('SUPER_ADMIN', 'SUPERADMIN');
    IF v_admin_roles = 0 OR v_super_roles = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ADMIN and SUPER_ADMIN/SUPERADMIN roles must exist before seeding';
    END IF;

    SELECT 'PASS' AS capability_rbac_preflight;
END$$
DELIMITER ;
CALL capability_rbac_preflight_v1();
DROP PROCEDURE capability_rbac_preflight_v1;
