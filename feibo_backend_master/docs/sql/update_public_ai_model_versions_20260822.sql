-- 智笔医言公共供应商模型版本刷新（2026-08-22）。
-- 目标数据库：生产 MySQL 8.0.36；无 DDL；幂等；
-- 任一校验失败均在 COMMIT 前中止。
--
-- 本脚本只修改已经供应商官方 /models 和官方文档双重确认的
-- runtime name，以及 Qwen 五个既有版本的 visible/sort。display_name
-- 和版本耦合 description 仅在精确等于已盘点旧值时更新，不覆盖自定义文案。
-- 不修改 display_description、model_code 或任何 API Key。
-- DeepSeek V4、HY3、Kimi K3 和私有供应商不在本迁移中。
--
-- 前置：模型逻辑删除 schema 迁移已创建 is_deleted；本脚本只允许
-- 更新 8 个 is_deleted=0 的目标行，任一目标已逻辑删除则前置校验失败。
-- 执行要求：停止应用写入；使用未开启 --force 的 mysql 客户端。
-- 回滚：rollback_public_ai_model_versions_20260822.sql。

SET @general_model_root_business_id = 1970339278848659457;

START TRANSACTION;

-- 与管理端排序共用根锁，再锁定这 8 个叶子模型。
SELECT root.id AS locked_general_model_root_id
FROM t_ai_model_config AS root
WHERE root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id
FOR UPDATE;

SELECT leaf.id AS locked_model_row_id,
       leaf.ai_model_config_id AS model_business_id,
       leaf.name AS current_runtime_name,
       leaf.visible AS current_visible,
       leaf.sort AS current_sort
FROM t_ai_model_config AS leaf
WHERE leaf.ai_model_config_id IN (
    9005200000000000001,
    1970339281184886785,
    1970339281478488066,
    2026505489033986048,
    2026505489033986049,
    1970339281906307073,
    1970339281788866562,
    1970687514398605378
)
  AND leaf.is_deleted = 0
ORDER BY leaf.ai_model_config_id
FOR UPDATE;

SELECT COUNT(*) INTO @general_root_count
FROM t_ai_model_config AS root
WHERE root.parent_id = 0
  AND root.ai_model_config_id = @general_model_root_business_id;

-- 全表中目标业务 ID 必须各有且只有一行。
SELECT COUNT(*), COUNT(DISTINCT ai_model_config_id),
       SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END)
INTO @target_business_row_count, @target_business_id_count,
     @active_target_business_row_count
FROM t_ai_model_config
WHERE ai_model_config_id IN (
    9005200000000000001,
    1970339281184886785,
    1970339281478488066,
    2026505489033986048,
    2026505489033986049,
    1970339281906307073,
    1970339281788866562,
    1970687514398605378
);

-- 每一行必须是通用模型根下、预期供应商的叶子，model_code 不得漂移；
-- runtime name 只接受迁移前或迁移后的确切值。
SELECT COUNT(*) INTO @valid_scoped_target_count
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider
  ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS root
  ON root.id = provider.parent_id
