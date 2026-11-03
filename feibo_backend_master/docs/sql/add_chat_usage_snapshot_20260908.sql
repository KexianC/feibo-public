-- Additive migration. Existing records stay NULL; no historical cost backfill.
-- Rollback application first; retaining this nullable column is safe.
SET @exists_usage = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='t_ai_message' AND column_name='usage_json');
SET @usage_sql = IF(@exists_usage=0,
 'ALTER TABLE t_ai_message ADD COLUMN usage_json JSON NULL COMMENT ''Provider usage and official price snapshot''', 'SELECT 1');
PREPARE usage_stmt FROM @usage_sql;
EXECUTE usage_stmt;
DEALLOCATE PREPARE usage_stmt;
