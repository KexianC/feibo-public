-- Apply before deploying feedback processing. Additive and rerunnable on MySQL 8.
-- Existing feedback becomes pending, medium priority, version 0. No existing content is modified.
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='status')=0,'ALTER TABLE t_feedback ADD COLUMN status TINYINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='priority')=0,'ALTER TABLE t_feedback ADD COLUMN priority TINYINT NOT NULL DEFAULT 1','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='reply')=0,'ALTER TABLE t_feedback ADD COLUMN reply TEXT NULL','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='version')=0,'ALTER TABLE t_feedback ADD COLUMN version BIGINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='handler_user_id')=0,'ALTER TABLE t_feedback ADD COLUMN handler_user_id BIGINT NULL','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SET @feedback_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME='update_time')=0,'ALTER TABLE t_feedback ADD COLUMN update_time DATETIME NULL','SELECT 1');
PREPARE feedback_stmt FROM @feedback_ddl; EXECUTE feedback_stmt; DEALLOCATE PREPARE feedback_stmt;
SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_feedback' AND COLUMN_NAME IN ('status','priority','reply','version','handler_user_id','update_time');