WHERE leaf.is_leaf = TRUE
  AND leaf.is_deleted = 0
  AND provider.is_leaf = FALSE
  AND provider.is_deleted = 0
  AND root.parent_id = 0
  AND root.is_deleted = 0
  AND root.ai_model_config_id = @general_model_root_business_id
  AND (
      (leaf.ai_model_config_id = 9005200000000000001
       AND provider.ai_model_config_id = 9005200000000000000
       AND BINARY leaf.model_code = BINARY 'glm52'
       AND BINARY leaf.name IN (BINARY 'glm-5.2', BINARY 'glm-5.3'))
      OR
      (leaf.ai_model_config_id = 1970339281184886785
       AND provider.ai_model_config_id = 1970339280589295618
       AND BINARY leaf.model_code = BINARY 'doubao'
       AND BINARY leaf.name IN (
           BINARY 'doubao-seed-1.6-250615',
           BINARY 'doubao-seed-2-1-pro-260628'))
      OR
      (leaf.ai_model_config_id = 1970339281478488066
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3-max-preview', BINARY 'qwen3.8-max'))
      OR
      (leaf.ai_model_config_id = 2026505489033986048
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3.5-plus', BINARY 'qwen3.7-plus'))
      OR
      (leaf.ai_model_config_id = 2026505489033986049
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3.5-flash', BINARY 'qwen3.7-flash'))
      OR
      (leaf.ai_model_config_id = 1970339281906307073
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name = BINARY 'qwen-max')
      OR
      (leaf.ai_model_config_id = 1970339281788866562
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name = BINARY 'qwen-flash')
      OR
      (leaf.ai_model_config_id = 1970687514398605378
       AND provider.ai_model_config_id = 1970687514398605965
       AND BINARY leaf.model_code = BINARY 'baichuan'
       AND BINARY leaf.name IN (BINARY 'Baichuan-M2', BINARY 'Baichuan-M3-Plus'))
  );

-- Qwen 必须整体处于旧版本可见态，或本迁移的完整新态。
-- 旧态同时接受：生产原 10..50 排序、上一个排序迁移的旧相对顺序 1..5，
-- 以及本迁移回滚后保留的产品顺序 1..5。拒绝其他不明部分态。
SELECT COUNT(*) INTO @qwen_original_identity_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066
       AND BINARY name = BINARY 'qwen3-max-preview' AND visible = 1)
   OR (ai_model_config_id = 1970339281788866562
       AND BINARY name = BINARY 'qwen-flash' AND visible = 1)
   OR (ai_model_config_id = 1970339281906307073
       AND BINARY name = BINARY 'qwen-max' AND visible = 1)
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY name = BINARY 'qwen3.5-plus' AND visible = 1)
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY name = BINARY 'qwen3.5-flash' AND visible = 1);

SELECT COUNT(*) INTO @qwen_legacy_sort_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 10)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 20)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 30)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 40)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 50);

SELECT COUNT(*) INTO @qwen_normalized_old_order_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 1)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 2)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 3)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 4)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 5);

SELECT COUNT(*) INTO @qwen_product_order_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 1)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 2)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 3)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 4)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 5);

SELECT COUNT(*) INTO @qwen_target_state_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066
       AND BINARY name = BINARY 'qwen3.8-max' AND visible = 1 AND sort = 1)
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY name = BINARY 'qwen3.7-plus' AND visible = 1 AND sort = 2)
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY name = BINARY 'qwen3.7-flash' AND visible = 1 AND sort = 3)
   OR (ai_model_config_id = 1970339281906307073
       AND BINARY name = BINARY 'qwen-max' AND visible = 0 AND sort = 4)
   OR (ai_model_config_id = 1970339281788866562
       AND BINARY name = BINARY 'qwen-flash' AND visible = 0 AND sort = 5);

SET @qwen_state_supported = (
    @qwen_target_state_count = 5
    OR (
        @qwen_original_identity_count = 5
        AND (
            @qwen_legacy_sort_count = 5
            OR @qwen_normalized_old_order_count = 5
            OR @qwen_product_order_count = 5
        )
    )
);

SELECT @general_root_count AS general_root_count,
       @target_business_row_count AS target_business_row_count,
       @target_business_id_count AS target_business_id_count,
       @active_target_business_row_count AS active_target_business_row_count,
       @valid_scoped_target_count AS valid_scoped_target_count,
       @qwen_original_identity_count AS qwen_original_identity_count,
       @qwen_legacy_sort_count AS qwen_legacy_sort_count,
       @qwen_normalized_old_order_count AS qwen_normalized_old_order_count,
       @qwen_product_order_count AS qwen_product_order_count,
       @qwen_target_state_count AS qwen_target_state_count,
       @qwen_state_supported AS qwen_state_supported;

SET @preflight_sql = IF(
    @general_root_count = 1
    AND @target_business_row_count = 8
    AND @target_business_id_count = 8
    AND @active_target_business_row_count = 8
    AND @valid_scoped_target_count = 8
    AND @qwen_state_supported = 1,
    'SELECT 1 AS public_model_version_preflight_verified',
    'SELECT * FROM __public_model_version_preflight_failed__'
);
PREPARE public_model_preflight_stmt FROM @preflight_sql;
EXECUTE public_model_preflight_stmt;
DEALLOCATE PREPARE public_model_preflight_stmt;

