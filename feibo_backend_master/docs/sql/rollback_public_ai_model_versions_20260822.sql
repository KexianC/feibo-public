-- 回滚 update_public_ai_model_versions_20260822.sql。
-- 目标数据库：生产 MySQL 8.0.36；无 DDL；幂等；
-- 失败时在 COMMIT 前中止。
--
-- 恢复已盘点的旧 runtime name，仅当 display_name/description 精确等于
-- 前向迁移生成的值时恢复旧文案，并将 Qwen 五行恢复为 visible=1。
-- 排序不回退到生产原 10..50；保留产品定义的连续 1..5：
-- 新 Max 对应旧 preview=1，Plus=2，Flash=3，历史 qwen-max=4，qwen-flash=5。
-- 不修改 display_description、model_code 或任何 API Key。
-- 前置：is_deleted 列已存在；回滚拒绝处理任一已逻辑删除的目标行。
-- 执行要求：停止应用写入；使用未开启 --force 的 mysql 客户端。

SET @public_model_version_rollback_confirmation = 'ROLLBACK_20260822_PUBLIC_MODELS';
SET @general_model_root_business_id = 1970339278848659457;

START TRANSACTION;

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
    9005200000000000001, 1970339281184886785, 1970339281478488066,
    2026505489033986048, 2026505489033986049, 1970339281906307073,
    1970339281788866562, 1970687514398605378
)
  AND leaf.is_deleted = 0
ORDER BY leaf.ai_model_config_id
FOR UPDATE;

SELECT COUNT(*) INTO @general_root_count
FROM t_ai_model_config
WHERE parent_id = 0
  AND ai_model_config_id = @general_model_root_business_id;

-- 全表中目标业务 ID 必须各有且只有一行，且八行都仍处于 active 状态。
SELECT COUNT(*), COUNT(DISTINCT ai_model_config_id),
       SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END)
INTO @rollback_target_count, @rollback_target_business_id_count,
     @rollback_active_target_count
FROM t_ai_model_config
WHERE ai_model_config_id IN (
    9005200000000000001, 1970339281184886785, 1970339281478488066,
    2026505489033986048, 2026505489033986049, 1970339281906307073,
    1970339281788866562, 1970687514398605378
);

-- 每行还必须是通用模型根下、预期供应商的 active 叶子。
SELECT COUNT(*) INTO @rollback_valid_scoped_target_count
FROM t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS root ON root.id = provider.parent_id
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
      OR (leaf.ai_model_config_id = 1970339281184886785
       AND provider.ai_model_config_id = 1970339280589295618
       AND BINARY leaf.model_code = BINARY 'doubao'
       AND BINARY leaf.name IN (BINARY 'doubao-seed-1.6-250615', BINARY 'doubao-seed-2-1-pro-260628'))
      OR (leaf.ai_model_config_id = 1970339281478488066
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3-max-preview', BINARY 'qwen3.8-max'))
      OR (leaf.ai_model_config_id = 2026505489033986048
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3.5-plus', BINARY 'qwen3.7-plus'))
      OR (leaf.ai_model_config_id = 2026505489033986049
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen'
       AND BINARY leaf.name IN (BINARY 'qwen3.5-flash', BINARY 'qwen3.7-flash'))
      OR (leaf.ai_model_config_id = 1970339281906307073
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen' AND BINARY leaf.name = BINARY 'qwen-max')
      OR (leaf.ai_model_config_id = 1970339281788866562
       AND provider.ai_model_config_id = 1970339280681570306
       AND BINARY leaf.model_code = BINARY 'qwen' AND BINARY leaf.name = BINARY 'qwen-flash')
      OR (leaf.ai_model_config_id = 1970687514398605378
       AND provider.ai_model_config_id = 1970687514398605965
       AND BINARY leaf.model_code = BINARY 'baichuan'
       AND BINARY leaf.name IN (BINARY 'Baichuan-M2', BINARY 'Baichuan-M3-Plus'))
  );

SELECT COUNT(*) INTO @rollback_qwen_old_identity_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND BINARY name = BINARY 'qwen3-max-preview')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY name = BINARY 'qwen3.5-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY name = BINARY 'qwen3.5-flash')
   OR (ai_model_config_id = 1970339281906307073 AND BINARY name = BINARY 'qwen-max')
   OR (ai_model_config_id = 1970339281788866562 AND BINARY name = BINARY 'qwen-flash');

SELECT COUNT(*) INTO @rollback_qwen_target_identity_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND BINARY name = BINARY 'qwen3.8-max')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY name = BINARY 'qwen3.7-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY name = BINARY 'qwen3.7-flash')
   OR (ai_model_config_id = 1970339281906307073 AND BINARY name = BINARY 'qwen-max')
   OR (ai_model_config_id = 1970339281788866562 AND BINARY name = BINARY 'qwen-flash');

