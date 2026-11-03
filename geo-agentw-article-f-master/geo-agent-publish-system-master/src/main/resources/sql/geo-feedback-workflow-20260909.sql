-- Additive, repeatable migration. Apply before the new GEO backend starts.
-- Legacy task ownership is intentionally not guessed or backfilled.
DELIMITER $$
DROP PROCEDURE IF EXISTS geo_feedback_add_column$$
CREATE PROCEDURE geo_feedback_add_column(IN table_name_arg VARCHAR(64), IN column_name_arg VARCHAR(64), IN definition_arg VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name=table_name_arg AND column_name=column_name_arg) THEN
    SET @geo_feedback_ddl=CONCAT('ALTER TABLE `',table_name_arg,'` ADD COLUMN `',column_name_arg,'` ',definition_arg);
    PREPARE geo_feedback_statement FROM @geo_feedback_ddl;
    EXECUTE geo_feedback_statement;
    DEALLOCATE PREPARE geo_feedback_statement;
  END IF;
END$$
DELIMITER ;
CALL geo_feedback_add_column('geo_feedback_suggestion','processing_note','VARCHAR(2000) DEFAULT NULL');
CALL geo_feedback_add_column('geo_feedback_suggestion','handled_by','BIGINT DEFAULT NULL');
CALL geo_feedback_add_column('geo_feedback_suggestion','manual_published_url','VARCHAR(2000) DEFAULT NULL');
CALL geo_feedback_add_column('geo_feedback_suggestion','publication_confirmed_by','BIGINT DEFAULT NULL');
CALL geo_feedback_add_column('geo_feedback_suggestion','publication_confirmed_time','DATETIME DEFAULT NULL');
CALL geo_feedback_add_column('geo_feedback_suggestion','active_retest_batch_id','BIGINT DEFAULT NULL');
CALL geo_feedback_add_column('geo_monitor_task','feedback_suggestion_id','BIGINT DEFAULT NULL');
CALL geo_feedback_add_column('geo_monitor_task','retest_batch_id','BIGINT DEFAULT NULL');
DROP PROCEDURE IF EXISTS geo_feedback_add_column;

SET @geo_feedback_ddl=IF(EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
  AND table_name='geo_monitor_task' AND index_name='idx_geo_feedback_retest_batch'), 'SELECT 1',
  'CREATE INDEX idx_geo_feedback_retest_batch ON geo_monitor_task(feedback_suggestion_id,retest_batch_id,status)');
PREPARE geo_feedback_statement FROM @geo_feedback_ddl;
EXECUTE geo_feedback_statement;
DEALLOCATE PREPARE geo_feedback_statement;
-- retest_status: 0 waiting, 1 scheduled, 2 running, 3 all succeeded, 4 finished with failure.
-- publish_status=2 on new records is an operator declaration with URL and actor, not automatic publication.
-- Rollback: deploy the previous application and retain these nullable columns and operator evidence.
