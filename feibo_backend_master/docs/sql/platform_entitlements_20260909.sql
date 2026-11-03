-- Apply once before deploying the entitlement-aware backend (MySQL 8 / InnoDB).
-- Existing accounts obtain their free buckets lazily; this never resets consumed quota.
CREATE TABLE IF NOT EXISTS t_entitlement_policy (
 id TINYINT NOT NULL PRIMARY KEY,
 premium_chat_daily_limit BIGINT NOT NULL,
 digital_human_seconds BIGINT NOT NULL,
 ai_video_seconds BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO t_entitlement_policy VALUES (1,10,300,60,CURRENT_TIMESTAMP(3));

-- A neutral compatibility template satisfies the legacy recharge-pack foreign key.
-- Concrete quantities are exclusively held in t_entitlement_product_config.
INSERT INTO t_ai_feature_template(ai_feature_template_id,template_code,name,description,create_time,update_time)
 SELECT 2090900000000000001,'PLATFORM_ENTITLEMENT','平台会员权益','高级对话与视频秒数由会员权益配置决定',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)
 WHERE NOT EXISTS (SELECT 1 FROM t_ai_feature_template WHERE template_code='PLATFORM_ENTITLEMENT');

CREATE TABLE IF NOT EXISTS t_entitlement_product_config (
 product_type VARCHAR(16) NOT NULL,
 product_id BIGINT NOT NULL,
 duration_days INT NOT NULL DEFAULT -1,
 premium_chat_daily_limit BIGINT NOT NULL DEFAULT 0,
 digital_human_seconds BIGINT NOT NULL DEFAULT 0,
 ai_video_seconds BIGINT NOT NULL DEFAULT 0,
 premium_chat_credits BIGINT NOT NULL DEFAULT 0,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(product_type,product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_entitlement_account (
 user_id BIGINT NOT NULL PRIMARY KEY,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_entitlement_grant (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 source_key VARCHAR(128) NOT NULL,
 product_type VARCHAR(16) NOT NULL,
 product_id BIGINT NULL,
 plan_name VARCHAR(128) NOT NULL,
 premium_chat_daily_limit BIGINT NOT NULL DEFAULT 0,
 digital_human_seconds BIGINT NOT NULL DEFAULT 0,
 ai_video_seconds BIGINT NOT NULL DEFAULT 0,
 premium_chat_credits BIGINT NOT NULL DEFAULT 0,
 starts_at DATETIME(3) NOT NULL,
 expires_at DATETIME(3) NULL,
 reason VARCHAR(500) NOT NULL,
 operator_id BIGINT NULL,
 snapshot LONGTEXT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_entitlement_grant_source (user_id,source_key),
 KEY ix_entitlement_grant_active (user_id,starts_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_entitlement_bucket (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 resource VARCHAR(32) NOT NULL,
 source_key VARCHAR(128) NOT NULL,
 capacity BIGINT NOT NULL,
 used BIGINT NOT NULL DEFAULT 0,
 reserved BIGINT NOT NULL DEFAULT 0,
 starts_at DATETIME(3) NOT NULL,
 expires_at DATETIME(3) NULL,
 UNIQUE KEY uq_entitlement_bucket_source (user_id,resource,source_key),
 KEY ix_entitlement_bucket_spend (user_id,resource,starts_at,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_entitlement_reservation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 biz_key VARCHAR(128) NOT NULL,
 resource VARCHAR(32) NOT NULL,
 units BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 unlimited BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_entitlement_reservation (user_id,biz_key),
 KEY ix_entitlement_reservation_state (status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_entitlement_allocation (
 reservation_id BIGINT NOT NULL,
 bucket_id BIGINT NOT NULL,
 units BIGINT NOT NULL,
 PRIMARY KEY(reservation_id,bucket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The persisted marker protects subsequent administrator model classifications
-- if this otherwise-idempotent schema migration is replayed.
CREATE TABLE IF NOT EXISTS t_entitlement_migration (
 migration_key VARCHAR(100) NOT NULL PRIMARY KEY,
 applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
UPDATE t_ai_model_config SET type = 0 WHERE COALESCE(type,1) <> 0
 AND NOT EXISTS (SELECT 1 FROM t_entitlement_migration WHERE migration_key='20260909_models_ordinary');
INSERT IGNORE INTO t_entitlement_migration(migration_key) VALUES ('20260909_models_ordinary');

-- Old products remain visible to administrators, but cannot be purchased until
-- their explicit new quota configuration is saved in the membership workbench.
-- No guessed conversion of legacy feature values is applied to paid products.
SELECT * FROM t_entitlement_policy WHERE id=1;
SELECT COUNT(*) AS remaining_premium_models FROM t_ai_model_config WHERE type=1;

