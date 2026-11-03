-- Add HY3 as an AI chat model.
--
-- Prerequisite:
--   Configure ai.models.hy3.base-url and ai.models.hy3.api-key before enabling this row.
--   For Tencent Hunyuan OpenAI-compatible API, use:
--     base-url: https://tokenhub.tencentmaas.com/v1
--
-- The service uses:
--   t_ai_model_config.model_code -> Spring strategy bean name, here: hy3
--   t_ai_model_config.name       -> upstream request model name, change as needed.

START TRANSACTION;

SET @hy3_category_biz_id = 9003000000000000000;
SET @hy3_leaf_biz_id = 9003000000000000001;
SET @common_model_biz_id = 1970339278848659457;
SET @hy3_model_code = 'hy3';
SET @hy3_model_name = 'hy3';
SET @hy3_image = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/hy3.png';
SET @common_parent_id = (
    SELECT id
    FROM t_ai_model_config
    WHERE ai_model_config_id = @common_model_biz_id
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id,
    parent_id,
    is_leaf,
    name,
    description,
    type,
    model_code,
    image,
    sort,
    create_time,
    update_time
)
SELECT
    @hy3_category_biz_id,
    @common_parent_id,
    0,
    'HY3',
    'HY3 AI chat model group',
    NULL,
    NULL,
    @hy3_image,
    7,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @hy3_category_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @common_parent_id,
    type = NULL,
    image = @hy3_image,
    sort = 7,
    update_time = NOW()
WHERE ai_model_config_id = @hy3_category_biz_id;

SET @hy3_parent_id = (
    SELECT id
    FROM t_ai_model_config
    WHERE ai_model_config_id = @hy3_category_biz_id
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id,
    parent_id,
    is_leaf,
    name,
    description,
    type,
    model_code,
    image,
    sort,
    create_time,
    update_time
)
SELECT
    @hy3_leaf_biz_id,
    @hy3_parent_id,
    1,
    @hy3_model_name,
    'HY3 AI chat model',
    0,
    @hy3_model_code,
    @hy3_image,
    NULL,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @hy3_leaf_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @hy3_parent_id,
    image = @hy3_image,
    sort = NULL,
    update_time = NOW()
WHERE ai_model_config_id = @hy3_leaf_biz_id;

COMMIT;