-- 保护本迁移明确禁止改动的字段。密文只参与 CRC 指纹，不输出原文。
-- display_name/description 不在指纹中，因为它们是本迁移的条件更新字段。
SELECT COUNT(*),
       COALESCE(SUM(CAST(CRC32(CONCAT(
           CAST(leaf.id AS CHAR), '#', CAST(leaf.parent_id AS CHAR), '#',
           IFNULL(leaf.model_code, 'NULL'), '#', CAST(leaf.is_deleted AS CHAR), '#',
           IFNULL(leaf.display_description, 'NULL'), '#',
           IFNULL(leaf.image, 'NULL'), '#', IFNULL(CAST(leaf.type AS CHAR), 'NULL'), '#',
           IFNULL(leaf.api_key_ciphertext, 'NULL'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.visible AS CHAR), 'NULL'), 'QWEN'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.sort AS CHAR), 'NULL'), 'QWEN')
       )) AS UNSIGNED)), 0),
       COALESCE(BIT_XOR(CAST(CRC32(CONCAT(
           CAST(leaf.id AS CHAR), '#', CAST(leaf.parent_id AS CHAR), '#',
           IFNULL(leaf.model_code, 'NULL'), '#', CAST(leaf.is_deleted AS CHAR), '#',
           IFNULL(leaf.display_description, 'NULL'), '#',
           IFNULL(leaf.image, 'NULL'), '#', IFNULL(CAST(leaf.type AS CHAR), 'NULL'), '#',
           IFNULL(leaf.api_key_ciphertext, 'NULL'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.visible AS CHAR), 'NULL'), 'QWEN'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.sort AS CHAR), 'NULL'), 'QWEN')
       )) AS UNSIGNED)), 0)
INTO @protected_count_before, @protected_sum_before, @protected_xor_before
FROM t_ai_model_config AS leaf
WHERE leaf.ai_model_config_id IN (
    9005200000000000001, 1970339281184886785, 1970339281478488066,
    2026505489033986048, 2026505489033986049, 1970339281906307073,
    1970339281788866562, 1970687514398605378
)
  AND leaf.is_deleted = 0;

UPDATE t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider
  ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS root
  ON root.id = provider.parent_id