SELECT COUNT(*) INTO @rollback_qwen_all_visible_count
FROM t_ai_model_config
WHERE ai_model_config_id IN (
    1970339281478488066, 2026505489033986048, 2026505489033986049,
    1970339281906307073, 1970339281788866562
)
  AND visible = 1;

SELECT COUNT(*) INTO @rollback_qwen_target_visibility_count
FROM t_ai_model_config
WHERE (ai_model_config_id IN (
           1970339281478488066, 2026505489033986048, 2026505489033986049
       ) AND visible = 1)
   OR (ai_model_config_id IN (1970339281906307073, 1970339281788866562)
       AND visible = 0);

SELECT COUNT(*) INTO @rollback_qwen_legacy_sort_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 10)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 20)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 30)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 40)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 50);

SELECT COUNT(*) INTO @rollback_qwen_normalized_old_order_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 1)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 2)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 3)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 4)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 5);

SELECT COUNT(*) INTO @rollback_qwen_product_order_count
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066 AND sort = 1)
   OR (ai_model_config_id = 2026505489033986048 AND sort = 2)
   OR (ai_model_config_id = 2026505489033986049 AND sort = 3)
   OR (ai_model_config_id = 1970339281906307073 AND sort = 4)
   OR (ai_model_config_id = 1970339281788866562 AND sort = 5);

SET @rollback_qwen_state_supported = (
    (@rollback_qwen_old_identity_count = 5 OR @rollback_qwen_target_identity_count = 5)
    AND (@rollback_qwen_all_visible_count = 5 OR @rollback_qwen_target_visibility_count = 5)
    AND (
        @rollback_qwen_legacy_sort_count = 5
        OR @rollback_qwen_normalized_old_order_count = 5
        OR @rollback_qwen_product_order_count = 5
    )
);

SET @rollback_preflight_sql = IF(
    BINARY @public_model_version_rollback_confirmation =
        BINARY 'ROLLBACK_20260822_PUBLIC_MODELS'
    AND @general_root_count = 1
    AND @rollback_target_count = 8
    AND @rollback_target_business_id_count = 8
    AND @rollback_active_target_count = 8
    AND @rollback_valid_scoped_target_count = 8
    AND @rollback_qwen_state_supported = 1,
    'SELECT 1 AS public_model_version_rollback_preflight_verified',
    'SELECT * FROM __public_model_version_rollback_preflight_failed__'
);
PREPARE public_model_rollback_preflight_stmt FROM @rollback_preflight_sql;
EXECUTE public_model_rollback_preflight_stmt;
DEALLOCATE PREPARE public_model_rollback_preflight_stmt;

