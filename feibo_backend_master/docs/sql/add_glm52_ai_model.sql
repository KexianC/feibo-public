-- Add GLM-5.2 as an AI chat model. This migration is idempotent.
-- Runtime prerequisites: GLM52_ENABLED=true and ZHIPU_API_KEY set securely.

START TRANSACTION;

SET @glm52_category_biz_id = 9005200000000000000;
SET @glm52_leaf_biz_id = 9005200000000000001;
SET @common_model_biz_id = 1970339278848659457;
SET @glm52_model_code = 'glm52';
SET @glm52_model_name = 'glm-5.2';
SET @glm52_image = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/glm-5.2.png';
SET @common_parent_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @common_model_biz_id
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, description,
    type, model_code, image, sort, create_time, update_time
)
SELECT
    @glm52_category_biz_id, @common_parent_id, 0, 'GLM',
    '智谱 GLM 模型', NULL, NULL, @glm52_image, 8, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @glm52_category_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @common_parent_id, name = 'GLM', description = '智谱 GLM 模型',
    type = NULL, model_code = NULL, image = @glm52_image, sort = 8, update_time = NOW()
WHERE ai_model_config_id = @glm52_category_biz_id;

SET @glm52_parent_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @glm52_category_biz_id
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, description,
    type, model_code, image, sort, create_time, update_time
)
SELECT
    @glm52_leaf_biz_id, @glm52_parent_id, 1, @glm52_model_name,
    '智谱 GLM-5.2 旗舰模型', 0, @glm52_model_code, @glm52_image, NULL, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM t_ai_model_config WHERE ai_model_config_id = @glm52_leaf_biz_id
);

UPDATE t_ai_model_config
SET parent_id = @glm52_parent_id, is_leaf = 1, name = @glm52_model_name,
    description = '智谱 GLM-5.2 旗舰模型', type = 0,
    model_code = @glm52_model_code, image = @glm52_image, sort = NULL, update_time = NOW()
WHERE ai_model_config_id = @glm52_leaf_biz_id;

COMMIT;
