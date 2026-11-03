-- Run AFTER platform_entitlements_20260909.sql while ALL old backend workers,
-- chat/video submissions, callbacks and payment schedulers are stopped.
-- Never run this with old and new versions concurrently accepting spending.
-- Capture a database backup first. Existing rows are retained unchanged.
-- New DATETIME quota timestamps use UTC regardless of the legacy session zone.
-- Before CALL, set @legacy_entitlement_timezone to the verified legacy stored
-- time offset (for example '+08:00'). For TIMESTAMP source columns this must
-- match the migration connection's session timezone; for DATETIME it must
-- match the historical application's stored wall time. No default is guessed.
-- Also set @legacy_digital_seconds_per_unit to 1 (stored seconds) or 60
-- (stored minutes), verified against actual sold packages and receipts. The old
-- code debits videoTime minutes, but an old pack example describes 100 minutes
-- with value=6000; that inconsistency forbids guessing a conversion factor.
-- Release-only reviewed exception: @legacy_preserve_reviewed_unspendable_20260909=1
-- preserves exactly the three documented NULL-expiry batches below, without
-- importing/converting/restoring them. Production baseline spending SQL requires
-- type=0 AND expiry_time>NOW(), so these rows are currently unspendable. Their
-- original source is unresolved; this does not identify them as free or paid.
-- Evidence: release/membership-20260909/backend-baseline-legacy-null-expiry.json
-- and membership-readonly-audit.json. Leave the flag unset in other deployments.

-- PREVIEW: compare these rows/totals to the deployment backup before CALL.
SELECT r.ai_feature_template_id,t.template_code,t.name,d.type,
 COUNT(*) AS batches,SUM(d.balance) AS legacy_balance,
 CASE WHEN r.ai_feature_template_id=1960611825570013185 AND d.type=0 AND d.balance=-1 AND d.expiry_time IS NULL THEN 'ordinary unlimited sentinel -> retained unchanged'
 WHEN r.ai_feature_template_id=1960611825570013186 AND d.type=0 AND d.expiry_time IS NULL THEN 'unspendable NULL expiry -> BLOCKED unless exact reviewed exception'
 WHEN r.ai_feature_template_id=1960611825570013186 THEN CONCAT('verified digital unit -> seconds x ',COALESCE(@legacy_digital_seconds_per_unit,'UNSET'))
 WHEN r.ai_feature_template_id=1960611825570013187 THEN 'advanced chat -> requests x 1' ELSE 'UNMAPPED - BLOCKED' END AS conversion
FROM t_user_ai_resource_detail d
JOIN t_user_ai_resource r ON r.user_ai_resource_id=d.user_ai_resource_id
LEFT JOIN t_ai_feature_template t ON t.ai_feature_template_id=r.ai_feature_template_id
WHERE d.balance<>0 AND (d.type=1 OR d.expiry_time IS NULL OR CONVERT_TZ(d.expiry_time,@legacy_entitlement_timezone,'+00:00')>UTC_TIMESTAMP(3))
GROUP BY r.ai_feature_template_id,t.template_code,t.name,d.type,d.balance,d.expiry_time;

