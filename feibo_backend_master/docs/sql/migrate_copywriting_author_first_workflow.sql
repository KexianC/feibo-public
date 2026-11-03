-- One-time, idempotent data migration for the author-first copywriting workflow.
-- Run while the application is stopped, before deploying code that uses:
-- author confirmation (0) -> AI review (2) -> expert review (1) -> organization final review (3).
--
-- Status codes were reused, so current_status alone cannot distinguish legacy and
-- new tasks. Every conversion below also checks successful transition history.

START TRANSACTION;

-- Legacy status 1 was the expert stage before AI (0 -> 1). Move it to AI unless
-- history proves that it is a new expert stage (2 -> 1) or that it was deliberately
-- reopened from a legacy organization stage by this migration (2 -> 3).
UPDATE t_copywriting_approval_flow flow
SET flow.current_status = 2,
    flow.current_processor_id = NULL,
    flow.update_time = NOW()
WHERE flow.current_status = 1
  AND EXISTS (
      SELECT 1
      FROM t_copywriting copywriting
      WHERE copywriting.copywriting_id = flow.copywriting_id
  )
  AND EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 1
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 1
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 3
        AND history.operation_status = 1
  );

-- A status-1 task without any recognized entry transition cannot safely be treated
-- as either old or new expert review. Return it to its author and replay the full
-- workflow. Excluding 2 -> 3 keeps this statement idempotent after the status-3
-- conversion below.
UPDATE t_copywriting_approval_flow flow
INNER JOIN t_copywriting copywriting
        ON copywriting.copywriting_id = flow.copywriting_id
SET flow.current_status = 0,
    flow.current_processor_id = copywriting.user_id,
    flow.update_time = NOW()
WHERE flow.current_status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 1
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 1
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 3
        AND history.operation_status = 1
  );

-- Former direct-to-AI submissions have status 2 without any known successful
-- transition into that status. Return only those records to the author. Legacy
-- expert-approved tasks (1 -> 2) stay at status 2 and are picked up immediately by
-- the AI dispatcher; new author-confirmed tasks (0 -> 2) are also left untouched.
UPDATE t_copywriting_approval_flow flow
INNER JOIN t_copywriting copywriting
        ON copywriting.copywriting_id = flow.copywriting_id
SET flow.current_status = 0,
    flow.current_processor_id = copywriting.user_id,
    flow.update_time = NOW()
WHERE flow.current_status = 2
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 2
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 1
        AND history.to_status = 2
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 1
        AND history.operation_status = 1
  );

-- The immediately preceding rollout used AI -> author -> expert -> final review
-- (2 -> 0 -> 1 -> 3). For still-active final-review tasks, rerun AI after the
-- author and then rerun the expert stage so the requested order is guaranteed.
-- New flows contain 0 -> 2 / 2 -> 1 and are deliberately excluded.
UPDATE t_copywriting_approval_flow flow
SET flow.current_status = 2,
    flow.current_processor_id = NULL,
    flow.update_time = NOW()
WHERE flow.current_status = 3
  AND EXISTS (
      SELECT 1
      FROM t_copywriting copywriting
      WHERE copywriting.copywriting_id = flow.copywriting_id
  )
  AND EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 0
        AND history.operation_status = 1
  )
  AND EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 1
        AND history.operation_status = 1
  )
  AND EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 1
        AND history.to_status = 3
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 0
        AND history.to_status = 2
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 1
        AND history.operation_status = 1
  );

-- The original legacy status 3 was reached directly from AI (2 -> 3), so it has
-- not completed the newly ordered post-AI expert review. Reopen it in the expert
-- pool. New flows have a successful 1 -> 3 transition and are preserved.
UPDATE t_copywriting_approval_flow flow
SET flow.current_status = 1,
    flow.current_processor_id = NULL,
    flow.update_time = NOW()
WHERE flow.current_status = 3
  AND EXISTS (
      SELECT 1
      FROM t_copywriting copywriting
      WHERE copywriting.copywriting_id = flow.copywriting_id
  )
  AND EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 2
        AND history.to_status = 3
        AND history.operation_status = 1
  )
  AND NOT EXISTS (
      SELECT 1
      FROM t_copywriting_approval_history history
      WHERE history.copywriting_approval_flow_id = flow.copywriting_approval_flow_id
        AND history.from_status = 1
        AND history.to_status = 3
        AND history.operation_status = 1
  );

-- Status 0 maps naturally to author confirmation. Repair its processor so only the
-- owning author can confirm or withdraw it.
UPDATE t_copywriting_approval_flow flow
INNER JOIN t_copywriting copywriting
        ON copywriting.copywriting_id = flow.copywriting_id
SET flow.current_processor_id = copywriting.user_id,
    flow.update_time = NOW()
WHERE flow.current_status = 0
  AND (flow.current_processor_id IS NULL
       OR flow.current_processor_id <> copywriting.user_id);

COMMIT;
