-- Persist a user-facing reason for failed digital-human compositions.
-- Run this migration before deploying the backend that writes failure_reason.

ALTER TABLE `t_digital_work`
    ADD COLUMN `failure_reason` VARCHAR(1000) NULL COMMENT '视频合成失败原因' AFTER `status`;

-- Verification (read only)
SELECT COUNT(*) AS failed_with_reason
FROM `t_digital_work`
WHERE `status` = 0
  AND `failure_reason` IS NOT NULL
  AND `failure_reason` <> '';
