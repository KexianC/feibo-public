package com.hzmwmt.domain.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hzmwmt.domain.entity.user.TAiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminModelConfigMapper extends BaseMapper<TAiModelConfig> {

    @Select("""
            SELECT leaf.id,
                   leaf.ai_model_config_id,
                   leaf.parent_id,
                   leaf.is_leaf,
                   leaf.name,
                   leaf.display_name,
                   leaf.description,
                   leaf.display_description,
                   leaf.model_code,
                   leaf.type,
                   leaf.image,
                   parent.ai_model_config_id AS provider_id,
                   COALESCE(parent.display_name, parent.name) AS provider_name,
                   parent.sort AS provider_order,
                   leaf.sort AS sort,
                   leaf.visible,
                   leaf.api_key_ciphertext,
                   leaf.create_time,
                   leaf.update_time
            FROM t_ai_model_config leaf
            JOIN t_ai_model_config parent ON parent.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = parent.parent_id
            WHERE leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND parent.is_deleted = FALSE
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            ORDER BY (parent.sort IS NULL), parent.sort, parent.ai_model_config_id,
                     (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id
            """)
    List<TAiModelConfig> selectManagedModels();

    @Select("""
            SELECT leaf.id, leaf.ai_model_config_id, leaf.parent_id, leaf.is_leaf,
                   leaf.name, leaf.display_name, leaf.description,
                   leaf.display_description, leaf.model_code,
                   leaf.type, leaf.image,
                   parent.ai_model_config_id AS provider_id,
                   COALESCE(parent.display_name, parent.name) AS provider_name,
                   parent.sort AS provider_order,
                   leaf.sort AS sort,
                   leaf.visible,
                   leaf.api_key_ciphertext, leaf.create_time, leaf.update_time
            FROM t_ai_model_config leaf
            JOIN t_ai_model_config parent ON parent.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = parent.parent_id
            WHERE leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND leaf.model_code = #{modelCode}
              AND parent.is_deleted = FALSE
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            ORDER BY (parent.sort IS NULL), parent.sort, parent.ai_model_config_id,
                     (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id
            LIMIT 1
            """)
    TAiModelConfig selectProviderTemplate(@Param("modelCode") String modelCode);

    @Select("""
            SELECT COUNT(DISTINCT parent.id)
            FROM t_ai_model_config leaf
            JOIN t_ai_model_config parent ON parent.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = parent.parent_id
            WHERE leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND leaf.model_code = #{modelCode}
              AND parent.is_leaf = FALSE
              AND parent.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int countManagedProviderScopesForModelCode(
            @Param("modelCode") String modelCode);

    @Select("""
            SELECT leaf.id, leaf.ai_model_config_id, leaf.parent_id, leaf.is_leaf,
                   leaf.name, leaf.display_name, leaf.description,
                   leaf.display_description, leaf.model_code,
                   leaf.type, leaf.image,
                   parent.ai_model_config_id AS provider_id,
                   COALESCE(parent.display_name, parent.name) AS provider_name,
                   parent.sort AS provider_order,
                   leaf.sort AS sort,
                   leaf.visible,
                   leaf.api_key_ciphertext, leaf.create_time, leaf.update_time
            FROM t_ai_model_config leaf
            JOIN t_ai_model_config parent ON parent.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = parent.parent_id
            WHERE leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND leaf.ai_model_config_id = #{aiModelConfigId}
              AND parent.is_deleted = FALSE
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            LIMIT 1
            """)
    TAiModelConfig selectManagedModelById(
            @Param("aiModelConfigId") Long aiModelConfigId);

    @Select("""
            SELECT root.id
            FROM t_ai_model_config root
            WHERE root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            FOR UPDATE
            """)
    Long lockManagedGeneralRootForUpdate();

    @Select("""
            SELECT provider.id
            FROM t_ai_model_config provider
            WHERE provider.id = #{parentId}
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND EXISTS (
                  SELECT 1
                  FROM t_ai_model_config root
                  WHERE root.id = provider.parent_id
                    AND root.parent_id = 0
                    AND root.is_deleted = FALSE
                    AND root.ai_model_config_id = 1970339278848659457
              )
            FOR UPDATE
            """)
    Long lockManagedProviderScopeForUpdate(@Param("parentId") Long parentId);

    @Select("""
            SELECT provider.id, provider.ai_model_config_id, provider.parent_id,
                   provider.is_leaf, provider.name, provider.display_name,
                   provider.sort
            FROM t_ai_model_config provider
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            WHERE provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            ORDER BY (provider.sort IS NULL), provider.sort,
                     provider.ai_model_config_id
            FOR UPDATE
            """)
    List<TAiModelConfig> selectManagedProvidersForUpdate();

    @Select("""
            SELECT COUNT(1)
            FROM t_ai_model_config provider
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            WHERE provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_ai_model_config leaf
                  WHERE leaf.parent_id = provider.id
                    AND leaf.is_leaf = TRUE
                    AND leaf.is_deleted = FALSE
              )
            """)
    int countManagedEmptyProviders();

    @Select("""
            SELECT leaf.id, leaf.ai_model_config_id, leaf.parent_id, leaf.is_leaf,
                   leaf.name, leaf.display_name, leaf.description,
                   leaf.display_description, leaf.model_code,
                   leaf.type, leaf.image, leaf.sort, leaf.visible,
                   leaf.api_key_ciphertext, leaf.create_time, leaf.update_time
            FROM t_ai_model_config leaf
            WHERE leaf.parent_id = #{parentId}
              AND leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND EXISTS (
                  SELECT 1
                  FROM t_ai_model_config provider
                  JOIN t_ai_model_config root ON root.id = provider.parent_id
                  WHERE provider.id = leaf.parent_id
                    AND provider.is_leaf = FALSE
                    AND provider.is_deleted = FALSE
                    AND root.parent_id = 0
                    AND root.is_deleted = FALSE
                    AND root.ai_model_config_id = 1970339278848659457
              )
            ORDER BY (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id
            FOR UPDATE
            """)
    List<TAiModelConfig> selectManagedSiblingModelsForUpdate(
            @Param("parentId") Long parentId);

    @Update("""
            UPDATE t_ai_model_config provider
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            SET provider.sort = #{order}
            WHERE provider.ai_model_config_id = #{providerId}
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int updateManagedProviderOrder(
            @Param("providerId") Long providerId,
            @Param("order") Integer order);

    @Update("""
            UPDATE t_ai_model_config
            SET sort = #{order}
            WHERE ai_model_config_id = #{aiModelConfigId}
              AND parent_id = #{parentId}
              AND is_leaf = TRUE
              AND is_deleted = FALSE
            """)
    int updateManagedModelOrder(
            @Param("aiModelConfigId") Long aiModelConfigId,
            @Param("parentId") Long parentId,
            @Param("order") Integer order);

    @Update("""
            UPDATE t_ai_model_config leaf
            JOIN t_ai_model_config provider ON provider.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            SET leaf.display_name = COALESCE(#{model.displayName}, leaf.display_name),
                leaf.name = COALESCE(#{model.name}, leaf.name),
                leaf.display_description = COALESCE(
                    #{model.displayDescription}, leaf.display_description),
                leaf.update_time = CURRENT_TIMESTAMP
            WHERE leaf.ai_model_config_id = #{model.aiModelConfigId}
              AND leaf.parent_id = #{model.parentId}
              AND leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int updateManagedLeafMetadata(@Param("model") TAiModelConfig model);

    @Update("""
            UPDATE t_ai_model_config leaf
            JOIN t_ai_model_config provider ON provider.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            SET leaf.visible = #{visible},
                leaf.update_time = CURRENT_TIMESTAMP
            WHERE leaf.ai_model_config_id = #{aiModelConfigId}
              AND leaf.parent_id = #{parentId}
              AND leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int updateManagedLeafVisibility(
            @Param("aiModelConfigId") Long aiModelConfigId,
            @Param("parentId") Long parentId,
            @Param("visible") Boolean visible);

    @Update("""
            UPDATE t_ai_model_config leaf
            JOIN t_ai_model_config provider ON provider.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            SET leaf.api_key_ciphertext = #{apiKeyCiphertext},
                leaf.update_time = CURRENT_TIMESTAMP
            WHERE leaf.ai_model_config_id = #{aiModelConfigId}
              AND leaf.parent_id = #{parentId}
              AND leaf.is_leaf = TRUE
              AND leaf.is_deleted = FALSE
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int updateManagedLeafApiKey(
            @Param("aiModelConfigId") Long aiModelConfigId,
            @Param("parentId") Long parentId,
            @Param("apiKeyCiphertext") String apiKeyCiphertext);

    @Update("""
            UPDATE t_ai_model_config leaf
            JOIN t_ai_model_config provider ON provider.id = leaf.parent_id
            JOIN t_ai_model_config root ON root.id = provider.parent_id
            SET leaf.visible = FALSE,
                leaf.is_deleted = TRUE,
                leaf.api_key_ciphertext = NULL,
                leaf.update_time = CURRENT_TIMESTAMP
            WHERE leaf.ai_model_config_id = #{aiModelConfigId}
              AND leaf.parent_id = #{parentId}
              AND leaf.is_leaf = TRUE
              AND leaf.visible = FALSE
              AND leaf.is_deleted = FALSE
              AND provider.is_leaf = FALSE
              AND provider.is_deleted = FALSE
              AND root.parent_id = 0
              AND root.is_deleted = FALSE
              AND root.ai_model_config_id = 1970339278848659457
            """)
    int softDeleteManagedLeafModel(
            @Param("aiModelConfigId") Long aiModelConfigId,
            @Param("parentId") Long parentId);

}