SET leaf.description = CASE
        WHEN leaf.ai_model_config_id = 9005200000000000001
             AND BINARY leaf.description = BINARY '智谱 GLM-5.2 旗舰模型'
            THEN '智谱 GLM-5.3 旗舰模型'
        WHEN leaf.ai_model_config_id = 1970339281478488066
             AND BINARY leaf.description = BINARY '通义千问3系列Max模型Preview版本，相较2.5系列整体通用能力有大幅度提升，中英文通用文本理解能力、复杂指令遵循能力、主观开放题回答能力、多语言能力、工具调用能力均显著增强，模型知识幻觉更少。'
            THEN '通义千问 Qwen3.8 Max 旗舰模型'
        WHEN leaf.ai_model_config_id = 2026505489033986048
             AND BINARY leaf.description = BINARY 'Qwen3.5原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.5系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。'
            THEN 'Qwen3.7原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.7系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。'
        WHEN leaf.ai_model_config_id = 2026505489033986049
             AND BINARY leaf.description = BINARY 'Qwen3.5原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。'
            THEN 'Qwen3.7原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。'
        ELSE leaf.description
    END,
    leaf.display_name = CASE
        WHEN leaf.ai_model_config_id = 9005200000000000001
             AND BINARY leaf.display_name = BINARY 'glm-5.2' THEN 'glm-5.3'
        WHEN leaf.ai_model_config_id = 1970339281184886785
             AND BINARY leaf.display_name = BINARY 'doubao-seed-1.6-250615'
            THEN 'doubao-seed-2-1-pro-260628'
        WHEN leaf.ai_model_config_id = 1970339281478488066
             AND BINARY leaf.display_name = BINARY 'qwen3-max-preview' THEN 'qwen3.8-max'
        WHEN leaf.ai_model_config_id = 2026505489033986048
             AND BINARY leaf.display_name = BINARY 'qwen3.5-plus' THEN 'qwen3.7-plus'
        WHEN leaf.ai_model_config_id = 2026505489033986049
             AND BINARY leaf.display_name = BINARY 'qwen3.5-flash' THEN 'qwen3.7-flash'
        WHEN leaf.ai_model_config_id = 1970687514398605378
             AND BINARY leaf.display_name = BINARY 'Baichuan-M2' THEN 'Baichuan-M3-Plus'
        ELSE leaf.display_name
    END,
    leaf.name = CASE leaf.ai_model_config_id
        WHEN 9005200000000000001 THEN 'glm-5.3'
        WHEN 1970339281184886785 THEN 'doubao-seed-2-1-pro-260628'
        WHEN 1970339281478488066 THEN 'qwen3.8-max'
        WHEN 2026505489033986048 THEN 'qwen3.7-plus'
        WHEN 2026505489033986049 THEN 'qwen3.7-flash'
        WHEN 1970339281906307073 THEN 'qwen-max'
        WHEN 1970339281788866562 THEN 'qwen-flash'
        WHEN 1970687514398605378 THEN 'Baichuan-M3-Plus'
        ELSE leaf.name
    END,
    leaf.visible = CASE leaf.ai_model_config_id
        WHEN 1970339281478488066 THEN 1
        WHEN 2026505489033986048 THEN 1
        WHEN 2026505489033986049 THEN 1
        WHEN 1970339281906307073 THEN 0
        WHEN 1970339281788866562 THEN 0
        ELSE leaf.visible
    END,
    leaf.sort = CASE leaf.ai_model_config_id
        WHEN 1970339281478488066 THEN 1
        WHEN 2026505489033986048 THEN 2
        WHEN 2026505489033986049 THEN 3
        WHEN 1970339281906307073 THEN 4
        WHEN 1970339281788866562 THEN 5
        ELSE leaf.sort
    END,
    leaf.update_time = NOW()
WHERE leaf.is_leaf = TRUE
  AND leaf.is_deleted = 0
  AND provider.is_leaf = FALSE
  AND provider.is_deleted = 0
  AND root.parent_id = 0
  AND root.is_deleted = 0
  AND root.ai_model_config_id = @general_model_root_business_id
  AND leaf.ai_model_config_id IN (
      9005200000000000001, 1970339281184886785, 1970339281478488066,
      2026505489033986048, 2026505489033986049, 1970339281906307073,
      1970339281788866562, 1970687514398605378
  )
  AND (
      NOT (leaf.name <=> CASE leaf.ai_model_config_id
          WHEN 9005200000000000001 THEN 'glm-5.3'
          WHEN 1970339281184886785 THEN 'doubao-seed-2-1-pro-260628'
          WHEN 1970339281478488066 THEN 'qwen3.8-max'
          WHEN 2026505489033986048 THEN 'qwen3.7-plus'
          WHEN 2026505489033986049 THEN 'qwen3.7-flash'
          WHEN 1970339281906307073 THEN 'qwen-max'
          WHEN 1970339281788866562 THEN 'qwen-flash'
          WHEN 1970687514398605378 THEN 'Baichuan-M3-Plus'
          ELSE leaf.name END)
      OR (leaf.model_code = 'qwen' AND NOT (leaf.visible <=> CASE leaf.ai_model_config_id
          WHEN 1970339281906307073 THEN 0
          WHEN 1970339281788866562 THEN 0
          ELSE 1 END))
      OR (leaf.model_code = 'qwen' AND NOT (leaf.sort <=> CASE leaf.ai_model_config_id
          WHEN 1970339281478488066 THEN 1
          WHEN 2026505489033986048 THEN 2
          WHEN 2026505489033986049 THEN 3
          WHEN 1970339281906307073 THEN 4
          WHEN 1970339281788866562 THEN 5
          ELSE leaf.sort END))
      OR (leaf.ai_model_config_id = 9005200000000000001
          AND BINARY leaf.display_name = BINARY 'glm-5.2')
      OR (leaf.ai_model_config_id = 1970339281184886785
          AND BINARY leaf.display_name = BINARY 'doubao-seed-1.6-250615')
      OR (leaf.ai_model_config_id = 1970339281478488066
          AND BINARY leaf.display_name = BINARY 'qwen3-max-preview')
      OR (leaf.ai_model_config_id = 2026505489033986048
          AND BINARY leaf.display_name = BINARY 'qwen3.5-plus')
      OR (leaf.ai_model_config_id = 2026505489033986049
          AND BINARY leaf.display_name = BINARY 'qwen3.5-flash')
      OR (leaf.ai_model_config_id = 1970687514398605378
          AND BINARY leaf.display_name = BINARY 'Baichuan-M2')
      OR (leaf.ai_model_config_id = 9005200000000000001
          AND BINARY leaf.description = BINARY '智谱 GLM-5.2 旗舰模型')
      OR (leaf.ai_model_config_id = 1970339281478488066
          AND BINARY leaf.description = BINARY '通义千问3系列Max模型Preview版本，相较2.5系列整体通用能力有大幅度提升，中英文通用文本理解能力、复杂指令遵循能力、主观开放题回答能力、多语言能力、工具调用能力均显著增强，模型知识幻觉更少。')
      OR (leaf.ai_model_config_id = 2026505489033986048
          AND BINARY leaf.description = BINARY 'Qwen3.5原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.5系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。')
      OR (leaf.ai_model_config_id = 2026505489033986049
          AND BINARY leaf.description = BINARY 'Qwen3.5原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。')
  );

