-- Permanent video-composition request audit.
--
-- Deployment order:
--   1. Run this migration before deploying the backend that writes the table.
--   2. Deploy the backend.
--   3. Deploy the management frontend.
--
-- There is deliberately no foreign key to t_digital_work. Deleting a work
-- must not delete its audit snapshot. Application code exposes no update or
-- delete mapper method for this table.

CREATE TABLE IF NOT EXISTS `t_digital_work_compose_audit` (
    `audit_id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '审计记录ID',
    `work_record_id` BIGINT NOT NULL COMMENT 't_digital_work.digital_work_Id，永久唯一关联键',
    `external_id` VARCHAR(255) NULL COMMENT '供应商任务ID',
    `user_id` BIGINT NOT NULL COMMENT '提交用户ID快照',
    `compose_params` LONGTEXT NOT NULL COMMENT '实际发送给供应商的业务JSON，不含鉴权头',
    `video_time` INT UNSIGNED NULL COMMENT '提交接口时的配额预计时长（分钟），历史数据不可还原时为空',
    `request_sha256` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'compose_params的SHA-256',
    `capture_source` VARCHAR(32) NOT NULL COMMENT 'legacy_body或provider_request',
    `submitted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
    PRIMARY KEY (`audit_id`),
    UNIQUE KEY `uk_compose_audit_work_record` (`work_record_id`),
    KEY `idx_compose_audit_external_id` (`external_id`),
    KEY `idx_compose_audit_user_time` (`user_id`, `submitted_at`),
    CONSTRAINT `chk_compose_audit_params_json` CHECK (JSON_VALID(`compose_params`)),
    CONSTRAINT `chk_compose_audit_source` CHECK (`capture_source` IN ('legacy_body', 'provider_request'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='数字人视频合成业务请求永久审计（只增不改不删）';

-- Production was checked before this migration was authored: all existing
-- t_digital_work.body values were valid JSON. JSON_VALID remains here so the
-- migration is safe in other environments too. INSERT IGNORE makes reruns
-- idempotent without modifying an existing immutable audit record.
INSERT IGNORE INTO `t_digital_work_compose_audit` (
    `work_record_id`,
    `external_id`,
    `user_id`,
    `compose_params`,
    `video_time`,
    `request_sha256`,
    `capture_source`,
    `submitted_at`
)
SELECT
    `digital_work_Id`,
    `external_id`,
    `user_id`,
    `body`,
    NULL,
    SHA2(`body`, 256),
    'legacy_body',
    COALESCE(`create_time`, `update_time`, CURRENT_TIMESTAMP(3))
FROM `t_digital_work`
WHERE `body` IS NOT NULL
  AND JSON_VALID(`body`);

-- Verification queries (read only):
SELECT COUNT(*) AS audit_count FROM `t_digital_work_compose_audit`;
SELECT `capture_source`, COUNT(*) AS source_count
FROM `t_digital_work_compose_audit`
GROUP BY `capture_source`;
SELECT COUNT(*) AS missing_legacy_audits
FROM `t_digital_work` work
LEFT JOIN `t_digital_work_compose_audit` audit
    ON audit.`work_record_id` = work.`digital_work_Id`
WHERE audit.`audit_id` IS NULL;
