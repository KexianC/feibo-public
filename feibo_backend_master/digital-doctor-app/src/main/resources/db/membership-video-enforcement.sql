-- Rerunnable, including recovery after an interrupted DDL operation.
-- Apply after the membership entitlement schema and existing digital-work schema.
SET @video_quota_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='t_digital_work' AND COLUMN_NAME='quota_biz_key'),
    'SELECT 1', 'ALTER TABLE t_digital_work ADD COLUMN quota_biz_key VARCHAR(128) NULL');
PREPARE video_quota_stmt FROM @video_quota_migration;
EXECUTE video_quota_stmt;
DEALLOCATE PREPARE video_quota_stmt;

SET @video_quota_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='t_digital_work' AND COLUMN_NAME='quota_hidden'),
    'SELECT 1', 'ALTER TABLE t_digital_work ADD COLUMN quota_hidden TINYINT(1) NOT NULL DEFAULT 0');
PREPARE video_quota_stmt FROM @video_quota_migration;
EXECUTE video_quota_stmt;
DEALLOCATE PREPARE video_quota_stmt;

SET @video_quota_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='t_digital_work' AND INDEX_NAME='uk_digital_work_quota'),
    'SELECT 1', 'ALTER TABLE t_digital_work ADD UNIQUE KEY uk_digital_work_quota (user_id, quota_biz_key)');
PREPARE video_quota_stmt FROM @video_quota_migration;
EXECUTE video_quota_stmt;
DEALLOCATE PREPARE video_quota_stmt;

-- NULL identifies tasks submitted before membership enforcement; never retroactively charge them.
-- New installations already include this column in video-generation.sql.
SET @video_quota_migration = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
           AND TABLE_NAME='ai_video_task' AND COLUMN_NAME='quota_biz_key'),
    'SELECT 1', 'ALTER TABLE ai_video_task ADD COLUMN quota_biz_key VARCHAR(128) NULL');
PREPARE video_quota_stmt FROM @video_quota_migration;
EXECUTE video_quota_stmt;
DEALLOCATE PREPARE video_quota_stmt;

CREATE TABLE IF NOT EXISTS t_video_quota_reconciliation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    task_ref VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    biz_key VARCHAR(128) NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_video_reconciliation_task(kind,task_ref)
);
