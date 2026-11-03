CREATE TABLE IF NOT EXISTS t_copywriting_expert_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_user_id BIGINT NOT NULL,
    expert_user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_copywriting_account_expert (account_user_id, expert_user_id),
    KEY idx_copywriting_expert_account (expert_user_id, account_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Review experts assigned to each copywriting account';

-- Release legacy claims held by disabled, deleted, or no-longer-EXPERT users.
-- They remain unclaimed until an administrator configures the account's experts.
UPDATE t_copywriting_approval_flow flow
LEFT JOIN t_user expert_user
    ON expert_user.user_id = flow.current_processor_id
SET flow.current_processor_id = NULL,
    flow.update_time = NOW()
WHERE flow.current_status = 1
  AND flow.current_processor_id IS NOT NULL
  AND (
      expert_user.user_id IS NULL
      OR COALESCE(expert_user.status, 0) <> 0
      OR NOT EXISTS (
          SELECT 1
          FROM t_user_role_map expert_role_map
          INNER JOIN t_role expert_role
              ON expert_role.role_id = expert_role_map.role_id
          WHERE expert_role_map.user_id = flow.current_processor_id
            AND UPPER(expert_role.name) = 'EXPERT'
      )
  );

-- Existing unclaimed expert-stage tasks are intentionally not auto-assigned.
-- Configure experts per account in the admin console before processing them.
-- Preserve already-claimed expert reviews during rollout.
INSERT IGNORE INTO t_copywriting_expert_assignment
    (account_user_id, expert_user_id, create_time, update_time)
SELECT copywriting.user_id,
       flow.current_processor_id,
       NOW(),
       NOW()
FROM t_copywriting_approval_flow flow
INNER JOIN t_copywriting copywriting ON copywriting.copywriting_id = flow.copywriting_id
INNER JOIN t_user_role_map user_role ON user_role.user_id = flow.current_processor_id
INNER JOIN t_role role ON role.role_id = user_role.role_id
WHERE flow.current_status = 1
  AND flow.current_processor_id IS NOT NULL
  AND UPPER(role.name) = 'EXPERT'
  AND copywriting.user_id IS NOT NULL;