SET @public_model_rows_updated = ROW_COUNT();
SELECT @public_model_rows_updated AS public_model_rows_updated;

SELECT COUNT(*) INTO @target_name_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001 AND BINARY name = BINARY 'glm-5.3')
   OR (ai_model_config_id = 1970339281184886785 AND BINARY name = BINARY 'doubao-seed-2-1-pro-260628')
   OR (ai_model_config_id = 1970339281478488066 AND BINARY name = BINARY 'qwen3.8-max')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY name = BINARY 'qwen3.7-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY name = BINARY 'qwen3.7-flash')
   OR (ai_model_config_id = 1970339281906307073 AND BINARY name = BINARY 'qwen-max')
   OR (ai_model_config_id = 1970339281788866562 AND BINARY name = BINARY 'qwen-flash')
   OR (ai_model_config_id = 1970687514398605378 AND BINARY name = BINARY 'Baichuan-M3-Plus');

SELECT COUNT(*) INTO @qwen_target_state_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066
       AND BINARY name = BINARY 'qwen3.8-max' AND visible = 1 AND sort = 1)
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY name = BINARY 'qwen3.7-plus' AND visible = 1 AND sort = 2)
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY name = BINARY 'qwen3.7-flash' AND visible = 1 AND sort = 3)
   OR (ai_model_config_id = 1970339281906307073
       AND BINARY name = BINARY 'qwen-max' AND visible = 0 AND sort = 4)
   OR (ai_model_config_id = 1970339281788866562
       AND BINARY name = BINARY 'qwen-flash' AND visible = 0 AND sort = 5);

SELECT COUNT(*) INTO @stale_default_display_name_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001 AND BINARY display_name = BINARY 'glm-5.2')
   OR (ai_model_config_id = 1970339281184886785 AND BINARY display_name = BINARY 'doubao-seed-1.6-250615')
   OR (ai_model_config_id = 1970339281478488066 AND BINARY display_name = BINARY 'qwen3-max-preview')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY display_name = BINARY 'qwen3.5-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY display_name = BINARY 'qwen3.5-flash')
   OR (ai_model_config_id = 1970687514398605378 AND BINARY display_name = BINARY 'Baichuan-M2');

SELECT COUNT(*) INTO @stale_standard_description_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001
       AND BINARY description = BINARY '智谱 GLM-5.2 旗舰模型')
   OR (ai_model_config_id = 1970339281478488066
       AND BINARY description = BINARY '通义千问3系列Max模型Preview版本，相较2.5系列整体通用能力有大幅度提升，中英文通用文本理解能力、复杂指令遵循能力、主观开放题回答能力、多语言能力、工具调用能力均显著增强，模型知识幻觉更少。')
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY description = BINARY 'Qwen3.5原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.5系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。')
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY description = BINARY 'Qwen3.5原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。');