-- Codes above are the exact existing resource IDs in UserAIResourceMapper.xml.
-- Old DigitalRepository debits videoTime in MINUTES, while the old recharge
-- metadata has inconsistent units. The operator must reconcile actual data.
-- Advanced AI deducts one per turn.
-- Other resource templates deliberately require a separate reviewed mapping.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_platform_entitlement_balances$$
CREATE PROCEDURE migrate_platform_entitlement_balances()
BEGIN
 DECLARE existing_marker INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 SELECT COUNT(*) INTO existing_marker FROM t_entitlement_migration
 WHERE migration_key='20260909_legacy_balances';
 IF existing_marker=0 THEN
  IF @legacy_entitlement_timezone IS NULL OR CONVERT_TZ('2026-09-09 00:00:00',@legacy_entitlement_timezone,'+00:00') IS NULL THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Set verified @legacy_entitlement_timezone before migrating legacy balances';
  END IF;
  IF @legacy_digital_seconds_per_unit IS NULL OR @legacy_digital_seconds_per_unit NOT IN (1,60) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Set verified @legacy_digital_seconds_per_unit (1 or 60) before migration';
  END IF;
  -- MySQL renders TIMESTAMP columns in the session timezone before SELECT.
  -- A mismatched source offset would double-shift expiry, so refuse it.
  IF EXISTS (
   SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type='timestamp'
    AND ((table_name='t_user_ai_resource_detail' AND column_name='expiry_time')
     OR (table_name='t_user_ai_subscription' AND column_name IN ('start_time','end_time')))
  ) AND TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),CURRENT_TIMESTAMP())<>
   TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),CONVERT_TZ(UTC_TIMESTAMP(),'+00:00',@legacy_entitlement_timezone)) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TIMESTAMP legacy source requires session timezone matching @legacy_entitlement_timezone';
  END IF;
  IF EXISTS (
   SELECT 1 FROM t_user_ai_resource_detail d
   LEFT JOIN t_user_ai_resource r ON r.user_ai_resource_id=d.user_ai_resource_id
   LEFT JOIN t_user u ON u.user_id=r.user_id
   WHERE d.balance<>0 AND (d.type=1 OR d.expiry_time IS NULL OR CONVERT_TZ(d.expiry_time,@legacy_entitlement_timezone,'+00:00')>UTC_TIMESTAMP(3))
    -- The verified ordinary-chat unlimited sentinel is retained unchanged.
    -- Ordinary chat is unlimited for every account in the new system, so this
    -- exact representation is neither a debt nor a consumable quota to import.
    AND NOT ((r.ai_feature_template_id <=> 1960611825570013185) AND (d.type <=> 0)
     AND (d.balance <=> -1) AND d.expiry_time IS NULL AND d.user_ai_resource_detail_id IS NOT NULL AND u.user_id IS NOT NULL)
    -- Preserve the exact reviewed, currently unspendable historical rows only.
    -- Any changed owner, amount, type, expiry, resource or business ID still blocks.
    AND NOT ((@legacy_preserve_reviewed_unspendable_20260909 <=> 1)
     AND (r.ai_feature_template_id <=> 1960611825570013186) AND (d.type <=> 0)
     AND (d.balance <=> 60) AND d.expiry_time IS NULL AND u.user_id IS NOT NULL
     AND (((d.user_ai_resource_detail_id <=> 1976196393937924098) AND (r.user_id <=> 1976196392964845570))
      OR ((d.user_ai_resource_detail_id <=> 1976200871164506114) AND (r.user_id <=> 1976200871068037122))
      OR ((d.user_ai_resource_detail_id <=> 1979456037116424194) AND (r.user_id <=> 1979456036931874818))))
    AND (d.balance<0 OR d.type IS NULL OR d.type NOT IN (0,1) OR d.user_ai_resource_detail_id IS NULL OR (d.type=0 AND d.expiry_time IS NULL)
     OR u.user_id IS NULL OR r.ai_feature_template_id IS NULL OR r.ai_feature_template_id NOT IN (1960611825570013186,1960611825570013187))
  ) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Legacy balances need review: negative, orphan, or unmapped resources; migration aborted';
  END IF;

  START TRANSACTION;
  -- Each batch imports exactly once. Purchase balances were historically
  -- perpetual, while subscriptions retain their original expiry.
  INSERT INTO t_entitlement_bucket(user_id,resource,source_key,capacity,used,reserved,starts_at,expires_at)
  SELECT r.user_id,
   CASE r.ai_feature_template_id WHEN 1960611825570013186 THEN 'DIGITAL_HUMAN_SECONDS' ELSE 'PREMIUM_CHAT' END,
   CONCAT('legacy:detail:',d.user_ai_resource_detail_id),
   CAST(d.balance AS SIGNED)*CASE r.ai_feature_template_id WHEN 1960611825570013186 THEN @legacy_digital_seconds_per_unit ELSE 1 END,
   0,0,UTC_TIMESTAMP(3),
   CASE WHEN d.type=1 THEN NULL ELSE CONVERT_TZ(d.expiry_time,@legacy_entitlement_timezone,'+00:00') END
  FROM t_user_ai_resource_detail d
  JOIN t_user_ai_resource r ON r.user_ai_resource_id=d.user_ai_resource_id
  WHERE d.balance>0 AND (d.type=1 OR CONVERT_TZ(d.expiry_time,@legacy_entitlement_timezone,'+00:00')>UTC_TIMESTAMP(3))
   AND r.ai_feature_template_id IN (1960611825570013186,1960611825570013187)
   AND NOT EXISTS (SELECT 1 FROM t_entitlement_bucket b WHERE b.user_id=r.user_id
    AND b.source_key=CONCAT('legacy:detail:',d.user_ai_resource_detail_id));

  -- Preserve active paid member labels and renewal dates. Legacy consumable
  -- balances above remain the actual entitlement, with the new free daily floor.
  INSERT INTO t_entitlement_grant(user_id,source_key,product_type,product_id,plan_name,
   premium_chat_daily_limit,digital_human_seconds,ai_video_seconds,premium_chat_credits,
   starts_at,expires_at,reason,operator_id,snapshot,created_at)
  SELECT s.user_id,CONCAT('legacy:subscription:',s.user_ai_subscription_id),'MEMBERSHIP',
   s.ai_membership_plan_id,p.name,0,0,0,0,
   CONVERT_TZ(s.start_time,@legacy_entitlement_timezone,'+00:00'),
   CASE WHEN YEAR(s.end_time)>=9999 THEN NULL ELSE CONVERT_TZ(s.end_time,@legacy_entitlement_timezone,'+00:00') END,
   '历史会员迁移，剩余额度按原批次保留',NULL,
   JSON_OBJECT('version',1,'legacySubscriptionId',CAST(s.user_ai_subscription_id AS CHAR),'legacyOrderId',CAST(s.order_id AS CHAR)),UTC_TIMESTAMP(3)
  FROM t_user_ai_subscription s
  JOIN t_ai_membership_plan p ON p.ai_membership_plan_id=s.ai_membership_plan_id
  WHERE CONVERT_TZ(s.end_time,@legacy_entitlement_timezone,'+00:00')>UTC_TIMESTAMP(3) AND p.plan_code<>'PLAN_FREE' AND p.price>0
   AND NOT EXISTS (SELECT 1 FROM t_entitlement_grant g WHERE g.user_id=s.user_id
    AND g.source_key=CONCAT('legacy:subscription:',s.user_ai_subscription_id));
  INSERT INTO t_entitlement_migration(migration_key) VALUES ('20260909_legacy_balances');
  COMMIT;
 END IF;