UPDATE t_ai_model_config AS leaf
JOIN t_ai_model_config AS provider ON provider.id = leaf.parent_id
JOIN t_ai_model_config AS root ON root.id = provider.parent_id
SET leaf.description = CASE
        WHEN leaf.ai_model_config_id = 9005200000000000001
             AND BINARY leaf.description = BINARY '智谱 GLM-5.3 旗舰模型'
            THEN '智谱 GLM-5.2 旗舰模型'
        WHEN leaf.ai_model_config_id = 1970339281478488066
             AND BINARY leaf.description = BINARY '通义千问 Qwen3.8 Max 旗舰模型'
            THEN '通义千问3系列Max模型Preview版本，相较2.5系列整体通用能力有大幅度提升，中英文通用文本理解能力、复杂指令遵循能力、主观开放题回答能力、多语言能力、工具调用能力均显著增强，模型知识幻觉更少。'
        WHEN leaf.ai_model_config_id = 2026505489033986048
             AND BINARY leaf.description = BINARY 'Qwen3.7原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.7系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。'
            THEN 'Qwen3.5原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.5系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。'
        WHEN leaf.ai_model_config_id = 2026505489033986049
             AND BINARY leaf.description = BINARY 'Qwen3.7原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。'
            THEN 'Qwen3.5原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。'
        ELSE leaf.description
    END,
    leaf.display_name = CASE
        WHEN leaf.ai_model_config_id = 9005200000000000001
             AND BINARY leaf.display_name = BINARY 'glm-5.3' THEN 'glm-5.2'
        WHEN leaf.ai_model_config_id = 1970339281184886785
             AND BINARY leaf.display_name = BINARY 'doubao-seed-2-1-pro-260628'
            THEN 'doubao-seed-1.6-250615'
        WHEN leaf.ai_model_config_id = 1970339281478488066
             AND BINARY leaf.display_name = BINARY 'qwen3.8-max' THEN 'qwen3-max-preview'
        WHEN leaf.ai_model_config_id = 2026505489033986048
             AND BINARY leaf.display_name = BINARY 'qwen3.7-plus' THEN 'qwen3.5-plus'
        WHEN leaf.ai_model_config_id = 2026505489033986049
             AND BINARY leaf.display_name = BINARY 'qwen3.7-flash' THEN 'qwen3.5-flash'
        WHEN leaf.ai_model_config_id = 1970687514398605378
             AND BINARY leaf.display_name = BINARY 'Baichuan-M3-Plus' THEN 'Baichuan-M2'
        ELSE leaf.display_name
    END,
    leaf.name = CASE leaf.ai_model_config_id
        WHEN 9005200000000000001 THEN 'glm-5.2'
        WHEN 1970339281184886785 THEN 'doubao-seed-1.6-250615'
        WHEN 1970339281478488066 THEN 'qwen3-max-preview'
        WHEN 2026505489033986048 THEN 'qwen3.5-plus'
        WHEN 2026505489033986049 THEN 'qwen3.5-flash'
        WHEN 1970339281906307073 THEN 'qwen-max'
        WHEN 1970339281788866562 THEN 'qwen-flash'
        WHEN 1970687514398605378 THEN 'Baichuan-M2'
        ELSE leaf.name
    END,
    leaf.visible = CASE
        WHEN leaf.model_code = 'qwen' THEN 1
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
          WHEN 9005200000000000001 THEN 'glm-5.2'
          WHEN 1970339281184886785 THEN 'doubao-seed-1.6-250615'
          WHEN 1970339281478488066 THEN 'qwen3-max-preview'
          WHEN 2026505489033986048 THEN 'qwen3.5-plus'
          WHEN 2026505489033986049 THEN 'qwen3.5-flash'
          WHEN 1970339281906307073 THEN 'qwen-max'
          WHEN 1970339281788866562 THEN 'qwen-flash'
          WHEN 1970687514398605378 THEN 'Baichuan-M2'
          ELSE leaf.name END)
      OR (leaf.model_code = 'qwen' AND leaf.visible <> 1)
      OR (leaf.model_code = 'qwen' AND NOT (leaf.sort <=> CASE leaf.ai_model_config_id
          WHEN 1970339281478488066 THEN 1
          WHEN 2026505489033986048 THEN 2
          WHEN 2026505489033986049 THEN 3
          WHEN 1970339281906307073 THEN 4
          WHEN 1970339281788866562 THEN 5
          ELSE leaf.sort END))
      OR (leaf.ai_model_config_id = 9005200000000000001
          AND BINARY leaf.display_name = BINARY 'glm-5.3')
      OR (leaf.ai_model_config_id = 1970339281184886785
          AND BINARY leaf.display_name = BINARY 'doubao-seed-2-1-pro-260628')
      OR (leaf.ai_model_config_id = 1970339281478488066
          AND BINARY leaf.display_name = BINARY 'qwen3.8-max')
      OR (leaf.ai_model_config_id = 2026505489033986048
          AND BINARY leaf.display_name = BINARY 'qwen3.7-plus')
      OR (leaf.ai_model_config_id = 2026505489033986049
          AND BINARY leaf.display_name = BINARY 'qwen3.7-flash')
      OR (leaf.ai_model_config_id = 1970687514398605378
          AND BINARY leaf.display_name = BINARY 'Baichuan-M3-Plus')
      OR (leaf.ai_model_config_id = 9005200000000000001
          AND BINARY leaf.description = BINARY '智谱 GLM-5.3 旗舰模型')
      OR (leaf.ai_model_config_id = 1970339281478488066
          AND BINARY leaf.description = BINARY '通义千问 Qwen3.8 Max 旗舰模型')
      OR (leaf.ai_model_config_id = 2026505489033986048
          AND BINARY leaf.description = BINARY 'Qwen3.7原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.7系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。')
      OR (leaf.ai_model_config_id = 2026505489033986049
          AND BINARY leaf.description = BINARY 'Qwen3.7原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。')
  );

