-- Capability RBAC v1 schema. Run only after the preflight returns PASS.
-- MySQL DDL implicitly commits; this script is deliberately rerunnable.

CREATE TABLE IF NOT EXISTS t_capability_rbac_migration (
    migration_id VARCHAR(64) NOT NULL,
    manifest_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    applied_at DATETIME NULL,
    rolled_back_at DATETIME NULL,
    PRIMARY KEY (migration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS t_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    permission_id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    reserved TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_permission_permission_id (permission_id),
    UNIQUE KEY uk_t_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS t_role_permission_map (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_role_permission (role_id, permission_id),
    KEY idx_t_role_permission_permission (permission_id),
    CONSTRAINT fk_t_role_permission_role
        FOREIGN KEY (role_id) REFERENCES t_role (role_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_t_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES t_permission (permission_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS capability_rbac_verify_schema_v1;
DELIMITER $$
CREATE PROCEDURE capability_rbac_verify_schema_v1()
BEGIN
    DECLARE v_tables INT DEFAULT 0;
    DECLARE v_permission_uniques INT DEFAULT 0;
    DECLARE v_map_unique INT DEFAULT 0;
    DECLARE v_foreign_keys INT DEFAULT 0;
    DECLARE v_nullable_keys INT DEFAULT 0;

    SELECT COUNT(*) INTO v_tables
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('t_permission', 't_role_permission_map')
      AND UPPER(engine) = 'INNODB';

    SELECT COUNT(*) INTO v_permission_uniques
    FROM (
        SELECT index_name,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_order
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_permission'
          AND non_unique = 0
          AND index_name IN ('uk_t_permission_permission_id', 'uk_t_permission_code')
        GROUP BY index_name
        HAVING (index_name = 'uk_t_permission_permission_id' AND columns_in_order = 'permission_id')
            OR (index_name = 'uk_t_permission_code' AND columns_in_order = 'code')
    ) exact_permission_uniques;

    SELECT COUNT(*) INTO v_map_unique
    FROM (
        SELECT index_name,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_order
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 't_role_permission_map'
          AND non_unique = 0
          AND index_name = 'uk_t_role_permission'
        GROUP BY index_name
        HAVING columns_in_order = 'role_id,permission_id'
    ) exact_map_unique;

    SELECT COUNT(*) INTO v_foreign_keys
    FROM information_schema.key_column_usage key_usage
    INNER JOIN information_schema.referential_constraints reference_rule
       ON reference_rule.constraint_schema = key_usage.constraint_schema
      AND reference_rule.table_name = key_usage.table_name
      AND reference_rule.constraint_name = key_usage.constraint_name
    WHERE key_usage.constraint_schema = DATABASE()
      AND key_usage.table_name = 't_role_permission_map'
      AND reference_rule.update_rule = 'RESTRICT'
      AND reference_rule.delete_rule = 'RESTRICT'
      AND ((key_usage.constraint_name = 'fk_t_role_permission_role'
            AND key_usage.column_name = 'role_id'
            AND key_usage.referenced_table_name = 't_role'
            AND key_usage.referenced_column_name = 'role_id')
        OR (key_usage.constraint_name = 'fk_t_role_permission_permission'
            AND key_usage.column_name = 'permission_id'
            AND key_usage.referenced_table_name = 't_permission'
            AND key_usage.referenced_column_name = 'permission_id'));

    SELECT COUNT(*) INTO v_nullable_keys
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND ((table_name = 't_permission' AND column_name IN ('permission_id', 'code'))
        OR (table_name = 't_role_permission_map' AND column_name IN ('role_id', 'permission_id')))
      AND is_nullable <> 'NO';

    IF v_tables <> 2 OR v_permission_uniques <> 2 OR v_map_unique <> 1
       OR v_foreign_keys <> 2 OR v_nullable_keys <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Capability RBAC schema fingerprint does not match v1';
    END IF;
    SELECT 'PASS' AS capability_rbac_schema;
END$$
DELIMITER ;
CALL capability_rbac_verify_schema_v1();
DROP PROCEDURE capability_rbac_verify_schema_v1;