END$$
DELIMITER ;
CALL migrate_platform_entitlement_balances();
DROP PROCEDURE migrate_platform_entitlement_balances;

-- Verification: each imported resource total must equal preview balance times
-- its documented conversion. Record these results with the release evidence.
SELECT resource,COUNT(*) AS batches,SUM(capacity) AS imported_units
FROM t_entitlement_bucket WHERE source_key LIKE 'legacy:detail:%' GROUP BY resource;
SELECT COUNT(*) AS imported_memberships FROM t_entitlement_grant WHERE source_key LIKE 'legacy:subscription:%';
SELECT COUNT(*) AS preserved_ordinary_unlimited_sentinels
FROM t_user_ai_resource_detail d JOIN t_user_ai_resource r ON r.user_ai_resource_id=d.user_ai_resource_id
JOIN t_user u ON u.user_id=r.user_id
WHERE r.ai_feature_template_id=1960611825570013185 AND d.type=0 AND d.balance=-1
 AND d.expiry_time IS NULL AND d.user_ai_resource_detail_id IS NOT NULL;
SELECT COUNT(*) AS preserved_reviewed_unspendable_batches,COALESCE(SUM(d.balance),0) AS preserved_raw_units
FROM t_user_ai_resource_detail d JOIN t_user_ai_resource r ON r.user_ai_resource_id=d.user_ai_resource_id
JOIN t_user u ON u.user_id=r.user_id
WHERE r.ai_feature_template_id=1960611825570013186 AND d.type=0 AND d.balance=60 AND d.expiry_time IS NULL
 AND ((d.user_ai_resource_detail_id=1976196393937924098 AND r.user_id=1976196392964845570)
  OR (d.user_ai_resource_detail_id=1976200871164506114 AND r.user_id=1976200871068037122)
  OR (d.user_ai_resource_detail_id=1979456037116424194 AND r.user_id=1979456036931874818));
