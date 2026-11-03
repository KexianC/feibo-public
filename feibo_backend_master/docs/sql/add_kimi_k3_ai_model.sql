-- Add Kimi K3 as an AI chat model. This migration is idempotent.
-- Runtime prerequisites: KIMI_K3_ENABLED=true and KIMI_K3_API_KEY set securely.
-- Kimi is intentionally type=0 so chats do not consume the platform AI quota.

START TRANSACTION;

SET @kimi_k3_category_biz_id = 9005300000000000000;
SET @kimi_k3_leaf_biz_id = 9005300000000000001;
SET @common_model_biz_id = 1970339278848659457;
SET @kimi_k3_model_code = 'kimik3';
SET @kimi_k3_model_name = 'kimi-k3';
SET @kimi_k3_avatar_url = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/provider-logos-20260825/kimi-k3-v2.png';
SET @common_parent_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @common_model_biz_id
    LIMIT 1
);
SET @kimi_k3_sort = COALESCE((
    SELECT sort + 1 FROM t_ai_model_config
    WHERE ai_model_config_id = 9005200000000000000
    LIMIT 1
), 4);
SET @kimi_k3_already_placed = EXISTS (
    SELECT 1 FROM t_ai_model_config
    WHERE ai_model_config_id = @kimi_k3_category_biz_id
      AND parent_id = @common_parent_id
      AND sort = @kimi_k3_sort
);

-- Make room immediately after GLM exactly once. Re-running the migration keeps
-- the existing order stable.
UPDATE t_ai_model_config
SET sort = sort + 1, update_time = NOW()
WHERE parent_id = @common_parent_id
  AND is_leaf = 0
  AND sort >= @kimi_k3_sort
  AND ai_model_config_id <> @kimi_k3_category_biz_id
  AND @kimi_k3_already_placed = 0;

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, description,
    type, model_code, image, sort, create_time, update_time
)
SELECT
    @kimi_k3_category_biz_id, @common_parent_id, 0, 'Kimi',
    '月之暗面 Kimi 模型', NULL, NULL, @kimi_k3_avatar_url, @kimi_k3_sort, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @kimi_k3_category_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @common_parent_id, is_leaf = 0, name = 'Kimi',
    description = '月之暗面 Kimi 模型', type = NULL, model_code = NULL,
    image = @kimi_k3_avatar_url, sort = @kimi_k3_sort, update_time = NOW()
WHERE ai_model_config_id = @kimi_k3_category_biz_id;

SET @kimi_k3_parent_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @kimi_k3_category_biz_id
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, description,
    type, model_code, image, sort, create_time, update_time
)
SELECT
    @kimi_k3_leaf_biz_id, @kimi_k3_parent_id, 1, @kimi_k3_model_name,
    'Kimi K3 旗舰模型', 0, @kimi_k3_model_code, @kimi_k3_avatar_url, NULL, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @kimi_k3_leaf_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @kimi_k3_parent_id, is_leaf = 1, name = @kimi_k3_model_name,
    description = 'Kimi K3 旗舰模型', type = 0,
    model_code = @kimi_k3_model_code, image = @kimi_k3_avatar_url, sort = NULL, update_time = NOW()
WHERE ai_model_config_id = @kimi_k3_leaf_biz_id;

COMMIT;
