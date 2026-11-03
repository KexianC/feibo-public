-- Run this migration before deploying the organization-scoped final-review code.
-- Assignment is account based because the legacy user model has no reliable
-- hospital/organization relation. Each author account may have multiple final reviewers.

CREATE TABLE IF NOT EXISTS `t_copywriting_organization_admin_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  `account_user_id` BIGINT NOT NULL COMMENT 'Author/submitting account user_id',
  `admin_user_id` BIGINT NOT NULL COMMENT 'Assigned organization final-review administrator user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_copywriting_org_admin_account_admin` (`account_user_id`, `admin_user_id`),
  KEY `idx_copywriting_org_admin_admin` (`admin_user_id`, `account_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Organization-scoped copywriting final-review assignment';

-- The application supports a dedicated ORG_ADMIN role. role_id/id are generated
-- by the application, so create this role from the admin role-management page
-- before assigning organization administrators. Existing global ADMIN accounts
-- may also be explicitly assigned during rollout.

-- Preserve valid legacy final-review claims by recording their exact account/admin
-- assignment. This avoids stranding an already claimed task when strict assignment
-- checks are enabled. INSERT IGNORE makes the backfill safe to rerun without
-- broadening unclaimed accounts.
INSERT IGNORE INTO t_copywriting_organization_admin_assignment
    (account_user_id, admin_user_id, create_time, update_time)
SELECT copywriting.user_id,
       flow.current_processor_id,
       NOW(),
       NOW()
FROM t_copywriting_approval_flow flow
INNER JOIN t_copywriting copywriting
  ON copywriting.copywriting_id = flow.copywriting_id
INNER JOIN t_user admin_user
  ON admin_user.user_id = flow.current_processor_id
WHERE flow.current_status = 3
  AND flow.current_processor_id IS NOT NULL
  AND COALESCE(admin_user.status, 0) = 0
  AND EXISTS (
      SELECT 1
      FROM t_user_role_map role_map
      INNER JOIN t_role role ON role.role_id = role_map.role_id
      WHERE role_map.user_id = flow.current_processor_id
        AND UPPER(role.name) IN ('ORG_ADMIN', 'ADMIN')
  );

-- Release stale claims held by inactive users or users that no longer have an
-- ORG_ADMIN/ADMIN role. This statement is safe to rerun.
UPDATE t_copywriting_approval_flow flow
LEFT JOIN t_user admin_user
  ON admin_user.user_id = flow.current_processor_id
SET flow.current_processor_id = NULL,
    flow.update_time = NOW()
WHERE flow.current_status = 3
  AND flow.current_processor_id IS NOT NULL
  AND (
    admin_user.user_id IS NULL
    OR COALESCE(admin_user.status, 0) <> 0
    OR NOT EXISTS (
      SELECT 1
      FROM t_user_role_map role_map
      INNER JOIN t_role role ON role.role_id = role_map.role_id
      WHERE role_map.user_id = flow.current_processor_id
        AND UPPER(role.name) IN ('ORG_ADMIN', 'ADMIN')
    )
  );
