-- Every result in this verification must be PASS before application rollout.

SELECT CASE WHEN COUNT(*) = 8 THEN 'PASS' ELSE 'FAIL' END AS permission_manifest,
       COUNT(*) AS actual_count
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

SELECT role_record.name,
       COUNT(DISTINCT role_permission.permission_id) AS permission_count,
       CASE
         WHEN UPPER(TRIM(role_record.name)) IN ('SUPER_ADMIN', 'SUPERADMIN')
              AND COUNT(DISTINCT role_permission.permission_id) = 8 THEN 'PASS'
         WHEN UPPER(TRIM(role_record.name)) = 'ADMIN'
              AND COUNT(DISTINCT role_permission.permission_id) >= 3
              AND SUM(permission.code IN ('identity.manage', 'audit.read', 'system.manage',
                                           'finance.manage', 'user.manage')) = 0 THEN 'PASS'
         ELSE 'FAIL'
       END AS seed_result
FROM t_role role_record
LEFT JOIN t_role_permission_map role_permission
  ON role_permission.role_id = role_record.role_id
LEFT JOIN t_permission permission
  ON permission.permission_id = role_permission.permission_id
WHERE UPPER(TRIM(role_record.name)) IN ('ADMIN', 'SUPER_ADMIN', 'SUPERADMIN')
GROUP BY role_record.id, role_record.name;

SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS orphan_check,
       COUNT(*) AS orphan_count
FROM t_role_permission_map mapping
LEFT JOIN t_role role_record ON role_record.role_id = mapping.role_id
LEFT JOIN t_permission permission ON permission.permission_id = mapping.permission_id
WHERE role_record.role_id IS NULL OR permission.permission_id IS NULL;

SELECT CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS duplicate_mapping_check,
       COUNT(*) AS duplicate_groups
FROM (
    SELECT role_id, permission_id
    FROM t_role_permission_map
    GROUP BY role_id, permission_id
    HAVING COUNT(*) > 1
) duplicate_mapping;

SELECT migration_id, manifest_sha256, status, applied_at, rolled_back_at
FROM t_capability_rbac_migration
WHERE migration_id = 'CAPABILITY_RBAC_V1';