SELECT COUNT(*),
       COALESCE(SUM(CAST(CRC32(CONCAT(
           CAST(leaf.id AS CHAR), '#', CAST(leaf.parent_id AS CHAR), '#',
           IFNULL(leaf.model_code, 'NULL'), '#', CAST(leaf.is_deleted AS CHAR), '#',
           IFNULL(leaf.display_description, 'NULL'), '#',
           IFNULL(leaf.image, 'NULL'), '#', IFNULL(CAST(leaf.type AS CHAR), 'NULL'), '#',
           IFNULL(leaf.api_key_ciphertext, 'NULL'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.visible AS CHAR), 'NULL'), 'QWEN'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.sort AS CHAR), 'NULL'), 'QWEN')
       )) AS UNSIGNED)), 0),
       COALESCE(BIT_XOR(CAST(CRC32(CONCAT(
           CAST(leaf.id AS CHAR), '#', CAST(leaf.parent_id AS CHAR), '#',
           IFNULL(leaf.model_code, 'NULL'), '#', CAST(leaf.is_deleted AS CHAR), '#',
           IFNULL(leaf.display_description, 'NULL'), '#',
           IFNULL(leaf.image, 'NULL'), '#', IFNULL(CAST(leaf.type AS CHAR), 'NULL'), '#',
           IFNULL(leaf.api_key_ciphertext, 'NULL'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.visible AS CHAR), 'NULL'), 'QWEN'), '#',
           IF(leaf.model_code <> 'qwen', IFNULL(CAST(leaf.sort AS CHAR), 'NULL'), 'QWEN')
       )) AS UNSIGNED)), 0)
INTO @protected_count_after, @protected_sum_after, @protected_xor_after
FROM t_ai_model_config AS leaf
WHERE leaf.ai_model_config_id IN (
    9005200000000000001, 1970339281184886785, 1970339281478488066,
    2026505489033986048, 2026505489033986049, 1970339281906307073,
    1970339281788866562, 1970687514398605378
)
  AND leaf.is_deleted = 0;

SET @protected_fields_changed = NOT (
    @protected_count_before <=> @protected_count_after
    AND @protected_sum_before <=> @protected_sum_after
    AND @protected_xor_before <=> @protected_xor_after
);

SELECT @target_name_count_after AS target_name_count_after,
       @qwen_target_state_count_after AS qwen_target_state_count_after,
       @stale_default_display_name_count_after AS stale_default_display_name_count_after,
       @stale_standard_description_count_after AS stale_standard_description_count_after,
       @protected_fields_changed AS protected_fields_changed;

SET @commit_verify_sql = IF(
    @target_name_count_after = 8
    AND @qwen_target_state_count_after = 5
    AND @stale_default_display_name_count_after = 0
    AND @stale_standard_description_count_after = 0
    AND @protected_fields_changed = 0,
    'SELECT 1 AS public_model_version_commit_verified',
    'SELECT * FROM __public_model_version_commit_verification_failed__'
);
PREPARE public_model_commit_stmt FROM @commit_verify_sql;
EXECUTE public_model_commit_stmt;
DEALLOCATE PREPARE public_model_commit_stmt;

COMMIT;

-- 最终明细（无密钥列）。立即原样重跑本文件时 updated 必须为 0。
SELECT leaf.ai_model_config_id AS model_business_id,
       leaf.name AS runtime_name,
       leaf.visible,
       leaf.sort,
       leaf.model_code
FROM t_ai_model_config AS leaf
WHERE leaf.ai_model_config_id IN (
    9005200000000000001, 1970339281184886785, 1970339281478488066,
    2026505489033986048, 2026505489033986049, 1970339281906307073,
    1970339281788866562, 1970687514398605378
)
  AND leaf.is_deleted = 0
ORDER BY leaf.model_code, leaf.sort, leaf.ai_model_config_id;
