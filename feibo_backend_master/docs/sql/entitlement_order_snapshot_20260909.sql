-- Apply once after backing up t_order, before deploying the entitlement release.
-- MySQL 8. Idempotent so re-running the release cannot overwrite paid terms.
SET @snapshot_column_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_order' AND column_name = 'entitlement_snapshot');
SET @snapshot_ddl = IF(@snapshot_column_exists = 0,
  'ALTER TABLE t_order ADD COLUMN entitlement_snapshot MEDIUMTEXT NULL COMMENT ''Immutable purchased entitlement terms''',
  'SELECT 1');
PREPARE snapshot_stmt FROM @snapshot_ddl;
EXECUTE snapshot_stmt;
DEALLOCATE PREPARE snapshot_stmt;

-- Pre-existing unpaid/paid-unfulfilled orders must be reconciled before release.
-- Do not populate their snapshots from a changed current product catalogue.
SELECT order_id, user_id, product_id, product_type, total_amount, status
FROM t_order WHERE status IN (0,1,2) AND entitlement_snapshot IS NULL;
