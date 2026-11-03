package com.hzmwmt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hzmwmt.domain.controller.user.AdminAiModelController;
import com.hzmwmt.domain.dto.AdminAiModelApiKeyDTO;
import com.hzmwmt.domain.dto.AdminAiModelCreateDTO;
import com.hzmwmt.domain.dto.AdminAiModelOrderDTO;
import com.hzmwmt.domain.dto.AdminAiModelUpdateDTO;
import com.hzmwmt.domain.vo.AdminAiModelVO;
import com.hzmwmt.types.annotations.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAiModelEndpointContractTest {

    @Test
    void endpointIsSuperAdminOnlyAndUsesTheVersionedContractPath() {
        RequestMapping mapping = AdminAiModelController.class
                .getAnnotation(RequestMapping.class);
        PreAuthorize authorization = AdminAiModelController.class
                .getAnnotation(PreAuthorize.class);

        assertThat(mapping.value()).containsExactly("/api/v1/admin/ai-models");
        assertThat(authorization.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }

    @Test
    void securityChainAlsoRestrictsTheExactVersionedPath() throws Exception {
        String securitySource = Files.readString(Path.of(
                "src/main/java/com/hzmwmt/config/SpringSecurityConfig.java"));

        assertThat(securitySource).contains(
                ".requestMatchers(\"/api/v1/admin/ai-models/**\").hasRole(\"SUPER_ADMIN\")");
    }

    @Test
    void everyMutationIsTransactionalAndSuppressesAuditPayloads() {
        Method[] mutations = Arrays.stream(AdminAiModelController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("create")
                        || method.getName().equals("update")
                        || method.getName().equals("updateProviderOrder")
                        || method.getName().equals("updateVisibility")
                        || method.getName().equals("updateApiKey")
                        || method.getName().equals("delete"))
                .toArray(Method[]::new);

        assertThat(mutations).hasSize(6);
        for (Method mutation : mutations) {
            OperationLog operationLog = mutation.getAnnotation(OperationLog.class);
            assertThat(mutation.getAnnotation(Transactional.class)).isNotNull();
            assertThat(operationLog).isNotNull();
            assertThat(operationLog.recordParams()).isFalse();
            assertThat(operationLog.recordResult()).isFalse();
            assertThat(operationLog.critical()).isTrue();
            assertThat(operationLog.targetIdParameterIndex())
                    .isEqualTo(mutation.getName().equals("delete") ? 0 : -1);
        }
    }

    @Test
    void credentialInputsAreWriteOnlyAndResponsesHaveNoCredentialField() throws Exception {
        AdminAiModelApiKeyDTO keyRequest = new AdminAiModelApiKeyDTO();
        keyRequest.setApiKey("never-serialize-me");
        AdminAiModelCreateDTO createRequest = new AdminAiModelCreateDTO();
        createRequest.setApiKey("never-serialize-me-either");

        ObjectMapper objectMapper = new ObjectMapper()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        assertThat(objectMapper.writeValueAsString(keyRequest))
                .doesNotContain("never-serialize-me");
        assertThat(objectMapper.writeValueAsString(createRequest))
                .doesNotContain("never-serialize-me-either");
        assertThat(keyRequest.toString()).doesNotContain("never-serialize-me");
        assertThat(createRequest.toString()).doesNotContain("never-serialize-me-either");
        assertThat(Arrays.stream(AdminAiModelVO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("apiKey", "apiKeyCiphertext");
        assertThat(AdminAiModelVO.class.getDeclaredField("id").getType())
                .isEqualTo(String.class);
    }

    @Test
    void managementQueriesAreScopedToTheGeneralModelRoot() throws Exception {
        String mapperSource = Files.readString(Path.of(
                "../digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));

        assertThat(mapperSource).contains("root.ai_model_config_id = 1970339278848659457");
        assertThat(mapperSource).contains("selectManagedModelById");
        assertThat(mapperSource).contains("lockManagedProviderScopeForUpdate");
        assertThat(mapperSource).doesNotContain("selectManagedProviderById");
        assertThat(mapperSource).contains(
                "countManagedProviderScopesForModelCode");
        assertThat(mapperSource).contains("COUNT(DISTINCT parent.id)");
    }

    @Test
    void managementContractSeparatesProviderAndVersionOrdering() throws Exception {
        String mapperSource = Files.readString(Path.of(
                "../digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));

        assertThat(AdminAiModelVO.class.getDeclaredField("providerName").getType())
                .isEqualTo(String.class);
        assertThat(AdminAiModelVO.class.getDeclaredField("providerId").getType())
                .isEqualTo(String.class);
        assertThat(AdminAiModelVO.class.getDeclaredField("providerOrder").getType())
                .isEqualTo(Integer.class);
        assertThat(AdminAiModelVO.class.getDeclaredField("order").getType())
                .isEqualTo(Integer.class);
        assertThat(mapperSource).contains(
                "parent.sort AS provider_order");
        assertThat(mapperSource).contains(
                "parent.ai_model_config_id AS provider_id");
        assertThat(mapperSource).contains("leaf.sort AS sort");
        assertThat(mapperSource).doesNotContain("COALESCE(leaf.sort, 0) AS sort");
        assertThat(mapperSource).doesNotContain(
                "COALESCE(leaf.sort, parent.sort, 0) AS sort");
        assertThat(mapperSource).contains(
                "ORDER BY (parent.sort IS NULL), parent.sort, parent.ai_model_config_id,\n" +
                        "                     (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id");
        assertThat(mapperSource).contains("selectManagedProvidersForUpdate");
        assertThat(mapperSource).contains("selectManagedSiblingModelsForUpdate");
        assertThat(mapperSource).contains("lockManagedGeneralRootForUpdate");
        assertThat(mapperSource).contains("lockManagedProviderScopeForUpdate");
        assertThat(mapperSource).contains("updateManagedModelOrder");
        assertThat(mapperSource).contains("FOR UPDATE");
        assertThat(mapperSource).doesNotContain("selectNextSort");
    }

    @Test
    void providerOrderEndpointIsDedicatedScopedAndUsesAStringSafeBusinessId() throws Exception {
        Method endpoint = AdminAiModelController.class.getDeclaredMethod(
                "updateProviderOrder",
                Long.class,
                AdminAiModelOrderDTO.class);
        org.springframework.web.bind.annotation.PutMapping mapping =
                endpoint.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
        String mapperSource = Files.readString(Path.of(
                "../digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));
        String providerOrderStatement = mapperStatement(
                mapperSource, "updateManagedProviderOrder");

        assertThat(mapping.value()).containsExactly("/providers/{providerId}/order");
        assertThat(AdminAiModelOrderDTO.class.getDeclaredField("order")
                .getAnnotation(jakarta.validation.constraints.NotNull.class)).isNotNull();
        assertThat(AdminAiModelOrderDTO.class.getDeclaredField("order")
                .getAnnotation(jakarta.validation.constraints.Min.class).value()).isEqualTo(1);
        assertThat(AdminAiModelOrderDTO.class.getDeclaredField("order")
                .getAnnotation(jakarta.validation.constraints.Max.class).value()).isEqualTo(10000);
        assertThat(AdminAiModelCreateDTO.class.getDeclaredField("order")
                .getAnnotation(jakarta.validation.constraints.Min.class).value()).isEqualTo(1);
        assertThat(AdminAiModelUpdateDTO.class.getDeclaredField("order")
                .getAnnotation(jakarta.validation.constraints.Min.class).value()).isEqualTo(1);
        assertThat(AdminAiModelVO.class.getDeclaredField("providerId").getType())
                .isEqualTo(String.class);
        assertThat(mapperSource).contains("lockManagedGeneralRootForUpdate");
        assertThat(mapperSource).contains("selectManagedProvidersForUpdate");
        assertThat(mapperSource).contains("countManagedEmptyProviders");
        assertThat(mapperSource).contains("updateManagedProviderOrder");
        assertThat(mapperSource).contains(
                "ORDER BY (provider.sort IS NULL), provider.sort,");
        assertThat(mapperSource).contains(
                "ORDER BY (leaf.sort IS NULL), leaf.sort, leaf.ai_model_config_id");
        assertThat(mapperSource).contains("FOR UPDATE");
        assertThat(mapperSource).contains("provider.is_leaf = FALSE");
        assertThat(mapperSource).contains("root.parent_id = 0");
        assertThat(mapperSource).contains("root.ai_model_config_id = 1970339278848659457");
        assertThat(providerOrderStatement)
                .contains("UPDATE t_ai_model_config provider")
                .doesNotContain(
                        "t_ai_message",
                        "message_reference",
                        "leaf.ai_model_config_id",
                        "NOT EXISTS");
    }

    @Test
    void deleteEndpointIsScopedAuditedAndCreatesAKeylessTombstone()
            throws Exception {
        Method endpoint = AdminAiModelController.class.getDeclaredMethod(
                "delete", Long.class);
        org.springframework.web.bind.annotation.DeleteMapping mapping =
                endpoint.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);
        String mapperSource = Files.readString(Path.of(
                "../digital-doctor-domain/src/main/java/com/hzmwmt/domain/mapper/user/AdminModelConfigMapper.java"));
        String deleteStatement = mapperStatement(
                mapperSource, "softDeleteManagedLeafModel");

        assertThat(mapping.value()).containsExactly("/{id}");
        assertThat(endpoint.getReturnType().getTypeName())
                .isEqualTo("com.hzmwmt.domain.common.Result");
        assertThat(deleteStatement).contains(
                "UPDATE t_ai_model_config leaf",
                "leaf.parent_id = #{parentId}",
                "leaf.is_leaf = TRUE",
                "leaf.visible = FALSE",
                "leaf.is_deleted = TRUE",
                "leaf.api_key_ciphertext = NULL",
                "leaf.is_deleted = FALSE",
                "provider.is_leaf = FALSE",
                "root.parent_id = 0",
                "root.ai_model_config_id = 1970339278848659457");
        assertThat(deleteStatement).doesNotContain("DELETE", "t_ai_message");
        assertThat(mapperSource).doesNotContain(
                "countAiMessageReferences", "deleteManagedLeafModel");
    }

    @Test
    void migrationFailsClosedAndEnforcesUniqueBusinessIds() throws Exception {
        String migration = Files.readString(Path.of("../docs/sql/add_ai_model_admin_fields.sql"));

        assertThat(migration).contains("display_description VARCHAR(1000)");
        assertThat(migration).contains("null_business_id_count");
        assertThat(migration).contains(
                "MODIFY COLUMN ai_model_config_id BIGINT UNSIGNED NOT NULL");
        assertThat(migration).contains("NOT NULL DEFAULT 0");
        assertThat(migration).contains("CREATE UNIQUE INDEX uq_ai_model_config_business_id");
        assertThat(migration.indexOf(
                "MODIFY COLUMN ai_model_config_id BIGINT UNSIGNED NOT NULL"))
                .isLessThan(migration.indexOf("ADD COLUMN display_name"));
        assertThat(migration.indexOf("CREATE UNIQUE INDEX uq_ai_model_config_business_id"))
                .isLessThan(migration.indexOf("ADD COLUMN display_name"));
        assertThat(migration).contains("general_leaf_null_sort_count");
        assertThat(migration).contains("candidate.sort IS NULL");
        assertThat(migration).contains("general_root.ai_model_config_id = 1970339278848659457");
        assertThat(migration).contains("MAX(explicit_order.sort)");
        assertThat(migration).contains("10 * (");
        assertThat(migration).contains("LEAST(10000,");
        assertThat(migration).contains("), 0), 0) + 10 * (");
    }

    @Test
    void productionOrderingMigrationIsScopedIdempotentAndContainsNoDdl() throws Exception {
        String migration = Files.readString(Path.of(
                "../docs/sql/backfill_general_model_leaf_sort.sql"));

        assertThat(migration).contains("general_leaf_null_sort_before");
        assertThat(migration).contains("general_leaf_rows_backfilled");
        assertThat(migration).contains("general_leaf_null_sort_after");
        assertThat(migration).contains("candidate.sort IS NULL");
        assertThat(migration).contains(
                "general_root.ai_model_config_id = @general_model_root_business_id");
        assertThat(migration).contains("MAX(explicit_order.sort)");
        assertThat(migration).contains("10 * (");
        assertThat(migration).contains("LEAST(10000,");
        assertThat(migration).contains("), 0), 0) + 10 * (");
        assertThat(migration).contains("leaf.update_time = leaf.update_time");
        assertThat(migration).contains("__ai_model_ordering_backfill_failed__");
        assertThat(migration).doesNotContain("ALTER TABLE", "CREATE TABLE", "DROP TABLE");
        assertThat(migration).doesNotContain("SET display_name", "SET visible");
    }

    @Test
    void continuousOrderingMigrationIsScopedVerifiedAndContainsNoDdl() throws Exception {
        String migration = Files.readString(Path.of(
                "../docs/sql/normalize_general_model_tree_sort.sql"));

        assertThat(migration).contains(
                "SET @general_model_root_business_id = 1970339278848659457");
        assertThat(migration).contains("START TRANSACTION", "FOR UPDATE");
        assertThat(migration).contains(
                "target_provider.sort = ranked_provider.normalized_sort");
        assertThat(migration).contains(
                "target_leaf.sort = ranked_leaf.normalized_sort");
        assertThat(migration).contains(
                "root.ai_model_config_id = @general_model_root_business_id");
        assertThat(migration).contains(
                "target_root.ai_model_config_id = @general_model_root_business_id");
        assertThat(migration).contains(
                "@provider_sort_invalid = 0",
                "@invalid_leaf_provider_count = 0",
                "@outside_sort_changed = 0");
        assertThat(migration).contains(
                "__general_model_tree_sort_normalization_failed__");
        assertThat(migration.indexOf("EXECUTE ordering_verify_stmt"))
                .isLessThan(migration.indexOf("\nCOMMIT;"));
        assertThat(migration).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "DROP TABLE",
                "TRUNCATE TABLE", "CREATE PROCEDURE");
        assertThat(migration).doesNotContain(
                "SET display_name", "SET visible", "SET api_key_ciphertext");
    }

    private String mapperStatement(String mapperSource, String methodName) {
        int methodIndex = mapperSource.indexOf(methodName + "(");
        if (methodIndex < 0) {
            throw new AssertionError("Mapper method not found: " + methodName);
        }
        int annotationIndex = Math.max(
                mapperSource.lastIndexOf("@Select", methodIndex),
                Math.max(
                        mapperSource.lastIndexOf("@Update", methodIndex),
                        mapperSource.lastIndexOf("@Delete", methodIndex)));
        int statementEnd = mapperSource.indexOf(';', methodIndex);
        if (annotationIndex < 0 || statementEnd < 0) {
            throw new AssertionError("Mapper statement not found: " + methodName);
        }
        return mapperSource.substring(annotationIndex, statementEnd + 1);
    }
}
