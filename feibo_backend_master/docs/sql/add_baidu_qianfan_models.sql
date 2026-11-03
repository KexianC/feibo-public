-- Add Baidu Qianfan as a managed general-model provider.
-- The API key is deliberately excluded and must be encrypted separately.
-- New models stay hidden until the production provider probes succeed.

START TRANSACTION;

SET @general_root_biz_id = 1970339278848659457;
SET @szdoctor_provider_biz_id = 9002500000000000100;
SET @qianfan_provider_biz_id = 9005400000000000000;
SET @qianfan_ernie_51_biz_id = 9005400000000000001;
SET @qianfan_ernie_50_biz_id = 9005400000000000002;
SET @qianfan_ernie_45_turbo_biz_id = 9005400000000000003;
SET @qianfan_avatar_url = 'https://digital-doctor-oss.oss-cn-hangzhou.aliyuncs.com/ai-model-logo/provider-logos-20260825/baidu-qianfan.png';

SET @general_root_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @general_root_biz_id
      AND is_leaf = 0
      AND COALESCE(is_deleted, 0) = 0
    LIMIT 1
);

-- The provider display name changes only; the runtime route remains szdoctor.
UPDATE t_ai_model_config
SET name = '木吾木同Medtalent', description = '木吾木同Medtalent', update_time = NOW()
WHERE ai_model_config_id = @szdoctor_provider_biz_id
  AND parent_id = @general_root_id
  AND is_leaf = 0;

SET @qianfan_provider_sort = COALESCE((
    SELECT MAX(sort) + 1
    FROM t_ai_model_config
    WHERE parent_id = @general_root_id
      AND is_leaf = 0
      AND COALESCE(is_deleted, 0) = 0
      AND ai_model_config_id <> @qianfan_provider_biz_id
), 1);

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, description,
    type, model_code, image, sort, visible, is_deleted,
    create_time, update_time
)
SELECT @qianfan_provider_biz_id, @general_root_id, 0, '百度千帆',
       '百度智能云千帆大模型平台', NULL, NULL, @qianfan_avatar_url,
       @qianfan_provider_sort, 1, 0, NOW(), NOW()
WHERE @general_root_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_ai_model_config
      WHERE ai_model_config_id = @qianfan_provider_biz_id
  );

UPDATE t_ai_model_config
SET parent_id = @general_root_id, is_leaf = 0, name = '百度千帆',
    description = '百度智能云千帆大模型平台', type = NULL,
    model_code = NULL, image = @qianfan_avatar_url, is_deleted = 0, update_time = NOW()
WHERE ai_model_config_id = @qianfan_provider_biz_id;

SET @qianfan_provider_id = (
    SELECT id FROM t_ai_model_config
    WHERE ai_model_config_id = @qianfan_provider_biz_id
      AND parent_id = @general_root_id
      AND is_leaf = 0
    LIMIT 1
);

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, display_name,
    description, display_description, type, model_code, image, sort,
    visible, is_deleted, create_time, update_time
)
SELECT @qianfan_ernie_51_biz_id, @qianfan_provider_id, 1,
       'ernie-5.1', 'ERNIE 5.1', NULL,
       '百度文心旗舰模型，适合复杂推理与通用文本任务。',
       0, 'qianfan', @qianfan_avatar_url, 1, 0, 0, NOW(), NOW()
WHERE @qianfan_provider_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_ai_model_config
      WHERE ai_model_config_id = @qianfan_ernie_51_biz_id
  );

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, display_name,
    description, display_description, type, model_code, image, sort,
    visible, is_deleted, create_time, update_time
)
SELECT @qianfan_ernie_50_biz_id, @qianfan_provider_id, 1,
       'ernie-5.0', 'ERNIE 5.0', NULL,
       '百度文心旗舰模型，兼顾高质量生成与复杂任务处理。',
       0, 'qianfan', @qianfan_avatar_url, 2, 0, 0, NOW(), NOW()
WHERE @qianfan_provider_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_ai_model_config
      WHERE ai_model_config_id = @qianfan_ernie_50_biz_id
  );

INSERT INTO t_ai_model_config (
    ai_model_config_id, parent_id, is_leaf, name, display_name,
    description, display_description, type, model_code, image, sort,
    visible, is_deleted, create_time, update_time
)
SELECT @qianfan_ernie_45_turbo_biz_id, @qianfan_provider_id, 1,
       'ernie-4.5-turbo-128k', 'ERNIE 4.5 Turbo 128K', NULL,
       '百度文心高吞吐长上下文模型，适合快速文本生成。',
       0, 'qianfan', @qianfan_avatar_url, 3, 0, 0, NOW(), NOW()
WHERE @qianfan_provider_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_ai_model_config
      WHERE ai_model_config_id = @qianfan_ernie_45_turbo_biz_id
  );

UPDATE t_ai_model_config
SET parent_id = @qianfan_provider_id, is_leaf = 1,
    name = CASE ai_model_config_id
        WHEN @qianfan_ernie_51_biz_id THEN 'ernie-5.1'
        WHEN @qianfan_ernie_50_biz_id THEN 'ernie-5.0'
        WHEN @qianfan_ernie_45_turbo_biz_id THEN 'ernie-4.5-turbo-128k'
    END,
    display_name = CASE ai_model_config_id
        WHEN @qianfan_ernie_51_biz_id THEN 'ERNIE 5.1'
        WHEN @qianfan_ernie_50_biz_id THEN 'ERNIE 5.0'
        WHEN @qianfan_ernie_45_turbo_biz_id THEN 'ERNIE 4.5 Turbo 128K'
    END,
    description = NULL,
    display_description = CASE ai_model_config_id
        WHEN @qianfan_ernie_51_biz_id THEN '百度文心旗舰模型，适合复杂推理与通用文本任务。'
        WHEN @qianfan_ernie_50_biz_id THEN '百度文心旗舰模型，兼顾高质量生成与复杂任务处理。'
        WHEN @qianfan_ernie_45_turbo_biz_id THEN '百度文心高吞吐长上下文模型，适合快速文本生成。'
    END,
    type = 0, model_code = 'qianfan', image = @qianfan_avatar_url,
    sort = CASE ai_model_config_id
        WHEN @qianfan_ernie_51_biz_id THEN 1
        WHEN @qianfan_ernie_50_biz_id THEN 2
        WHEN @qianfan_ernie_45_turbo_biz_id THEN 3
    END,
    is_deleted = 0, update_time = NOW()
WHERE ai_model_config_id IN (
    @qianfan_ernie_51_biz_id,
    @qianfan_ernie_50_biz_id,
    @qianfan_ernie_45_turbo_biz_id
);

COMMIT;
