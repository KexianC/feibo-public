-- Additive, rerunnable capability RBAC v1 seed.

DROP PROCEDURE IF EXISTS capability_rbac_seed_v1;
DELIMITER $$
CREATE PROCEDURE capability_rbac_seed_v1()
BEGIN
    DECLARE v_collision_count INT DEFAULT 0;
    DECLARE v_permission_count INT DEFAULT 0;
    DECLARE v_super_count INT DEFAULT 0;
    DECLARE v_admin_count INT DEFAULT 0;
    DECLARE v_admin_sensitive_count INT DEFAULT 0;
    DECLARE v_manifest CHAR(64);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET v_manifest = SHA2('capability-rbac-v1|admin.console|user.read|user.manage|identity.manage|audit.read|system.manage|finance.manage|content.manage|SUPER=ALL|ADMIN=admin.console,user.read,content.manage', 256);

    SELECT COUNT(*) INTO v_collision_count
    FROM t_permission
    WHERE (permission_id BETWEEN 861000000000000001 AND 861000000000000008
           AND code <> CASE permission_id
               WHEN 861000000000000001 THEN 'admin.console'
               WHEN 861000000000000002 THEN 'user.read'
               WHEN 861000000000000003 THEN 'user.manage'
               WHEN 861000000000000004 THEN 'identity.manage'
               WHEN 861000000000000005 THEN 'audit.read'
               WHEN 861000000000000006 THEN 'system.manage'
               WHEN 861000000000000007 THEN 'finance.manage'
               WHEN 861000000000000008 THEN 'content.manage'
           END)
       OR (code IN ('admin.console', 'user.read', 'user.manage', 'identity.manage',
                    'audit.read', 'system.manage', 'finance.manage', 'content.manage')
           AND permission_id <> CASE code
               WHEN 'admin.console' THEN 861000000000000001
               WHEN 'user.read' THEN 861000000000000002
               WHEN 'user.manage' THEN 861000000000000003
               WHEN 'identity.manage' THEN 861000000000000004
               WHEN 'audit.read' THEN 861000000000000005
               WHEN 'system.manage' THEN 861000000000000006
               WHEN 'finance.manage' THEN 861000000000000007
               WHEN 'content.manage' THEN 861000000000000008
           END);
    IF v_collision_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Capability seed ID/code collision detected';
    END IF;

    SELECT COUNT(*) INTO v_super_count
    FROM t_role WHERE UPPER(TRIM(name)) IN ('SUPER_ADMIN', 'SUPERADMIN');
    SELECT COUNT(*) INTO v_admin_count
    FROM t_role WHERE UPPER(TRIM(name)) = 'ADMIN';
    IF v_super_count = 0 OR v_admin_count = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required protected roles are missing';
    END IF;

    SELECT COUNT(*) INTO v_admin_sensitive_count
    FROM t_role role_record
    INNER JOIN t_role_permission_map mapping ON mapping.role_id = role_record.role_id
    INNER JOIN t_permission permission ON permission.permission_id = mapping.permission_id
    WHERE UPPER(TRIM(role_record.name)) = 'ADMIN'
      AND permission.code IN ('identity.manage', 'audit.read', 'system.manage',
                              'finance.manage', 'user.manage');
    IF v_admin_sensitive_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ADMIN already has a sensitive capability; seed refused';
    END IF;

    START TRANSACTION;

    INSERT INTO t_permission (permission_id, code, name, description, reserved)
    VALUES
        (861000000000000001, 'admin.console', '后台控制台', '登录和使用管理后台基础功能', 1),
        (861000000000000002, 'user.read', '用户只读', '查询用户及用户业务记录', 1),
        (861000000000000003, 'user.manage', '用户维护', '创建、修改和停用用户', 1),
        (861000000000000004, 'identity.manage', '身份权限管理', '管理角色、用户角色和角色权限', 1),
        (861000000000000005, 'audit.read', '审计日志读取', '读取登录和操作审计日志', 1),
        (861000000000000006, 'system.manage', '系统管理', '管理系统级配置', 1),
        (861000000000000007, 'finance.manage', '财务管理', '维护订单、套餐和财务配置', 1),
        (861000000000000008, 'content.manage', '内容管理', '维护内容、素材和审核配置', 1)
    ON DUPLICATE KEY UPDATE
        name = VALUES(name),
        description = VALUES(description),
        reserved = 1;

    INSERT INTO t_role_permission_map (role_id, permission_id)
    SELECT role_record.role_id, permission.permission_id
    FROM t_role role_record
    CROSS JOIN t_permission permission
    LEFT JOIN t_role_permission_map existing_mapping
      ON existing_mapping.role_id = role_record.role_id
     AND existing_mapping.permission_id = permission.permission_id
    WHERE UPPER(TRIM(role_record.name)) IN ('SUPER_ADMIN', 'SUPERADMIN')
      AND permission.code IN ('admin.console', 'user.read', 'user.manage', 'identity.manage',
                              'audit.read', 'system.manage', 'finance.manage', 'content.manage')
      AND existing_mapping.id IS NULL;

    INSERT INTO t_role_permission_map (role_id, permission_id)
    SELECT role_record.role_id, permission.permission_id
    FROM t_role role_record
    INNER JOIN t_permission permission
      ON permission.code IN ('admin.console', 'user.read', 'content.manage')
    LEFT JOIN t_role_permission_map existing_mapping
      ON existing_mapping.role_id = role_record.role_id
     AND existing_mapping.permission_id = permission.permission_id
    WHERE UPPER(TRIM(role_record.name)) = 'ADMIN'
      AND existing_mapping.id IS NULL;

    SELECT COUNT(*) INTO v_permission_count
    FROM t_permission
    WHERE reserved = 1
      AND permission_id BETWEEN 861000000000000001 AND 861000000000000008;
    IF v_permission_count <> 8 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Capability permission seed verification failed';
    END IF;

    INSERT INTO t_capability_rbac_migration
        (migration_id, manifest_sha256, status, applied_at, rolled_back_at)
    VALUES ('CAPABILITY_RBAC_V1', v_manifest, 'APPLIED', NOW(), NULL)
    ON DUPLICATE KEY UPDATE
        manifest_sha256 = VALUES(manifest_sha256),
        status = 'APPLIED',
        applied_at = NOW(),
        rolled_back_at = NULL;

    COMMIT;
END$$
DELIMITER ;
CALL capability_rbac_seed_v1();
DROP PROCEDURE capability_rbac_seed_v1;