SET @public_model_rows_rolled_back = ROW_COUNT();
SELECT @public_model_rows_rolled_back AS public_model_rows_rolled_back;

SELECT COUNT(*) INTO @rollback_name_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001 AND BINARY name = BINARY 'glm-5.2')
   OR (ai_model_config_id = 1970339281184886785 AND BINARY name = BINARY 'doubao-seed-1.6-250615')
   OR (ai_model_config_id = 1970339281478488066 AND BINARY name = BINARY 'qwen3-max-preview')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY name = BINARY 'qwen3.5-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY name = BINARY 'qwen3.5-flash')
   OR (ai_model_config_id = 1970339281906307073 AND BINARY name = BINARY 'qwen-max')
   OR (ai_model_config_id = 1970339281788866562 AND BINARY name = BINARY 'qwen-flash')
   OR (ai_model_config_id = 1970687514398605378 AND BINARY name = BINARY 'Baichuan-M2');

SELECT COUNT(*) INTO @qwen_original_state_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 1970339281478488066
       AND BINARY name = BINARY 'qwen3-max-preview' AND visible = 1 AND sort = 1)
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY name = BINARY 'qwen3.5-plus' AND visible = 1 AND sort = 2)
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY name = BINARY 'qwen3.5-flash' AND visible = 1 AND sort = 3)
   OR (ai_model_config_id = 1970339281906307073
       AND BINARY name = BINARY 'qwen-max' AND visible = 1 AND sort = 4)
   OR (ai_model_config_id = 1970339281788866562
       AND BINARY name = BINARY 'qwen-flash' AND visible = 1 AND sort = 5);

SELECT COUNT(*) INTO @migration_generated_display_name_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001 AND BINARY display_name = BINARY 'glm-5.3')
   OR (ai_model_config_id = 1970339281184886785 AND BINARY display_name = BINARY 'doubao-seed-2-1-pro-260628')
   OR (ai_model_config_id = 1970339281478488066 AND BINARY display_name = BINARY 'qwen3.8-max')
   OR (ai_model_config_id = 2026505489033986048 AND BINARY display_name = BINARY 'qwen3.7-plus')
   OR (ai_model_config_id = 2026505489033986049 AND BINARY display_name = BINARY 'qwen3.7-flash')
   OR (ai_model_config_id = 1970687514398605378 AND BINARY display_name = BINARY 'Baichuan-M3-Plus');

SELECT COUNT(*) INTO @migration_generated_description_count_after
FROM t_ai_model_config
WHERE (ai_model_config_id = 9005200000000000001
       AND BINARY description = BINARY '智谱 GLM-5.3 旗舰模型')
   OR (ai_model_config_id = 1970339281478488066
       AND BINARY description = BINARY '通义千问 Qwen3.8 Max 旗舰模型')
   OR (ai_model_config_id = 2026505489033986048
       AND BINARY description = BINARY 'Qwen3.7原生视觉语言系列Plus模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。在多项任务评测中，3.7系列均展现出与当前顶尖前沿模型相媲美的卓越性能，模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步。')
   OR (ai_model_config_id = 2026505489033986049
       AND BINARY description = BINARY 'Qwen3.7原生视觉语言系列Flash模型，基于混合架构设计，融合了线性注意力机制与稀疏混合专家模型，实现了更高的推理效率。模型效果在纯文本与多模态方面相较3系列均实现飞跃式进步；响应速度快，兼具推理速度和性能。');

SET @rollback_commit_sql = IF(
    @rollback_name_count_after = 8
    AND @qwen_original_state_count_after = 5
    AND @migration_generated_display_name_count_after = 0
    AND @migration_generated_description_count_after = 0,
    'SELECT 1 AS public_model_version_rollback_commit_verified',
    'SELECT * FROM __public_model_version_rollback_commit_failed__'
);
PREPARE public_model_rollback_commit_stmt FROM @rollback_commit_sql;
EXECUTE public_model_rollback_commit_stmt;
DEALLOCATE PREPARE public_model_rollback_commit_stmt;

COMMIT;

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
